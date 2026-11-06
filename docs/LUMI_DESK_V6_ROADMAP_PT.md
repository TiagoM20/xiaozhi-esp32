# LUMI DESK — roteiro V6 (6 novembro 2026)

## Limites e prioridades

1. Preservar WakeNet10 PT `wn10_pt_olaesp`, confirmado pelo utilizador na Waveshare 1.54.
2. Não incluir MultiNet6/MultiNet5 em simultâneo, porque V4 provocou boot loop e V5 não reconheceu «Olá Lumi».
3. Manter recuperação integral e backup de 16 MB antes de qualquer flash.
4. Incluir microSD opcional: nunca impedir o boot se ausente, ilegível ou sem FAT32; nunca autoformatar.
5. A linguagem, prosódia e endpointing são responsabilidade do servidor LUMI HUB, não dos modelos WakeNet da DESK.

## Cartão microSD (Waveshare 1.54)

- SDMMC 1-bit a 10 MHz: CLK GPIO16, CMD GPIO15, D0 GPIO17.
- FAT32: montado em `/sdcard` se compatível, caso contrário o programa continua normalmente.
- Diagnóstico `/sdcard/LUMI/boot.log`: guarda apenas causa do reinício e identificação da compilação.
- Ferramenta MCP: `self.sdcard.get_status`.
- Não guarda voz nem conversas e não tenta formatar o cartão.
- A capacidade do cartão serve para ficheiros, não para aumentar a PSRAM.

## «Olá Lumi» com a pronúncia «Olá LÚ-mi»

O modelo `wn10_pt_olaesp` deteta a frase para a qual foi treinado; alterar o texto visível para `Olá Lumi` não produz um modelo novo.

Solução de produção: modelo WakeNet10 português personalizado para a frase `Olá Lumi`, com tonicidade na primeira sílaba de `Lumi`.
A solicitação existente está em https://github.com/espressif/esp-sr/issues/88.
A Espressif já distribui modelos em português, mas treinar novo modelo PT via TTS Pipeline V3 ainda não está confirmado como funcionalidade pública self-service.

Possível amostra real: 10–20 gravações curtas em pt-PT (silêncio antes/depois, diferentes distâncias e intensidades). Só com consentimento e para validar/fornecer ao processo de treino. NÃO se assume que as gravações treinem automaticamente o modelo.

Enquanto não houver modelo específico validado, manter `Olá ESP`; não fabricar pseudo-reconhecimento com `OH LA LOO ME` em inglês.

## Pausas de fala: corrigir no LUMI HUB / endpointing

O temporizador `listening_silence_since_us_` da DESK (15 segundos) fecha uma conversa ociosa; não altera a decisão do servidor de que a pergunta terminou.

No servidor XiaoZhi baseado em SileroVAD, alterar o valor de **fim de frase**, não o temporizador da conversa:

```yaml
VAD:
  SileroVAD:
    threshold: 0.5
    model_dir: models/snakers4_silero-vad
    min_silence_duration_ms: 2500
```

O caminho/capitalização efetivos dependem do servidor instalado; evitar colar diretamente sem encontrar a secção equivalente. Recomenda-se testar 1800, 2500, 3200 ms com frases como «LUMI, qual é o tempo para amanhã? ... e para os próximos cinco dias?». Depois escolher o menor valor que não interrompa a pausa habitual. Isto atrasa a resposta em perguntas muito curtas.

Se o LUMI HUB usa outro servidor ou VAD, aplicar ao endpoint de fim de fala equivalente.

## Personalidade/voz

PT-PT com respostas fluidas, mais alegres e vivas, sem gargalhadas ou exclamações forçadas. Exemplos de instruções para o LLM:

> Fala português europeu. Mantém um tom natural, próximo, bem disposto e expressivo, com frases curtas, alguma variação de ritmo e entusiasmo moderado. Evita apresentações repetitivas e linguagem artificial. Nunca respondas antes de a pessoa terminar a pergunta.

EdgeTTS (caso o HUB esteja a usar `pt-PT-RaquelNeural`): experimentar prosódia `rate="+6%"` e `pitch="+3Hz"`. Não altera a gravação no ESP32; tem de ser ajustado no serviço TTS do HUB. Manter volume normal e testar na coluna real.

## Verificação de uma futura V6

- Confirmar boot, Wi-Fi, expressão de standby, 10 ativações «Olá ESP», 10 conversas completas.
- Confirmar SD presente/ausente sem reinício e consultar `self.sdcard.get_status`.
- Analisar `/sdcard/LUMI/boot.log` e logs seriais.
- Nenhuma formatação automática, gravação de áudio ou gravação de modelos do microSD para PSRAM.
- Apenas depois experimentar separadamente uma nova WakeNet treinada para «Olá Lumi».
