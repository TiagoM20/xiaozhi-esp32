#!/usr/bin/env python3
"""LUMI Desk Manager: audited, offline backup/install/restore for Waveshare ESP32-S3.
All FLASH WRITES require verified complete backup and an explicit confirmation.
The application doesn't upload any credentials or flash images.
"""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import struct
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from native_assets import merge as merge_native_assets, parse as inspect_assets

FLASH = 0x1000000
APP_OFFSET = 0x20000
APP_LENGTH = 0x3F0000
APP_OFFSETS = (0x20000, 0x410000)
OTA_DATA_OFFSET = 0xD000
OTA_DATA_LENGTH = 0x2000
ASSETS_OFFSET = 0x800000
ASSETS_LENGTH = 0x800000
EXPECTED_HASH = "01713cabee21dbe33344f05c1cb6b777b703f4c5080ade1567b189b01fe3ee78"
FW_NAME = "LUMI-NATIVE-HI-ESP-app.bin"
MODEL_NAME = "LUMI-NATIVE-HI-ESP-model-template.bin"
MODEL_HASH = "PENDING"


def resource(name):
    root = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
    return root / name


def get_fw():
    return resource(FW_NAME)


def check_app(data, want=None):
    if len(data) < 256 or data[0] != 0xE9:
        raise ValueError("Imagem ESP32 invalida.")
    if data[32:36] != struct.pack("<I", 0xABCD5432):
        raise ValueError("Descricao ESP-IDF ausente.")
    version = data[48:80].split(b"\0")[0].decode("ascii", "replace")
    name = data[80:112].split(b"\0")[0].decode("ascii", "replace")
    if name != "xiaozhi" or (want and version != want):
        raise ValueError("Imagem de firmware inesperada (projeto/versao).")
    return version


def check_fw():
    fw = get_fw()
    if not fw.is_file():
        raise ValueError("Firmware QUIET 2.5.3 nao foi incluido neste executavel.")
    data = fw.read_bytes()
    if not 1000000 < len(data) <= APP_LENGTH:
        raise ValueError("Tamanho do firmware invalido.")
    check_app(data, "2.5.3")
    if hashlib.sha256(data).hexdigest() != EXPECTED_HASH:
        raise ValueError("O firmware nao corresponde ao binario aprovado no GitHub.")
    template = resource(MODEL_NAME)
    if not template.is_file() or hashlib.sha256(template.read_bytes()).hexdigest() != MODEL_HASH:
        raise ValueError("Modelo WakeNet nativo ausente ou nao validado.")
    files, index = inspect_assets(template.read_bytes())
    if index.get("srmodels") != "srmodels.bin" or "srmodels.bin" not in files:
        raise ValueError("WakeNet nao encontrado no pacote nativo.")
    if "multinet_model" in index:
        raise ValueError("Modelo nativo ainda contem MultiNet; bloqueado.")
    return data


def select_ota(otadata):
    """Read-only ESP-IDF v6.1 selection. Never modify OTA metadata."""
    import zlib
    if len(otadata) != OTA_DATA_LENGTH:
        raise ValueError("OTA metadata incompleta.")
    records = []
    for i, sector in enumerate((0, 0x1000)):
        seq, _, state, crc = struct.unpack("<I20sII", otadata[sector:sector + 32])
        good_crc = crc == (zlib.crc32(struct.pack("<I", seq), 0xFFFFFFFF) & 0xFFFFFFFF)
        valid = seq not in (0, 0xFFFFFFFF) and state not in (3, 4) and good_crc
        records.append({"indice": i, "sequencia": seq, "estado": state,
                        "crc_valido": good_crc, "valido": valid})
    valid = [r for r in records if r["valido"]]
    if not valid:
        if otadata == b"\xff" * OTA_DATA_LENGTH:
            return {"slot": 0, "offset": APP_OFFSETS[0], "sequencia": None,
                    "estado": "inicial", "em_branco": True, "registos": records}
        raise ValueError("Dados OTA preenchidos mas sem CRC valido. Nenhuma gravacao permitida.")
    active = max(valid, key=lambda r: r["sequencia"])
    if active["estado"] not in (2, 0xFFFFFFFF):
        raise ValueError("OTA ativo NEW/PENDING. Por seguranca, instalacao bloqueada.")
    slot = (active["sequencia"] - 1) % len(APP_OFFSETS)
    return {"slot": slot, "offset": APP_OFFSETS[slot],
            "sequencia": active["sequencia"], "estado": active["estado"],
            "em_branco": False, "registos": records}


