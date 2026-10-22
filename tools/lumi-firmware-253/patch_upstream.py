#!/usr/bin/env python3
"""Minimal LUMI changes on pinned upstream XiaoZhi 2.5.3.
Safe by design: fail on source drift; never touch NVS, GIFs, keys or partitions.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "upstream"
APP = ROOT / "main/application.cc"
HEADER = ROOT / "main/application.h"
CONFIG = ROOT / "main/boards/waveshare/esp32-s3-touch-lcd-1.54/config.json"
VERSION = ROOT / "CMakeLists.txt"


def replace_once(source, before, after, label):
    n = source.count(before)
    if n != 1:
        raise RuntimeError(f"{label}: expected one anchor; found {n}. Stop, review upstream.")
    return source.replace(before, after, 1)


def between(source, begin, end, replacement, label):
    if source.count(begin) != 1 or source.count(end) != 1:
        raise RuntimeError(f"{label}: upstream anchors changed; stop instead of guessing")
    a, b = source.index(begin), source.index(end)
    if a >= b:
        raise RuntimeError(f"{label}: invalid source ordering")
    return source[:a] + replacement + source[b:]


assert 'set(PROJECT_VER "2.5.3")' in VERSION.read_text(encoding="utf-8"), "Wrong upstream version"
app = APP.read_text(encoding="utf-8")
header = HEADER.read_text(encoding="utf-8")

# Make queued wake events harmless while responding or already listening.
start_wake = "void Application::HandleWakeWordDetectedEvent() {"
end_wake = "void Application::BeginWakeWordInvoke("
old = app[app.index(start_wake):app.index(end_wake)]
assert "AbortSpeaking(kAbortReasonWakeWordDetected);" in old, "Upstream wake behavior changed"
wake = """void Application::HandleWakeWordDetectedEvent() {
    if (!protocol_) {
        return;
    }
    auto state = GetDeviceState();
    // LUMI: ignore queued wake recognition until the conversation is idle.
    if (state == kDeviceStateSpeaking || state == kDeviceStateListening) {
        ESP_LOGW(TAG, "LUMI: ignoring wake event during conversation (state=%d)", (int)state);
        return;
    }
    auto wake_word = audio_service_.GetLastWakeWord();
    ESP_LOGI(TAG, "Wake word detected: %s (state: %d)", wake_word.c_str(), (int)state);
    if (state == kDeviceStateIdle) {
        BeginWakeWordInvoke(wake_word);
    } else if (state == kDeviceStateNotifying) {
        StopNotification();
        BeginWakeWordInvoke(wake_word);
    } else if (state == kDeviceStateActivating) {
        SetDeviceState(kDeviceStateIdle);
    }
}

