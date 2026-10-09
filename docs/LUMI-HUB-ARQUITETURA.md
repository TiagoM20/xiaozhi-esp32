# LUMI Hub — arquitetura doméstica (2026-10-09)

## Decisão

Utilizar **PC Windows sempre ligado** como central local; tablet pode ser painel/controlo, mas não é a opção preferida para serviços persistentes por restrições de processos em segundo plano.

Manter a LUMI ESP32-S3 como interface de voz/olhos. A memória, automações e TV correm no PC.

## Fluxo confirmado pelos mecanismos publicados do XiaoZhi

```
LUMI ESP32-S3 --Wi-Fi--> servidor XiaoZhi (ASR/LLM/TTS)
                                  |
                                  | MCP (WebSocket outbound)
                                  v
                            xiaozhi-client
                                  |
                                  v
                        LUMI Hub no PC Windows
                        /                  \
                Memória SQLite        LG webOS (LAN)
                                      (emparelhamento)
```

O cliente comunitário `xiaozhi-client` suporta um endpoint MCP do serviço xiaozhi.me e servidores MCP locais via configuração. A integração só poderá ser testada depois de confirmar que o servidor usado pela LUMI disponibiliza endpoint MCP e de o titular configurar o URL **localmente** (nunca guardar URL/token no GitHub).

- Ponte: https://github.com/shenjingnan/xiaozhi-client
- Protocolo de ponte alternativa: https://stanleychanh.github.io/MCP2Xiaozhi/quick-start/
- Home Assistant LG webOS: https://www.home-assistant.io/integrations/webostv

## Ordem de implementação

1. Serviço local independente e testável, com API MCP para `lumi.memory_search`, `lumi.memory_save`, `lumi.memory_delete`, `lumi.tv_status`, `lumi.tv_power_off`, `lumi.tv_volume`, `lumi.tv_open_app`.
2. Memória com SQLite local, registos explícitos, edição/apagamento, backups, sem gravação automática de transcrições sensíveis.
3. TV LG emparelhada via rede local com pedido de autorização no próprio televisor, com chave guardada apenas no PC.
4. Conector local ao servidor XiaoZhi via MCP (URL inserido no PC), testes com ferramenta de diagnóstico antes de ativar ações por voz.
5. Panel GUI simples para estado, configurações, permissões e memórias. Inicialização automática com Windows apenas depois de funcionar.
6. Histórias interativas: memória apenas de personagens, capítulos e preferências autorizadas.
7. Diagnóstico dos cortes de TTS e testes da voz pt-PT a realizar separadamente. O dispositivo pt-PT **não implica** sintetizador pt-PT: essa voz depende principalmente do backend.

## Rede e segurança

- PC, LUMI e TV devem comunicar através da mesma rede local ou redes roteáveis com regras apropriadas.
- PC liga *de saída* ao endpoint MCP; não abrir portas do router/internet para o LUMI Hub.
- Serviço de memória privado; dados SQLite apenas no PC com acesso limitado, e ferramenta de apagar memória.
- Controlo de televisão limitado às operações aprovadas; ações destrutivas não implementadas.
- LG webOS requer emparelhamento aceite no ecrã. Desligar e volume geralmente locais; ligar pode necessitar de Wake-on-LAN e a opção adequada na TV.
- Se for usado Home Assistant, preferir instalação adequada e estável; num Windows sem HA, ligação LG direta poderá evitar virtualização/Docker.

## Única informação que falta para instalar

- Confirmar o sistema do PC doméstico (Windows 10/11) e se está na mesma rede que a TV LG.
- Depois, obter no painel do servidor XiaoZhi o MCP endpoint configurado e inseri-lo na GUI local (não enviar credenciais na conversa).

Nenhuma alteração a firmware/partição de assets é necessária para este primeiro marco.
