#!/usr/bin/env python3
"""LUMI DESK: gerar um teste de pronúncia PT-PT sem tocar no firmware.

Edge TTS:  python voice_test.py --provider edge --output demo.mp3
Azure:     SPEECH_KEY=... SPEECH_REGION=... python voice_test.py --provider azure
Chaves secretas são lidas apenas de variáveis de ambiente, nunca de argumentos.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from xml.sax.saxutils import escape

VOICES = ("pt-PT-RaquelNeural", "pt-PT-FernandaNeural")
DEFAULT_TEXT = (
    "Olá! Eu sou a Lumi. Estou muito contente por poder conversar contigo "
    "em português de Portugal. Hoje está um dia bonito em Rebordosa. "
    "Se precisares, posso ajudar-te a desligar a televisão da sala, "
    "contar uma história ao Leonardo ou lembrar-te de uma tarefa. "
    "Vamos preparar o pequeno-almoço e começar o dia com um sorriso? "
    "Às dezasseis horas temos tempo para uma nova aventura."
)

def build_ssml(text: str, voice: str = VOICES[0]) -> bytes:
    if voice not in VOICES:
        raise ValueError("Voz não autorizada para o teste PT-PT.")
    if not text.strip() or len(text) > 1000:
        raise ValueError("O texto deve ter entre 1 e 1000 caracteres.")
    payload = (
        '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" '
        'xml:lang="pt-PT">'
        f'<voice name="{voice}" xml:lang="pt-PT">{escape(text)}</voice>'
        '</speak>'
    )
    return payload.encode("utf-8")

def validate_mp3(data: bytes) -> None:
    # Magic-only test: does not prove decoding quality, latency or pronunciation.
    if len(data) < 1024 or not (
        data.startswith(b"ID3") or (data[0] == 0xFF and data[1] & 0xE0 == 0xE0)
    ):
        raise ValueError("O serviço não devolveu um MP3 válido (assinatura/tamanho).")

def azure_tts(text: str, voice: str, *, key: str, region: str) -> bytes:
    if not key or not region:
        raise ValueError("É preciso configurar SPEECH_KEY e SPEECH_REGION.")
    if not re.fullmatch(r"[a-z0-9-]{2,40}", region):
        raise ValueError("SPEECH_REGION inválida.")
    url = f"https://{region}.tts.speech.microsoft.com/cognitiveservices/v1"
    request = urllib.request.Request(
        url,
        data=build_ssml(text, voice),
        headers={
            "Ocp-Apim-Subscription-Key": key,
            "Content-Type": "application/ssml+xml",
            "X-Microsoft-OutputFormat": "audio-24khz-48kbitrate-mono-mp3",
            "User-Agent": "LUMI-Voice-Lab",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            data = response.read(6_000_001)
        if len(data) > 6_000_000:
            raise ValueError("Resposta de áudio demasiado grande.")
    except urllib.error.HTTPError as exc:
        # Não escrever cabeçalhos, token ou detalhes privados da resposta.
        raise RuntimeError(f"Azure Speech devolveu HTTP {exc.code}.") from None
    except urllib.error.URLError:
        raise RuntimeError("Falha de ligação ao Azure Speech.") from None
    validate_mp3(data)
    return data

def edge_tts(text: str, voice: str, output: Path) -> None:
    if voice not in VOICES:
        raise ValueError("Voz não autorizada.")
    result = subprocess.run(
        [sys.executable, "-m", "edge_tts",
         "--voice", voice, "--text", text,
         "--write-media", str(output)],
        capture_output=True, text=True, timeout=90,
        check=False,
    )
    if result.returncode:
        # Saída do prestador pode conter detalhes da ligação; não expor.
        raise RuntimeError("EdgeTTS não conseguiu sintetizar a fala.")
    validate_mp3(output.read_bytes())

def main() -> int:
    p = argparse.ArgumentParser(description="Demonstração de voz LUMI PT-PT")
    p.add_argument("--provider", choices=("edge", "azure"), default="edge")
    p.add_argument("--voice", choices=VOICES, default=VOICES[0])
    p.add_argument("--output", default="lumi-raquel-ptpt.mp3")
    p.add_argument("--text", default=DEFAULT_TEXT)
    args = p.parse_args()
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    try:
        if args.provider == "edge":
            edge_tts(args.text, args.voice, out)
        else:
            data = azure_tts(args.text, args.voice,
                             key=os.getenv("SPEECH_KEY", ""),
                             region=os.getenv("SPEECH_REGION", ""))
            out.write_bytes(data)
    except Exception as exc:
        out.unlink(missing_ok=True)
        print(f"Erro no teste PT-PT: {exc}", file=sys.stderr)
        return 1
    print(f"Teste de áudio PT-PT criado: {out.name} ({out.stat().st_size} bytes).")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
