# LUMI HUB V13 — central doméstica simplificada (proposta de arquitetura)

Data: 26 outubro 2026. Base de código: HUB v12.8 (Blackview Tab 15, Android 12). **Documento de decisão, não funcionalidades já implementadas.** 

## 1. Decisões de arquitetura

- **Descontinuar LUMI CORE Windows como requisito.** Manter binário v0.1.x antigo só como ferramenta de manutenção/diagnóstico, oculto na interface normal; não exigir PC para gerir o HUB no dia a dia.
- **O Blackview é o painel central**, para utilização e personalização de agentes, biblioteca, dispositivos e atualizações.
- **A DESK não muda de motor de voz:** wake na ESP32-S3; áudio ASR/LLM/TTS diretamente com o serviço xiaozhi.me; Raquel pt-PT no servidor já configurado. O HUB não deve interceptar/retransmitir áudio do dispositivo por defeito.
- **A consola https://xiaozhi.me/console/agents permanece a fonte autorizada da configuração remota da IA.** Não foi confirmada API pública estável para editar diretamente agente/voz/personalidade no xiaozhi.me: V13 deve abrir a configuração oficial com sessão controlada pelo browser (Android Custom Tabs ou ACTION_VIEW), sem scraping/WebView com tokens próprios; editor local só pode dizer 'rascunho' até existir sincronização verificada.
- **Home Assistant é o controlador da casa**, não um motor adicional de IA. Usar a integração oficial 'Model Context Protocol Server' do HA + ligação do XiaoZhi por uma ponte MCP comprovada (avaliar mac8005/xiaozhi-mcp-ha e py-xiaozhi). Não inventar API, hostname, ou IDs de entidades.
- **HA exige instalação persistente separada do painel Android**, preferencialmente HA OS numa máquina pequena sempre ligada (HA Green, mini-PC, Raspberry Pi). O tablet continua a ser central de interface, mesmo quando o servidor HA é outro dispositivo. Não prometer que HA OS corre nativamente no Tab 15.
- **Privacidade e permissões:** expor ao agente apenas entidades necessárias; comandos de portas, fechaduras, alarmes e ações potencialmente arriscadas requerem confirmação.
- **Firmware e assets:** mostrar versões do HUB/DESK e releases verificadas; instalar APK assinado do HUB normalmente. Firmware da DESK só por processo OTA **compatível e confirmado** (slot ativo, checksum, versão/board únicos), com recuperação; sem reflash direto à partição assets contendo os 21 GIFs e modelo wake. A palavra de ativação 'Lumi' fica pendente do modelo nativo pt-PT realmente treinado.

## 2. Navegação V13: 3 separadores + definições

1. **Casa** (default, dashboard): cabeçalho horas/tempo, estado LUMI e conectividade, ações rápidas (TV Sala, Aspirador, câmaras) se emparelhadas, divisão por áreas (Sala, Quarto...) e dispositivo no detalhe. Estados reais (ligado/desligado/desconhecido), nunca simular controlo de dispositivos ausentes. Não duplicar links externos em todos os tiles. Layout 'informação essencial em 2 toques', sem painel técnico.
2. **Lumi**: estado resumido DESK (online quando houver sinal verificado), voz ativa, personalidade do agente, um único botão **Abrir consola XiaoZhi** para editar diretamente a configuração existente, ações para biblioteca e estado da wake word. Avançado DESK com diagnóstico, backup e update em subpágina; não exibir 10 botões técnicos no primeiro nível.
3. **Biblioteca**: Histórias / Memórias / Personagens; editor local com criação/versões/exportação e eliminação por pedido; dois tipos de memórias claramente identificados: 'guardadas neste tablet' e 'usadas pelo agente XiaoZhi'. **Memórias locais não afetam automaticamente o agente** sem sincronização autorizada. Histórias prontas a copiar/exportar como instruções de personalidade na consola oficial, até API verificada.
4. **Ícone de engrenagem no cabeçalho**: Ligações, Home Assistant, Contas, Atualizações, Aparência, Diagnósticos/Avançado, Sobre. LUMI CORE Windows fica em ferramentas legadas, opcional.

### DESK — tela reduzida
Na página Lumi, cabeçalho 'LUMI DESK' com imagem/indicador estado e 4 controlos visíveis: **Personalidade**, **Voz Raquel**, **Histórias e memória**, **Firmware e diagnóstico**. Se o controlo vive no XiaoZhi, abrir consola, não fingir alteração local. Informar claramente a origem das informações.

### Design visual
Espaçamento 16-20dp; 1 cabeçalho; cartões com cores neutras e contraste AA; 1-2 colunas conforme orientação; tipografia 16/22/28sp, botões táteis >48dp, ícones consistentes, animação opcional leve; evitar blur pesado, polling/anim em excesso. Tema escuro e claro, cada cartão só mostra metadados necessários; controlos avançados em folha/modal. Inspirado em UI de dashboards minimalistas, sem reutilizar código de terceiros com licença não permissiva.

## 3. Interação sem duplicar a voz

DESK (ESP32-S3) → [XiaoZhi cloud: ASR → LLM → Raquel TTS] → DESK

Se a intenção é doméstica:
XiaoZhi MCP tool → [proxy/bridge MCP] → HA MCP Server → integração do dispositivo → estado/resultado a XiaoZhi → resposta áudio.

