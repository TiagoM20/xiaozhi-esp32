"""Restore reliable middle PWR button click on Waveshare ESP32-S3 Touch LCD 1.54.
Keep the stock XiaoZhi 2.6.0 long, double and triple-click functions unchanged.
Apply AFTER patch_board.py. No changes to audio/AFE/wake word or assets.
"""
from pathlib import Path
import sys

BOARD="main/boards/waveshare/esp32-s3-touch-lcd-1.54/esp32-s3-touch-lcd-1.54.cc"

def apply(repo: Path) -> None:
    path=repo/BOARD
    src=path.read_text(encoding="utf-8")
    first="        pwr_button_.OnPressUp([this]() {\n"
    last="        volume_up_button_.OnClick([this]() {"
    assert src.count(first)==1 and src.count(last)==1, "Unexpected Waveshare button layout"
    before, tail=src.split(first,1)
    original, after=tail.split(last,1)
    # Exact original official 2.6.0 binding. No broad search/replace.
    expected="""\
            pwr_button_.OnClick([this]() {
                auto& app = Application::GetInstance();
                app.ToggleChatState();
            });
            pwr_button_.OnLongPress([this]() {
                printf("Power button long press\\n");
                // gpio_set_level(BATTERY_EN_PIN, 0);
                if (power_manager_ != nullptr){
                    power_manager_->PowerOff();
                }
            });

            pwr_button_.OnDoubleClick([this]() {
                static uint8_t brightness_last = 0;
                auto backlight = Board::GetInstance().GetBacklight();
                if (backlight->brightness() == 0) {
                    brightness_last = 0;
                    if (brightness_last == 0) {
                        backlight->SetBrightness(50, true);
                    } else {
                        backlight->SetBrightness(brightness_last, true);
                    }
                } else {
                    brightness_last = backlight->brightness();
                    backlight->SetBrightness(0);
                }
            });
            pwr_button_.OnMultipleClick([this]() {
                EnterWifiConfigMode();
            }, 3);
            pwr_button_.OnPressUpDel();
        });

"""
    assert original==expected, "Original button block changed; refusing patch"
    replacement="""\
        // GPIO5: middle PWR button must work on the FIRST normal click.
        // Avoid registering click callbacks from inside BUTTON_PRESS_UP.
        pwr_button_.OnPressDown([this]() {
            ESP_LOGI(TAG, "PWR GPIO5: immediate press, toggle chat");
            Application::GetInstance().ToggleChatState();
        });
        pwr_button_.OnLongPress([this]() {
            ESP_LOGI(TAG, "PWR long press: power off");
            if (power_manager_ != nullptr) {
                power_manager_->PowerOff();
            }
        });
        pwr_button_.OnDoubleClick([this]() {
            static uint8_t brightness_last = 0;
            auto backlight = Board::GetInstance().GetBacklight();
            if (backlight->brightness() == 0) {
                backlight->SetBrightness(brightness_last == 0 ? 50 : brightness_last, true);
            } else {
                brightness_last = backlight->brightness();
                backlight->SetBrightness(0);
            }
        });
        pwr_button_.OnMultipleClick([this]() {
            EnterWifiConfigMode();
        }, 3);

"""
    final=before+replacement+last+after
    assert final.count("pwr_button_.OnPressDown([this]() {")==1
    assert "pwr_button_.OnPressUp([this]() {" not in final
    assert "pwr_button_.OnPressUpDel();" not in final
    path.write_text(final,encoding="utf-8")
    print("Middle PWR button: GPIO5 click enabled on first press; unchanged wake engine")

if __name__=="__main__":
    if len(sys.argv)!=2: raise SystemExit("usage: patch_power_button.py UPSTREAM_PATH")
    apply(Path(sys.argv[1]))
