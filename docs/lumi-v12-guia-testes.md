# LUMI V12 — notas de entrega e teste seguro

**Estado:** ramo experimental. A LUMI DESK real não foi alterada. Nenhuma operação aqui apaga ou grava a flash. O sistema público XiaoZhi continua como referência estável até validação física.

## Funcionalidades implementadas no código

- **LUMI HUB 0.12.0:** mantém as páginas HOME/Dispositivos/Memórias/Atualizações e os 5 temas; novo ecrã «LUMI CORE · V12» dentro da LUMI DESK.
- **Memória MCP:** o LUMI HUB anuncia duas ferramentas através da ligação MCP do agente XiaoZhi:
  - `lumi.memoria_guardar(titulo, detalhe)`, apenas a pedido explícito, com validação de tamanho (100 / 1500 caracteres) e proteção contra duplicados;
  - `lumi.memoria_procurar(tema)`, pesquisa local SQLite e devolve no máximo 5 recordações relacionadas.
  - A IA apenas acede se a ligação MCP estiver ativa e o modelo optar por invocar as ferramentas.
  - **Não confundir** estas memórias com transcrição automática de todas as conversas ou reconhecimento de membros da família.
- **TV LG:** sinal Wake-on-LAN usando o MAC e IP já guardados, seguido de tentativa de acesso à porta webOS 3001 para verificar se o serviço responde. Ter «ligar por Wi-Fi e Bluetooth» ativo ajuda; o MAC deve corresponder à interface correta. Bluetooth não é usado diretamente.
- **Ponte áudio V12 (experimental):** serviço LAN Android com portas 8765 (WebSocket) e 8766 (OTA), código de emparelhamento aleatório, e reprodução de uma demonstração pré-gravada de **Raquel PT-PT** convertida para Opus 24 kHz / 60 ms. O dispositivo pode receber a frase pela coluna, depois de apontado explicitamente para a OTA do HUB. **Não faz STT, LLM, TTS dinâmico, nem conversas.**
- **Firmware candidato:** preserva toda a lógica de wake word e áudio; se a OTA foi explicitamente mudada para `/lumi/ota/` e contém WebSocket, dá prioridade a esse transporte. Caso contrário, mantém a preferência MQTT anterior. **Não instalar antes de teste e autorização.**

## Testar primeiro apenas no Blackview Tab 15

1. Obter a APK **assinada com a mesma chave da V11**, para atualizar sem apagar dados. Não usar a variante debug para substituir a versão assinada.
2. Instalar a atualização. Abrir **Dispositivos → LUMI DESK → LUMI CORE V12**.
3. Confirmar as memórias existentes e ouvir uma voz PT-PT no laboratório do tablet.
4. Associar o MCP ao agente, se não estiver já associado. Iniciar a central Android.
5. Pedir à LUMI DESK atual: «Lumi, lembra-te de uma história chamada ...». Confirmar no HUB se a ferramenta `lumi.memoria_guardar` foi chamada. Se não, é necessária orientação do agente na consola XiaoZhi; não interpretar a ausência como sucesso.
6. Em casa, com o tablet e a DESK na mesma rede Wi-Fi, ativar a ponte de áudio na página **LUMI CORE V12**. «Ver estado da ponte» deverá indicar que as portas estão prontas.
7. **Parar aqui até confirmação de recuperação.** Copiar o endereço privado da OTA local apenas depois de guardar o **endereço original de OTA XiaoZhi**. O endereço contém uma chave privada; não o enviar em capturas.
8. Configurar temporariamente a DESK para a OTA local pelo modo de provisionamento avançado documentado para o firmware atual **somente após autorização expressa**. A resposta da OTA local fornece `websocket.url` e `token`, sem solicitar atualizações de firmware; envia áudio de demonstração quando a DESK iniciar a escuta.
9. Se a ligação não funcionar, regressar à OTA original e ao firmware de reserva. Não apagar NVS, partição de assets ou GIFs; não utilizar `erase_flash` nem flash integral de um binário de aplicação.

## Limitações e segurança

- A Raquel reproduz uma gravação de demonstração já criada anteriormente. Uma voz dinâmica exige ligar um motor TTS ao serviço; uma conversa livre exige STT e LLM, com custos/quotas a avaliar.
- A ponte experimental escuta apenas na rede local, sem TLS. **Não expor as portas no router, VPN pública ou cloud.** O código de emparelhamento não deve ser partilhado. Não está pronta para venda.
- Android pode interromper serviços em segundo plano por poupança de bateria. Confirmar o comportamento real do Blackview com ecrã desligado.
- A memória do tablet não está sincronizada automaticamente com cartão SD, não identifica pessoas por voz e não substitui cópias de segurança.
- Se a TV estiver desligada, o serviço webOS pode ficar inacessível. O envio bem-sucedido do pacote WOL **não confirma** que a TV acordou.

## Critérios para passar a versão estável

- APK V12 compilada e assinada, instalável sobre V11 sem perda de memórias.
- Teste de memória via MCP verificado, sem guardar dados não solicitados.
- TV LG testada a ligar e a desligar; respostas de rede sem confirmações falsas.
- Áudio de demonstração enviado ao ESP32 através do HUB, compreensível e sem cortes.
- Backup da DESK verificado e retorno à OTA/firmware XiaoZhi público ensaiado.
- Só depois: STT português, TTS Raquel dinâmico, IA e memórias automáticas com consentimento.

Referências:
- Firmware/WS: `main/protocols/websocket_protocol.cc`, `main/ota.cc`, `main/application.cc`.
- HUB: `lumi-hub-android/app/src/main/java/pt/lumistudio/lumihub/`.
- Amostra de voz: release `lumi-voice-ptpt-demo-v0.1`.