O Blackview corre o dashboard e pode enviar comandos locais de HA via REST/WebSocket com autenticação guardada no Android Keystore. Não participa no percurso de cada pacote de áudio. **A ponte MCP deve estar ativa em HA/servidor permanente**, não depender do tablet estar com o ecrã ligado. O token/endpoint MCP original do agente só se introduz na configuração do componente suportado; nunca em GitHub ou relatório.

Comandos-alvo e precondições:
- «Lumi, desliga a TV da sala» → HA expõe entidade LG webOS TV autenticada, ação media_player.turn_off.
- «Lumi, liga o aspirador» → HA dispõe de integração do modelo e entidade vacuum, ação vacuum.start (depende de capacidades do aspirador).
- «Lumi, ativa modo cinema» → HA cena/script exposto ao agente, com nomes coerentes.

## 4. Arquitetura com menor manutenção

**Opção A, recomendada:** Blackview (HUB UI) + XiaoZhi cloud (voz, personalidade) + HA Green/mini-PC sempre ligado (MCP + integrações e automações). 0 serviços extra no Windows, 1 ponte MCP apenas quando há controlo da casa. Os comandos domésticos acrescentam latência de rede e processamento do HA; não se quantifica sem medir.

**Opção B, temporária:** manter controlos diretos existentes do HUB para TV LG e links para apps de câmaras/Spotify enquanto HA não existe. Não dar falsa indicação de controlo universal de aspirador por voz. Isto evita hardware adicional imediato, mas não oferece a automação/integração de HA.

**Não recomendado:** correr mais ASR/TTS/LLM no Blackview para intermediar o áudio da DESK; duplicar TTS, contornar auth do XiaoZhi por scraping, fazer proxy LAN exposto à internet ou instalar HA OS não suportado no Android.

## 5. Plano por marcos

- **M1 — Limpeza de interface sem novas dependências:** manter V12.8 como versão restaurável. Refatorar navegação para Casa / Lumi / Biblioteca + Definições, DESK só 4 cartões, caminhos antigos preservados, teste no Tab15 vertical/horizontal. Compilar APK assinado V13.0 RC. Validar que Raquel no XiaoZhi não é alterada e que dados existentes continuam acessíveis.
- **M2 — XiaoZhi como cérebro:** abrir consola oficial autenticada, templates de personalidade e histórias no tablet, copiar/exportar. Confirmar endpoint/API documentada antes de permitir edição e guardar na cloud automaticamente.
- **M3 — Home Assistant (primeiro controlo real):** decidir host, instalar HA OS e integração LG + aspirador, testar ações MANUAIS e entidades, só depois instalar/configurar MCP e exposição mínima. Medir comando de voz real em 10 tentativas e tempo resposta.
- **M4 — OTA/desempenho:** lista única 'Atualizações' (HUB, DESK, recursos), estado verificado, backup e possibilidade de reverter; testes latência/consumo, modo repouso, reconexão de rede e extensibilidade.
- **M5 — Modelo Lumi:** integrar só após Espressif entregar modelo pt-PT real; manter proteção contra falsos despertares e conversa de seguimento, validar fisicamente e preservar 21 GIFs.

## 6. Pesquisa que suportou as decisões

- XiaoZhi oficial: https://github.com/78/xiaozhi-esp32 ; atual assets/OTA: https://github.com/78/xiaozhi-esp32/blob/main/partitions/v2/README.md
- Ponte MCP específica XiaoZhi→HA: https://github.com/mac8005/xiaozhi-mcp-ha . É integração comunitária, versão/segurança requerem validação antes de produção.
- HA oficial MCP Server: https://www.home-assistant.io/integrations/mcp_server
- HA OS e servidores dedicados: https://www.home-assistant.io/installation
- HA webOS TV: https://www.home-assistant.io/integrations/webostv ; vacuum: https://www.home-assistant.io/integrations/vacuum
- Kiosk Satellite: https://github.com/jxlarrea/kiosk-satellite — aprender UI e gestão de ecrã/desempenho, não copiar código ou vender forks sem verificar licença.
- Debate Reddit UI orientada a divisão + popups: https://www.reddit.com/r/homeassistant/comments/1qlb9rr/ha_dashboard_2026_refresh_less_clutter_more/
- Reddit desempenho: https://www.reddit.com/r/homeassistant/comments/1q8rmr5/another_year_another_refresh_making_my_dashboard/
- Reddit Kiosk Sat: https://www.reddit.com/r/homeassistant/comments/1vp9aky/kiosk_satellite_voice_satellite_looking_for_your/

## 7. Critérios de aceitação da V13
- Até 3 opções de navegação permanentes no tablet, sem botão CORE Windows na HOME.
- DESK com painel simples e até 4 ações primárias.
- Voz e conversação existentes não passam pelo HUB; nenhuma alteração ao firmware ESP32.
- Memórias locais nunca anunciadas como sincronizadas com a cloud sem resultado confirmado.
- Ações não ligadas mostram "por configurar" e nunca indicadores falsos.
- Sem tokens em logs e GitHub; tempo real de ligações sem polling desnecessário.
- APK assinado com a chave LUMI pré-existente; rollback possível para a V12.8.
