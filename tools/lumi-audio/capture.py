#!/usr/bin/env python3
"""LUMI DESK: safe serial log collector for existing or diagnostic ESP32 firmware.

Usage (Windows):
    py -m pip install pyserial
    py tools/lumi-audio/capture.py --port COM6 --seconds 180

Records only selected technical status lines. Never logs complete Wi-Fi setup,
MCP endpoints, JSON messages, token data or speech transcripts.
"""
import argparse
import re
import sys
import time
from pathlib import Path

ALLOWED = (
    "LUMI_AUDIO_DIAG",
    "Failed to decode audio",
    "Audio decoder is not configured",
    "Encode queue is full",
    "Audio output task stopped",
    "Failed to create audio decoder",
)
ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


def allowed_line(raw: str) -> str | None:
    clean = ANSI.sub("", raw).strip()
    if not any(marker in clean for marker in ALLOWED):
        return None
    # Defense in depth: never leak URLs/tokens even if a library appends one.
    if any(secret in clean.lower() for secret in (
        "token=", "authorization:", "password=", "ssid=", "wss://", "https://"
    )):
        return None
    return clean[:400]


def main() -> int:
    p = argparse.ArgumentParser(description="Captura filtrada de erros de áudio LUMI")
    p.add_argument("--port", required=True, help="Porta série (por exemplo COM6)")
    p.add_argument("--seconds", type=int, default=180)
    p.add_argument("--out", default="LUMI-audio-diagnostico.txt")
    args = p.parse_args()
    if not (10 <= args.seconds <= 600):
        p.error("--seconds deve estar entre 10 e 600")
    try:
        import serial
    except ImportError:
        print("Instala: py -m pip install pyserial", file=sys.stderr)
        return 2

    destination = Path(args.out)
    print("A capturar linhas técnicas filtradas. Nenhum texto da conversa será guardado.")
    print(f"Porta {args.port}; {args.seconds} segundos. Repete a contagem 1 a 10.")
    count = 0
    try:
        with serial.Serial(args.port, 115200, timeout=0.5) as device, destination.open(
            "w", encoding="utf-8"
        ) as file:
            started = time.monotonic()
            while time.monotonic() - started < args.seconds:
                line = device.readline().decode("utf-8", "replace")
                matched = allowed_line(line)
                if matched:
                    stamp = f"{time.monotonic()-started:07.1f}s"
                    print(stamp, matched, flush=True)
                    file.write(f"{stamp} {matched}\n")
                    file.flush()
                    count += 1
    except KeyboardInterrupt:
        print("Captura interrompida pelo utilizador.")
    except Exception as e:
        print(f"Falha a abrir/ler a porta série: {type(e).__name__}: {e}", file=sys.stderr)
        return 1

    print(f"Captura terminada: {destination} ({count} linhas).")
    if count == 0:
        print("Nenhuma linha correspondente. Isto NAO confirma que o áudio está correto: "
              "o firmware atual pode não ter ainda os contadores de diagnóstico.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
