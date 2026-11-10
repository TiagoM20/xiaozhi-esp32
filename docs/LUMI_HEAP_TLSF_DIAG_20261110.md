# LUMI DESK — diagnóstico de corrupção de heap/TLSF (10-11-2026)

**NÃO INSTALAR / NÃO FAZER FLASH.** Versões V5–V8 provocaram ciclos de reinicialização na Waveshare. Este ramo existe apenas para compilar variantes e preparar instrumentação.

## Evidência real

No log da V8 (9 Nov 2026, ELF prefixo b87b57ab4):
- Wi-Fi, MQTT e `MODEL_LOADER: Successfully load srmodels` concluídos.
- `AFE_CONFIG: Set WakeNet Model: wn10_pt_olaesp`.
- `Before AFE create: free=129431 min=39812 largest=77824`.
- `assert failed: block_trim_free tlsf_control_functions.h:548 (block_is_free(block) && "block must be free")`.
- A falha é detetada quando o ESP-DL aloca memória para criar o WakeNet10, mas o código que a corrompeu pode ter corrido antes disso.

## Comparação V3 → V8 (código GitHub)

No código-fonte das branches `lumi/wakenet10-pt-test-261` e `lumi/v8-expressive-safe-20261109`, os ficheiros:
- `main/application.cc`
- `main/audio/engines/afe_audio_engine.cc`
- `main/display/lcd_display.cc`
- `main/idf_component.yml`
- `main/boards/waveshare/esp32-s3-touch-lcd-1.54/config.json`

têm o mesmo Git blob SHA. As alterações funcionais concentram-se no ficheiro da placa Waveshare e no novo cabeçalho `lumi_expressive_display.h`. **Isto não prova que as imagens instaladas têm os mesmos sdkconfig, bibliotecas e dados de flash**, mas permite isolar as diferenças de código.

## Instrumentação

O ficheiro `main/lumi_heap_diagnostic.h` implementa `LumiHeapProbe("checkpoint")`, que usa `heap_caps_check_integrity(MALLOC_CAP_INTERNAL,true)`. Escreve em série as estatísticas `internal, free, largest, min, psram_free`. Não verifica toda a PSRAM em cada checkpoint para não tornar o próprio diagnóstico a causa de um watchdog.

Os checkpoints estão em:

- `CustomBoard`: após power manager, power-save, I2C, SPI, LCD, toque, botões, ferramentas e backlight.
- `Application::Initialize`: antes/depois da criação da board, após `display->SetupUI()`, antes/depois da inicialização do áudio e após `audio_service_.Start()`.
- `Application::CheckAssetsVersion`: antes/depois de `assets.Apply()`.
- `AfeAudioEngine::Initialize`: entrada, após lista de modelos, antes do `afe_config_init`, após a configuração, antes/depois de `esp_afe_handle_from_config` e antes/depois de `create_from_config`.

A primeira linha **`internal=CORRUPT`** delimita o trecho entre a última verificação `OK` e a primeira falha. Não significa necessariamente que o checkpoint provocou a corrupção.

## Compilações comparáveis

O workflow GitHub `lumi-tlsf-heap-diagnostic.yml` compila em paralelo três variantes:

| Variante | LCD | Corruption detection | Wake word |
|---|---|---|---|
| `legacy-lcd-basic` | Original `SpiLcdDisplay`, sem rosto novo e sem MCP do rosto | Basic | WakeNet10 «Olá ESP» |
| `expressive-lcd-basic` | Novo `LumiExpressiveDisplay` | Basic | Idem |
| `expressive-lcd-light-poisoning` | Rosto novo | Light impact | Idem |

Todos os artefactos incluem ELF para análise e têm **`NAO-INSTALAR`** no nome e no manifesto. Não há OTA, publicação de release ou gravação na Waveshare. Uma compilação não demonstra que a firmware funciona.

O efeito do `heap poisoning` na memória pode alterar a reprodução do bug. Mesmo depois de builds bem-sucedidos, falta uma sequência de testes em hardware com UART e recuperação comprovada.

## Instalador CORE

O instalador V8.1 gravava a mesma imagem em `ota_0` e `ota_1`, suprimindo qualquer fallback. **Não continuar a usar esse processo.** A estratégia futura é a OTA suportada pelo ESP-IDF:
1. Ler/validar backup completo 16MB, mapa de partições e hash.
2. Usar o firmware *em execução* para descarregar a imagem para a **partição OTA inativa**, nunca gravar ambos os slots por esptool.
3. Ativar `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE` se suportado, verificar nova imagem e confirmar só após boot, áudio e AFE bem-sucedidos.
4. Falha em boot deve voltar ao slot anterior; verificar `otadata`, `esp_ota_get_state_partition` e `esp_ota_mark_app_valid_cancel_rollback` quando efetivamente implementado.

**Esta OTA segura ainda não está implementada.** O ZIP Windows CORE V8.2 disponível na conversa desativa a instalação e mantém somente backup/restauro explícito.

## Próximo passo autorizado

Continuar investigação no computador. **Não instalar as variantes de diagnóstico na DESK restaurada** sem preparar um procedimento de teste com retorno verificável, monitorização UART e sem substituir o slot válido. Não formatar microSD. Evitar publicar SSID, IPs e logs completos.
