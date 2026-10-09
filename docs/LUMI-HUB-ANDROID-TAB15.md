# LUMI Hub — central Android Blackview Tab 15

Data: 2026-10-09. **Decisão do utilizador: o Tab 15 substitui o PC Windows 11 como central permanente.**

## Hardware verificado

- Blackview Tab 15: Android 12 com Doke OS_P 3.0, 8 GB RAM, 128 GB armazenamento, Wi-Fi 802.11ac, bateria 8280 mAh; fonte: https://store.blackview.hk/pt/products/tab15-price
- ESP32-S3 LUMI continua responsável por ecrã, 21 GIFs, microfone, coluna, palavra `Olá Lumi`; nenhuma mudança aos assets.
- Televisão: LG 50UR78006LK, webOS24, mesma rede/router (informação do utilizador).

## Objetivos e arquitetura

```text
LUMI (ESP32-S3)  --Wi-Fi-->  servidor XiaoZhi (ASR/LLM/TTS)
                                           |
                                  MCP (validar endpoint)
                                           |
                               Tablet Blackview Tab 15
                               LUMI Hub Android nativo
                               - Serviço em foreground
                               - WebSocket/cliente MCP
                               - Memória SQLite local
                               - Comandos LG webOS
                               - Histórias/capítulos
                               - Controlo bateria/tomada
                                           |
                                      LG webOS (LAN)
```

### Tecnologia para app

- App Android nativa em Kotlin (interface e estado), serviço em primeiro plano com notificação persistente, Room/SQLite, permissões mínimas.
- Sem root. Verificar comportamento real do Doke OS ao bloquear ecrã, após reinício e com poupança de energia; Android 12 limita os serviços iniciados em background.
- MCP: implementar cliente/ponte **após confirmar** o endereço MCP real disponibilizado pela plataforma XiaoZhi e seu método de autenticação; não assumir que qualquer exemplo comunitário é compatível.
- Não abrir portas no router. Se painel web estiver exposto à LAN, exigir autenticação local e limitar superfície de rede.
- O processamento ASR/LLM/TTS principal permanece no backend XiaoZhi; sotaque pt-PT tem de ser configurado no serviço de TTS.

### Módulos por prioridade

1. **Estabilidade do hub:** estado ligado/religação, boot, Wi-Fi, logs sem transcrições privadas.
2. **Memória:** guardar com consentimento, listar, editar, apagar. Local por defeito. Cópias de segurança opcionais.
3. **LG webOS:** emparelhamento no ecrã; estado, volume, aplicações, desligar. Wake-on-LAN para ligar se suportado e configurado.
4. **Histórias:** personagens, preferências, capítulos e opções persistidos na memória.
5. **Bateria:** percentagem, corrente, temperatura quando disponível; controlo de tomada compatível LAN sem dependência circular do servidor de IA.

## Carregamento automático

O Tab 15 não anuncia na ficha oficial uma capacidade de impor limite de carga de bateria por software. **Não prometer** que um app normal pode cortar a carga diretamente sem root ou uma função de fabricante.

Abordagem proposta: carregador original ligado a tomada Wi-Fi com comando LAN local documentado, p.ex. Shelly Plus Plug S / Shelly Plug S Gen3 com `Switch.Set`.

- Padrão recomendado: carregar quando **<=40%**, desligar a tomada quando **>=80%**; limiares configuráveis, incluindo 20–80% se o utilizador preferir.
- Não alternar rapidamente: antioscilação (histerese), cooldown, confirmação do estado da tomada e repetição idempotente.
- Failsafe: em caso de serviço morto / Wi-Fi indisponível a tomada deve voltar a ligar em horário de segurança ou por rotina no próprio dispositivo de alimentação, para evitar descarregar até 0; risco térmico monitorizado, interromper carga quando houver aviso de alta temperatura.
- Recuperação após reinício da app e tomada, e teste de 48-72h antes de confiar sem supervisão.
- Wi-Fi e software de automação têm de continuar ativos com ecrã desligado.
- Não expor comandos Shelly à internet; rede local e autenticação se disponível.
- Fontes API: https://shelly-api-docs.shelly.cloud/gen2/ComponentsAndServices/Switch/
- Android serviços: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

## Marcos de entrega

M0. Documento de arquitetura (este ficheiro), sem alterar firmware.
M1. Código-fonte Android + interface e serviço de foreground com teste de persistência; APK de teste só depois de compilar/validar.
M2. Memória local com consentimento.
M3. Controlo LG com emparelhamento.
M4. Ponte MCP integrada com serviço real XiaoZhi (dependente de credenciais e permissões do utilizador).
M5. Controlo de carregamento após escolha e configuração da tomada; testes de falha/reinício.
M6. Histórias, melhoras voz pt-PT do lado servidor e diagnóstico dos cortes de TTS (fluxo independente).

## Informação pendente

1. Já existe tomada Wi-Fi? Marca/modelo; se não existir, escolher uma com controlo local e modo seguro.
2. Confirmar versão Android instalada e opções de bateria nas Definições do Tab15.
3. Endpoint/credenciais da plataforma XiaoZhi inseridos localmente no Android e autorizados pelo utilizador (nunca publicar segredos).
4. Emparelhamento LG — confirmação no ecrã quando chegar a altura.

**Nota:** documento de arquitetura `LUMI-HUB-ARQUITETURA.md` descreve a opção PC que ficou preterida; o Tab 15 é a direção principal.
