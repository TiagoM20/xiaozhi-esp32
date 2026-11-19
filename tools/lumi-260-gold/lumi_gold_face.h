#pragma once

// LUMI GOLD — XiaoZhi 2.6.0 baseline face-only experiment.
// Only replaces the display renderer of Waveshare ESP32-S3-Touch-LCD-1.54.
// No custom wake word, audio engine, heap patches, MCP, SD, NVS, assets or Wi-Fi.
// Retains the no-iris two-eye design from LUMI V8, fixed GOLD (#D8B35A).
#include "display/lcd_display.h"
#include "assets/lang_config.h"

#include <lvgl.h>
#include <esp_log.h>
#include <esp_timer.h>
#include <cmath>
#include <initializer_list>
#include <cstring>
#include <cstdint>

class LumiGoldFaceDisplay final : public SpiLcdDisplay {
public:
    LumiGoldFaceDisplay(esp_lcd_panel_io_handle_t io, esp_lcd_panel_handle_t panel,
                        int width, int height, int ox, int oy,
                        bool mirror_x, bool mirror_y, bool swap_xy)
        : SpiLcdDisplay(io, panel, width, height, ox, oy,
                        mirror_x, mirror_y, swap_xy) {}

    ~LumiGoldFaceDisplay() override {
        DisplayLockGuard guard(this);
        if (guard && animation_ != nullptr) {
            lv_timer_delete(animation_);
            animation_ = nullptr;
        }
    }

    void SetupUI() override {
        // Leave official 2.6.0 LCD initialisation completely intact.
        SpiLcdDisplay::SetupUI();
        DisplayLockGuard guard(this);
        if (!guard || face_ != nullptr || emoji_box_ == nullptr) return;

        face_ = lv_obj_create(emoji_box_);
        lv_obj_set_size(face_, 212, 156);
        lv_obj_set_style_pad_all(face_, 0, 0);
        lv_obj_set_style_border_width(face_, 0, 0);
        lv_obj_set_style_radius(face_, 0, 0);
        lv_obj_set_style_bg_opa(face_, LV_OPA_TRANSP, 0);
        lv_obj_remove_flag(face_, LV_OBJ_FLAG_SCROLLABLE);
        lv_obj_set_scrollbar_mode(face_, LV_SCROLLBAR_MODE_OFF);
        lv_obj_align(face_, LV_ALIGN_CENTER, 0, 0);

        for(int i=0;i<2;++i) {
            eye_[i]=lv_obj_create(face_);
            brow_[i]=lv_obj_create(face_);
            for(lv_obj_t* item : {eye_[i],brow_[i]}) {
                lv_obj_set_style_pad_all(item,0,0);
                lv_obj_set_style_border_width(item,0,0);
                lv_obj_set_style_bg_color(item,lv_color_hex(kGold),0);
                lv_obj_set_style_bg_opa(item,LV_OPA_COVER,0);
                lv_obj_set_scrollbar_mode(item,LV_SCROLLBAR_MODE_OFF);
                lv_obj_remove_flag(item,LV_OBJ_FLAG_SCROLLABLE);
                lv_obj_set_style_radius(item,LV_RADIUS_CIRCLE,0);
            }
        }
        mouth_=lv_obj_create(face_);
        lv_obj_set_style_border_width(mouth_,0,0);
        lv_obj_set_style_pad_all(mouth_,0,0);
        lv_obj_set_scrollbar_mode(mouth_,LV_SCROLLBAR_MODE_OFF);
        lv_obj_remove_flag(mouth_,LV_OBJ_FLAG_SCROLLABLE);
        lv_obj_set_style_radius(mouth_,LV_RADIUS_CIRCLE,0);
        lv_obj_set_style_bg_color(mouth_,lv_color_hex(kGold),0);
        lv_obj_set_style_bg_opa(mouth_,LV_OPA_COVER,0);

        if(emoji_label_) lv_obj_add_flag(emoji_label_,LV_OBJ_FLAG_HIDDEN);
        if(emoji_image_) lv_obj_add_flag(emoji_image_,LV_OBJ_FLAG_HIDDEN);
        DrawFrame();
        // 10 updates/s with six persistent LVGL widgets and no per-frame allocations.
        animation_=lv_timer_create(&LumiGoldFaceDisplay::Tick,100,this);
        ESP_LOGI("LUMI_GOLD","Gold face initialized (6 LVGL widgets, 100ms timer, pt-PT states)");
    }

    // Detect actual XiaoZhi speaking/listening states; affect the display only.
    void SetStatus(const char* status) override {
        SpiLcdDisplay::SetStatus(status);
        int next = 0;
        if (status != nullptr) {
            if (std::strcmp(status, Lang::Strings::SPEAKING) == 0) next = 3;
            else if (std::strcmp(status, Lang::Strings::LISTENING) == 0) next = 1;
            else if (std::strcmp(status, Lang::Strings::CONNECTING) == 0) next = 2;
        }
        if (face_ == nullptr) return;
        DisplayLockGuard guard(this);
        if (guard) {
            status_mood_ = next;
            DrawFrame();
        }
    }

    bool SetEmotion(const char* emotion) override {
        const char* value=emotion?emotion:"neutral";
        if (face_ == nullptr) return SpiLcdDisplay::SetEmotion(value);
        DisplayLockGuard guard(this);
        if(!guard) return false;
        mood_=Mood(value);
        // Prevent legacy GIF playback underneath the gold face.
        if(gif_controller_) {
            gif_controller_->Stop();
            gif_controller_.reset();
        }
        if(emoji_label_) lv_obj_add_flag(emoji_label_,LV_OBJ_FLAG_HIDDEN);
        if(emoji_image_) lv_obj_add_flag(emoji_image_,LV_OBJ_FLAG_HIDDEN);
        DrawFrame();
        return true;
    }

private:
    static constexpr uint32_t kGold=0xD8B35A;
    lv_obj_t* face_=nullptr;
    lv_obj_t* eye_[2]={nullptr,nullptr};
    lv_obj_t* brow_[2]={nullptr,nullptr};
    lv_obj_t* mouth_=nullptr;
    lv_timer_t* animation_=nullptr;
    int mood_=0;
    int status_mood_=0;

