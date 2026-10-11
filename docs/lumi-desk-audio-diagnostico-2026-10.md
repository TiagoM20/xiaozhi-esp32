# LUMI DESK — Diagnóstico de gaguez, silêncio e ruído na coluna (11/10/2026)

**Estado: investigação do código e documentação oficial. Sem alteração de firmware nem flash.** Verificar qual o binário real instalado antes de atribuir a causa a uma versão do GitHub.

## Hardware exato

Waveshare **ESP32-S3-Touch-LCD-1.54**, Flash 16MB / PSRAM 8MB, microfones via **ES7210**, codec da coluna **ES8311**, amplificador **NS4150B**, LCD SPI ST7789 240x240, e Wi-Fi 2.4GHz. O firmware correspondente está em `main/boards/waveshare/esp32-s3-touch-lcd-1.54`.

- `config.h`: `AUDIO_INPUT_SAMPLE_RATE 24000`, `AUDIO_OUTPUT_SAMPLE_RATE 24000`, `AUDIO_CODEC_PA_PIN GPIO_NUM_7`, I2S MCLK 8, BCLK 9, WS 10, DOUT 12, DIN 11.
- `esp32-s3-touch-lcd-1.54.cc`: usa `BoxAudioCodec(..., true)` para áudio full-duplex, com referência de captação. Verificar AEC antes de alterar modos.
- `main/audio/codecs/box_audio_codec.cc`: usa `esp_codec_dev` para saída, ES8311 para DAC e ES7210 para entrada; `EnableOutput` liga/desliga via driver. Não controlar diretamente o PA em paralelo com o driver sem verificar a sequência de power/mute.

Documentação do fabricante: https://docs.waveshare.com/ESP32-S3-Touch-LCD-1.54

## O que está no firmware do GitHub (hipóteses)

1. `main/audio/audio_service.h`: `MAX_DECODE_PACKETS_IN_QUEUE = 1200 / 60 = 20` pacotes; fila PCM de saída tem apenas **2** tarefas. A reprodução inicia sem pre-buffer explícito. Sob atrasos irregulares / CPU ocupada, a fala pode ficar entrecortada.
2. `main/audio/audio_service.cc` `PushPacketToDecodeQueue()`: quando chega ao limite, com `wait=false`, devolve `false` (drop do pacote). O callback em `main/application.cc` ignora esse resultado, impossibilitando distinguir packet loss de má qualidade TTS sem diagnósticos.
3. O callback `OnIncomingAudio` só enfileira pacotes quando `GetDeviceState() == kDeviceStateSpeaking`. A UI muda para falar ao receber `tts start`, antes de confirmar chegada/reprodução Opus. Por isso **mostrar «a falar» não prova áudio audível**.
4. `AudioOutputTask()` escreve PCM no ES8311, mas sem telemetria de espera na fila. O decoder já regista erros com `Failed to decode audio`.
5. `CheckAndUpdateAudioPowerState()` não desativa a saída após 15 s se entrada duplex estiver ativa, por comentário explícito: não parar o relógio TX enquanto o RX necessita dele. Isto pode deixar o amplificador ativo em silêncio e amplificar ruído. **Não remover a condição:** pode bloquear o microfone.
6. LCD e GIFs compartilham recursos do ESP32-S3 com áudio. Congelar temporariamente as animações **em teste controlado**, sem alterar assets, ajudará a despistar sobrecarga.

## Outros projetos com sintomas semelhantes

- [Issue XiaoZhi #1844](https://github.com/78/xiaozhi-esp32/issues/1844): cortes TTS e estado «speaking» preso numa **outra** Waveshare. Caso comparável, não é prova da causa nesta 1.54.
- [Issue ESPHome #109](https://github.com/m5stack/esphome-yaml/issues/109): ruído contínuo de amp NS4150B com DAC ainda sem áudio, em **outro dispositivo**.
- [ES8311+NS4150B, análise independente](https://github.com/Afstkla/esp32-matter-bridge/blob/main/docs/hardware/es8311-audio.md): outro projeto mediu atraso de arranque do amplificador (~175 ms) numa placa diferente. Usar apenas como alerta para sequenciar inicialização, **não assumir o mesmo atraso na nossa Waveshare**.

## Diagnóstico sem programação / sem apagar configurações

1. Em repouso, **sem a LUMI a falar**, ouvir a coluna durante 20 s. Anotar se o ruído já existe.
2. Definir volume do próprio ESP32 para ~30–40% usando o botão físico. Repetir. Se o ruído acompanha o volume, suspeitar do ganho/cadeia de áudio; se continua igual, investigar amplificador/alimentação.
3. Testar a mesma resposta com cabo USB-C/fonte estável diferente; se há bateria corretamente instalada, comparar alimentação da bateria. Evitar ligações improvisadas.
4. Comparar um som local OGG (ex. alerta embutido) com fala online. **Som local bom + TTS gagueja** indica problema a montante (rede/servidor/decoder/carga); **ambos maus** aponta à cadeia local DAC/I2S/amp/CPU.
5. No monitor série via ambiente ESP-IDF (porta e baud corretos), capturar 30–60 s de reprodução e verificar `AudioService`, `Failed to decode audio`, `Encode queue is full`, `I2S`, `WDT`, `heap` e desconexões. Não partilhar tokens nem configurações secretas.

## Possíveis correções programáticas (por ordem, depois de medir)

1. **Observabilidade primeiro:** adicionar contadores agregados (a cada 5 s, nunca em cada pacote): pacotes recebidos, descartados por estado/fila, erros do decoder, nível máximo/mínimo das filas, latência de saída I2S, memória livre, reinícios do codec. Incluir flags compiláveis; guardar o binário atual para reversão.
2. **Jitter buffering:** testar pré-buffer de 2–4 quadros Opus (120–240 ms) no início de cada fala, com esvaziamento ao fim e controlo de interrupção. Eleva latência mas pode suavizar gaguez. Não alterar `MAX_*` cegamente nem bloquear o callback de rede.
3. **Prioridades concorrentes:** comparar comportamento com GIFs temporariamente congelados durante fala (diagnóstico) e monitorizar carga; só otimizar LVGL/SPI caso haja correlação.
4. **Amplificador/ruído:** investigar mute digital do ES8311 e transições PA GPIO7 (com driver responsável), rampa de volume e desativação analógica do amp no repouso **sem parar clocks I2S necessários ao microfone**. Confirmar polaridade e atraso antes de mexer.
5. Testar fonte de alimentação e estado do altifalante antes de compensar por software interferências elétricas; o firmware não consegue corrigir componentes defeituosos.

## Voz

A utilizadora prefere `pt-PT-RaquelNeural` ou `pt-pt-x-sfs-network` (soa mais natural). Esta última é **voice network** do motor Android e não serve como garantia de fala sem Internet; nem a escolha da voz no tablet muda automaticamente a TTS da DESK. Esperar até a reprodução estar estável antes de migrar o backend da DESK.

## Limites da investigação

Os sintomas foram descritos pelo utilizador; **não houve medição física, recolha de logs de série nem teste de áudio no dispositivo**. Os arquivos GitHub podem diferir da imagem atual flashada na Waveshare. Nenhuma correção foi validada ainda no hardware.
