# LUMI DESK V8 — EXPERIMENTAL / NÃO INSTALAR

**Base:** `lumi/wakenet10-pt-test-261` (modelo único WakeNet10 pt-PT «Olá ESP»).

**Placa:** Waveshare ESP32-S3-Touch-LCD-1.54, ecrã 240 × 240, flash 16 MB.

## Implementado no código

- Rosto LVGL nativo, olhos circulares **sem íris/pupilas** e animações leves: respirar, olhar, piscar e reagir ao estado enviado pelo XiaoZhi.
- 7 paletas guardadas em NVS: ciano, rosa, violeta, dourado, verde, gelo e coral.
- Ferramentas MCP: `self.lumi.face.set_color`, `self.lumi.face.set_enabled`, `self.lumi.face.react`, `self.lumi.capabilities`. O servidor/agente continua a ser o XiaoZhi.
- Reversão imediata para emojis/GIF: **dois toques no botão Volume +** da DESK ou MCP `self.lumi.face.set_enabled(false)`. A escolha fica guardada localmente.
- Sem microSD no arranque, sem montagem ou formatação; não integra V6/V7.
- Não substitui assets, nem configura Wi-Fi nem mexe no mapa de partições por si só.

## Não implementado — não apresentar como pronto

- Wake word «LUMI» ou «Olá LUMI»: **sem modelo WakeNet10 português compatível validado**; continua «Olá ESP». Não inventar um binário ou renomear `wn10_pt_olaesp`.
- Agente autónomo **ESP-Claw na ESP32**: ainda **não integrado**. O MCP acrescenta ferramentas ao agente XiaoZhi existente; não é um LLM local.
- Configuração automática a partir da aplicação Android HUB: falta expor um canal autenticado e implementar envio de comandos; a seleção de cores no HUB V16 é apenas pré-visualização local até essa integração.
- Áudio, fotografias, vídeo e microSD: funcionalidades V7 **não incluídas**.
- Temporizadores e regras autónomas via ESP-Claw: ainda não implementados.

## Avaliar wake word «LUMI»

1. Continuar a acompanhar [pedido Espressif issue #88](https://github.com/espressif/esp-sr/issues/88#issuecomment-6391280213). Não há modelo entregue/validado.
2. Avaliar em ramo separado o backend `temm1e-labs/xiaozhi-tflite` para modelos INT8 de microWakeWord na ESP32-S3 — mas **sem simultaneidade** com WakeNet10 e sem flash antes de medir memória, latência, falsos positivos e regressões.
3. Quando existir modelo **real** `LUMI` pt-PT, avaliar primeiro num conjunto de gravações e num programa offline, depois compile-time, depois teste isolado na placa com plano de rollback.

## Bloqueios antes de gravar na placa

1. Passar a compilação ESP-IDF e confirmar o tamanho do firmware e assets.
2. Confirmar mapa real das partições da placa e verificar porquê o backup anterior tinha cabeçalho assets `FF` em `0x800000`. Os assets do novo pacote **não devem ser fundidos automaticamente** com os anteriores.
3. Validar o CORE com *dry-run*, SHA-256, backup integral de 16 MB verificável, e mecanismo de restauro.
4. Revisão estática por outra pessoa e recolha da UART desde o reset no primeiro ensaio experimental.
5. Não publicar como firmware «estável», não gerar release auto-instalável, não modificar a placa sem passo de instalação explícito.

> Compilação bem-sucedida prova apenas compatibilidade do código, não arranque, wake word, áudio, consumo de RAM ou conectividade na Waveshare.
