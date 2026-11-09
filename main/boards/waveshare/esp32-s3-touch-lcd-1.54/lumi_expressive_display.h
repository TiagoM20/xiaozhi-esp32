#pragma once

// LUMI V8: Lightweight expressive face, confined to the Waveshare 240x240 board.
// No image assets, SD mounting, new wake models, or additional boot dependencies.
#include "display/lcd_display.h"
#include "settings.h"

#include <lvgl.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <string>
#include <esp_log.h>
#include <esp_timer.h>

class LumiExpressiveDisplay final : public SpiLcdDisplay {
public:
    LumiExpressiveDisplay(esp_lcd_panel_io_handle_t io, esp_lcd_panel_handle_t panel,
                          int width, int height, int ox, int oy,
                          bool mirror_x, bool mirror_y, bool swap_xy)
        : SpiLcdDisplay(io, panel, width, height, ox, oy, mirror_x, mirror_y, swap_xy) {
        Settings saved("lumi_face");
        color_index_ = std::clamp(saved.GetInt("color", 0), 0, 6);
        active_ = saved.GetBool("enabled", true);
    }

    ~LumiExpressiveDisplay() override {
        DisplayLockGuard lock(this);
        if (tick_ != nullptr) {
            lv_timer_delete(tick_);
            tick_ = nullptr;
        }
    }

    void SetupUI() override {
        SpiLcdDisplay::SetupUI();
        DisplayLockGuard lock(this);
        if (face_ != nullptr || emoji_box_ == nullptr) return;

        face_ = lv_obj_create(emoji_box_);
        lv_obj_set_size(face_, 212, 156);
        lv_obj_set_style_pad_all(face_, 0, 0);
        lv_obj_set_style_border_width(face_, 0, 0);
        lv_obj_set_style_radius(face_, 0, 0);
        lv_obj_set_style_bg_opa(face_, LV_OPA_TRANSP, 0);
        lv_obj_set_scrollbar_mode(face_, LV_SCROLLBAR_MODE_OFF);
        lv_obj_align(face_, LV_ALIGN_CENTER, 0, 0);

        for (int i = 0; i < 2; ++i) {
            brows_[i] = lv_obj_create(face_);
            eyes_[i] = lv_obj_create(face_);
            for (lv_obj_t* obj : {brows_[i], eyes_[i]}) {
                lv_obj_set_style_border_width(obj, 0, 0);
                lv_obj_set_style_pad_all(obj, 0, 0);
                lv_obj_set_scrollbar_mode(obj, LV_SCROLLBAR_MODE_OFF);
            }
            lv_obj_set_style_radius(eyes_[i], LV_RADIUS_CIRCLE, 0);
            lv_obj_set_style_radius(brows_[i], LV_RADIUS_CIRCLE, 0);
        }
        mouth_ = lv_obj_create(face_);
        lv_obj_set_style_border_width(mouth_, 0, 0);
        lv_obj_set_style_pad_all(mouth_, 0, 0);
        lv_obj_set_scrollbar_mode(mouth_, LV_SCROLLBAR_MODE_OFF);
        lv_obj_set_style_radius(mouth_, LV_RADIUS_CIRCLE, 0);

        if (active_) {
            if (emoji_label_) lv_obj_add_flag(emoji_label_, LV_OBJ_FLAG_HIDDEN);
            if (emoji_image_) lv_obj_add_flag(emoji_image_, LV_OBJ_FLAG_HIDDEN);
        } else {
            lv_obj_add_flag(face_, LV_OBJ_FLAG_HIDDEN);
        }

        tick_ = lv_timer_create(&LumiExpressiveDisplay::TickCallback, 85, this);
        DrawFrame();
    }

    void SetEmotion(const char* emotion) override {
        const char* name = emotion != nullptr ? emotion : "neutral";
        if (!active_) {
            SpiLcdDisplay::SetEmotion(name);
            return;
        }
        DisplayLockGuard lock(this);
        last_emotion_ = name;
        mood_ = ParseMood(name);
        if (face_ == nullptr) return;
        // Any old animated emoji must be stopped before hiding its LVGL object.
        if (gif_controller_) {
            gif_controller_->Stop();
            gif_controller_.reset();
        }
        if (emoji_label_) lv_obj_add_flag(emoji_label_, LV_OBJ_FLAG_HIDDEN);
        if (emoji_image_) lv_obj_add_flag(emoji_image_, LV_OBJ_FLAG_HIDDEN);
        lv_obj_remove_flag(face_, LV_OBJ_FLAG_HIDDEN);
        DrawFrame();
    }

    bool SetFaceColor(int idx) {
        if (idx < 0 || idx > 6) return false;
        {
            DisplayLockGuard lock(this);
            color_index_ = idx;
            DrawFrame();
        }
        Settings saved("lumi_face", true);
        saved.SetInt("color", idx);
        return true;
    }

    void SetFaceEnabled(bool enabled) {
        {
            DisplayLockGuard lock(this);
            active_ = enabled;
            if (face_) {
                if (enabled) {
                    if (gif_controller_) {
                        gif_controller_->Stop();
                        gif_controller_.reset();
                    }
                    if (emoji_label_) lv_obj_add_flag(emoji_label_, LV_OBJ_FLAG_HIDDEN);
                    if (emoji_image_) lv_obj_add_flag(emoji_image_, LV_OBJ_FLAG_HIDDEN);
                    lv_obj_remove_flag(face_, LV_OBJ_FLAG_HIDDEN);
                } else {
                    lv_obj_add_flag(face_, LV_OBJ_FLAG_HIDDEN);
                }
            }
        }
        Settings saved("lumi_face", true);
        saved.SetBool("enabled", enabled);
        if (!enabled) SpiLcdDisplay::SetEmotion(last_emotion_.c_str());
    }

