# LUMI DESK: requisito da palavra de ativação

## Pedido (24 outubro 2026)
Palavra principal única: **Lumi**, pronunciada "LÚ-mi" em português de Portugal.
Não ativar por "Olá Lumi" nem por "Hey Lumi" até existir um modelo genuíno e ensaiado.
O WakeNet pode ter até cinco palavras treinadas; o nosso projeto poderá aceitar duas ou três depois, mas só se não aumentar os falsos despertares.

Hardware: Waveshare ESP32-S3-Touch-LCD-1.54; firmware XiaoZhi 2.5.3; sistema português europeu; voz Raquel PT-PT.
A assistente não deve falar quando não foi chamada. Deve escutar perguntas de seguimento durante ~15 segundos depois de responder. Manter 21 GIFs e NVS, backup integral e rollback.

## Por que não basta mudar o texto
Os modelos nativos precisam de treino prévio; não há atualmente um modelo pronto "Lumi" no catálogo ESP-SR.
No MultiNet inglês, a frase "Olá Lumi" exigiu oito tentativas e houve vários falsos positivos. "Hey Lumi" não foi reconhecido. O modelo Hi ESP não teve boa utilização pelo utilizador. A nova versão "Olá ESP" em português foi compilada, mas reconhece "Olá ESP", não "Lumi".

## Rascunho de pedido de treino ao fornecedor (não enviado)
Destination: https://github.com/espressif/esp-sr/issues/88
Target hardware: ESP32-S3, Waveshare ESP32-S3-Touch-LCD-1.54.
Language: Portuguese (Portugal, pt-PT).
Requested trained wake phrase: "Lumi" (two syllables, Portuguese pronunciation LÚ-mi).
Optional future variants: "Olá Lumi" and "Hey Lumi", if a single model supports variants without unacceptable false activations.
Desired model: WakeNet10 in ESP-SR >=2.6, trained on Portuguese TTS with full model files, MODEL_INFO and integration instructions.
Project: https://github.com/TiagoM20/xiaozhi-esp32
Must be robust around normal family speech and children playing nearby.
Request open-access model if possible; private custom training may incur charges.

## Critérios de aceitação
1. Pelo menos 9 em 10 ativações verdadeiras dizendo "Lumi" normalmente a ~1m.
2. Zero ativações e respostas espontâneas durante 30 minutos de conversa familiar normal e ruído ambiente no ensaio.
3. Depois da resposta, continua a escutar durante até 15 segundos sem nova palavra de ativação.
4. Volta a repouso silencioso quando a conversa termina.
5. Backup, recuperação das duas partições, 21 GIFs e NVS intactos.
Estes são critérios de um ensaio local, não garantias estatísticas de zero falhas.

## Estado
Perfil em lumi_wake_profile.json.
validate_lumi_model.py bloqueia builds quando o modelo treinado não existe e quando respostas espontâneas são permitidas.
Não há firmware "Lumi" instalável: não substituir pela frase nativa "Olá ESP" ou modelo em inglês sem informar o utilizador.
