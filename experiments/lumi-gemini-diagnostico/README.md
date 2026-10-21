# LUMI Gemini — diagnóstico Arduino, sem flash automático

Experiência **isolada** da base XiaoZhi/ESP-IDF, criada para validar a ligação Wi-Fi e a API Gemini em ESP32-S3.

## Âmbito
- Wi-Fi de casa e segundo Wi-Fi, com reconexão.
- Chave Gemini guardada em NVS, nunca colocada no código ou logs do GitHub.
- Pedido de texto ao Gemini através de HTTPS com validação TLS usando GTS Root R1.
- Modelo configurado: `gemini-3.5-flash-lite`.
- Diagnóstico pelo monitor série USB.

**Não reproduz áudio, não inicializa os codecs ES8311/ES7210, não mantém os olhos nem a ativação «Olá Lumi». Não deve ser gravado na DESK operacional.** A compilação do teste só valida o código e as dependências, não a comunicação com a API nem o hardware.

## Comandos do monitor série
Com a placa de TESTE ligada por USB, 115200 baud:
- `WIFI1 nome_da_rede|palavra-passe`
- `WIFI2 nome_da_rede|palavra-passe`
- `KEY chave_api`
- `RESTART`
- `STATUS`
- `ASK Como estás?`

As credenciais são guardadas em NVS em texto não cifrado: **uso exclusivo de protótipo**, não produção.

## Compilação
O GitHub Actions `lumi-gemini-diagnostico.yml` publica um artifact contendo `firmware.bin` e o bootloader/tabela de partições. Não substituir `xiaozhi.bin` por este ficheiro.

## Estratégia recomendada
Manter o XiaoZhi funcional no firmware principal e integrar Gemini/voz pt-PT por backend, Home Assistant ou ponte controlada, evitando implementar microfone+TTS+codec novamente em Arduino.