    bool FaceEnabled() const { return active_; }
    int FaceColor() const { return color_index_; }

private:
    lv_obj_t* face_ = nullptr;
    lv_obj_t* eyes_[2] = {nullptr, nullptr};
    lv_obj_t* brows_[2] = {nullptr, nullptr};
    lv_obj_t* mouth_ = nullptr;
    lv_timer_t* tick_ = nullptr;
    std::string last_emotion_ = "neutral";
    int mood_ = 0;
    int color_index_ = 0;
    bool active_ = true;
    uint32_t frames_ = 0;

    static int ParseMood(const char* name) {
        if (!name) return 0;
        if (!strcmp(name, "happy") || !strcmp(name, "laughing") ||
            !strcmp(name, "loving") || !strcmp(name, "kissy")) return 4;
        if (!strcmp(name, "surprised") || !strcmp(name, "shocked")) return 5;
        if (!strcmp(name, "confused") || !strcmp(name, "angry") ||
            !strcmp(name, "sad")) return 6;
        if (!strcmp(name, "sleepy") || !strcmp(name, "sleeping")) return 7;
        if (!strcmp(name, "thinking") || !strcmp(name, "cool")) return 2;
        if (!strcmp(name, "listening") || !strcmp(name, "curious")) return 1;
        if (!strcmp(name, "speaking") || !strcmp(name, "talking")) return 3;
        if (!strcmp(name, "excited")) return 9;
        return 0;
    }

    static void TickCallback(lv_timer_t* timer) {
        auto* self = static_cast<LumiExpressiveDisplay*>(lv_timer_get_user_data(timer));
        if (self && self->active_ && self->face_) self->DrawFrame();
    }

    void DrawFrame() {
        if (!face_ || !eyes_[0] || !eyes_[1] || !mouth_) return;
        static const uint32_t palette[] = {
            0x73EBEE, 0xF0A9D8, 0xAE9AFF, 0xFFBD76,
            0x75EBAA, 0xE8F4FF, 0xFF7988
        };
        lv_color_t accent = lv_color_hex(palette[color_index_]);
        uint32_t now = static_cast<uint32_t>(esp_timer_get_time() / 1000);
        // Non-blocking ~150ms blink every 5.5s, and gentle vertical breathing.
        bool blink = (now % 5500) > 5350;
        int breathe = static_cast<int>(std::sin(now / 850.0f) * 2.0f);
        int look = static_cast<int>(std::sin(now / 1710.0f) * 3.0f);

        int eyes_y = 40 + breathe + (mood_ == 2 ? 7 : 0);
        int width = mood_ == 5 ? 36 : mood_ == 2 ? 34 : 43;
        int height = mood_ == 7 ? 4 : blink ? 3 : mood_ == 4 ? 28 : width;
        for (int i = 0; i < 2; ++i) {
            int eye_x = (i == 0 ? 40 : 128) + look;
            lv_obj_set_size(eyes_[i], width, height);
            lv_obj_align(eyes_[i], LV_ALIGN_TOP_LEFT, eye_x, eyes_y + (width-height)/2);
            lv_obj_set_style_bg_color(eyes_[i], accent, 0);
            lv_obj_set_style_bg_opa(eyes_[i], LV_OPA_COVER, 0);

            // Brows are restrained and only appear when reacting.
            bool expressive = mood_ == 2 || mood_ == 5 || mood_ == 6 || mood_ == 9;
            if (expressive) {
                lv_obj_remove_flag(brows_[i], LV_OBJ_FLAG_HIDDEN);
                lv_obj_set_size(brows_[i], 33, 4);
                lv_obj_align(brows_[i], LV_ALIGN_TOP_LEFT, eye_x + 2,
                             eyes_y - (mood_ == 5 ? 19 : 12) + (i ? 2 : 0));
                lv_obj_set_style_bg_color(brows_[i], accent, 0);
            } else {
                lv_obj_add_flag(brows_[i], LV_OBJ_FLAG_HIDDEN);
            }
        }
        int mouth_w = 34, mouth_h = 7, mouth_y = 119;
        switch (mood_) {
            case 3: // speaking
                mouth_w = 21;
                mouth_h = 7 + static_cast<int>(11 * std::abs(std::sin(now / 130.0f)));
                break;
            case 4: case 8: case 9: mouth_w=49; mouth_h=11; break;
            case 5: mouth_w=20; mouth_h=21; break;
            case 6: mouth_w=24; mouth_h=5; mouth_y=124; break;
            case 7: mouth_w=22; mouth_h=4; mouth_y=126; break;
            default: break;
        }
        lv_obj_set_size(mouth_, mouth_w, mouth_h);
        lv_obj_align(mouth_, LV_ALIGN_TOP_LEFT, 106-mouth_w/2, mouth_y);
        lv_obj_set_style_bg_color(mouth_, accent, 0);
        lv_obj_set_style_bg_opa(mouth_, LV_OPA_COVER, 0);
        ++frames_;
    }
};
