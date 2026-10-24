import json
import tempfile
import unittest
from pathlib import Path
from validate_lumi_model import validate, WakeModelMissing


class LumiWakeProfileTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.profile = {
            "language": "pt-PT", "engine": "wakenet10",
            "accepted_wake_words": ["Lumi"], "model_name": "wn10_pt_lumi",
            "allow_flash_without_model": False, "allow_unprompted_tts": False,
            "notify_on_wake": False, "followup_seconds": 15
        }
        self.path = self.root / "profile.json"
        self.save()

    def tearDown(self):
        self.temp.cleanup()

    def save(self):
        self.path.write_text(json.dumps(self.profile), encoding="utf-8")

    def test_no_real_model_rejected(self):
        with self.assertRaises(WakeModelMissing):
            validate(self.path, self.root)

    def test_english_model_renaming_not_sufficient(self):
        path = self.root / "wn10_pt_lumi"
        path.mkdir()
        (path / "_MODEL_INFO_").write_text("fake")
        with self.assertRaisesRegex(WakeModelMissing, "weights"):
            validate(self.path, self.root)

    def test_multinet_rejected(self):
        self.profile["engine"] = "multinet"
        self.save()
        with self.assertRaisesRegex(ValueError, "MultiNet"):
            validate(self.path, self.root)

    def test_spontaneous_tts_rejected(self):
        self.profile["allow_unprompted_tts"] = True
        self.save()
        with self.assertRaisesRegex(ValueError, "spontaneous"):
            validate(self.path, self.root)

    def test_extra_unapproved_variants_rejected(self):
        self.profile["accepted_wake_words"] = ["Lumi", "Olá Lumi"]
        self.save()
        with self.assertRaisesRegex(ValueError, "only the approved"):
            validate(self.path, self.root)

    def test_15s_followup_required(self):
        self.profile["followup_seconds"] = 0
        self.save()
        with self.assertRaisesRegex(ValueError, "follow-up"):
            validate(self.path, self.root)


if __name__ == "__main__":
    unittest.main()
