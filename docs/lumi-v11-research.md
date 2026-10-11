# Investigação GitHub e decisões técnicas — LUMI Hub V11

Revisão realizada em 11/10/2026 sobre projetos públicos. **Inspiração arquitetural, sem reutilização de código externo.**

## Projetos efetivamente consultados

1. [thecowan/wallpanel-android](https://github.com/thecowan/wallpanel-android): painel Android para automação, câmara, TTS, ecrã, integrações MQTT/HTTP. Sugere separar interface de utilização contínua das funcionalidades de configuração e dar feedback visual sem interromper o utilizador.
2. [home-assistant/android](https://github.com/home-assistant/android): aplicação Android da Home Assistant. Confirma utilidade de manter estado e controlos de dispositivos associados a serviços reais, em vez de representar visualmente estados não verificados. *A análise foi à descrição geral do repositório, não é uma auditoria ao código completo.*
3. [xinnan-tech/xiaozhi-esp32-server](https://github.com/xinnan-tech/xiaozhi-esp32-server): suporta WebSocket, MQTT/UDP, ferramentas MCP e envio de comandos via MQTT através de consola configurada. **Importante**: disponibilizar um servidor MCP no Hub não significa que seja possível o Hub pedir ao XiaoZhi público que reproduza uma frase no ESP32. O comando `Encontrar LUMI DESK` requer um canal de retorno autenticado e mecanismo de reprodução comprovado.
4. [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx): ASR/TTS offline compatível com Android; candidato a ensaios futuros de voz offline no Tab 15, depois de medir memória, latência e verificar um modelo em português europeu.
5. [rhasspy/piper](https://github.com/rhasspy/piper): sistema neural TTS local; considerar vozes pt-PT e licenças individuais, sem as confundir com a Raquel Neural do Azure.

## Alterações realizadas na V11

- **TV LG:** resposta do comando via texto inline + Toast não bloqueante; modal de certificado apenas para verificação de identidade inicial da TV. Emparelhamento e definições mantêm as confirmações necessárias. Feedback distingue mensagem da TV de confirmação do estado físico (ex. Wake-on-LAN não prova que acordou).
- **LUMI DESK:** remover cartões de expressões que não comunicavam com o ESP32. Indicar estado real do MCP. Oferecer aplicação local no tablet para voz/texto e comandos domésticos efetivos. Explicar honestamente dependência do canal de retorno para o ESP32 dizer «Olá, estou aqui».
- **Voz PT-PT:** nunca escolher automaticamente a primeira voz do Android: o Android `Voice` não fornece metadados de género. Listar vozes pt-PT, ouvir, escolher e persistir. As vozes marcadas como *offline* por `isNetworkConnectionRequired()` são exibidas como tal; testar também com Wi-Fi desligado. A Raquel Neural em MP3 é uma demonstração, não TTS dinâmico.
- **LUMI no tablet:** pedidos por texto disponíveis sempre; `RecognizerIntent` opcional em pt-PT (o motor de reconhecimento Android pode necessitar de Internet). Comandos reconhecidos: TV via webOS/TLS com pin existente, Spotify, Google Find Hub, estado da bateria, meteorologia Rebordosa. Sem LLM nem wake word «Olá Lumi» permanente no tablet nesta versão.

## Limitações ainda abertas

- O MAC da LG e o emparelhamento precisam de testes na televisão real.
- O agente MCP XiaoZhi ainda não foi configurado no Tab 15.
- Localizar fisicamente a LUMI DESK com uma frase audível não foi implementado, porque ainda falta autenticação, ID do dispositivo, endpoint de envio real, confirmação de entrega e reprodução no ESP32.
- A aplicação Android não pode garantir que existe uma voz feminina PT-PT offline instalada. A utilizadora escolhe pela audição. A Raquel Neural requer TTS remoto autorizado para fala dinâmica.
- A LUMI no tablet funciona como assistente de comandos locais, não como espelho conversacional completo da LUMI DESK.

## Próxima arquitetura a investigar

- Integrar **serviço próprio XiaoZhi** para ASR em português e Azure Speech Raquel Neural; manter os recursos e firmware ESP32 existentes através de mudança de endpoint reversível.
- Expor no Hub um mecanismo de notificações/estado sem abrir modais de operação repetitiva.
- Ensaiar caminho de retorno para reprodução sonora remota na DESK, por MQTT ou mecanismo documentado do serviço escolhido, sem incluir segredos no repositório.
