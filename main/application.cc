name: LUMI 2.5.1 - Ola Lumi - Sem interrupcoes

on:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest

    container:
      image: espressif/idf:v6.1

    steps:
      - name: Obter codigo XiaoZhi
        uses: actions/checkout@v4

      - name: Configurar LUMI
        shell: bash
        run: |
          python3 - <<'PY'
          import json
          from pathlib import Path

          path = Path("main/boards/waveshare/esp32-s3-touch-lcd-1.54/config.json")
          config = json.loads(path.read_text(encoding="utf-8"))
          options = config["builds"][0]["sdkconfig_append"]
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
          options = [
              item for item in options
              if item.split("=", 1)[0] not in overrides
          ]
          options.extend(f"{key}={value}" for key, value in overrides.items())
          config["builds"][0]["sdkconfig_append"] = options
          path.write_text(
              json.dumps(config, indent=2, ensure_ascii=False) + "\n",
              encoding="utf-8"
          )
          PY

      - name: Esperar que a voz termine antes de escutar
        shell: bash
        run: |
          python3 - <<'PY'
          import re
          from pathlib import Path

          p = Path("main/application.cc")
          source = p.read_text(encoding="utf-8")

          marker = '#define TAG "Application"'
          assert source.count(marker) == 1, "TAG nao encontrado"
          source = source.replace(marker, marker + '''

          // LUMI: do not activate the conversation mic until audio has drained.
          static bool lumi_waiting_for_speech_end = false;
          static int64_t lumi_playback_idle_since_us = 0;''', 1)

          start_marker = 'if (strcmp(state->valuestring, "start") == 0) {'
          stop_marker = '} else if (strcmp(state->valuestring, "stop") == 0) {'
          sentence_marker = '} else if (strcmp(state->valuestring, "sentence_start") == 0) {'
          assert all(source.count(m) == 1 for m in (start_marker, stop_marker, sentence_marker)), "Estrutura TTS mudou"
          a = source.index(start_marker)
          b = source.index(stop_marker, a)
          c = source.index(sentence_marker, b)

          # Reset timer on every new TTS start
          start_part = source[a:b]
          pattern = r'(?m)^(\s*)SetDeviceState\(kDeviceStateSpeaking\);$'
          assert len(re.findall(pattern, start_part)) == 1, "TTS start mudou"
          start_part = re.sub(
              pattern,
              lambda m: m.group(1) + 'lumi_waiting_for_speech_end = false;\n'
                        + m.group(1) + 'lumi_playback_idle_since_us = 0;\n'
                        + m.group(1) + 'SetDeviceState(kDeviceStateSpeaking);',
              start_part,
              count=1,
          )

          # Keep speaking state after server TTS STOP until actual audio has ended.
          stop_part = source[b:c]
          pattern = r'(?m)^(\s*)SetDeviceState\(kDeviceStateListening\);$'
          assert len(re.findall(pattern, stop_part)) == 1, "TTS stop mudou"
          stop_part = re.sub(
              pattern,
              lambda m: m.group(1) + 'lumi_waiting_for_speech_end = true;\n'
                        + m.group(1) + 'lumi_playback_idle_since_us = 0;',
              stop_part,
              count=1,
          )
          source = source[:a] + start_part + stop_part + source[c:]

          # A 1 Hz clock event checks for physical playback completion.
          # A 750 ms acoustic cooldown prevents self-triggering immediately after speech.
          tick = '            clock_ticks_++;'
          assert source.count(tick) == 1, "Clock tick mudou"
          source = source.replace(tick, tick + '''
              if (lumi_waiting_for_speech_end) {
                  if (GetDeviceState() != kDeviceStateSpeaking) {
                      lumi_waiting_for_speech_end = false;
                      lumi_playback_idle_since_us = 0;
                  } else if (!audio_service_.IsPlaybackIdle()) {
                      lumi_playback_idle_since_us = 0;
                  } else {
                      int64_t now = esp_timer_get_time();
                      if (lumi_playback_idle_since_us == 0) {
                          lumi_playback_idle_since_us = now;
                      } else if (now - lumi_playback_idle_since_us >= 750000) {
                          lumi_waiting_for_speech_end = false;
                          lumi_playback_idle_since_us = 0;
                          SetDeviceState(kDeviceStateListening);
                      }
                  }
              }''', 1)

          p.write_text(source, encoding="utf-8")
          print("LUMI: espera pelo fim do som antes de voltar a escutar.")
          PY

      - name: Bloquear wake word durante fala e escuta
        shell: bash
        run: |
          python3 - <<'PY'
          from pathlib import Path

          p = Path('main/application.cc')
          source = p.read_text(encoding='utf-8')

          # Block wake recognition during speaking/listening even if it was queued
          # before the audio task was switched off.
          start = source.index('void Application::HandleWakeWordDetectedEvent() {')
          end = source.index('void Application::BeginWakeWordInvoke(', start)
          handler = source[start:end]
          anchor = '    auto state = GetDeviceState();\n    auto wake_word = audio_service_.GetLastWakeWord();'
          assert handler.count(anchor) == 1, 'Wake handler anchor missing'
          handler = handler.replace(anchor,
              '    auto state = GetDeviceState();\n'
              '    if (state == kDeviceStateSpeaking || state == kDeviceStateListening) {\n'
              '        ESP_LOGW(TAG, "LUMI: Ignoring queued wake event during conversation");\n'
              '        return;\n'
              '    }\n'
              '    auto wake_word = audio_service_.GetLastWakeWord();', 1)

          old_branch = '    } else if (state == kDeviceStateSpeaking || state == kDeviceStateListening) {'
          next_branch = '    } else if (state == kDeviceStateActivating) {'
          assert handler.count(old_branch) == 1 and handler.count(next_branch) == 1, 'Wake branches changed'
          a = handler.index(old_branch)
          b = handler.index(next_branch, a)
          handler = handler[:a] + handler[b:]
          source = source[:start] + handler + source[end:]

          # MultiNet custom wake words are implemented through AFE. The previous
          # IsAfeWakeWord() check therefore *enabled* wake detection while speaking.
          start = source.index('        case kDeviceStateSpeaking:')
          end = source.index('        case kDeviceStateNotifying:', start)
          speaking = source[start:end]
          active = '                audio_service_.EnableWakeWordDetection(audio_service_.IsAfeWakeWord());'
          assert speaking.count(active) == 1, 'Speaking wake detector no longer matches'
          speaking = speaking.replace('                // Only AFE wake word can be detected in speaking mode\n', '', 1)
          speaking = speaking.replace(active+'\n', '', 1)
          reset = '            audio_service_.ResetDecoder();'
          assert speaking.count(reset) == 1, 'Speaking decoder reset anchor missing'
          speaking = speaking.replace(reset,
              '            // LUMI: block all wake detection until speaking finishes.\n'
              '            audio_service_.EnableWakeWordDetection(false);\n'
              +reset, 1)
          source = source[:start] + speaking + source[end:]

          # Listening is voice input, never wake detection. Only the idle state
          # enables the 'Ola Lumi' detector to start a session.
          start = source.index('void Application::ConfigureWakeWordForListening() {')
          end = source.index('void Application::StartNotification(', start)
          func = source[start:end]
          assert 'CONFIG_WAKE_WORD_DETECTION_IN_LISTENING' in func, 'Listening wake config changed'
          source = (source[:start]
              + 'void Application::ConfigureWakeWordForListening() {\n'
                '    // LUMI: listen normally, without running the wake-word detector.\n'
                '    audio_service_.EnableWakeWordDetection(false);\n'
                '}\n\n'
              + source[end:])

          # Protect against silent regressions.
          start = source.index('void Application::HandleWakeWordDetectedEvent() {')
          end = source.index('void Application::BeginWakeWordInvoke(', start)
          handler = source[start:end]
          assert 'AbortSpeaking(' not in handler
          assert 'Ignoring queued wake event during conversation' in handler
          start = source.index('        case kDeviceStateSpeaking:')
          end = source.index('        case kDeviceStateNotifying:', start)
          speaking = source[start:end]
          assert 'audio_service_.EnableWakeWordDetection(false);' in speaking
          assert 'audio_service_.EnableWakeWordDetection(audio_service_.IsAfeWakeWord())' not in speaking
          assert 'lumi_waiting_for_speech_end = true;' in source, 'Audio-drain protection missing'
          assert 'lumi_playback_idle_since_us' in source
          p.write_text(source, encoding='utf-8')
          print('LUMI: no wake or abort while speaking, auto-listening after playback.')
          PY

      - name: Compilar firmware LUMI
        shell: bash
        run: |
          source "$IDF_PATH/export.sh"
          python3 scripts/build.py \
            waveshare/esp32-s3-touch-lcd-1.54 \
            --name esp32-s3-touch-lcd-1.54 \
            --language pt-PT

      - name: Verificar configuracao
        shell: bash
        run: |
          grep -qx 'CONFIG_USE_CUSTOM_WAKE_WORD=y' sdkconfig
          grep -qx 'CONFIG_SR_MN_EN_MULTINET6_QUANT=y' sdkconfig
          grep -qx 'CONFIG_LANGUAGE_PT_PT=y' sdkconfig
          grep -qx '# CONFIG_USE_DEVICE_AEC is not set' sdkconfig
          grep -qx '# CONFIG_USE_SERVER_AEC is not set' sdkconfig
          test -s build/xiaozhi.bin
          echo "LUMI: protecao contra cancelamentos e escuta apos fala compilados."

      - name: Guardar firmware
        uses: actions/upload-artifact@v4
        with:
          name: LUMI-2.5.1-Ola-Lumi-Sem-Interrupcoes
          path: |
            build/xiaozhi.bin
            sdkconfig
          retention-days: 7
