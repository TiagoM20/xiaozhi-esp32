"""Ligação LUMI CORE <-> HUB: AES-256-GCM fim a fim, sem credenciais no GitHub."""
import base64
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.request
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

AAD = b"LUMI-CORE-SYNC-V1"
APP_DIR = Path(os.environ.get("APPDATA", Path.home())) / "LumiCoreWindows"
SETTINGS_FILE = APP_DIR / "pairing.json"


def save_pairing(ip, key):
    """Windows DPAPI protege a chave de emparelhamento no perfil do utilizador."""
    if os.name != "nt":
        raise RuntimeError("A gravação segura requer Windows.")
    import win32crypt
    ip = ip.strip()
    key = key.strip().lower()
    validate(ip, key)
    protected = win32crypt.CryptProtectData(bytes.fromhex(key), "LUMI CORE", None, None, None, 0)
    APP_DIR.mkdir(parents=True, exist_ok=True)
    SETTINGS_FILE.write_text(json.dumps({
        "tablet_ip": ip,
        "pairing_dpapi": base64.b64encode(protected).decode("ascii")
    }, indent=2), encoding="utf-8")


def load_pairing():
    if not SETTINGS_FILE.exists():
        return "", ""
    try:
        import win32crypt
        data = json.loads(SETTINGS_FILE.read_text(encoding="utf-8"))
        raw = base64.b64decode(data["pairing_dpapi"], validate=True)
        key = win32crypt.CryptUnprotectData(raw, None, None, None, 0)[1].hex()
        ip = data.get("tablet_ip", "")
        validate(ip, key)
        return ip, key
    except Exception:
        return "", ""


def forget_pairing():
    if SETTINGS_FILE.exists():
        SETTINGS_FILE.unlink()


def validate(ip, key):
    parts = ip.split(".")
    if len(parts) != 4 or not all(p.isdecimal() and 0 <= int(p) <= 255 for p in parts):
        raise ValueError("Indica o endereço IPv4 do Blackview.")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", key):
        raise ValueError("O código de emparelhamento deve ter 64 caracteres hexadecimais.")


class LumiClient:
    def __init__(self, ip, key):
        validate(ip, key)
        self.ip = ip
        self.key = bytes.fromhex(key)

    def request(self, command, data=None):
        if command not in ("status", "logs", "configure"):
            raise ValueError("Comando não permitido.")
        nonce = os.urandom(12)
        sent_nonce = base64.b64encode(nonce).decode("ascii")
        message = {
            "ts": int(time.time()), "cmd": command
        }
        if data is not None:
            message["data"] = data
        encrypted = AESGCM(self.key).encrypt(
            nonce, json.dumps(message, ensure_ascii=False).encode("utf-8"), AAD
        )
        envelope = json.dumps({
            "n": sent_nonce,
            "d": base64.b64encode(encrypted).decode("ascii"),
        }).encode("ascii")
        req = urllib.request.Request(
            "http://" + self.ip + ":8770/lumi-core/v1",
            data=envelope,
            headers={"Content-Type": "application/json", "Cache-Control": "no-store"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=9) as response:
                if response.status != 200:
                    raise ConnectionError("O HUB recusou a operação.")
                body = response.read(262145)
                if len(body) > 262144:
                    raise ConnectionError("Resposta demasiado longa.")
        except urllib.error.HTTPError as error:
            if error.code == 403:
                raise ConnectionError("Emparelhamento inválido ou recusado. Verifica o código no tablet.") from None
            raise ConnectionError("Erro HTTP " + str(error.code)) from None
        except (OSError, TimeoutError) as error:
            raise ConnectionError("Sem ligação ao tablet. Verifica IP, Wi-Fi e se a ligação Windows está ativa.") from error
        response_envelope = json.loads(body.decode("utf-8"))
        plain = AESGCM(self.key).decrypt(
            base64.b64decode(response_envelope["n"], validate=True),
            base64.b64decode(response_envelope["d"], validate=True), AAD
        )
        response = json.loads(plain.decode("utf-8"))
        if response.get("request") != sent_nonce or response.get("cmd") != command or not response.get("ok"):
            raise ConnectionError("Resposta não autenticada ou inesperada.")
        return response.get("data", {})
