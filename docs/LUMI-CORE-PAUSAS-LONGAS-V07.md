# LUMI CORE v0.7.0 — Pausas Longas (teste experimental)

Esta versao inclui o firmware compilado pela run
https://github.com/TiagoM20/xiaozhi-esp32/actions/runs/41057578681
(sha256 da app `475548614441af772dd2c1cb6610f852e2759df8aa7e10d404d7589ada7169b1`).

## Utilizacao
- Windows 64 bits, executavel unico.
- USB da DESK > detetar COM > testar ligacao > INSTALAR PAUSAS LONGAS.
- O CORE faz backup integral de 16MB, valida a placa e metadados, pede confirmacao,
  grava so app no slot ativo, verifica o resultado; em erro tenta rollback.
- O programa guarda backup privado em Documentos/LUMI-DESK/Backups.
- Depois de reiniciar a DESK, DOIS CLIQUES em VOLUME+ quando em repouso ativam
  ou desativam o modo de pausas longas.
- O modo normal permanece ativo por defeito e regressa apos reinicio.
- No modo experimental, a DESK tenta esperar >=1,8s de silencio local antes
  de mandar `listen stop` ao XiaoZhi online. A verificacao de relogio pode
  atrasar a resposta ate mais 1 segundo.
- Se nao terminar por voz, um clique curto em POWER enquanto escuta envia o fim da frase.
- Se falhar, desativar com duplo clique VOL+, ou reiniciar e repor o firmware
  com o botao RECUPERAR UMA COPIA.

**Nao ha garantia de que o backend hospedado aceite este comportamento.**
Nao entregar esta versao como firmware estavel ate existirem testes fisicos
com pausas reais, voz Raquel, conversas multi-turn e Wi-Fi.

O executavel Windows nao tem assinatura digital. Nao desativar SmartScreen
ou antivirus. Evitar partilhar backups, contêm credenciais privadas.
