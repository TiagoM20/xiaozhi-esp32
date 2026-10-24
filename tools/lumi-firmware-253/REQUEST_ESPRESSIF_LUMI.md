# Pedido oficial Espressif: wake word «Lumi» (pt-PT)

**A publicar no comentário da issue oficial:** https://github.com/espressif/esp-sr/issues/88

**Estado:** NÃO PUBLICADO. A ligação GitHub respondeu `403 Resource not accessible by integration` ao tentar comentar a issue da Espressif em 24/10/2026. Não reenviar automaticamente sem permissão.

## Texto preparado para a equipa Espressif

### WakeNet10 community wake-word request: “Lumi” (European Portuguese, ESP32-S3)

Hello Espressif ESP-SR team,

I'd like to request a **genuinely trained native WakeNet wake word for “Lumi”**, pronounced in **European Portuguese (pt-PT)** as **LÚ-mi, /ˈlu.mi/**.

**Active open-source project:** LUMI Desk
- Project repository: https://github.com/TiagoM20/xiaozhi-esp32
- Requirements and safety checks: https://github.com/TiagoM20/xiaozhi-esp32/pull/18
- Board: Waveshare ESP32-S3-Touch-LCD-1.54 (ESP32-S3 with PSRAM, 16 MB flash).
- Firmware: XiaoZhi 2.5.3, ESP-IDF 6.1, experimenting with ESP-SR 2.6.x / WakeNet10.
- Assistant conversation and TTS language: European Portuguese.
- Needs **on-device wake detection independent of a PC**.

**Physical testing so far:** MultiNet6 English custom “Olá Lumi” required eight attempts and often triggered falsely with ordinary family speech. “Hey Lumi” was not recognized reliably. Native “Hi ESP” was difficult to pronounce naturally. Changing thresholds has not solved both missed detections and false triggers.

**Most important requirement:** the device must remain **completely silent when not intentionally addressed**, including while adults talk or play with children nearby. The word “Lumi” is short and may be difficult to distinguish; we'd appreciate guidance on achievable false-acceptance rates and whether WakeNet10 pt-PT can support “Lumi” alone.

**Request:** A community/free TTS-trained **WakeNet10 pt-PT model** for “Lumi”, including the genuine model data, ESP-SR compatibility information, integration instructions, and licensing. We understand that changing a command string does not train a model.

Initially please train **only “Lumi”**. “Olá Lumi” and “Hey Lumi” would be optional future variants, if multi-keyword training can preserve very low false-activation rates. We can test the model on the actual ESP32-S3 and report anonymized positive/negative detection results.

Could you consider adding “Lumi” to your Portuguese wake-word training queue and advise whether you need more pronunciation or project details?

Thank you.

## Pesquisa de alternativas

1. **Comunidade Espressif (preferencial):** a issue #88 aceita pedidos de modelos TTS comunitários associados a projetos públicos. Sem garantia de aceitação ou prazo. Publicar o comentário acima diretamente, através de conta GitHub com acesso de escrita ao issue. Fonte: https://github.com/espressif/esp-sr/issues/88
2. **Treino comercial Espressif:** contacto oficial sales@espressif.com. Segundo a documentação, tarifa de personalização única, sem royalties por dispositivo; pedir orçamento antes de assumir custos. Fonte: https://docs.espressif.com/projects/esp-sr/en/latest/esp32s3/wake_word_engine/ESP_Wake_Words_Customization.html
3. **microWakeWord (plano B sem depender da Espressif):** pode criar um modelo INT8 TFLite personalizado com vozes portuguesas para ESP32-S3, mas **não pode ser carregado diretamente no XiaoZhi/ESP-SR**. Exigiria integrar um motor TensorFlow Lite Micro alternativo no firmware ou migrar a stack; maior esforço e risco de interferir com GIFs e áudio. Fontes: https://microwakeword.com/ ; https://esphome.io/components/micro_wake_word/ ; https://github.com/temm1e-labs/customWakeWord/blob/main/RELEASE_PROTOCOL.md
4. **Atenção à licença**: modelos microWakeWord públicos podem ser para uso pessoal/não comercial; confirmar licença individual se o projeto passar a produto.

## Condições para instalar

Não emitir nova versão .exe até existir modelo treinado confirmado e pacote instalável preservando os 21 GIFs. Provas físicas: 9/10 ativações verdadeiras em voz normal; zero despertares nos primeiros 30 min de ambiente familiar (amostra limitada); seguimento 15 s depois de responder; nenhuma fala sem pedido; backup e restauro verificados.

Uma wake word de só duas sílabas tende a ter colisões com fala corrente. Não prometer «0 falsos positivos» sem avaliação muito mais longa.
