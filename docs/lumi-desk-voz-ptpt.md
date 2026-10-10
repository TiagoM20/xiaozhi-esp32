# LUMI DESK — voz portuguesa de Portugal (PT-PT)

Estado: investigação e decisão arquitetural, 10/10/2026. **Não é uma implementação pronta.**

## Objetivo

Voz feminina natural em português de Portugal, preferencialmente Microsoft `pt-PT-RaquelNeural`, com respostas produzidas integralmente pelo backend e enviadas por Opus ao ESP32-S3. Preservar firmware funcional XiaoZhi v2.5.1, wake word «Olá Lumi», 21 GIFs, conversação contínua e não interrupção da própria voz.

## Constatações no repositório

- `main/protocols/websocket_protocol.cc` recebe áudio Opus binário através do canal do servidor, sem sintetizar voz localmente.
- `main/ota.cc` recebe os parâmetros WebSocket/MQTT do serviço OTA e guarda-os em NVS.
- `main/Kconfig.projbuild` contém local `LANGUAGE_PT_PT`, mas essa configuração é para recursos de interface, não escolhe a voz TTS remota.
- O repositório atual não contém `azure_tts.cc`; não colocar chaves API nos fontes.
- Em setembro de 2026 houve tentativa de TTS Azure diretamente no ESP32 com alterações ao fluxo de áudio; provocou problemas de reprodução/respostas. Foi retirada e reposto firmware estável. Não repetir essa integração sem um plano de teste isolado.

## Voz pretendida

Microsoft Azure Speech: `pt-PT-RaquelNeural` (feminina, português europeu), oficialmente suportada. Alternativa: `pt-PT-FernandaNeural`.

Documentação oficial: https://learn.microsoft.com/pt-pt/azure/ai-services/speech-service/language-support

## Caminho A — preferido, mínimo risco

1. Verificar no painel do agente em `https://xiaozhi.me/` as vozes realmente disponíveis em «Configurar personagem / Voz / Timbre».
2. Se existir uma voz PT-PT aceitável, selecioná-la e testar a fala na LUMI DESK, sem compilar ou alterar firmware.
3. Se a voz Raquel não estiver no catálogo, **não assumir que é possível impor Azure TTS ao servidor público**. A escolha é limitada pelo backend desse serviço.

Q&A oficial sobre alteração de vozes: https://xiaozhi.me/xz-docs/docs/help-doc/xiaozhi-ai-chatbot-q%26a/

## Caminho B — backend próprio, apenas se o caminho A não bastar

Existe implementação open-source `xinnan-tech/xiaozhi-esp32-server` com `TTS.EdgeTTS.voice` configurável. Exemplo **apenas de referência**:

```yaml
selected_module:
  TTS: EdgeTTS
TTS:
  EdgeTTS:
    type: edge
    voice: pt-PT-RaquelNeural
    output_dir: tmp/
```

Documentação: https://github.com/xinnan-tech/xiaozhi-esp32-server/blob/main/main/xiaozhi-server/config.yaml

Este exemplo usa EdgeTTS (serviço não oficial, sujeito a alterações), e **não é o mesmo que Azure Speech autenticado**. Para Azure oficial é necessária chave/região e um adaptador TTS compatível com o servidor; as credenciais nunca devem entrar no GitHub.

O backend próprio precisaria de serviço contínuo, ASR/LLM/TTS, encaminhamento Opus e novo endpoint OTA/WebSocket. O tablet Blackview Tab 15 permanece central doméstica; não pressupor PC ligado permanentemente, nem afirmar viabilidade/estabilidade de correr o servidor no Android antes de teste.

## Critérios de aceitação

- LUMI diz «Olá» com pronúncia portuguesa real, não brasileira.
- Lê «televisão», «pequeno-almoço», «dezasseis» e «vou desligar a luz da sala» sem sotaque estrangeiro.
- Mantém compreensão das perguntas e comandos da televisão.
- Não se interrompe; 15 segundos de escuta após resposta sem fala; respeita wake word.
- Sem cortes nem regressões sonoras.
- Reversível, sem apagar NVS, assets ou memórias.

## Próxima dependência

Captura da lista de vozes configuráveis do agente atual na consola xiaozhi.me (sem tokens, passwords ou chaves API), para decidir se é possível usar a voz pretendida sem trocar servidor.
