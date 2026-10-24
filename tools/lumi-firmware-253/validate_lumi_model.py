"""Fail-closed validation before configuring a private LUMI WakeNet10 model.

A sentence in the JSON config does NOT create a wake-word neural network.
Only a genuine model package with the expected model information may be staged.
Do not attempt to replace an existing ESP-SR model by renaming its directory.
"""
import argparse
import json
from pathlib import Path

EXPECTED_MODEL = "wn10_pt_lumi"
EXPECTED_LANGUAGE = "pt-PT"


class WakeModelMissing(RuntimeError):
    pass


def validate(profile_path, model_root):
    profile = json.loads(Path(profile_path).read_text(encoding="utf-8"))
    phrases = profile.get("accepted_wake_words")
    if not isinstance(phrases, list) or not 1 <= len(phrases) <= 3:
        raise ValueError("Wake words must be 1 to 3 trained phrases.")
    if phrases != ["Lumi"]:
        raise ValueError("This build has only the approved 'Lumi' wake phrase enabled.")
    if profile.get("language") != EXPECTED_LANGUAGE:
        raise ValueError("Wake word must be Portuguese (Portugal).")
    if profile.get("engine") != "wakenet10":
        raise ValueError("Custom speech recognition model is required, not MultiNet.")
    if profile.get("model_name") != EXPECTED_MODEL:
        raise ValueError("Unexpected model name. A renamed English model is forbidden.")
    if profile.get("allow_flash_without_model", True):
        raise ValueError("Unsafe config permits flashing without the model.")
    if profile.get("allow_unprompted_tts", True):
        raise ValueError("Unsafe config permits spontaneous assistant speech.")
    if profile.get("notify_on_wake", True):
        raise ValueError("Unsafe config sends wake greeting.")
    if profile.get("followup_seconds") != 15:
        raise ValueError("Keep 15s follow-up listening after a genuine interaction.")
    folder = Path(model_root) / EXPECTED_MODEL
    info = folder / "_MODEL_INFO_"
    if not info.is_file():
        raise WakeModelMissing(
            "No trained Portuguese WakeNet model for 'Lumi' was supplied. "
            "DO NOT BUILD OR FLASH: adding the keyword to a configuration is not training."
        )
    # Require actual binary weights, not only a fake metadata file.
    weights = [
        x for x in folder.rglob("*") if x.is_file()
        and x.name != "_MODEL_INFO_" and x.stat().st_size > 64_000
    ]
    if not weights:
        raise WakeModelMissing("WakeNet model weights missing/incomplete. Do not flash.")
    return {"phrase": "Lumi", "engine": "WakeNet10",
            "model": EXPECTED_MODEL, "weight_files": len(weights),
            "model_bytes": sum(x.stat().st_size for x in weights)}


def main():
    parser = argparse.ArgumentParser(description="Validate trained Lumi WakeNet10 package")
    parser.add_argument("--profile", default=str(Path(__file__).with_name("lumi_wake_profile.json")))
    parser.add_argument("--model-root", required=True,
                        help="Parent directory containing the genuine wn10_pt_lumi model folder")
    args = parser.parse_args()
    print(json.dumps(validate(args.profile, args.model_root), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
