"""Apply optional Waveshare 1.54 QMI8658 shake response after the gold-face
and PWR GPIO5 patches. Fail closed if upstream board shape changes."""
from pathlib import Path
import sys

BOARD="main/boards/waveshare/esp32-s3-touch-lcd-1.54/esp32-s3-touch-lcd-1.54.cc"
HEADER="main/boards/waveshare/esp32-s3-touch-lcd-1.54/lumi_shake_sensor.h"

def apply(repo:Path, header:Path):
    file=repo/BOARD
    src=file.read_text(encoding="utf-8")
    changes=[
      ('#include "lumi_gold_face.h"','#include "lumi_gold_face.h"\n#include "lumi_shake_sensor.h"'),
      ('    PowerSaveTimer* power_save_timer_ = nullptr;', '    PowerSaveTimer* power_save_timer_ = nullptr;\n    LumiShakeSensor* shake_sensor_ = nullptr;'),
      ('    void InitializePowerManager() {',
       '    void InitializeShakeSensor() {\n'
       '        shake_sensor_ = new LumiShakeSensor();\n'
       '        shake_sensor_->Start(i2c_bus_, static_cast<LumiGoldFaceDisplay*>(display_));\n'
       '    }\n\n'
       '    void InitializePowerManager() {'),
      ('        InitializeTools();\n        GetBacklight()->RestoreBrightness();',
       '        InitializeTools();\n        InitializeShakeSensor();\n        GetBacklight()->RestoreBrightness();')
    ]
    for before,after in changes:
        assert src.count(before)==1, 'Unexpected upstream Waveshare source: '+before
        src=src.replace(before,after)
    target=repo/HEADER
    assert not target.exists(), 'IMU header already present'
    assert 'class LumiShakeSensor' in header.read_text(encoding="utf-8")
    target.write_text(header.read_text(encoding="utf-8"),encoding="utf-8")
    file.write_text(src,encoding="utf-8")
    print("Optional QMI8658 shake overlay added; no changes to audio/wake/OTA")

if __name__=="__main__":
    if len(sys.argv)!=3: raise SystemExit("Usage: patch_imu.py UPSTREAM_PATH IMU_HEADER_PATH")
    apply(Path(sys.argv[1]),Path(sys.argv[2]))
