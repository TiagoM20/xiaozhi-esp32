#!/usr/bin/env python3
"""Converte Ogg Opus (quadros de 60 ms) em pacotes binarios length-prefixed.
Uso: python3 ogg_packets.py source.ogg output.pkt
Sem bibliotecas adicionais. Valida framing mas não avalia qualidade sonora.
"""
from pathlib import Path
import struct
import sys

def extract(raw: bytes) -> list[bytes]:
    pos = 0
    buf = bytearray()
    packets: list[bytes] = []
    while pos < len(raw):
        if raw[pos:pos+4] != b"OggS":
            raise ValueError("Página Ogg inválida")
        if pos+27 > len(raw):
            raise ValueError("Cabeçalho truncado")
        n = raw[pos+26]
        if pos+27+n > len(raw):
            raise ValueError("Tabela de segmentos truncada")
        lacing = raw[pos+27:pos+27+n]
        body_start = pos+27+n
        body_end = body_start+sum(lacing)
        if body_end > len(raw):
            raise ValueError("Corpo Ogg truncado")
        q = body_start
        for length in lacing:
            buf.extend(raw[q:q+length])
            q += length
            if length < 255:
                packets.append(bytes(buf))
                buf.clear()
        pos = body_end
    if buf:
        raise ValueError("Pacote final incompleto")
    if len(packets) < 12 or not packets[0].startswith(b"OpusHead") or not packets[1].startswith(b"OpusTags"):
        raise ValueError("Ogg não contém áudio Opus válido")
    data = packets[2:]
    if len(data) < 10 or any(len(p) > 8192 or len(p) == 0 for p in data):
        raise ValueError("Número/tamanho de pacotes inválido")
    return data

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Uso: ogg_packets.py entrada.ogg saida.pkt")
    frames = extract(Path(sys.argv[1]).read_bytes())
    output = b"".join(struct.pack(">H", len(f))+f for f in frames)
    Path(sys.argv[2]).parent.mkdir(parents=True, exist_ok=True)
    Path(sys.argv[2]).write_bytes(output)
    print(f"Gerados {len(frames)} pacotes Opus; {len(output)} bytes.")
