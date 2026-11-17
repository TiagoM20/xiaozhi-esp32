#pragma once

// LUMI GOLD — XiaoZhi 2.6.0 baseline face-only experiment.
// Only replaces the display renderer of Waveshare ESP32-S3-Touch-LCD-1.54.
// No custom wake word, audio engine, heap patches, MCP, SD, NVS, assets or Wi-Fi.
// Retains the no-iris two-eye design from LUMI V8, fixed GOLD (#D8B35A).
#include "display/lcd_display.h"

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
        // ~4 frames/s rather than the older V8's ~12fps; reduced LVGL work.
        animation_=lv_timer_create(&LumiGoldFaceDisplay::Tick,250,this);
        ESP_LOGI("LUMI_GOLD","Gold face initialized (6 LVGL widgets, 250ms timer)");
    }

    bool SetEmotion(const char* emotion) override {
        const char* value=emotion?emotion:"neutral";
        if(face_==nullptr) return SpiLcdDisplay::SetEmotion(value);
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

    static int Mood(const char* s) {
        if(!strcmp(s,"happy")||!strcmp(s,"laughing")||
           !strcmp(s,"loving")||!strcmp(s,"kissy")||!strcmp(s,"excited"))return 4;
        if(!strcmp(s,"surprised")||!strcmp(s,"shocked"))return 5;
        if(!strcmp(s,"confused")||!strcmp(s,"angry")||!strcmp(s,"sad"))return 6;
        if(!strcmp(s,"sleepy")||!strcmp(s,"sleeping"))return 7;
        if(!strcmp(s,"thinking")||!strcmp(s,"cool"))return 2;
        if(!strcmp(s,"listening")||!strcmp(s,"curious"))return 1;
        if(!strcmp(s,"speaking")||!strcmp(s,"talking"))return 3;
        return 0;
    }

    static void Tick(lv_timer_t* timer) {
        auto* face=static_cast<LumiGoldFaceDisplay*>(lv_timer_get_user_data(timer));
        if(face && face->face_)face->DrawFrame();
    }

    void DrawFrame() {
        if(!face_||!eye_[0]||!eye_[1]||!mouth_)return;
        const int64_t ms=esp_timer_get_time()/1000;
        const bool blink=(ms%5700)>5490;
        const int breathe=(int)(std::sin(ms/900.f)*2.f);
        const int glance=(int)(std::sin(ms/1900.f)*3.f);
        const int width=mood_==5?36:mood_==2?34:43;
        const int height=(mood_==7)?4:blink?3:mood_==4?28:width;
        const int y=40+breathe+(mood_==2?7:0);
        for(int i=0;i<2;i++){
            const int x=(i==0?40:128)+glance;
            lv_obj_set_size(eye_[i],width,height);
            lv_obj_align(eye_[i],LV_ALIGN_TOP_LEFT,x,y+(width-height)/2);
            const bool expressive=(mood_==2||mood_==5||mood_==6||mood_==4);
            if(expressive){
                lv_obj_remove_flag(brow_[i],LV_OBJ_FLAG_HIDDEN);
                lv_obj_set_size(brow_[i],33,4);
                lv_obj_align(brow_[i],LV_ALIGN_TOP_LEFT,x+2,y-(mood_==5?19:12)+(i?2:0));
            }else{
                lv_obj_add_flag(brow_[i],LV_OBJ_FLAG_HIDDEN);
            }
        }
        int mouthW=34,mouthH=7,mouthY=119;
        switch(mood_){
          case 3: mouthW=21;mouthH=7+(int)(11*std::fabs(std::sin(ms/150.f)));break;
          case 4: mouthW=49;mouthH=11;break;
          case 5: mouthW=20;mouthH=21;break;
          case 6: mouthW=24;mouthH=5;mouthY=124;break;
          case 7: mouthW=22;mouthH=4;mouthY=126;break;
          default: break;
        }
        lv_obj_set_size(mouth_,mouthW,mouthH);
        lv_obj_align(mouth_,LV_ALIGN_TOP_LEFT,106-mouthW/2,mouthY);
    }
};
