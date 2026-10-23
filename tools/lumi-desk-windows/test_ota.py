"""Offline regression tests: ESP-IDF OTA metadata and backup selection, no hardware."""
import importlib.util
import json
import struct
import sys
import unittest
from pathlib import Path
import zlib

MANAGER = Path(__file__).with_name("lumi_desk_manager.py")
spec = importlib.util.spec_from_file_location("lumi_desk_manager", MANAGER)
lumi = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lumi)


def make_entry(seq, state=2, damage_crc=False):
    raw_seq = struct.pack("<I", seq)
    crc = zlib.crc32(raw_seq, 0xFFFFFFFF) & 0xFFFFFFFF
    if damage_crc:
        crc ^= 0xFA55FA55
    return struct.pack("<I20sII", seq, bytes(20), state, crc)


def with_ota(first=None, second=None):
    data = bytearray(b"\xff" * lumi.OTA_DATA_LENGTH)
    if first:
        data[0:32] = first
    if second:
        data[0x1000:0x1020] = second
    return bytes(data)


def synthetic_flash(ota=None, previous_0="2.5.1", previous_1="2.5.2"):
    data = bytearray(b"\xff" * lumi.FLASH)
    data[0] = 0xE9
    entries = [
        ("nvs", 1, 2, 0x9000, 0x4000),
        ("otadata", 1, 0, 0xD000, 0x2000),
        ("phy_init", 1, 1, 0xF000, 0x1000),
        ("ota_0", 0, 0x10, 0x20000, 0x3F0000),
        ("ota_1", 0, 0x11, 0x410000, 0x3F0000),
        ("assets", 1, 0x82, 0x800000, 0x800000),
    ]
    for index, (name, typ, sub, off, length) in enumerate(entries):
        struct.pack_into("<HBBII16sI", data, 0x8000 + index * 32,
                         0x50AA, typ, sub, off, length, name.encode(), 0)
    def app_at(offset, version):
        data[offset] = 0xE9
        struct.pack_into("<I", data, offset + 32, 0xABCD5432)
        data[offset + 48:offset + 80] = version.encode().ljust(32, b"\0")
        data[offset + 80:offset + 112] = b"xiaozhi".ljust(32, b"\0")
    app_at(lumi.APP_OFFSETS[0], previous_0)
    app_at(lumi.APP_OFFSETS[1], previous_1)
    data[0xD000:0xF000] = ota if ota is not None else b"\xff" * 0x2000
    index = b'{"version":1}'
    table = struct.pack("<32sIIHH", b"index.json", len(index), 0, 0, 0)
    content = table + b"ZZ" + index
    struct.pack_into("<III", data, lumi.ASSETS_OFFSET,
                     1, sum(content) & 0xFFFF, len(content))
    begin = lumi.ASSETS_OFFSET + 12
    data[begin:begin + len(content)] = content
    return bytes(data)


class OtaSelectionTest(unittest.TestCase):
    def test_fresh_flash_uses_ota0(self):
        r = lumi.parse_flash(synthetic_flash())
        self.assertEqual(r["ota_ativo"], "ota_0")
        self.assertEqual(r["versao_atual"], "2.5.1")
        self.assertTrue(r["otadata_em_branco"])

    def test_ota0_record(self):
        ota = with_ota(first=make_entry(1))
        r = lumi.parse_flash(synthetic_flash(ota))
        self.assertEqual(r["ota_ativo"], "ota_0")
        self.assertEqual(r["offset_ativo"], "0x20000")
        self.assertFalse(r["otadata_em_branco"])

    def test_ota1_record(self):
        ota = with_ota(first=make_entry(1), second=make_entry(2))
        r = lumi.parse_flash(synthetic_flash(ota))
        self.assertEqual(r["ota_ativo"], "ota_1")
        self.assertEqual(r["offset_ativo"], "0x410000")
        self.assertEqual(r["versao_atual"], "2.5.2")

    def test_later_ota0_overrides_older_ota1(self):
        ota = with_ota(first=make_entry(3), second=make_entry(2))
        r = lumi.parse_flash(synthetic_flash(ota))
        self.assertEqual(r["ota_ativo"], "ota_0")

    def test_corrupted_crc_stops(self):
        ota = with_ota(first=make_entry(1, damage_crc=True))
        with self.assertRaisesRegex(ValueError, "CRC"):
            lumi.parse_flash(synthetic_flash(ota))

    def test_new_pending_blocks_writes(self):
        for state in (0, 1):
            with self.subTest(state=state):
                with self.assertRaisesRegex(ValueError, "NEW/PENDING"):
                    lumi.parse_flash(synthetic_flash(with_ota(first=make_entry(2, state))))

    def test_invalid_app_state_ignores_higher_sequence(self):
        ota = with_ota(first=make_entry(1), second=make_entry(4, 3))
        self.assertEqual(lumi.parse_flash(synthetic_flash(ota))["ota_ativo"], "ota_0")

    def test_unknown_partition_stops(self):
        blob = bytearray(synthetic_flash())
        blob[0x8000 + 2] = 9
        with self.assertRaisesRegex(ValueError, "Tabela de particoes"):
            lumi.parse_flash(blob)


if __name__ == "__main__":
    unittest.main()
