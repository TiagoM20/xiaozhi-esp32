#!/usr/bin/env python3
"""Testes isolados: não precisam de Azure, Edge nem de hardware."""
import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch
from xml.etree import ElementTree as ET

MODULE = Path(__file__).with_name("voice_test.py")
spec = importlib.util.spec_from_file_location("voice_test", MODULE)
voice = importlib.util.module_from_spec(spec)
spec.loader.exec_module(voice)


class FakeResponse:
    def __init__(self, data):
        self.data = data
    def __enter__(self):
        return self
    def __exit__(self, *args):
        return False
    def read(self, limit):
        return self.data


class SpeechTests(unittest.TestCase):
    def test_ssml_ptpt_escapes_user_text(self):
        xml = voice.build_ssml('Olá & "até já" <Lumi>')
        root = ET.fromstring(xml)
        node = root.find("{http://www.w3.org/2001/10/synthesis}voice")
        self.assertEqual(node.attrib["name"], "pt-PT-RaquelNeural")
        self.assertEqual(node.attrib["{http://www.w3.org/XML/1998/namespace}lang"], "pt-PT")
        self.assertEqual(node.text, 'Olá & "até já" <Lumi>')

    def test_reject_non_ptpt_and_empty_text(self):
        with self.assertRaises(ValueError):
            voice.build_ssml("olá", "pt-BR-FranciscaNeural")
        with self.assertRaises(ValueError):
            voice.build_ssml(" ")

    def test_validate_mp3(self):
        voice.validate_mp3(b"ID3" + b"\0" * 1400)
        with self.assertRaises(ValueError):
            voice.validate_mp3(b"<!DOCTYPE html><html>")
        with self.assertRaises(ValueError):
            voice.validate_mp3(b"")

    @patch.object(voice.urllib.request, "urlopen")
    def test_azure_request_protocol_and_no_embedded_key(self, urlopen):
        urlopen.return_value = FakeResponse(b"ID3" + b"\0" * 1400)
        audio = voice.azure_tts(
            "Televisão da sala", "pt-PT-RaquelNeural",
            key="apenas-um-token-de-teste", region="westeurope")
        self.assertTrue(audio.startswith(b"ID3"))
        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url,
            "https://westeurope.tts.speech.microsoft.com/cognitiveservices/v1")
        self.assertEqual(request.get_header("X-microsoft-outputformat"),
            "audio-24khz-48kbitrate-mono-mp3")
        self.assertIn(b"pt-PT-RaquelNeural", request.data)
        self.assertNotIn(b"apenas-um-token-de-teste", request.data)

    def test_reject_bad_region(self):
        with self.assertRaises(ValueError):
            voice.azure_tts("olá", voice.VOICES[0], key="test",
                            region="a.example.com")


if __name__ == "__main__":
    unittest.main()
