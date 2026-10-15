# LUMI CORE · Windows (v0.1.0 experimental)

Aplicação nativa para Windows que administra o Blackview Tab 15 através de rede Wi-Fi local. Não substitui a LUMI HUB Android nem altera firmware da DESK.

## Arquitetura

- **Windows**: interface de configuração e diagnóstico; não precisa de ficar ligado.
- **Blackview (HUB)**: central de execução 24/7, armazena configurações e credenciais (Android Keystore) e comunica com a DESK.
- **DESK (ESP32-S3)**: microfone, altifalante e 21 GIFs sem alterações nesta fase.
- **GitHub**: guarda apenas código e executáveis; nunca chaves, gravações ou histórico doméstico.

## Emparelhamento

1. Instalar no Blackview a versão HUB **0.12.4** ou superior.
2. LUMI HUB → Dispositivos → LUMI DESK → Configurações → Emparelhar LUMI CORE Windows.
3. Ativar ligação Windows. O ecrã mostra IP do tablet e disponibiliza um código privado (256 bits/64 caracteres hexadecimais).
4. Abrir **LUMI-CORE-Windows.exe** no PC ligado à mesma rede Wi-Fi.
5. No separador Ligação, preencher IP e código, depois **Emparelhar e verificar**.
6. O emparelhamento é guardado cifrado pelo Windows DPAPI no perfil do utilizador. A revogação faz-se a qualquer momento no tablet.

## Funções desta primeira versão

- Estado do HUB, ponte DESK e serviços Azure/OpenAI sem revelar chaves.
- Editar região Azure, modelo de IA, substituir chave Azure ou OpenAI. Campo de chave vazio mantém chave anterior. As chaves NÃO são guardadas no Windows após a sincronização.
- Editar IP e MAC da TV e tema visual do HUB (10 opções).
- Consultar, copiar e exportar para .txt os eventos e conversas registados no HUB.
- O Windows pode fechar após aplicar as definições; o tablet continua a funcionar com os valores sincronizados.

## Segurança

- Tráfego de aplicação cifrado e autenticado com AES-256-GCM, chave partilhada aleatória e IV novo por pedido/resposta. HTTPS não é usado nesta versão; os metadados da ligação (IP/porta/tamanho) não estão cifrados.
- Só disponível quando o utilizador o ativa no tablet; porta TCP 8770 apenas em LAN privada.
- Código de emparelhamento nunca deve ser enviado para ChatGPT, GitHub ou capturas públicas.
- Recomendado Wi-Fi privado, não rede de convidados nem Wi-Fi público.
- Azure e OpenAI ficam protegidos no Android Keystore. Os valores existentes não são descarregáveis pelo Windows.
- Os logs exportados podem conter informação pessoal; rever antes de partilhar.
- Ainda não existe sincronização de todas as opções do HUB nem controlo do firmware; esta é uma primeira versão experimental.

## Desenvolvimento

No Windows, com Python 3.12:

```powershell
cd lumi-core-windows
py -3.12 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python lumi_core.py
```

O workflow GitHub Actions `lumi-core-windows.yml` produz `LUMI-CORE-Windows.exe` (PyInstaller, x64).
