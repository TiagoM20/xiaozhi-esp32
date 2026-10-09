# LUMI Hub Android — primeira versão

Aplicação Android nativa experimental destinada ao Blackview Tab 15 (Android 12).

## Já funciona
- Interface visual em português (painel, memória, ligações e bateria).
- Guardar, consultar e apagar memórias por decisão explícita do utilizador; SQLite local, sem sincronização.
- Consultar carga da bateria do tablet.
- Iniciar/parar serviço Android com notificação permanente que observa alterações da bateria.

## Ainda não está ligado
- Nenhuma ligação ativa ao servidor XiaoZhi/MCP; memórias não afetam a resposta de voz da LUMI nesta fase.
- LG webOS sem emparelhamento, e sem comando real.
- Sem tomada Wi-Fi: limites de carga 40–80% são apenas informativos.
- Serviço em segundo plano ainda precisa de teste de persistência real no Doke OS do Tab 15.
- Sem arranque automático no reinício; opção futura após testes.

## Compilação
GitHub Actions: workflow LUMI Hub Android gera app-debug.apk.
Em desenvolvimento local, Android SDK e Gradle 8.9 / JDK 17:

    gradle :app:assembleDebug

O APK encontra-se em app/build/outputs/apk/debug/app-debug.apk.

Não publicar este APK como a *última Release* do repositório de firmware:
o instalador de ESP32 usa a última Release para obter LUMI-firmware.zip.

## Segurança
Aplicação de teste, sem permissões de acesso a ficheiros pessoais, sem exportação da base de dados, sem servidor de rede e sem credenciais. A eliminação da aplicação elimina a base de dados local, pelo que ainda não deve ser usada para informação insubstituível.
