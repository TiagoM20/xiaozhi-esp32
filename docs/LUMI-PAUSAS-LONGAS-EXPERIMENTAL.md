# LUMI DESK: pausas longas — protótipo experimental

**Estado: código de prova de conceito, não validado em hardware nem no XiaoZhi online. Não instalar no dispositivo principal sem backup e testes.**

## Objetivo
Evitar que uma pausa de cerca de 1 s a meio da frase leve o serviço XiaoZhi online a responder antes de o utilizador terminar.

## Arquitetura preservada
- Mesma DESK, mesmo agente **xiaozhi.me**, DeepSeek, memória, voz Raquel e MCP.
- Nenhuma modificação no servidor, na NVS, nem na partição de GIFs / `srmodels.bin`.
- Modo normal inalterado por defeito. Protótipo ativado só em memória; reiniciar repõe modo normal.

## Ativar ou desativar
**Dois cliques rápidos no botão VOLUME +**, apenas com a DESK em repouso (Idle).
O ecrã apresenta “Pausas longas: ATIVADAS/DESATIVADAS”.
Os botões simples de volume continuam a funcionar normalmente.

No modo experimental, após ativação física ou wake word, a DESK envia
`listen/start` com `mode:"manual"`, continua a transmitir áudio ao XiaoZhi,
e observa o VAD local AFE. Quando deteta fala seguida de pelo menos 1800ms
sem fala, envia `listen/stop`, terminando a frase no servidor.
Uma verificação de relógio de ~1s introduz latência extra até ~1s.
O silêncio sem fala continua limitado localmente a cerca de 15s.

**Botão POWER**: na escuta experimental pode forçar o envio de `listen/stop`
quando o detetor local não reconhecer a pausa. Depois de a Raquel responder,
a DESK tenta regressar automaticamente à escuta.

## Hipóteses ainda por validar
1. O servidor oficial `xiaozhi.me` respeita realmente `manual` e só transcreve
   quando recebe `stop`. Não confundir com o servidor open source de terceiros.
2. O AFE VAD identifica fala e silêncio sem falsos positivos, inclusive com
   o som de popup/eco e com voz de baixo volume.
3. Regressar à escuta após TTS com `manual` não quebra a conversação multi-turn.
4. O encerramento 15s, recuperação de erro e gravação do áudio não têm regressões.
5. Os modelos ESP-SR e os 21 GIFs permanecem exatamente como no firmware atual.

## Critérios de teste físico (obrigatório antes de considerar solução)
- Fazer backup integral de 16MB da DESK com o CORE.
- Iniciar em modo normal e confirmar que continua como antes.
- Ativar modo pausas longas e testar dez frases com pausas de 1s, 1.5s e 2s.
- Confirmar resposta só após a frase terminada, sem perder o início ou fim.
- Testar 10 ciclos de resposta→nova pergunta e comando de cancelamento.
- Reiniciar e verificar que modo experimental fica novamente desligado.
- Reverter ao firmware anterior se houver qualquer problema.

**Esta prova de conceito ainda não é uma correção concluída.** É uma forma
controlada de estudar se o protocolo público de escuta manual permite contornar
o endpointing rápido do XiaoZhi hospedado. Não modifica a wake word.
