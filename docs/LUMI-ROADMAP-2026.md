# LUMI — plano de evolução (outubro de 2026)

## Estado atual (ponto de restauro)

- Waveshare ESP32-S3-Touch-LCD-1.54, XiaoZhi 2.5.1, português de Portugal.
- Palavra de ativação local: `Olá Lumi` (MultiNet6 custom, `ola lumi`).
- AEC de dispositivo e de servidor desativados; conversação em modo `auto`.
- Wake word desativada durante `speaking` e `listening`; evento pendente ignorado.
- Escuta termina após 15 segundos de silêncio local (build #8; validar no aparelho).
- 21 GIFs / assets próprios: **não modificar a partição de assets**.
- Atualizações: GitHub Actions + Release `LUMI-firmware.zip` + instalador Windows, apenas `xiaozhi.bin` em `0x20000`, com cópia de segurança e verificação.
- Baseline que funcionou no teste de não interrupção: build #6. A build #8 agrega temporizador de silêncio; regressão a testar.

## Prioridade 1 — áudio estável e português de Portugal

### 1A. Cortes durante o TTS
Hipóteses ainda por confirmar: chegada irregular dos pacotes da rede/servidor, reprodução sem pré-buffer suficiente, tarefas de descodificação e saída em competição, perda/atraso de pacotes, ou problemas no próprio TTS.

Ações seguras:
1. Instrumentar contadores de pacotes recebidos, descodificados, fila vazia durante fala, duração entre pacotes, RSSI e memória (registos sem dados privados).
2. Comparar ensaios perto do router e em outra rede; considerar Wi-Fi convidado/rede isolada.
3. Só depois avaliar buffer de arranque/limite para reprodução; otimizar sem aumentar demasiado a latência.
4. Garantir que nenhuma mudança repõe wake-word a falar ou mexe nos assets.

Critério: resposta > 2 minutos sem auto-interrupções e sem palavras truncadas em condições de rede razoáveis.

### 1B. Voz com sotaque pt-PT
- O idioma de interface configurado no ESP32 NÃO define, por si, a voz de síntese do servidor.
- Confirmar primeiro que serviço TTS está configurado no servidor utilizado (atualmente endpoint remoto de XiaoZhi; acesso a essa configuração ainda por confirmar).
- Testar vozes explicitamente pt-PT e comparar naturalidade, latência e custo; evitar trocar o sistema de voz do ESP32 prematuramente.
- Não armazenar credenciais de fornecedores no GitHub nem no firmware público.
- A implementação pode exigir acesso à consola do fornecedor ou um backend próprio.

## Prioridade 2 — casa inteligente / LG 50UR78006LK (webOS24)

Objetivos iniciais: `liga a TV da sala`, `desliga`, `volume`, `abre o YouTube`.

Arquitetura preferida: ponte local segura / Home Assistant com integração LG webOS e chamadas por MCP, a partir do backend de IA. Alternativa: integração direta no dispositivo ESP32, com emparelhamento e credenciais protegidas.

- Comandos ligados/desligados via webOS/SSAP após emparelhamento na TV; ligar a partir de standby pode requerer Wake-on-LAN e configurações na TV.
- Primeira prova: aceder e comandar a TV a partir da mesma LAN, antes de dar acesso à IA.
- Restrição: LUMI e TV precisam de uma rota de rede válida; redes de convidados podem bloquear dispositivos LAN.
- Requisitos de segurança: lista explícita de ações permitidas e confirmação para ações sensíveis.
- Depende de saber se existe um PC/servidor sempre ligado em casa ou se é preferível solução apenas no ESP32.

## Prioridade 3 — memória pessoal (opt-in)

- Guardar preferências, contexto de histórias e tarefas num backend externo (SQLite/serviço), nunca como promessa de persistência na RAM do ESP32.
- Distinguir memórias da LUMI, estado temporário da conversa e lembretes.
- Política: perguntar antes de memorizar informação sensível; permitir consultar, corrigir, apagar e desativar a memória.
- Não fazer logs de áudio/transcrições inteiras por omissão; guardar apenas itens autorizados.
- Conceber ferramentas MCP como `memory.search`, `memory.save`, `memory.delete`, com autenticação.
- Requer hospedar serviço em PC local sempre ligado, Raspberry Pi ou alojamento remoto; dados e credenciais fora do repositório público.

## Prioridade 4 — histórias interativas

- Utilizar memória consentida para personagens/capítulos, enredo e escolhas.
- Dividir histórias em capítulos com decisões no fim de cada capítulo.
- Manter respostas em blocos curtos para evitar falhas de streaming e facilitar pausas naturais.
- Emoções e GIFs através de identificadores existentes; não substituir as animações nem assets.
- Fazer uma primeira versão apenas ao nível do prompt/backend; alterar firmware só se necessário.

## Regras de entrega

1. Uma alteração isolada por build, com versão e descrição.
2. Compilar em GitHub, confirmar `sdkconfig`, `xiaozhi.bin` e Release.
3. Sempre backup de aplicação e só flash `0x20000`; nunca `erase-flash` ou `assets.bin` por defeito.
4. No hardware, testar: wake `Olá Lumi`, cara/GIFs, resposta longa sem interrupções, transição para escuta, inatividade, Wi-Fi e voz.
5. Ter reversão para build validada. Não alterar remotamente a LUMI sem teste/pedido de instalação.

## Intervenções do utilizador (mínimas)

- Para áudio: um ensaio curto de audição perto do router e, se necessário, um log da COM.
- Para voz pt-PT: confirmar acesso à consola de IA/TTS ou autorizar um backend.
- Para LG: emparelhamento uma vez no ecrã da TV, e confirmação da rede/PC de suporte.
- Para memória: escolher onde guardar os dados e aprovar a política de memórias.