def parse_flash(data):
    if len(data) != FLASH:
        raise ValueError("Backup incompleto: sao necessarios exatamente 16 MB.")
    if data[0] != 0xE9:
        raise ValueError("Bootloader invalido ou dispositivo encriptado.")
    expected = {
        "nvs": (1, 2, 0x9000, 0x4000),
        "otadata": (1, 0, 0xd000, 0x2000),
        "phy_init": (1, 1, 0xf000, 0x1000),
        "ota_0": (0, 0x10, APP_OFFSET, APP_LENGTH),
        "ota_1": (0, 0x11, 0x410000, APP_LENGTH),
        "assets": (1, 0x82, ASSETS_OFFSET, ASSETS_LENGTH),
    }
    found = {}
    for i in range(96):
        start = 0x8000 + i * 32
        entry = data[start:start + 32]
        if entry == b"\xff" * 32:
            break
        if len(entry) != 32:
            raise ValueError("Tabela de particoes truncada.")
        magic, typ, sub, offset, length, rawname, flags = struct.unpack("<HBBII16sI", entry)
        if magic == 0xEBEB:
            break
        if magic != 0x50AA:
            raise ValueError("Tabela de particoes desconhecida.")
        name = rawname.split(b"\0", 1)[0].decode("ascii", "replace")
        if name in found:
            raise ValueError("Particao duplicada: " + name)
        found[name] = (typ, sub, offset, length)
    if found != expected:
        raise ValueError("Tabela de particoes diferente da LUMI validada. Nao gravar.")
    ota = select_ota(data[OTA_DATA_OFFSET:OTA_DATA_OFFSET + OTA_DATA_LENGTH])
    version = check_app(data[ota["offset"]:ota["offset"] + 512])
    if version not in ("2.5.1", "2.5.2", "2.5.3"):
        raise ValueError("Versao atual nao autorizada: " + version)
    count, checksum, length = struct.unpack_from("<III", data, ASSETS_OFFSET)
    if not 1 <= count <= 4096 or count * 44 > length or length > ASSETS_LENGTH - 12:
        raise ValueError("Formato de recursos (GIFs) nao reconhecido.")
    content = data[ASSETS_OFFSET + 12:ASSETS_OFFSET + 12 + length]
    unsigned = sum(content) & 65535
    signed = (unsigned - 256 * sum(v >= 128 for v in content)) & 65535
    if checksum not in (unsigned, signed):
        raise ValueError("Checksum dos recursos invalido.")
    files = {}
    for i in range(count):
        raw, size, offset, width, height = struct.unpack_from("<32sIIHH", content, i * 44)
        name = raw.split(b"\0", 1)[0].decode("utf-8", "replace")
        files[name] = (size, offset)
    if "index.json" not in files:
        raise ValueError("Indice de recursos nao encontrado.")
    size, offset = files["index.json"]
    begin = count * 44 + offset
    if begin + size + 2 > length or content[begin:begin + 2] != b"ZZ":
        raise ValueError("Indice de recursos incompatível.")
    index = json.loads(content[begin + 2:begin + 2 + size].decode("utf-8"))
    if not isinstance(index, dict) or index.get("version", 1) > 1:
        raise ValueError("Versao de assets nao suportada.")
    gifs = [name for name in files if name.lower().endswith(".gif")]
    return {
        "versao_atual": version,
        "particoes": [{"nome": name, "offset": hex(v[2]), "tamanho": hex(v[3])}
                      for name, v in expected.items()],
        "numero_de_recursos": count,
        "gifs_identificados": len(gifs),
        "nomes_dos_gifs": gifs[:30],
        "checksum_recursos": "OK",
        "otadata_em_branco": ota["em_branco"],
        "ota_ativo": "ota_" + str(ota["slot"]),
        "offset_ativo": hex(ota["offset"]),
        "estado_ota": str(ota["estado"]),
        "sequencia_ota": ota["sequencia"],
        "registos_ota": ota["registos"],
        "escrita_permitida_apenas_em": hex(ota["offset"]) + " - imagem de aplicacao",
        "observacao": "Backup contem credenciais e deve permanecer no PC."
    }


