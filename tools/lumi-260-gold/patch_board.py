"""Apply precisely one display substitution to the pinned official XiaoZhi 2.6.0.

No AFE, wakeword, NVS, MCP, OTA, partition, LCD driver or SDK modifications.
"""
from pathlib import Path
import sys

EXPECTED_COMMIT="67ca62d4bb7d91e514d7ccd5e6977745002f41f4"
BOARD="main/boards/waveshare/esp32-s3-touch-lcd-1.54/esp32-s3-touch-lcd-1.54.cc"
HEADER="main/boards/waveshare/esp32-s3-touch-lcd-1.54/lumi_gold_face.h"


def apply(repo: Path, asset: Path):
    board=repo/BOARD
    header=repo/HEADER
    source=board.read_text(encoding="utf-8")
    old_include='#include "display/lcd_display.h"'
    old_ctor="display_ = new SpiLcdDisplay(panel_io, panel,"
    assert source.count(old_include)==1,"Original LCD include changed; refusing patch"
    assert source.count(old_ctor)==1,"Original Waveshare display constructor changed; refusing patch"
    assert not header.exists(),"Gold header already exists; refusing patch"
    text=asset.read_text(encoding="utf-8")
    assert "class LumiGoldFaceDisplay" in text
    assert "void SetEmotion(const char* emotion) override" in text
    assert "static constexpr uint32_t kGold=0xD8B35A" in text
    assert "#include <esp_timer.h>" in text
    assert 'Settings' not in text and 'McpServer' not in text
    header.write_text(text,encoding="utf-8")
    source=source.replace(old_include,old_include+'\n#include "lumi_gold_face.h"')
    source=source.replace(old_ctor,"display_ = new LumiGoldFaceDisplay(panel_io, panel,")
    board.write_text(source,encoding="utf-8")
    print("Gold-face-only patch applied to official XiaoZhi 2.6.0 Waveshare board")
    print("No firmware behavior/audio/recognition code modified")


if __name__=="__main__":
    if len(sys.argv)!=3:raise SystemExit("Usage: patch_board.py UPSTREAM_PATH GOLD_HEADER_PATH")
    apply(Path(sys.argv[1]),Path(sys.argv[2]))
