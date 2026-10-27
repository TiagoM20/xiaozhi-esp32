# LUMI HUB / DESK V14 - implementação e testes

## Objetivo
LUMI DESK (Waveshare ESP32-S3) recebe fala, XiaoZhi interpreta e chama as ferramentas MCP externas do LUMI HUB (Blackview Android). O HUB envia comandos à LG por webOS (WSS com fingerprint aceite na TV) e Wake-on-LAN para ligar (só se TV/rede permitirem). **O Home Assistant é opcional.**

## HUB V14
- Acesso de primeira classe à ponte MCP nas páginas Casa e Definições.
- A credencial de endpoint, que inclui token, é encriptada no Android Keystore. No primeiro uso migra a versão 13, removendo o URL anterior em texto das SharedPreferences depois de guardar com sucesso.
- Liga o serviço central a partir do diálogo MCP, sem precisar de ativar Home Assistant.
- Reconnection MCP com agendamento único e ignora sockets obsoletos.
- Home e grelha Dispositivos deixam de destacar equipamentos não integrados. Casa mostra o Home Assistant apenas se já configurado.
- A TV LG continua com emparelhamento, certificado pinado, controlos e Wake-on-LAN já existentes.
- A assinatura, ID Android e dados locais mantêm-se para atualização 13.3 → 14.0 (versionCode 26 → 27).

## DESK V14: experimental, não testado no hardware
- Recusa TTS remoto e notificações espontâneas recebidos em idle. Mensagens após iniciação ativa de conversa continuam permitidas.
- Mostra 'thinking' ao ligar e 'happy' por omissão ao falar; respeita futuras emoções definidas pelo agente.
- Mantém o fecho aos 15 s de silêncio, evita wake durante fala/escuta e preserva a estratégia de AEC existente.
- Configuração MultiNet EN 'ola lumi' com threshold 55 (anterior 20), sem carregar áudio de wake para a cloud; isto é **experimental e não prova deteção de 'Lumi' em pt-PT**.
- Reutiliza o suporte de assets e sleep que já existe na board; não grava novos modelos/assets que possam sobrescrever partições.
- Firmware apenas app para gravação em 0x20000, **após backup integral**. Não apagar flash. A voz Raquel continua configurada no backend XiaoZhi.

## Teste humano obrigatório
1. HUB: instalar APK assinado por cima de 13.3, validar manutenção de dados, abrir definições > LUMI DESK/MCP, guardar token privadamente.
2. Confirmar que o estado da ponte muda para 'Ligado ao XiaoZhi'; no XiaoZhi, ferramentas externas 'home.tv_*' devem aparecer. Não enviar token por chat.
3. Configurar a TV LG: IP e MAC reais, aceitar fingerprint e emparelhamento; testar Ligar, Desligar e volume por botões. Ligar por Wake-on-LAN depende da TV.
4. DESK: ANTES DE FLASHEAR, guardar firmware completo. Gravar apenas aplicação em 0x20000. Testar botões, face, áudio Raquel, rede, 20 despertares e 30 min de repouso, eventos falsos e mensagens MCP.
5. Não declarar 'funciona' nem migrar como firmware estável até confirmação destes testes.

## Dependências / limitações
- XiaoZhi cloud requer Internet; Android pode suspender serviço mesmo com START_STICKY em alguns equipamentos (rever otimização de bateria no Blackview).
- A integração de TV não equivale a um Home Assistant completo, nem garante suporte a todos os dispositivos; novas marcas necessitam integrações por protocolo.
- Integração local da voz Azure/OpenAI DeskVoiceGateway é distinta do agente XiaoZhi; priorizar um caminho por vez.
