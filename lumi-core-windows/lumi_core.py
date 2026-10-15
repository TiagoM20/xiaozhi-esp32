"""LUMI CORE Windows: controlo nativo da central Blackview por LAN."""
import queue
import threading
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
from tkinter.scrolledtext import ScrolledText
from core_client import LumiClient, load_pairing, save_pairing, forget_pairing, diagnose_port

VERSION = "0.1.1"
THEMES = ["LUMI Azul", "Escuro", "Claro", "Colorido", "Aurora",
          "Areia", "Floresta", "Rosa Névoa", "Terracota", "Minimal"]
BG, CARD, TEXT, MUTED, ACCENT = "#101724", "#1d293b", "#edf4ff", "#b1c3d7", "#72e4ef"


class LumiCore(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("LUMI CORE · Windows")
        self.geometry("930x715")
        self.minsize(770, 580)
        self.configure(bg=BG)
        self.client = None
        self.events = queue.Queue()
        self.logs = ""
        self.status_message = tk.StringVar(value="Desligado · Emparelha o Blackview para começar.")
        self._style()
        self._build()
        ip, key = load_pairing()
        self.pair_ip.insert(0, ip)
        if ip and key:
            self.client = LumiClient(ip, key)
            self._run_job("A verificar o Blackview…", self.client_status, self._show_status)
        self.after(100, self._process_events)

    def _style(self):
        st = ttk.Style(self)
        st.theme_use("clam")
        st.configure("TNotebook", background=BG, borderwidth=0)
        st.configure("TNotebook.Tab", background=CARD, foreground=TEXT, padding=(18, 11),
                     font=("Segoe UI", 10))
        st.map("TNotebook.Tab", background=[("selected", "#29455e")],
               foreground=[("selected", "#ffffff")])
        st.configure("TCombobox", fieldbackground=CARD, background=CARD,
                     foreground=TEXT, selectforeground=TEXT)
        st.configure("TProgressbar", background=ACCENT, troughcolor=CARD)

    def _label(self, parent, text, size=11, color=TEXT, bold=False):
        label = tk.Label(parent, text=text, font=("Segoe UI", size, "bold" if bold else "normal"),
                         fg=color, bg=parent.cget("bg"), anchor="w", justify="left")
        label.pack(fill="x", pady=(5, 4))
        return label

    def _field(self, parent, title, default="", secret=False):
        self._label(parent, title, 10, MUTED)
        entry = tk.Entry(parent, font=("Consolas", 11), fg=TEXT, bg="#28364b",
                         insertbackground=TEXT, relief="flat", highlightthickness=1,
                         highlightbackground="#374c61", highlightcolor=ACCENT,
                         show="*" if secret else "")
        entry.pack(fill="x", ipady=8, pady=(0, 5))
        if default:
            entry.insert(0, default)
        return entry

    def _button(self, parent, title, callback, accent=False):
        b = tk.Button(parent, text=title, command=callback, font=("Segoe UI", 10, "bold"),
                      fg="#0b1822" if accent else TEXT, bg=ACCENT if accent else "#34445a",
                      relief="flat", activebackground="#a2ecf2", cursor="hand2",
                      padx=18, pady=10)
        b.pack(anchor="w", pady=(8, 5))
        return b

    def _panel(self, parent):
        panel = tk.Frame(parent, bg=CARD, padx=20, pady=17)
        panel.pack(fill="x", padx=18, pady=12)
        return panel

    def _page(self):
        p = tk.Frame(self.tabs, bg=BG)
        return p

    def _build(self):
        top = tk.Frame(self, bg=BG, padx=24, pady=14)
        top.pack(fill="x")
        tk.Label(top, text="LUMI CORE", fg=TEXT, bg=BG,
                 font=("Segoe UI", 24, "bold")).pack(side="left")
        tk.Label(top, text="Windows · v" + VERSION, fg=MUTED, bg=BG,
                 font=("Segoe UI", 10)).pack(side="right", pady=12)
        self.tabs = ttk.Notebook(self)
        self.tabs.pack(fill="both", expand=True, padx=14, pady=4)
        self.page_link = self._page()
        self.page_voice = self._page()
        self.page_devices = self._page()
        self.page_logs = self._page()
        self.tabs.add(self.page_link, text="Ligação")
        self.tabs.add(self.page_voice, text="Voz e IA")
        self.tabs.add(self.page_devices, text="Dispositivos")
        self.tabs.add(self.page_logs, text="Diagnóstico")
        self._build_link()
        self._build_voice()
        self._build_devices()
        self._build_logs()
        footer = tk.Frame(self, bg=BG, padx=24, pady=8)
        footer.pack(fill="x")
        tk.Label(footer, textvariable=self.status_message, fg=ACCENT, bg=BG,
                 font=("Segoe UI", 10), anchor="w").pack(fill="x")

    def _build_link(self):
        card = self._panel(self.page_link)
        self._label(card, "Ligação ao Blackview Tab 15", 17, TEXT, True)
        self._label(card, "O HUB mantém a central e a DESK a funcionar mesmo com o PC desligado.",
                    10, MUTED)
        self.pair_ip = self._field(card, "IP local do Blackview (ex.: 192.168.1.120)")
        self.pair_secret = self._field(card, "Código privado copiado do HUB (64 caracteres)", secret=True)
        row = tk.Frame(card, bg=CARD)
        row.pack(fill="x")
        self._button(row, "Emparelhar e verificar", self.pair_and_test, True)
        self._button(row, "Verificar ligação guardada", self.refresh_status)
        self._button(row, "Diagnosticar rede", self.diagnose_connection)
        self._button(row, "Esquecer este PC", self.forget)
        self._label(card, "No tablet: LUMI DESK → Configurações → Emparelhar LUMI CORE Windows."
                    "\nAtiva a ligação, copia o código e introduz o IP nesta janela."
                    "\nA chave de emparelhamento é protegida pelo Windows DPAPI.", 10, MUTED)
        info = self._panel(self.page_link)
        self._label(info, "Estado da central", 16, TEXT, True)
        self.state_text = tk.StringVar(value="Ainda não ligado.")
        tk.Label(info, textvariable=self.state_text, bg=CARD, fg=TEXT, font=("Consolas", 11),
                 justify="left", anchor="nw").pack(fill="x", pady=8)

    def _build_voice(self):
        card = self._panel(self.page_voice)
        self._label(card, "Raquel Neural · Português de Portugal", 17, TEXT, True)
        self._label(card, "As chaves novas são enviadas cifradas ao Blackview."
                    "\nCampos de chave vazios preservam as credenciais já configuradas.", 10, MUTED)
        self.region = self._field(card, "Região Azure Speech", "northeurope")
        self.azure_key = self._field(card, "Nova chave Azure Speech (opcional)", secret=True)
        self.model = self._field(card, "Modelo OpenAI", "gpt-4.1-mini")
        self.openai_key = self._field(card, "Nova chave OpenAI API (opcional)", secret=True)
        self._button(card, "Guardar Voz e IA no HUB", self.push_voice, True)
        self._label(card, "Nunca é possível ler as chaves existentes de volta do HUB. "
                    "O Windows mostra apenas se estão configuradas.", 10, MUTED)

    def _build_devices(self):
        card = self._panel(self.page_devices)
        self._label(card, "Dispositivos e aparência", 17, TEXT, True)
        self.tv_ip = self._field(card, "IP da TV da Sala")
        self.tv_mac = self._field(card, "MAC da TV da Sala (AA:BB:CC:DD:EE:FF)")
        self._label(card, "Tema do HUB", 10, MUTED)
        self.theme = ttk.Combobox(card, values=THEMES, state="readonly", width=34,
                                  font=("Segoe UI", 11))
        self.theme.set(THEMES[0])
        self.theme.pack(fill="x", ipady=6, pady=8)
        self._button(card, "Guardar dispositivos e tema no HUB", self.push_devices, True)
        self._label(card, "Alterar o IP da TV elimina o emparelhamento webOS antigo. "
                    "Esta versão não altera o firmware nem a configuração interna da DESK.", 10, MUTED)

    def _build_logs(self):
        card = self._panel(self.page_logs)
        self._label(card, "Diagnóstico da LUMI DESK", 17, TEXT, True)
        self._label(card, "Eventos, erros e conversas guardadas pelo Blackview. "
                    "O histórico não é enviado automaticamente para o GitHub.", 10, MUTED)
        controls = tk.Frame(card, bg=CARD)
        controls.pack(fill="x")
        for title, fn in (("Atualizar logs", self.refresh_logs),
                          ("Copiar logs", self.copy_logs),
                          ("Guardar .txt", self.save_logs)):
            tk.Button(controls, text=title, command=fn, font=("Segoe UI", 10),
                      fg=TEXT, bg="#34445a", relief="flat", padx=16, pady=8).pack(
                          side="left", padx=(0, 8), pady=7)
        self.log_text = ScrolledText(self.page_logs, wrap="word", bg="#0d1827", fg="#d8f4e7",
                                    insertbackground=TEXT, font=("Consolas", 10),
                                    relief="flat", height=16)
        self.log_text.pack(fill="both", expand=True, padx=18, pady=(0, 14))

    def _process_events(self):
        try:
            while True:
                success, title, callback, payload = self.events.get_nowait()
                if success:
                    self.status_message.set(title)
                    try:
                        callback(payload)
                    except Exception as error:
                        messagebox.showerror("LUMI CORE", str(error))
                else:
                    self.status_message.set("Erro · " + str(payload))
                    messagebox.showerror("LUMI CORE", str(payload))
        except queue.Empty:
            pass
        self.after(130, self._process_events)

    def _run_job(self, message, job, done):
        self.status_message.set(message)
        def work():
            try:
                self.events.put((True, "Concluído · " + message, done, job()))
            except Exception as error:
                self.events.put((False, "", None, str(error)))
        threading.Thread(target=work, daemon=True).start()

    def _require_client(self):
        if self.client is None:
            raise RuntimeError("Emparelha primeiro o Blackview na aba Ligação.")
        return self.client

    def client_status(self):
        return self._require_client().request("status")

    def pair_and_test(self):
        ip = self.pair_ip.get().strip()
        secret = self.pair_secret.get().strip()
        # Permite verificar nova ligação com código previamente protegido neste PC.
        if not secret and self.client and ip == self.client.ip:
            secret = self.client.key.hex()
        try:
            client = LumiClient(ip, secret)
        except Exception as error:
            messagebox.showerror("Emparelhamento", str(error))
            return
        def job():
            status = client.request("status")
            save_pairing(ip, secret)
            return client, status
        def on_done(value):
            self.client, status = value
            self.pair_secret.delete(0, "end")
            self._show_status(status)
            messagebox.showinfo("LUMI CORE", "Blackview emparelhado. Ligação cifrada confirmada.")
        self._run_job("A emparelhar e verificar…", job, on_done)

    def _show_status(self, status):
        if not isinstance(status, dict):
            return
        self.region.delete(0, "end")
        self.region.insert(0, status.get("azure_region", "northeurope"))
        self.model.delete(0, "end")
        self.model.insert(0, status.get("openai_model", "gpt-4.1-mini"))
        self.tv_ip.delete(0, "end")
        self.tv_ip.insert(0, status.get("tv_ip", ""))
        self.tv_mac.delete(0, "end")
        self.tv_mac.insert(0, status.get("tv_mac", ""))
        index = status.get("theme", 0)
        self.theme.set(THEMES[index] if isinstance(index, int) and 0 <= index < len(THEMES) else THEMES[0])
        yes = lambda value: "Sim" if value else "Não"
        self.state_text.set(
            "HUB Android: " + status.get("version", "?") + "\n" +
            "IP do tablet: " + status.get("tablet_ip", "?") + "\n" +
            "Central ativa: " + yes(status.get("central_enabled")) + "\n" +
            "Ponte DESK ativa: " + yes(status.get("bridge_enabled")) + "\n" +
            "Estado da DESK: " + status.get("bridge_state", "?") + "\n" +
            "Azure configurado: " + yes(status.get("azure_configured")) + "\n" +
            "OpenAI configurado: " + yes(status.get("openai_configured")) + "\n" +
            "MCP configurado: " + yes(status.get("mcp_configured"))
        )

    def diagnose_connection(self):
        ip = self.pair_ip.get().strip()
        if not ip:
            messagebox.showerror("LUMI CORE", "Indica primeiro o IP do Blackview.")
            return
        def finished(report):
            self.state_text.set(report)
            try:
                self.clipboard_clear()
                self.clipboard_append(report)
                self.status_message.set("Diagnóstico copiado para a área de transferência.")
            except Exception:
                self.status_message.set("Diagnóstico concluído.")
            messagebox.showinfo("Diagnóstico de rede", report + "\\n\\nPodes colar este resultado no ChatGPT. Não inclui chaves.")
        self._run_job("A testar ligação direta ao tablet…",
                      lambda: diagnose_port(ip), finished)

    def refresh_status(self):
        if not self.client:
            messagebox.showinfo("LUMI CORE", "Emparelha primeiro o Blackview.")
            return
        self._run_job("A consultar o estado do HUB…", self.client_status, self._show_status)

    def forget(self):
        if not messagebox.askyesno("LUMI CORE", "Esquecer a ligação guardada neste PC?"):
            return
        forget_pairing()
        self.client = None
        self.pair_secret.delete(0, "end")
        self.pair_ip.delete(0, "end")
        self.state_text.set("Emparelhamento removido do Windows.")
        self.status_message.set("Chave de emparelhamento apagada deste PC.")

    def _push(self, payload, label, clear_keys=False):
        if not self.client:
            messagebox.showinfo("LUMI CORE", "Emparelha primeiro o Blackview.")
            return
        def job():
            result = self.client.request("configure", payload)
            return result
        def on_done(data):
            if clear_keys:
                self.azure_key.delete(0, "end")
                self.openai_key.delete(0, "end")
            messagebox.showinfo("LUMI CORE", data.get("message", "Configuração guardada."))
            self.refresh_status()
        self._run_job(label, job, on_done)

    def push_voice(self):
        region = self.region.get().strip().lower()
        model = self.model.get().strip()
        if not region.isalnum() or not model:
            messagebox.showerror("LUMI CORE", "Confirma a região e o modelo.")
            return
        data = {"azure_region": region, "openai_model": model}
        if self.azure_key.get().strip():
            data["azure_key"] = self.azure_key.get().strip()
        if self.openai_key.get().strip():
            data["openai_key"] = self.openai_key.get().strip()
        self._push(data, "A enviar Voz/IA cifrados ao tablet…", True)

    def push_devices(self):
        ip = self.tv_ip.get().strip()
        mac = self.tv_mac.get().strip().upper()
        if ip and (len(ip.split(".")) != 4 or not all(
                part.isdecimal() and 0 <= int(part) <= 255 for part in ip.split("."))):
            messagebox.showerror("LUMI CORE", "IP da TV inválido.")
            return
        if mac:
            import re
            if not re.fullmatch(r"([0-9A-F]{2}:){5}[0-9A-F]{2}", mac):
                messagebox.showerror("LUMI CORE", "MAC da TV inválido.")
                return
        data = {"tv_ip": ip, "tv_mac": mac, "theme": THEMES.index(self.theme.get())}
        self._push(data, "A sincronizar dispositivos e tema…")

    def refresh_logs(self):
        if not self.client:
            messagebox.showinfo("LUMI CORE", "Emparelha primeiro o Blackview.")
            return
        def done(result):
            self.logs = result.get("text", "")
            self.log_text.delete("1.0", "end")
            self.log_text.insert("1.0", self.logs)
        self._run_job("A receber os logs cifrados…", lambda: self.client.request("logs"), done)

    def copy_logs(self):
        if not self.logs:
            messagebox.showinfo("LUMI CORE", "Atualiza primeiro os logs.")
            return
        self.clipboard_clear()
        self.clipboard_append(self.logs)
        self.status_message.set("Logs copiados. Revê dados pessoais antes de partilhar.")

    def save_logs(self):
        if not self.logs:
            messagebox.showinfo("LUMI CORE", "Atualiza primeiro os logs.")
            return
        path = filedialog.asksaveasfilename(title="Guardar diagnóstico LUMI",
                defaultextension=".txt", initialfile="LUMI-DESK-Diagnostico.txt",
                filetypes=[("Ficheiro de texto", "*.txt")])
        if path:
            with open(path, "w", encoding="utf-8") as f:
                f.write(self.logs)
            self.status_message.set("Diagnóstico guardado em " + path)


if __name__ == "__main__":
    LumiCore().mainloop()
