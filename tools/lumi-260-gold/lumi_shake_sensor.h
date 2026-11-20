#pragma once
// Optional Waveshare QMI8658 on existing audio/touch I2C bus.
// No new I2C controller, no modifications to WiFi, audio pipeline, wake word,
// partition table, NVS or assets. Sensor failure disables shake only.
#include "lumi_gold_face.h"
#include "driver/i2c_master.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include <cmath>
#include <cstdint>

class LumiShakeSensor final {
public:
    bool Start(i2c_master_bus_handle_t bus, LumiGoldFaceDisplay* face) {
        if (!bus || !face) return false;
        face_ = face;
        i2c_device_config_t config{};
        config.dev_addr_length = I2C_ADDR_BIT_LEN_7;
        config.device_address = 0x6B; // Waveshare official QMI8658 address
        config.scl_speed_hz = 100000;
        if (i2c_master_bus_add_device(bus, &config, &device_) != ESP_OK) {
            ESP_LOGW("LUMI_SHAKE","QMI8658 unavailable; shake disabled");
            return false;
        }
        uint8_t who = 0;
        if (Read(0x00,&who,1) != ESP_OK || who != 0x05) {
            ESP_LOGW("LUMI_SHAKE","QMI8658 ID invalid (%02x); disabling shake",who);
            i2c_master_bus_rm_device(device_);
            device_=nullptr;
            return false;
        }
        // Manufacturer's ESP-IDF factory sample for this exact 1.54 board.
        if (Write(0x60,0xB0) != ESP_OK) return false;
        vTaskDelay(pdMS_TO_TICKS(100));
        if (Write(0x02,0x40) != ESP_OK || // CTRL1
            Write(0x08,0x03) != ESP_OK || // CTRL7: accel+gyro
            Write(0x03,0x95) != ESP_OK || // CTRL2: +/-4g
            Write(0x04,0xD5) != ESP_OK) { // CTRL3
            ESP_LOGW("LUMI_SHAKE","QMI8658 configuration failed; shake disabled");
            return false;
        }
        if (xTaskCreate(&LumiShakeSensor::Task, "lumi_imu", 3072, this, 2, nullptr) != pdPASS) {
            ESP_LOGW("LUMI_SHAKE","Not enough memory for IMU; shake disabled");
            return false;
        }
        ESP_LOGI("LUMI_SHAKE","QMI8658 enabled for shake->dizzy; idle 20 Hz");
        return true;
    }

private:
    i2c_master_dev_handle_t device_=nullptr;
    LumiGoldFaceDisplay* face_=nullptr;

    esp_err_t Read(uint8_t reg,uint8_t* data,size_t n) {
        if(!device_) return ESP_FAIL;
        return i2c_master_transmit_receive(device_,&reg,1,data,n,100);
    }
    esp_err_t Write(uint8_t reg,uint8_t value) {
        if(!device_) return ESP_FAIL;
        uint8_t payload[2]={reg,value};
        return i2c_master_transmit(device_,payload,2,100);
    }
    static void Task(void* ctx) {
        auto* self=static_cast<LumiShakeSensor*>(ctx);
        bool haveLast=false;
        float last[3]={0.f,0.f,0.f};
        int strong=0;
        int64_t lastHit=0;
        int64_t lastTrigger=0;
        for (;;) {
            uint8_t status=0;
            uint8_t bytes[6]={};
            bool ok=self->Read(0x2E,&status,1)==ESP_OK && (status&0x01) &&
                    self->Read(0x35,bytes,6)==ESP_OK;
            if(ok) {
                float a[3];
                for(int i=0;i<3;++i) {
                    const uint16_t raw=static_cast<uint16_t>(bytes[i*2]) |
                                       (static_cast<uint16_t>(bytes[i*2+1])<<8);
                    const int16_t signedRaw=static_cast<int16_t>(raw);
                    a[i]=static_cast<float>(signedRaw)*4.0f/32768.f;
                }
                const int64_t now=esp_timer_get_time()/1000;
                if (haveLast) {
                    const float dx=a[0]-last[0],dy=a[1]-last[1],dz=a[2]-last[2];
                    const float delta2=dx*dx+dy*dy+dz*dz;
                    // Two distinct sharp changes within 650 ms, 4 s cooldown.
                    if(delta2>0.68f*0.68f && now-lastTrigger>4000) {
                        if(now-lastHit>650) strong=0;
                        ++strong;
                        lastHit=now;
                        if(strong>=2) {
                            self->face_->ShowDizzy();
                            lastTrigger=now;
                            strong=0;
                            ESP_LOGI("LUMI_SHAKE","Shake detected -> dizzy face");
                        }
                    }
                }
                for(int i=0;i<3;++i) last[i]=a[i];
                haveLast=true;
            }
            vTaskDelay(pdMS_TO_TICKS(50));
        }
    }
};
