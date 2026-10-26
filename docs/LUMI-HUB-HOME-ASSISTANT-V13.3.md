# Home Assistant no LUMI HUB V13.3

## Implementado no Android

Casa ou Definições > Home Assistant abre o painel de ligação. A app aceita URL base e token de acesso pessoal criado no Home Assistant, valida GET /api/ antes de guardar, encripta o token com Android Keystore e permite descobrir entidades reais com GET /api/states. Mostra light, switch, media_player, vacuum e fan; estados unavailable não têm controlos ativos. As ações são exclusivamente chamadas POST /api/services por lista de serviços permitidos, e o estado volta a ser consultado após cada ação. Confirmar explicitamente antes de iniciar o aspirador. Não usar ações de fechaduras, alarmes ou segurança. A televisão LG direta continua funcional.

O URL é validado: HTTP apenas em endereços locais IPv4 privados/.local; HTTPS para servidores remotos. A aplicação não envia tokens em redirecionamentos e não partilha credenciais com o GitHub.

## Necessário antes de testar em casa

1. Home Assistant instalado num dispositivo sempre ligado (HA Green, Raspberry Pi ou mini-PC); não precisa do PC com LUMI CORE nem do tablet como servidor.
2. Android na rede que alcança o Home Assistant.
3. No perfil Home Assistant, Segurança > criar Long-Lived Access Token; NÃO enviar o token por chat.
4. No HUB, abrir Home Assistant e indicar por exemplo http://homeassistant.local:8123 (apenas se for realmente o endereço local correto) e o token.
5. Testar e guardar; consultar estados, identificar a entidade da TV ou aspirador e testar um comando.
6. A TV e o aspirador apenas aparecerão se houver integrações reais configuradas no HA.

## Ligação de voz com XiaoZhi

Independente da ligação REST do tablet. Para pedir à DESK por voz que desligue a TV, instalar no Home Assistant a integração oficial Model Context Protocol Server e avaliar a integração comunitária HACS https://github.com/mac8005/xiaozhi-mcp-ha. Esta ponte recebe o endpoint MCP do agente em https://xiaozhi.me/console/agents e um token HA específico. Expor só entidades autorizadas e verificar compatibilidade, estabilidade e permissões antes de usar. O HUB não altera a pilha de áudio XiaoZhi nem o firmware WakeNet. O host HA tem de ficar ligado, mas não é necessário PC Windows.

## Limites reais

- Não há servidor Home Assistant, URL, token ou entidades confirmados no projeto até este ponto; logo, a integração só foi validada por compilação Android e necessita teste físico com um HA instalado.
- A voz por MCP não fica ativa por instalar apenas o APK. Requer instalar/configurar a ponte no Home Assistant.
- O painel usa refresh manual, não subscrição WebSocket contínua; evita polling elevado.
- Alguns dispositivos não implementam todos os serviços; apresentar erro real do servidor.
- Histórias e memórias do tablet não sincronizam automaticamente com a conta XiaoZhi.

## Fontes

Home Assistant API https://contributors.home-assistant.io/core/internals/rest-api/
Documentação de instalação https://www.home-assistant.io/installation/
Servidor MCP oficial https://www.home-assistant.io/integrations/mcp_server/
Bridge XiaoZhi MCP https://github.com/mac8005/xiaozhi-mcp-ha
