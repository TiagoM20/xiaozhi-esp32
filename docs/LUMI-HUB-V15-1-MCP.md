# LUMI HUB v15.1 — integracao MCP XiaoZhi (corrigida)

## O que muda
- Guarda o URL privado `wss://api.xiaozhi.me/mcp/?token=...` encriptado no Android Keystore em **todos** os ecras.
- Elimina a credencial encriptada e qualquer copia antiga em texto quando pedes para remover.
- O botao **Religar ou testar** reinicia apenas a ponte MCP, nao a central toda.
- A mensagem `Ligado ao XiaoZhi` surge apos o servidor inicializar MCP, nao apenas ao abrir o WebSocket.
- Estado de ligacao pode ser atualizado manualmente no painel MCP.
- Mantem os dispositivos e definicoes existentes; versionCode passa 28→29 para atualizar sobre V15.

## Como validar no Blackview Tab 15
1. Instalar o APK por cima da V15 **sem desinstalar nem limpar dados**.
2. Abrir LUMI DESK > Configuracoes > Integracao MCP.
3. Se nao houver URL guardado, copiar o URL MCP do agente no xiaozhi.me e colar apenas na app (nunca em mensagens).
4. Escolher Guardar e ligar ao XiaoZhi. Confirmar estado `Ligado ao XiaoZhi`.
5. No XiaoZhi, verificar que ferramentas `home.hub_estado` e `home.dispositivos_listar` aparecem.
6. Testar consulta da bateria e lista de dispositivos; **nao** executar comandos destrutivos nem pagamentos.
7. Usar «Religar» se a ligacao cair; nao deve parar CORE, voz local ou Home Assistant.
8. Remover ligacao, validar `Por configurar`, e voltar a configurar se quiseres.

## Limites
Nao corrige o VAD/pausas do XiaoZhi online, nem a wake word «Lumi».
Nao integra o MCP do Continente. O projeto original exige Node.js/Chromium e um ambiente de autenticacao humana.
Verificacao Android em CI nao substitui teste no tablet.
