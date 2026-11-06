#pragma once
// LUMI DESK SD media service, opt-in only. NEVER mount the card during boot.
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>

struct sdmmc_card_t;

class LumiSdMedia {
public:
    LumiSdMedia() = default;
    LumiSdMedia(const LumiSdMedia&) = delete;
    LumiSdMedia& operator=(const LumiSdMedia&) = delete;

    // Status MUST NOT touch SDMMC hardware; it is safe even with a faulty card.
    std::string Status();
    std::string Mount();
    std::string List(const std::string& folder);
    std::string Play(const std::string& filename, bool long_audio);
    std::string ShowImage(const std::string& filename);
    std::string Stop();
    std::string SetTimer(int seconds);
    std::string CancelTimer();

private:
    struct MediaJob {
        LumiSdMedia* self;
        std::string path;
        bool image;
    };
    struct TimerJob {
        LumiSdMedia* self;
        uint32_t generation;
        int seconds;
    };

    static bool SafeName(const std::string& name);
    static bool AllowedExtension(const std::string& name, bool image);
    static void MediaTask(void* context);
    static void TimerTask(void* context);
    bool WaitUntilIdle(int max_seconds);
    void StreamOgg(const std::string& path);
    void DisplayPicture(const std::string& path);
    void Record(const char* kind, const char* info);
    void SetError(const std::string& error);
    std::string LaunchMedia(const std::string& path, bool image);

    std::mutex mutex_;
    sdmmc_card_t* card_ = nullptr;
    bool mounted_ = false;
    std::string last_error_ = "not mounted";
    std::atomic<bool> playing_{false};
    std::atomic<bool> cancel_playback_{false};
    std::atomic<uint32_t> timer_generation_{0};
};
