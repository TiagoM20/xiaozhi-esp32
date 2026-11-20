# LUMI DESK — XiaoZhi 2.6.0 + caras douradas (17/11/2026)

**Objetivo:** testar a versão oficial recente, com **a única alteração visual** de usar as expressões faciais sem pupilas criadas na V8, em dourado (`#D8B35A`), para investigar se os reinícios continuam.

**Código oficial:** `78/xiaozhi-esp32` commit `67ca62d4bb7d91e514d7ccd5e6977745002f41f4` (2.6.0). Coincide com o commit mostrado na consola XiaoZhi.

**Placa:** Waveshare ESP32-S3-Touch-LCD-1.54, LCD 240×240, flash 16 MB.

## Exatamente o que é alterado

No código **oficial** da Waveshare:
- acrescenta `#include "lumi_gold_face.h"`;
- troca `new SpiLcdDisplay(...)` por `new LumiGoldFaceDisplay(...)`;
- acrescenta o cabeçalho `lumi_gold_face.h`: seis widgets LVGL; olhos sem íris, sobrancelhas e boca; piscar, respiração, olhar e expressões; boca animada nos estados de fala reais do XiaoZhi; cor fixa dourada; temporizador LVGL a 100 ms.

O único comportamento adicional é visual: `SetStatus` acompanha os estados de ouvir e falar já comunicados pela aplicação. Há 10 atualizações por segundo, sem alocar novos objetos em cada frame.

**Não** copia nenhum dos patches de V5–V8 (AFEs alterados, WakeNet10 PT customizado, wake words duplas, dual-slot forçado, SD/multimédia, novas ferramentas MCP, alterações de heap, NVS, layout ou Wi-Fi). Mantém `CONFIG_USE_DEVICE_AEC=y`, conforme preset oficial da placa. O áudio/IA seguem o XiaoZhi, e pt-PT é selecionado na compilação.

Os modelos de voz disponíveis dependem da build oficial 2.6.0 e dos recursos **efetivamente instalados no equipamento**. Não é prometido reconhecimento de "Lumi".

## Histórico da falha

V5–V8 falharam no hardware. O log de 10/11 reportava:
`assert failed: block_trim_free tlsf_control_functions.h:548 (block_is_free(block))`
depois de o WakeNet10 ser carregado e durante a criação do AFE. Isto **não prova** que a nova cara foi a causa; a corrupção pode ter ocorrido antes. Código de referência: `docs/LUMI_HEAP_TLSF_DIAG_20261110.md` na branch `lumi/diagnostico-tlsf-v3-v8-20261110`.

## Construção

Workflow: `.github/workflows/lumi-260-gold-faces-build.yml`.

A compilação é isolada: checkout exato do commit oficial, aplica somente as duas substituições da placa e acrescenta o cabeçalho; verifica programaticamente que não houve outras alterações. Compila com ESP-IDF v6.1 e português de Portugal. Produz `app.bin`, `elf`, `sdkconfig`, modelo/assets **apenas para inspeção** e manifestos SHA256.

**Aviso:** uma compilação bem sucedida não demonstra que deixou de reiniciar. A gravação na DESK não deve acontecer sem um teste controlado e mecanismo de recuperação.

## Regras para um futuro teste físico

1. Confirmar cópia **integral e verificada** dos 16 MB e preservar num local privado.
2. Ler o **mapa real de partições**, incluindo recursos e `otadata`. O backup anterior tinha `0xFF` onde se pensava estar o cabeçalho de assets em `0x800000`; não assumir que esse offset serve para instalar modelos.
3. **Nunca** apagar toda a flash, instalar template de assets, sobrescrever ambos os slots OTA ou mudar NVS.
4. Confirmar estratégia de recuperação da instalação anterior e **não** confiar num rollback inexistente. Idealmente iniciar num slot OTA inativo através da API OTA oficial e reverter antes do reboot loop, mas este mecanismo **ainda não está implementado**.
5. Capturar UART desde o arranque durante ensaio (anonimizar Wi-Fi/tokens/endereços pessoais).
6. Testar primeiro 10 arranques, depois 30 minutos em repouso e 10 ativações manuais, com atenção a resets, AFE e cor/expressões.

Sem estes passos, o pacote é um **candidato para análise, não uma atualização pronta a instalar**.

## Se o GitHub Actions não iniciar

O código e o workflow estão guardados no PR. O GitHub não iniciou automaticamente uma execução na branch; não publicar qualquer arquivo como se tivesse sido compilado. Aceder às opções GitHub Actions com a conta TiagoM20 para verificar permissão e lançar o workflow, ou compilar numa estação com ESP-IDF 6.1. Não editar/flashar o aparelho a partir do botão de firmware da consola XiaoZhi: esse construtor só compila placas e fontes incorporadas no projeto oficial e não inclui este cabeçalho personalizado.
