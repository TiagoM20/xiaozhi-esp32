# LUMI DESK — WakeNet10 PT-PT (laboratório, não instalável)

Esta branch preserva o XiaoZhi online, a voz Raquel e o protótipo de pausas longas
**sem os aplicar ao equipamento atual**.

## Propósito
Compilar a aplicação e os modelos de reconhecimento adequados para o ESP32-S3.
Apenas testar a frase provisória **«Olá ESP»**. «Lumi» depende do
modelo personalizado pedido à Espressif em
https://github.com/espressif/esp-sr/issues/88#issuecomment-6391280213 .

## Diferença face ao firmware atual
- ESP-SR 2.4.7 → 2.6.1 **apenas nesta branch**.
- WakeNet10 `wn10_pt_olaesp` (modelo pt).
- Mantém a arquitetura XiaoZhi cloud e o código de pausa longa da v0.7.
- Um firmware que procura WakeNet10 tem de receber o `srmodels.bin` certo.
  O modelo está na partição **assets**, com os GIFs e dados existentes.

## Segurança e critérios
- A compilação é executada no GitHub Actions em modo laboratorial.
- Apenas gera um relatório e hashes; **não publica firmware para instalar**.
- A auditoria da partição confirma os ficheiros GIF e `wn10_data` renomeado
  para ESP32-S3. Isso não equivale a preservar os GIFs do equipamento real.
- Só podemos avançar para uma instalação depois de comparar o backup integral
  da DESK física com a imagem laboratorial e garantir recuperação de assets.
- A DESK não pode receber apenas a app nova: perderia o reconhecimento de voz
  se os modelos existentes no dispositivo não fossem compatíveis.
- Mesmo que compile, é necessário testar taxa de deteção, falsos positivos,
  Wi-Fi, voz Raquel, GIFs, memória e retoma automática da conversa.

## Estado de trabalho
O CORE 0.7 para pausas longas mantém-se como único ensaio instalável em USB.
Não alterar a DESK sem backup. Não integrar ainda no ramo principal.