    static int Mood(const char* s) {
        if(!strcmp(s,"happy")||!strcmp(s,"laughing")||
           !strcmp(s,"loving")||!strcmp(s,"kissy")||!strcmp(s,"excited"))return 4;
        if(!strcmp(s,"surprised")||!strcmp(s,"shocked"))return 5;
        if(!strcmp(s,"confused")||!strcmp(s,"sad"))return 6;
        if(!strcmp(s,"sleepy")||!strcmp(s,"sleeping"))return 7;
        if(!strcmp(s,"thinking")||!strcmp(s,"cool"))return 2;
        if(!strcmp(s,"listening")||!strcmp(s,"curious"))return 1;
        if(!strcmp(s,"wink")||!strcmp(s,"winking"))return 8;
        if(!strcmp(s,"angry"))return 9;
        if(!strcmp(s,"speaking")||!strcmp(s,"talking"))return 3;
        return 0;
    }

    static void Tick(lv_timer_t* timer) {
        auto* face=static_cast<LumiGoldFaceDisplay*>(lv_timer_get_user_data(timer));
        if(face && face->face_)face->DrawFrame();
    }

    void DrawFrame() {
        if (!face_ || !eye_[0] || !eye_[1] || !mouth_) return;
        const int64_t ms = esp_timer_get_time() / 1000;
        // Actual listening/speaking state takes precedence over stale AI emotions.
        const int mode = (status_mood_ == 3 || status_mood_ == 1)
            ? status_mood_ : (mood_ != 0 ? mood_ : status_mood_);
        const float t = static_cast<float>(ms);
        // Slow breathing/gaze, varied closed-eye blinks; no allocations per tick.
        const float blink_period = 4300.f + 650.f * std::sin(t / 12000.f);
        const float blink_phase = std::fmod(t, blink_period);
        const float blink = blink_phase < 130.f
            ? 1.f - std::fabs(blink_phase - 65.f) / 65.f : 0.f;
        const int breath = static_cast<int>(2.f * std::sin(t / 960.f));
        const int gaze = static_cast<int>(3.f * std::sin(t / 1460.f));
        const int nod = mode == 1 ? static_cast<int>(2.f * std::sin(t / 230.f)) : 0;
        const int base_w = mode == 5 ? 37 : mode == 2 ? 35 : mode == 9 ? 38 : 43;
        const int base_h = mode == 7 ? 4 : mode == 4 ? 29 : 40;
        const int eye_h = blink > 0.f ? static_cast<int>(base_h * (1.f - 0.92f * blink)) : base_h;
        const int top = 39 + breath + nod + (mode == 2 ? 6 : 0);
        for (int i = 0; i < 2; ++i) {
            const int x = (i == 0 ? 39 : 129) + gaze;
            const int h = mode == 8 && i == 0 ? 4 : (eye_h < 3 ? 3 : eye_h);
            lv_obj_set_size(eye_[i], base_w, h);
            lv_obj_align(eye_[i], LV_ALIGN_TOP_LEFT, x, top + (base_h - h) / 2);
            const bool eyebrows = mode == 2 || mode == 4 || mode == 5 ||
                                  mode == 6 || mode == 9 || mode == 8;
            if (eyebrows) {
                lv_obj_remove_flag(brow_[i], LV_OBJ_FLAG_HIDDEN);
                const int by = top - (mode == 5 ? 19 : mode == 6 ? 8 : 13) +
                               (mode == 9 ? (i ? 4 : -2) : i);
                lv_obj_set_size(brow_[i], mode == 5 ? 37 : 34, mode == 9 ? 6 : 4);
                lv_obj_align(brow_[i], LV_ALIGN_TOP_LEFT, x + 2, by);
            } else {
                lv_obj_add_flag(brow_[i], LV_OBJ_FLAG_HIDDEN);
            }
        }
        int mw = 33, mh = 7, my = 119;
        switch (mode) {
            case 1: // listening
                mw = 30; mh = 6 + static_cast<int>(2 * std::fabs(std::sin(t / 330.f))); break;
            case 2: // thinking/connecting
                mw = 22; mh = 6; my = 122; break;
            case 3: // speaking: a changing mouth, not an artificial waveform
                mw = 24 + static_cast<int>(3 * std::sin(t / 190.f));
                mh = 6 + static_cast<int>(14 * std::fabs(
                     std::sin(t / 125.f) * std::cos(t / 305.f))); break;
            case 4: // happy
                mw = 50; mh = 12 + static_cast<int>(2 * std::sin(t / 430.f)); break;
            case 5: // surprised
                mw = 20; mh = 22; break;
            case 6: // sad/confused
                mw = 26; mh = 5; my = 125; break;
            case 7: // sleepy
                mw = 20; mh = 4; my = 127; break;
            case 8: // wink
                mw = 42; mh = 9; break;
            case 9: // angry
                mw = 31; mh = 4; my = 122; break;
            default: // neutral, slow breathing
                mh = 7 + static_cast<int>(2 * std::sin(t / 1000.f)); break;
        }
        lv_obj_set_size(mouth_, mw, mh);
        lv_obj_align(mouth_, LV_ALIGN_TOP_LEFT, 106 - mw / 2, my);
    }
};
