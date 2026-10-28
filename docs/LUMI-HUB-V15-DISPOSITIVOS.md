# LUMI HUB V15 - Adicionar dispositivos

## Implementado
- Registo persistente de equipamentos; vazio por defeito. Migração de TV LG V14 apenas se existia IP real configurado.
- Casa apresenta apenas favoritos do registo; Dispositivos permite adicionar, editar nome, escolher divisão, marcar favorito e remover.
- Adicionar via SSDP/mDNS local voluntário, LG webOS por IP/MAC e registo manual do IP (sem controlo automático).
- LG webOS preserva controlador e emparelhamento antigos; ligar por Wake-on-LAN exige MAC e ativação de rede no televisor.
- Planta apresenta dispositivos por divisão. Pessoa pode escolher a sua imagem pelo seletor de ficheiros Android; localização não é lida.
- Ponte XiaoZhi MCP lista equipamentos registados e permite ações genéricas seguras só para LG, além dos comandos antigos condicionados ao registo real.
- Home Assistant continua opcional com a sua API REST já existente.

## Limitações explícitas
- Google Home, SmartThings e Tuya/Smart Life são APENAS atalhos para abrir as apps; nenhuma importação de conta ou equipamento.
- Android não permite listar universalmente dispositivos de todas as aplicações. Matter/QR e Bluetooth não implementados.
- SSDP e mDNS provam apenas anúncio na rede no momento da pesquisa; não prometem autenticação nem controlo.
- Sem sincronização automática entre A55 e Blackview e sem testes físicos de rede, voz ou TV.

## Teste rápido
1. Instalar APK assinado V15 sobre V14 (mesma aplicação/certificado, versionCode 28).
2. Confirmar aba Dispositivos vazia ou TV LG migrada quando IP previamente guardado.
3. Adicionar por pesquisa Wi-Fi e por IP; verificar aviso 'sem controlo' em protocolos não suportados.
4. Adicionar LG, emparelhar e testar comandos pelo HUB.
5. Editar nome, divisão, favorito; confirmar Casa e Planta. Remover.
6. Pessoas: escolher fotografias, fechar e reabrir app.
7. MCP: perguntar à DESK quais os dispositivos e executar ação de TV; não necessita de firmware V15 na DESK.