"""
app = between(app, start_wake, end_wake, wake, "wake-word guard")

# Do not activate the microphone/wake detector while the device is speaking.
old_speaking = """        case kDeviceStateSpeaking:
            display->SetStatus(Lang::Strings::SPEAKING);

            if (listening_mode_ != kListeningModeRealtime) {
                audio_service_.EnableVoiceProcessing(false);
                // Only AFE wake word can be detected in speaking mode
                audio_service_.EnableWakeWordDetection(audio_service_.IsAfeWakeWord());
            }
            audio_service_.ResetDecoder();
            break;"""
new_speaking = """        case kDeviceStateSpeaking:
            display->SetStatus(Lang::Strings::SPEAKING);
            // LUMI: never capture speech or wake while playing a spoken answer.
            audio_service_.EnableVoiceProcessing(false);
            audio_service_.EnableWakeWordDetection(false);
            audio_service_.ResetDecoder();
            break;"""
app = replace_once(app, old_speaking, new_speaking, "speaking mode")

# Preserve 15-second silence timeout from the working custom firmware.
app = replace_once(
    app,
    """        if (bits & MAIN_EVENT_VAD_CHANGE) {
            if (GetDeviceState() == kDeviceStateListening) {
                auto led = Board::GetInstance().GetLed();""",
    """        if (bits & MAIN_EVENT_VAD_CHANGE) {
            if (GetDeviceState() == kDeviceStateListening) {
                listening_silence_since_us_ = audio_service_.IsVoiceDetected()
                    ? 0 : esp_timer_get_time();
                auto led = Board::GetInstance().GetLed();""",
    "voice activity")
app = replace_once(
    app,
    """        if (bits & MAIN_EVENT_CLOCK_TICK) {
            clock_ticks_++;
            auto display = Board::GetInstance().GetDisplay();""",
    """        if (bits & MAIN_EVENT_CLOCK_TICK) {
            clock_ticks_++;
            // Only timeout a fully enabled microphone in auto-stop mode;
            // do not count silence while outgoing TTS audio is still draining.
            if (GetDeviceState() == kDeviceStateListening &&
                listening_mode_ == kListeningModeAutoStop &&
                !pending_listening_start_ &&
                audio_service_.IsAudioProcessorRunning() &&
                audio_service_.IsPlaybackIdle()) {
                if (audio_service_.IsVoiceDetected()) {
                    listening_silence_since_us_ = 0;
                } else {
                    const int64_t now = esp_timer_get_time();
                    if (listening_silence_since_us_ == 0) {
                        listening_silence_since_us_ = now;
                    } else if (now - listening_silence_since_us_ >= 15000000) {
                        ESP_LOGI(TAG, "LUMI: 15s silence, closing conversation");
                        listening_silence_since_us_ = 0;
                        if (protocol_ && protocol_->IsAudioChannelOpened()) {
                            protocol_->CloseAudioChannel();
                        } else {
                            SetDeviceState(kDeviceStateIdle);
                        }
                    }
                }
            }
            auto display = Board::GetInstance().GetDisplay();""",
    "silence watchdog")
app = replace_once(
    app,
    """    DeviceState new_state = state_machine_.GetState();
    clock_ticks_ = 0;""",
    """    DeviceState new_state = state_machine_.GetState();
    clock_ticks_ = 0;
    listening_silence_since_us_ = 0;""",
    "state timer reset")
app = replace_once(
    app,
    """    protocol_->SendStartListening(listening_mode_);
    audio_service_.EnableVoiceProcessing(true);""",
    """    protocol_->SendStartListening(listening_mode_);
    audio_service_.EnableVoiceProcessing(true);
    listening_silence_since_us_ = esp_timer_get_time();""",
    "start listening timer")

# Avoid waking on the assistant's own audio, but continue normal user ASR
# once playback finishes. Upstream already defers that transition correctly.
app = between(
    app,
    "void Application::ConfigureWakeWordForListening() {",
    "void Application::StartNotification(",
    """void Application::ConfigureWakeWordForListening() {
    // LUMI: conversation input remains enabled, wake word is idle-only.
    audio_service_.EnableWakeWordDetection(false);
}

""",
    "listening wake guard")

header = replace_once(
    header,
    "    bool pending_listening_start_ = false;  // Waiting for playback to drain before starting listening (auto mode)",
    "    bool pending_listening_start_ = false;  // Waiting for playback to drain before starting listening (auto mode)\n"
    "    int64_t listening_silence_since_us_ = 0;  // Last moment without detected user speech",
    "header silence field")

# XiaoZhi 2.5.3 builder now deliberately overrides wake selections.
# Teach this pinned builder ONE local custom choice (avoid choosing stock "Hi ESP").
BUILDER = ROOT / "scripts/build.py"
builder = BUILDER.read_text(encoding="utf-8")
builder = replace_once(
    builder,
    '    if normalized == "disabled":\n        options.append("CONFIG_WAKE_WORD_DISABLED=y")',
    '    if normalized == "lumi_custom":\n'
    '        if target not in _AFE_WAKE_WORD_TARGETS:\n'
    '            raise ValueError("LUMI custom wake requires an AFE-capable target")\n'
    '        options.append("CONFIG_USE_CUSTOM_WAKE_WORD=y")\n'
    '        return normalized, options, ["CONFIG_USE_CUSTOM_WAKE_WORD"]\n'
    '    if normalized == "disabled":\n'
    '        options.append("CONFIG_WAKE_WORD_DISABLED=y")',
    "custom wake selection in XiaoZhi 2.5.3 builder",
)
BUILDER.write_text(builder, encoding="utf-8")

assert "MAIN_EVENT_PLAYBACK_DRAINED" in app, "Loss of upstream playback drain fix"
assert "audio_service_.IsPlaybackIdle()" in app, "Loss of upstream playback check"
assert "ShowStartupWakeWordHint()" in app, "Loss of upstream 2.5.3 functionality"
assert "AbortSpeaking(kAbortReasonWakeWordDetected)" not in app[app.index(start_wake):app.index(end_wake)]
APP.write_text(app, encoding="utf-8")
HEADER.write_text(header, encoding="utf-8")

board = json.loads(CONFIG.read_text(encoding="utf-8"))
assert board["type"] == "esp32-s3-touch-lcd-1.54"
assert len(board["builds"]) == 1
opts = board["builds"][0]["sdkconfig_append"]
overrides = {
    "CONFIG_USE_DEVICE_AEC": "n",
    "CONFIG_USE_SERVER_AEC": "n",
    "CONFIG_WAKE_WORD_DISABLED": "n",
    "CONFIG_USE_ESP_WAKE_WORD": "n",
    "CONFIG_USE_AFE_WAKE_WORD": "n",
    "CONFIG_USE_CUSTOM_WAKE_WORD": "y",
    "CONFIG_CUSTOM_WAKE_WORD": '"ola lumi"',
    "CONFIG_CUSTOM_WAKE_WORD_DISPLAY": '"Olá Lumi"',
    "CONFIG_CUSTOM_WAKE_WORD_THRESHOLD": "20",
    "CONFIG_SEND_WAKE_WORD_DATA": "y",
    "CONFIG_SR_MN_CN_NONE": "y",
    "CONFIG_SR_MN_EN_MULTINET6_QUANT": "y",
}
opts = [item for item in opts if item.split("=", 1)[0] not in overrides]
opts.extend(f"{k}={v}" for k, v in overrides.items())
board["builds"][0]["sdkconfig_append"] = opts
CONFIG.write_text(json.dumps(board, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("LUMI 2.5.3 patched: wake idle-only; no self-interruption; playback drain; 15s silence; pt-PT custom wake config")
