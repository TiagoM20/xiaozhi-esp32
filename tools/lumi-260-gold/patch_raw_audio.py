#!/usr/bin/env python3
"""LUMI RAW AUDIO rescue: keep official XiaoZhi 2.6.0 codec/audio transport, but
skip blocked FD AFE startup triggered by legacy wn10_pt_olaesp assets on DESK.
Manual PWR / long-touch listening stays available. No NVS/assets/partition changes.
Apply after the LUMI gold face, physical button and QMI8658 patches.
"""
from pathlib import Path
import json
import sys

BOARD_CONFIG = Path("main/boards/waveshare/esp32-s3-touch-lcd-1.54/config.json")
AFE = Path("main/audio/engines/afe_audio_engine.cc")

def patch(repo: Path):
    config_path=repo/BOARD_CONFIG
    raw=config_path.read_text(encoding="utf-8")
    config=json.loads(raw)
    assert config["type"]=="esp32-s3-touch-lcd-1.54"
    assert len(config["builds"])==1
    opts=config["builds"][0]["sdkconfig_append"]
    assert "CONFIG_USE_DEVICE_AEC=y" in opts
    opts.remove("CONFIG_USE_DEVICE_AEC=y")
    opts.extend(["CONFIG_USE_AUDIO_PROCESSOR=n",
                 "CONFIG_USE_DEVICE_AEC=n",
                 "CONFIG_WAKE_WORD_DISABLED=y"])
    assert len(opts)==len(set(opts))
    config_path.write_text(json.dumps(config,indent=4,ensure_ascii=False)+"\n",encoding="utf-8")

    src=repo/AFE
    code=src.read_text(encoding="utf-8")
    anchor='    frame_samples_ = frame_duration_ms * 16000 / 1000;\n    output_buffer_.reserve(frame_samples_);\n'
    assert code.count(anchor)==1, "Upstream audio engine modified; refusing patch"
    guard='''

#if defined(CONFIG_BOARD_TYPE_WAVESHARE_ESP32_S3_TOUCH_LCD_1_54)
#if !CONFIG_USE_AUDIO_PROCESSOR && CONFIG_WAKE_WORD_DISABLED
    // DESK recovery (20 Nov 2026): the old assets partition contains
    // wn10_pt_olaesp even though the new app selected wn9_hiesp.
    // esp_afe_handle_from_config/create_from_config blocked after
    // "Before AFE create", so main app could not process PWR or play audio.
    // Do NOT call esp_srmodel_init/filter or create FD AFE in this build.
    // AudioService uses the normal codec, Opus, MQTT and raw mono uplink.
    wake_detector_ = WakeDetector::kNone;
    ESP_LOGW(TAG, "LUMI AUDIO RECOVERY: raw microphone, no AFE/WakeNet; manual PWR/touch");
    return true;
#else
#error "LUMI audio recovery requires audio processor OFF and wake word DISABLED"
#endif
#endif
'''
    code=code.replace(anchor,anchor+guard)
    src.write_text(code,encoding="utf-8")
    print("LUMI RAW recovery patch: AFE disabled, raw mic + DAC retained, WakeNet disabled")
    print("No changes to shared assets partition / NVS / OTA / codec / I2C / touchscreen")

if __name__=="__main__":
    if len(sys.argv)!=2: raise SystemExit("Usage: patch_raw_audio.py UPSTREAM_PATH")
    patch(Path(sys.argv[1]))