def backup_dir():
    root = Path(os.environ.get("USERPROFILE", str(Path.home()))) / "Documents" / "LUMI-DESK" / "Backups"
    root.mkdir(parents=True, exist_ok=True)
    return root


def esp_child(args):
    try:
        import esptool
        esptool.main(args)
        return 0
    except SystemExit as ex:
        return int(ex.code or 0) if isinstance(ex.code, (int, type(None))) else 1
    except Exception as ex:
        print("FALHA ESPTOOL:", str(ex), flush=True)
        return 1


class LumiManager:
    def __init__(self, master):
        self.master = master
        self.master.title("LUMI DESK | HI ESP Nativo 2.5.3")
        self.master.geometry("820x690")
        self.master.minsize(660, 580)
        self.master.configure(bg="#0C1321")
        self.events = queue.Queue()
        self.working = False
        self.last_backup = None

        top = tk.Frame(master, bg="#0C1321")
        top.pack(fill="x", padx=24, pady=(19, 10))
        tk.Label(top, text="⌂  LUMI DESK", bg="#0C1321", fg="#FFFFFF",
                 font=("Segoe UI Semibold", 23)).pack(anchor="w")
        tk.Label(top, text="WakeNet9 nativo  •  Hi ESP  •  Raquel PT-PT  •  Seguimento de conversa",
                 bg="#0C1321", fg="#B8C8DE", font=("Segoe UI", 10)).pack(anchor="w", pady=3)
        tk.Label(top, text="CÓPIA COMPLETA ANTES DE QUALQUER GRAVAÇÃO",
                 bg="#0C1321", fg="#FFC107", font=("Segoe UI Semibold", 10)).pack(anchor="w", pady=(10, 0))

        conn = tk.Frame(master, bg="#18253A")
        conn.pack(fill="x", padx=24, pady=10)
        tk.Label(conn, text="Porta USB da LUMI:", bg="#18253A", fg="#EAF4FF",
                 font=("Segoe UI", 10)).pack(side="left", padx=14, pady=17)
        self.port = tk.StringVar()
        self.portbox = ttk.Combobox(conn, textvariable=self.port, width=15, state="readonly")
        self.portbox.pack(side="left", padx=6)
        self.refresh_btn = tk.Button(conn, text="Detetar portas", command=self.refresh_ports,
                                     bg="#263D60", fg="white", relief="flat", padx=14, pady=7)
        self.refresh_btn.pack(side="left", padx=6)
        self.test_btn = tk.Button(conn, text="Testar ligação", command=self.test_connection,
                                  bg="#0565CF", fg="white", relief="flat", padx=14, pady=7)
        self.test_btn.pack(side="right", padx=12)

        operations = tk.Frame(master, bg="#0C1321")
        operations.pack(fill="x", padx=24, pady=10)
        self.backup_btn = self.op_button(operations, "1  ANALISAR E FAZER CÓPIA", "#0668E7",
                                         self.do_backup)
        self.backup_btn.pack(fill="x", pady=4)
        self.install_btn = self.op_button(operations, "2  INSTALAR HI ESP NATIVO 2.5.3", "#365274",
                                          self.do_install)
        self.install_btn.pack(fill="x", pady=4)
        self.restore_btn = self.op_button(operations, "3  REPOR UMA CÓPIA", "#365274",
                                          self.do_restore)
        self.restore_btn.pack(fill="x", pady=4)
        self.inspect_btn = self.op_button(operations,
                        "REANALISAR CÓPIA EXISTENTE (SEM LER A DESK)",
                        "#243B5B", self.do_inspect)
        self.inspect_btn.pack(fill="x", pady=4)

        tk.Label(master, text="Relatório e diagnóstico (sem publicação de dados)",
                 bg="#0C1321", fg="#EAF4FF", font=("Segoe UI Semibold", 11)).pack(
            anchor="w", padx=26, pady=(14, 4))
        container = tk.Frame(master, bg="#132034")
        container.pack(fill="both", expand=True, padx=24, pady=(0, 14))
        self.log = tk.Text(container, bg="#132034", fg="#DAE9F5",
                           font=("Consolas", 9), relief="flat", wrap="word", padx=12, pady=12)
        self.log.pack(fill="both", expand=True)
        self.log.configure(state="disabled")
        self.status = tk.StringVar(value="Pronto. Seleciona a porta da LUMI DESK.")
        tk.Label(master, textvariable=self.status, bg="#0C1321", fg="#B8C8DE",
                 font=("Segoe UI", 9), anchor="w").pack(fill="x", padx=25, pady=(0, 16))

        self.refresh_ports()
        try:
            check_fw()
            self.say("Firmware nativo Hi ESP e modelo validados por SHA256.")
        except Exception as err:
            self.say("ATENÇÃO: " + str(err))
        self.say("Sem esptool externo e sem Python instalado: ambas as ferramentas estão incluídas.")
        self.say("Os backups ficam em Documentos\\LUMI-DESK\\Backups e contêm dados privados.")
        self.master.after(100, self.drain)

    def op_button(self, parent, title, color, callback):
        return tk.Button(parent, text=title, command=callback, bg=color,
                         fg="#FFFFFF", activebackground="#0053B8",
                         font=("Segoe UI Semibold", 11), relief="flat", bd=0,
                         cursor="hand2", pady=11)

    def refresh_ports(self):
        from serial.tools import list_ports
        ports = [p.device for p in list_ports.comports()]
        self.portbox["values"] = ports
        if ports and (self.port.get() not in ports):
            self.port.set("COM6" if "COM6" in ports else ports[0])
        if not ports:
            self.port.set("")
            self.say("Nenhuma porta COM detetada. Liga a DESK por USB e carrega em Detetar portas.")

    def say(self, text):
        if threading.current_thread() is threading.main_thread():
            self.append(text)
        else:
            self.events.put(("log", str(text)))

    def append(self, text):
        self.log.configure(state="normal")
        self.log.insert("end", str(text) + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")

    def drain(self):
        while True:
            try:
                event, payload = self.events.get_nowait()
            except queue.Empty:
                break
            if event == "log":
                self.append(payload)
            elif event == "status":
                self.status.set(payload)
            elif event == "done":
                self.working = False
                self.lock_buttons(False)
                self.status.set(payload)
            elif event == "question":
                title, body, answer, wait = payload
                answer.append(messagebox.askyesno(title, body))
                wait.set()
            elif event == "error":
                messagebox.showerror("Operação interrompida", payload)
            elif event == "ok":
                messagebox.showinfo("LUMI DESK", payload)
        self.master.after(100, self.drain)

    def lock_buttons(self, working):
        for button in (self.backup_btn, self.install_btn, self.restore_btn,
                       self.refresh_btn, self.test_btn, self.inspect_btn):
            button.configure(state="disabled" if working else "normal")

    def start(self, action, arg=None):
        if self.working:
            return
        port = self.port.get().strip()
        if not re.fullmatch(r"COM[0-9]+", port, re.IGNORECASE):
            messagebox.showerror("Porta USB", "Liga a LUMI DESK e escolhe a porta COM.")
            return
        self.working = True
        self.lock_buttons(True)
        self.status.set("Operação em curso — não desligues o cabo USB.")
        threading.Thread(target=self.worker, args=(action, port, arg), daemon=True).start()

    def choose(self, title, body):
        wait = threading.Event()
        answer = []
        self.events.put(("question", (title, body, answer, wait)))
        wait.wait()
        return answer[0]

    def esp(self, *args):
        import esptool

        class OutputToGui:
            def __init__(self, emit):
                self.emit = emit
                self.parts = []
                self.all_lines = []
                self.encoding = "utf-8"

            def write(self, value):
                if not value:
                    return 0
                text = str(value).replace("\r", "\n")
                self.parts.append(text)
                buf = "".join(self.parts)
                chunks = buf.split("\n")
                self.parts = [chunks.pop()]
                for line in chunks:
                    line = line.strip()
                    if line:
                        self.all_lines.append(line)
                        self.emit(line)
                return len(value)

            def flush(self):
                if self.parts:
                    line = "".join(self.parts).strip()
                    if line:
                        self.all_lines.append(line)
                        self.emit(line)
                    self.parts = []

            def isatty(self):
                return False

        args = ["--chip", "esp32s3", "--port", self.current_port] + [str(x) for x in args]
        self.say("> esptool " + " ".join(args[4:8]))
        out = OutputToGui(self.say)
        previous_out, previous_err = sys.stdout, sys.stderr
        try:
            sys.stdout, sys.stderr = out, out
            try:
                esptool.main(args)
            except SystemExit as exc:
                if exc.code not in (0, None):
                    raise RuntimeError("Falha na comunicação ESP32: " + str(exc.code))
        finally:
            sys.stdout, sys.stderr = previous_out, previous_err
            out.flush()
        return "\n".join(out.all_lines)

    def mac(self):
        details = self.esp("read-mac")
        found = re.search(r"(?:MAC|MAC address):\s*([0-9a-f]{2}(?::[0-9a-f]{2}){5})",
                          details, flags=re.I)
        if not found:
            raise ValueError("Não foi possível confirmar o identificador da placa.")
        return found.group(1).lower()

    def acquire_backup(self, mac):
        directory = backup_dir() / (dt.datetime.now().strftime("%Y%m%d-%H%M%S-%f")
                                    + "-" + self.current_port.upper())
        directory.mkdir(parents=True, exist_ok=False)
        target = directory / "flash-integral-16MB.bin"
        self.say("A ler os 16 MB completos. Não está a escrever nada na placa.")
        self.esp("read-flash", "0x0", "0x1000000", target)
        data = target.read_bytes()
        result = parse_flash(data)
        result["sha256_backup"] = hashlib.sha256(data).hexdigest()
        result["tamanho_backup"] = len(data)
        result["mac"] = mac
        # Private metadata stays only beside the complete backup. Never upload.
        (directory / "metadata.json").write_text(
            json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
        public_result = {k: v for k, v in result.items()
                         if k not in ("mac", "sha256_backup")}
        (directory / "relatorio-sem-credenciais.json").write_text(
            json.dumps(public_result, indent=2, ensure_ascii=False), encoding="utf-8")
        self.say("BACKUP CONFIRMADO: " + str(target))
        self.say("Firmware atual: " + result["versao_atual"]
                 + " | " + result["ota_ativo"]
                 + " (" + result["offset_ativo"] + ")"
                 + " | GIFs na tabela: " + str(result["gifs_identificados"]))
        self.say("Recursos preservados. Guarda a cópia privada no teu PC.")
        return directory, target, result

    def verify_readback(self, expected_path, offset):
        expected = expected_path.read_bytes()
        verification = expected_path.parent / "verificacao-temporaria.bin"
        try:
            self.esp("--after", "no-reset", "read-flash", hex(offset), str(len(expected)), verification)
            if hashlib.sha256(verification.read_bytes()).digest() != hashlib.sha256(expected).digest():
                raise ValueError("A verificação da gravação falhou. Utiliza REPOSIÇÃO.")
            self.say("Verificação dos bytes gravados: OK.")
        finally:
            verification.unlink(missing_ok=True)

    def verify_ota_unchanged(self, backup_path):
        """A post-backup check to avoid writing into a slot after OTA selection changes."""
        backup_path = Path(backup_path)
        data = backup_path.read_bytes()
        saved = data[OTA_DATA_OFFSET:OTA_DATA_OFFSET + OTA_DATA_LENGTH]
        temp = backup_path.parent / "otadata-verificacao-temporaria.bin"
        try:
            self.esp("read-flash", hex(OTA_DATA_OFFSET), hex(OTA_DATA_LENGTH), temp)
            if temp.read_bytes() != saved:
                raise ValueError("OTA mudou desde a copia de seguranca. "
                                 "Nenhuma escrita autorizada. Faz um novo backup.")
            self.say("OTA revalidado: partição ativa não mudou.")
        finally:
            temp.unlink(missing_ok=True)

    def do_inspect(self):
        path = filedialog.askopenfilename(
            title="Reanalisar backup integral que já foi guardado",
            initialdir=str(backup_dir()),
            filetypes=[("Imagem flash integral", "*.bin")])
        if not path:
            return
        try:
            p = Path(path)
            if p.name != "flash-integral-16MB.bin":
                raise ValueError("Escolhe flash-integral-16MB.bin.")
            report = parse_flash(p.read_bytes())
            report_path = p.parent / "relatorio-sem-credenciais.json"
            report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2),
                                   encoding="utf-8")
            self.say("Relatório de cópia existente: " + str(report_path))
            self.say("Versão: " + report["versao_atual"] + " | " + report["ota_ativo"]
                     + " | " + report["offset_ativo"])
            messagebox.showinfo("Backup analisado",
                                "Backup compatível e relatório guardado.\n\n"
                                + report["ota_ativo"] + " · " + report["versao_atual"]
                                + "\n\n" + str(report_path))
        except Exception as ex:
            messagebox.showerror("Backup não validado", str(ex))

    def restore_both_from_backup(self, source, report):
        """Restore app+original assets after failure or by explicit request."""
        source = Path(source)
        original = source.read_bytes()
        if len(original) != FLASH:
            raise ValueError("Backup nao e uma imagem flash integral de 16MB.")
        ota = select_ota(original[OTA_DATA_OFFSET:OTA_DATA_OFFSET+OTA_DATA_LENGTH])
        offset = int(report["offset_ativo"], 16)
        if ota["offset"] != offset:
            raise ValueError("Slot OTA do backup nao corresponde ao relatorio.")
        app = source.parent / "app-original-para-recuperacao.bin"
        app.write_bytes(original[offset:offset+APP_LENGTH])
        part = original[ASSETS_OFFSET:ASSETS_OFFSET+ASSETS_LENGTH]
        files, index = inspect_assets(part)
        orig_len = struct.unpack_from("<I",part,8)[0]
        assets = source.parent / "assets-originais-para-recuperacao.bin"
        assets.write_bytes(part[:12+orig_len])
        self.say("A recuperar todos os GIFs e o modelo originais.")
        self.esp("--after","no-reset","write-flash",hex(ASSETS_OFFSET),assets)
        self.verify_readback(assets,ASSETS_OFFSET)
        self.say("A recuperar a aplicação original.")
        self.esp("--after","no-reset","write-flash",hex(offset),app)
        self.verify_readback(app,offset)
        self.say("Recuperação completa verificada: app + assets.")

    def worker(self, action, port, arg):
        self.current_port = port
        try:
            check_fw()
            self.say("\n--- " + action.upper() + " | " + port + " ---")
            self.esp("flash-id")
            mac = self.mac()
            if action == "test":
                self.events.put(("ok", "LUMI DESK detetada em " + port + "."))
            elif action in ("backup", "install"):
                folder, source, report = self.acquire_backup(mac)
                self.last_backup = str(source)
                if action == "backup":
                    self.events.put(("ok", "Cópia e diagnóstico concluídos.\n\n"
                                     + str(folder)
                                     + "\n\nNão foi feita qualquer gravação."))
                else:
                    fw = get_fw()
                    self.say("A combinar WakeNet nativo com os GIFs originais.")
                    old = source.read_bytes()
                    old_assets = old[ASSETS_OFFSET:ASSETS_OFFSET+ASSETS_LENGTH]
                    new_assets, changes = merge_native_assets(
                        old_assets, resource(MODEL_NAME).read_bytes())
                    merged_path = folder / "assets-com-wakenet-e-gifs-originais.bin"
                    merged_path.write_bytes(new_assets)
                    (folder / "verificacao-recursos.json").write_text(
                        json.dumps(changes,ensure_ascii=False,indent=2),encoding="utf-8")
                    self.say("GIFs preservados: " + str(changes["gifs_preservados"]))
                    self.say("Restantes recursos preservados: " + str(changes["recursos_preservados"]))
                    if not self.choose("Instalação nativa Hi ESP",
                        "Backup integral confirmado.\n"
                        + str(changes["gifs_preservados"]) + " GIFs preservados sem alterações.\n\n"
                        "Wake word: Hi ESP (WakeNet9 nativo).\n"
                        "Raquel continua PT-PT, com seguimento durante cerca de 15s.\n\n"
                        "ATENÇÃO: esta instalação grava DUAS partições:\n"
                        "• App: " + report["ota_ativo"] + " (" + report["offset_ativo"] + ")\n"
                        "• Recursos: assets (0x800000), com os GIFs originais.\n"
                        "Existe backup integral para repor ambas.\n\n"
                        "Confirmas a instalação experimental?"):
                        self.say("Instalação cancelada: nada gravado.")
                    else:
                        offset = int(report["offset_ativo"],16)
                        self.verify_ota_unchanged(source)
                        try:
                            self.say("Fase 1/2: escrever assets repacotados com WakeNet.")
                            self.esp("--after","no-reset","write-flash",hex(ASSETS_OFFSET),merged_path)
                            self.verify_readback(merged_path,ASSETS_OFFSET)
                            self.say("Fase 2/2: atualizar aplicação.")
                            self.esp("--after","no-reset","write-flash",hex(offset),fw)
                            self.verify_readback(fw,offset)
                        except Exception as error:
                            self.say("Gravação interrompida: " + str(error))
                            try:
                                self.restore_both_from_backup(source,report)
                            except Exception as rollback:
                                raise RuntimeError("RECUPERAÇÃO NÃO CONFIRMADA: "+str(rollback)
                                                   +". NÃO desligues USB. Backup: "+str(source))
                            raise RuntimeError("Instalação falhou mas backup app+assets foi reposto.")
                        self.events.put(("ok",
                            "Hi ESP instalado e verificado.\n"
                            "Reinicia a DESK e diz 'Hi ESP'.\n"
                            "Depois de responder, Raquel continua a escutar."))
            elif action == "restore":
                original = Path(arg)
                if original.name != "flash-integral-16MB.bin":
                    raise ValueError("Seleciona uma cópia criada pelo gestor LUMI.")
                data = original.read_bytes()
                report = parse_flash(data)
                meta_path = original.parent / "metadata.json"
                if not meta_path.exists():
                    raise ValueError("Metadados da cópia em falta.")
                meta = json.loads(meta_path.read_text(encoding="utf-8"))
                if meta.get("mac") != mac or meta.get("sha256_backup") != hashlib.sha256(data).hexdigest():
                    raise ValueError("A cópia não pertence a esta placa ou foi alterada.")
                if not self.choose("Recuperação completa",
                    "Repor a aplicação " + report["versao_atual"] + " e todos os assets?\n"
                    "• Aplicação original: " + report["ota_ativo"] + "\n"
                    "• GIFs e modelo originais: 0x800000\n"
                    "Wi-Fi e definições NVS ficam intactas.\n\n"
                    "Confirma a recuperação completa?"):
                    self.say("Reposição cancelada.")
                else:
                    self.verify_ota_unchanged(original)
                    self.restore_both_from_backup(original, report)
                    self.events.put(("ok","Aplicação e todos os assets restaurados.\n"
                                     "Reinicia a LUMI DESK."))
            else:
                raise ValueError("Operação desconhecida.")
            self.events.put(("done", "Operação terminada."))
        except Exception as exc:
            self.say("OPERAÇÃO BLOQUEADA: " + str(exc))
            self.say("Não usar erase-flash. Não partilhar os backups privados.")
            self.events.put(("error", str(exc)))
            self.events.put(("done", "Interrompido com segurança; consulta o relatório."))

    def test_connection(self):
        self.start("test")

    def do_backup(self):
        self.start("backup")

    def do_install(self):
        self.start("install")

    def do_restore(self):
        file = filedialog.askopenfilename(
            title="Escolhe o backup integral desta DESK",
            initialdir=str(backup_dir()),
            filetypes=[("Backup integral LUMI", "flash-integral-16MB.bin"),
                       ("Imagens BIN", "*.bin")])
        if file:
            self.start("restore", file)


def main():
    root = tk.Tk()
    try:
        ttk.Style().theme_use("clam")
    except tk.TclError:
        pass
    LumiManager(root)
    root.mainloop()


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--self-test":
        import esptool
        from serial.tools import list_ports
        output = {
            "candidate_hash_ok": bool(check_fw()),
            "version": check_app(check_fw(), "2.5.3"),
            "esptool_loaded": bool(esptool),
            "serial_loaded": bool(list_ports),
            "tkinter_loaded": bool(tk),
        }
        Path(sys.argv[2]).write_text(json.dumps(output, indent=2), encoding="utf-8")
    else:
        main()
