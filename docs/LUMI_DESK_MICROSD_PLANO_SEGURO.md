# LUMI DESK — microSD funcional e seguro (investigação 2026-11-06)

## Contexto e decisão
A V6 tentou montar o SDMMC automaticamente dentro do construtor `CustomBoard` e entrou em boot loop. A versão com WakeNet10 pt-PT «Olá ESP» continua funcional. Não reinstalar V6. Esta branch contém apenas documentação, **NENHUM firmware novo**.

## Projetos concretos consultados
1. Waveshare firmware para a nossa placa: https://github.com/waveshareteam/ESP32-S3-Touch-LCD-1.54 ; `examples/ESP32-S3-Touch-LCD-1.54-demo/ESP-IDF-5.5.1/01_factory/components/esp_bsp/bsp_sdcard.c`; BSP Brookesia na pasta `06_esp-brookesia/components/esp32_s3_touch_lcd_1_54`.
2. Demonstração Waveshare: https://docs.waveshare.com/ESP32-S3-Touch-LCD-1.54/Instructions-For-Use — biblioteca de fotografias, MP3, vídeo e e-books num TF.
3. XiaoZhi KB https://github.com/kangjinshan/xiaozhi-kb — SD e gravação WAV, alerta que gravar logs diretamente nos callbacks provoca falhas de stack e conflitos com display.
4. ESPHome Voice Assistant https://github.com/MichalZaniewicz/esphome-waveshare-esp32-s3-audio-va — boas ideias sobre temporizadores, wake sounds, TTS e alarmes (hardware diferente).
5. Upstream XiaoZhi https://github.com/78/xiaozhi-esp32/issues/1053 e https://github.com/78/xiaozhi-esp32/issues/2070 — pedido SD player e ferramentas de pesquisa/reprodução de música.
6. Referência técnica SDMMC ESP-IDF: https://docs.espressif.com/projects/esp-idf/en/stable/esp32s3/api-reference/peripherals/sdmmc_host.html

## Controlador correto
- Cartão nativo usa **SDMMC**, não SDSPI. A implementação BSP da Waveshare reporta que o modo SDSPI não é suportado na configuração da placa.
- Pins SDMMC modo 1-bit: CLK GPIO16, CMD GPIO15, D0 GPIO17; D1/D2/D3 e restantes devem estar `GPIO_NUM_NC`. CD e WP não utilizados.
- Experimentar 10 MHz, FAT32, montagem opcional `/sdcard`, `format_if_mount_failed=false`; validar cartão e pinout com a documentação oficial.
- SD serve para armazenamento persistente, **não expande PSRAM** e não resolve dois motores de wake simultâneos.

## Primeira funcionalidade útil: biblioteca local
```
/LUMI/
  sons/
    acordar.ogg
    confirmacao.ogg
    aviso.ogg
    temporizador.ogg
  imagens/
    casa.jpg
  registos/
```
- Sons curtos locais de avisos, alarmes e timers, e mais tarde sons de ambiente offline.
- No XiaoZhi existente, `main/audio/audio_service.cc` contém `AudioService::PlaySound`, que aceita **Ogg Opus** por `OggDemuxer`: aproveitar este formato antes de trazer um decoder MP3 extra.
- Áudios maiores só com leitura **em blocos** e controlo de fila. Não carregar ficheiro grande inteiro em RAM; não reproduzir enquanto o TTS fala.
- Ferramentas MCP projetadas: `self.sdcard.status`, `self.sdcard.list_sounds`, `self.sdcard.play_sound`; só ler a pasta de sons e validar nomes dos ficheiros. Não gravar automaticamente conversas nem dados privados.

## Fases seguras
**Fase A — só diagnóstico, sem uso normal do cartão:**
- A partir da branch WakeNet10 funcional, fazer módulo SD opcional **desligado por defeito**.
- Montagem apenas depois de a LUMI já estar em standby, acionada por teste explícito e numa tarefa dedicada de baixa prioridade, nunca no construtor/boot e nunca no callback de áudio ou `esp_log_set_vprintf`.
- O caminho crítico de Wi-Fi, ecrã e wake funciona com/sem cartão. Não formatar.
- Testar identificar, montar, listar, ler, escrever ficheiro temporário e desmontar; logs USB série.

**Fase B — biblioteca local:** sons breves Ogg Opus, indexação e comandos MCP.

**Fase C — opcionais:** imagens 240x240 JPG, músicas/meditações longas com streaming, temporizadores locais. Importar integração UI LVGL existente de forma gradual.

## Regras de aprovação
- Proibir instalar novo firmware sem backup integral 16MB e logs seriais previamente recolhidos.
- Verificar 30 arranques com e sem microSD, 10 ativações «Olá ESP», 10 conversas e 10 reproduções curtas sem panic/watchdog.
- Falha SD não pode fazer restart, mostrar ecrã «A Inicializar» nem bloquear voz, Wi-Fi ou ecrã.
- Nunca substituir o firmware estável antes da validação física.
