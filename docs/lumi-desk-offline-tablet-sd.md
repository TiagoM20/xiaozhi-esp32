# LUMI DESK — modo offline e servidor de voz no Tab 15

Estado: estudo técnico de 10/10/2026. **O firmware da LUMI DESK não foi alterado.** Nenhuma das alternativas abaixo está ligada ao ESP32; funcionalidades offline devem ser experimentadas em separado.

## Três níveis de funcionamento

1. **Online atual (estável):** "Olá Lumi" no ESP32 -> servidor XiaoZhi -> ASR/LLM/TTS -> Opus -> ESP32. A Raquel Neural já foi testada como MP3 de demonstração, mas falta integrar no backend.
2. **Offline em casa, sem Internet mas com Wi-Fi/LAN:** LUMI DESK mantém microfone, ecrã, GIFs e áudio; Tab 15 atua como servidor local. Tarefas: reconhecer fala PT-PT offline, interpretar intenções domésticas, sintetizar voz PT-PT offline e codificar áudio num fluxo Opus compatível. Requer implementação e validação de um backend LAN XiaoZhi; a app Android atual não o fornece.
3. **Totalmente offline no ESP32, sem tablet:** wake word local, um conjunto limitado de comandos pré-definidos e respostas gravadas no SD. Não confundir com conversa livre de IA; a presença de microSD aumenta armazenamento, NÃO a RAM/CPU. Não é sensato prometer LLM, Whisper e Raquel Neural a correr no ESP32-S3.

## Prova de conceito Android — já adicionada ao Hub

`OfflineVoiceLabActivity` (Dispositivos -> LUMI DESK -> Testar voz PT-PT offline no Tab 15):
- Consulta as vozes disponíveis no motor TTS do Android.
- Filtra localidade exata `pt-PT` e `Voice.isNetworkConnectionRequired() == false`.
- Se existir voz offline PT-PT, permite ouvir uma frase **localmente** com Wi-Fi desligado.
- Se não existir, informa a indisponibilidade; não usa pt-BR nem uma voz que dependa de rede como fallback escondido.
- Não grava voz, não publica as vozes na rede, não interage com ESP32; deixa o TTS ao sair.

## Alternativas de motor offline para Android

| Componente | Opção investigada | Observações |
|---|---|---|
| ASR (voz -> texto) | sherpa-onnx com Whisper tiny multilingue ou Vosk | Whisper suporta português; Vosk tem modelo pequeno `vosk-model-small-pt-0.3` de 31 MB, mas a qualidade em PT-PT é incerta e requer teste real. |
| Resposta/intenções | Regras locais para TV e estado da casa; LLM pequeno posteriormente | A voz PT-PT offline só é útil se o Hub interpretar pelo menos comandos domésticos. Não prometer LLM sem benchmark de RAM/temperatura/latência. |
| TTS (texto -> voz) | Voz offline Android instalada; Piper ou sherpa-onnx PT-PT | O Piper dispõe do modelo `pt_PT-tugão-medium`; sherpa-onnx também lista APK/modelos de PT-PT. Não são Raquel Neural: são vozes distintas que exigem comparação auditiva e licenças. |
| Protocolo com ESP32 | XiaoZhi WebSocket áudio Opus | Exige compreender handshake, frames, timeouts, VAD, interrupções e gestão das sessões; não basta disponibilizar uma porta HTTP de TTS. |
| Execução no tablet | Processo de serviço local Android, com foreground service e gestão de energia | Android pode matar serviços por poupança de energia; antes de investir neste caminho, testar viabilidade de servidor persistente no Blackview Tab 15. |

## MicroSD da Waveshare

O cartão SD pode guardar:
- Frases fixas, notificações ou respostas MP3/OGG/Opus.
- Configurações de comandos, recursos gráficos, histórias áudio e conteúdo de cache.
- Conjunto reduzido de intenções e respostas.

A verificação elétrica depende do **modelo e revisão exata** da placa Waveshare. Não assumir GPIOs nem velocidade SPI/SDMMC a partir de outra Waveshare. Na instalação funcional da LUMI, confirmar layout de flash e partições antes de acrescentar acesso a SD.

## Arquitetura LAN proposta

```text
LUMI DESK ESP32-S3 (wake word, AFE, microfone, GIFs, Opus)
          |    Wi-Fi local sem Internet
          v
BLACKVIEW TAB 15 / LUMI HUB (servidor local opcional)
          |
          +-- ASR pt-PT offline (Whisper tiny ou similar)
          +-- Comandos locais (TV, estado da casa, memórias autorizadas)
          +-- TTS pt-PT offline (Piper/Android engine)
          +-- Codificador Opus -> protocolo de áudio XiaoZhi
          +-- Endpoints LAN autenticados, não expostos à Internet
```

## Próximos testes de validação

1. No tablet, usar Laboratório offline e confirmar se há voz PT-PT sem rede.
2. Medir CPU, RAM e tempo de resposta ASR/TTS com motor offline real.
3. Produzir áudio de teste PT-PT por Piper, confirmar licenças e aceitabilidade.
4. Construir protótipo de servidor local em porta LAN com autenticação e um cliente de teste: **sem apontar ainda a LUMI DESK**.
5. Validar Opus/handshake V2/V3, reprodução, interrupções e 15 s de escuta; só depois configurar ESP32 de modo reversível.
6. Só com autorização e backup do firmware, explorar respostas cache/SD e comandos offline no ESP32.

## Referências

- Firmware XiaoZhi neste repositório: `main/protocols/websocket_protocol.cc`, `main/audio/audio_service.cc`
- https://alphacephei.com/vosk/models
- https://github.com/k2-fsa/sherpa-onnx
- https://k2-fsa.github.io/sherpa/onnx/tts/apk.html
- https://github.com/rhasspy/piper/blob/master/VOICES.md
- https://docs.espressif.com/projects/esp-sr/en/latest/esp32/wake_word_engine/ESP_Wake_Words_Customization.html

**Nota:** O modo offline do Hub é uma investigação e um teste de voz local; não é um serviço de conversação XiaoZhi funcional. A infraestrutura do servidor e o desempenho devem ser demonstrados antes de os anunciar como concluídos.
