# LUMI CORE DESK v0.6.0 — Firmware V14 experimental incorporado

Este executavel Windows x64 inclui a imagem original da release
[lumi-desk-v14-experimental](https://github.com/TiagoM20/xiaozhi-esp32/releases/tag/lumi-desk-v14-experimental),
verificada por SHA-256 `7332e4f7060ac221fa4bd4f667f1d62a463a1b4c142f9f03d82ed3406431bc08`.
Nao requer Python, esptool nem um BIN externo.

**Cautela:** V14 continua a ser firmware XiaoZhi 2.5.1 experimental com 'Olá Lumi' detetado pelo
MultiNet ingles; nao e modelo WakeNet em portugues europeu e pode reconhecer pior.
A V14 nao foi testada fisicamente na Waveshare ESP32-S3-Touch-LCD-1.54.

## Instalacao
1. Ligar ESP32-S3 por USB ao PC Windows.
2. Abrir o EXE, clicar em 'Detetar portas', escolher a porta COM da DESK.
3. Clicar em 'Testar ligacao'.
4. Clicar em 'INSTALAR DESK V14 EXPERIMENTAL'; o CORE faz sempre backup completo de 16 MB
   no PC e verifica versao, particoes, MAC, recursos e slot OTA ativo.
5. Ler aviso e confirmar conscientemente a instalacao. O CORE grava **apenas** a aplicacao
   no slot ativo: 0x20000 ou 0x410000, conforme a selecao OTA validada.
6. O programa verifica byte a byte (SHA-256) os dados gravados.
7. Reiniciar a DESK e testar audio, Wi-Fi, faces e voz.
8. Se houver falhas, usar 'RECUPERAR UMA COPIA', escolhendo backup criado pelo proprio CORE.

NUNCA formatar/erase-flash. Manter os backups locais privados:
`Documentos/LUMI-DESK/Backups`. Contem credenciais e historico da placa.
Um processo de restauracao so deve correr sobre a mesma placa e com OTA igual ao backup.
Se a deteccao de particoes ou dados OTA falhar, o CORE **nao grava**.
EXE nao assinado para Windows: verificar a origem no GitHub sem desativar protecoes.

## Estados / testes
A CI compila o EXE numa maquina Windows, verifica firmware e assinatura SHA-256,
executa testes offline de OTA/assets e um self-test real do EXE. Nao substitui
teste fisico no ESP32-S3.
