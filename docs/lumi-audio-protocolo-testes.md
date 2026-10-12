# LUMI DESK — protocolo de teste experimental para falha de áudio

**Não há firmware instalado automaticamente.** A compilação isolada da branch `diagnostics/lumi-audio-v1-20261012` **não atualiza a Waveshare**. O firmware atual continua a funcionar sem alterações.

## Objetivo

Reproduzir a falha ao contar de 1 a 10 sem mudar o áudio, a fala, o microfone, os 21 GIFs, a wake word ou o servidor. O firmware experimental **só adiciona contadores**. Não implementa buffer de jitter nem equalização. Isso depende dos resultados.

## Preservação antes de instalar

1. Confirmar que a placa física é a Waveshare `ESP32-S3-Touch-LCD-1.54` e anotar a versão mostrada no ecrã/arranque.
2. Guardar **backup integral da flash real** (tamanho e COM obtidos do `esptool flash-id` antes de escolher comprimento da leitura); verificar tamanho e checksum do ficheiro no PC. Manter original privado, nunca no GitHub.
3. Preservar o binário e as partições de assets, NVS, ativação, configurações, GIFs e a partição de boot. O backup antigo parcial de assets **não equivale a backup integral**.
4. Só instalar o **binário da app** após inspecionar a tabela de partições **real** e confirmar endereço/tamanho da partição ativa. A documentação histórica menciona `0x20000`, mas não assumir que vale para todas as revisões e backups.
5. Preparar e testar o procedimento de rollback. **Sem backup confirmado e método de rollback, não escrever na flash.**
6. Não alterar endpoint XiaoZhi, ligação MCP, chaves, assets ou OTA. Não publicar `sdkconfig` de instalações pessoais.
7. Não enviar logs completos indiscriminadamente: podem conter Wi-Fi, URLs e informações do dispositivo. Partilhar só linhas filtradas `LUMI_AUDIO_DIAG`, `Failed to decode audio`, `Encode queue is full` e avisos I2S, após verificar que não contêm segredos.

## O que será registado

Cada 5 s durante o estado de fala e quando chega o evento `tts stop` é impressa uma linha iniciada por `LUMI_AUDIO_DIAG` com contadores **acumulados desde o arranque**:

- `rx`: total de pacotes encaminhados ao callback de receção;
- `queued`: aceites para descodificação;
- `drop_state`: ignorados porque o estado do dispositivo não era «a falar»;
- `drop_full`: descartados devido à fila Opus cheia;
- `drop_reset`: rejeitados por reset/stop concorrente;
- `opus_ok` / `opus_err`: descodificações bem-sucedidas / falhadas (não distingue todas as causas);
- `pcm_out`: blocos enviados para a saída da coluna;
- `gap180`: pausas superiores a 180 ms entre blocos PCM processados, que **podem incluir pausas naturais de fala**;
- `max_gap_ms`: maior intervalo registado;
- `i2s_slow`: chamadas de escrita PCM cuja duração excedeu o tempo estimado do áudio em mais de 45 ms;
- `q_opus` e `q_pcm`: ocupação instantânea das filas ao emitir o log.

Estes números ajudam a distinguir falha de entrada, descodificador ou saída, **mas não medem a qualidade do som nem garantem deteção de perda antes de chegar ao ESP32**. Nenhum texto falado é incluído nas linhas de diagnóstico.

## Como testar depois de instalar (não antes)

1. Ligar o terminal série pela porta USB-C e confirmar `LUMI_AUDIO_DIAG`.
2. Manter a fonte, distância e volume semelhantes aos vídeos anteriores.
3. Pedir: «Lumi, conta de 1 a 10 lentamente».
4. Repetir **cinco vezes**. Para cada repetição, registar `Boa`, `Interrompida` ou `Ininteligível`.
5. Partilhar as linhas filtradas de diagnóstico correspondentes e referir se o áudio esteve sempre baixo/ruidoso ou se cortou totalmente.
6. Não mudar automaticamente firmware ou servidor no mesmo teste; comparar uma variável de cada vez.
7. Após a análise, restaurar a versão anterior se for necessário. A solução final só é lançada depois de uma nova compilação e teste controlado.

## Problemas atuais ainda sem explicação confirmada

A imagem pode indicar «A falar» e a coluna ficar muda porque o estado TTS é estabelecido separadamente da entrega Opus. A fila pode encher, o descodificador pode errar, I2S pode bloquear, ou pode faltar áudio a montante do ESP32. Esta versão pretende identificar **qual desses casos aparece num mau teste**, sem assumir a causa antecipadamente.
