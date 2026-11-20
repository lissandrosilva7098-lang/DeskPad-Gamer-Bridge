# DeskPad Gamer Bridge

Aplicativo Android auxiliar (pacote `com.deskpad.gamerbridge`) que transforma o botão físico Gamer do REDMAGIC num
controle do DeskPad: **ligar o botão abre o DeskPad** (só com monitor externo conectado) e, quando o DeskPad oferecer
o contrato de encerramento, **desligar o botão encerra a sessão desktop**.

Alvo: REDMAGIC 11 Pro Plus, Android 16 / REDMAGIC OS. Interface em português.

## Como funciona

- **Detecção do botão** (comprovada no aparelho): o REDMAGIC OS muda três configurações do sistema quando o botão é
  ligado ou desligado (`zte_keypad_slide_on_or_off`, `cc_nubia_game_key`, `gcs_need_kill_game_launcher`). O Bridge as
  observa com `ContentObserver`, sem polling e sem permissão especial, e só age quando **2 de 3** concordam.
- **Abertura**: intent de lançamento do DeskPad, na tela do celular (o DeskPad leva o desktop para o monitor). Em segundo
  plano, o Android exige "Sobrepor a outros apps"; se mesmo assim recusar, aparece um aviso para abrir com um toque.
- **Encerramento**: só pelo [contrato com o DeskPad](docs/CONTRATO-DESKPAD.md), que **ainda não existe no DeskPad**.
  Até lá, o desligamento é detectado e registrado, mas nada é encerrado.

## Situação

| Etapa | Estado |
|---|---|
| 1. Análise | Concluída: [`docs/ETAPA-1-ANALISE.md`](docs/ETAPA-1-ANALISE.md) |
| 2. Implementação | Concluída (aguarda teste físico) |
| 3. Compilação | GitHub Actions: `.github/workflows/build.yml` (APK no artefato do workflow) |
| 4. Testes | Automáticos no CI; roteiro no aparelho: [`docs/TESTES.md`](docs/TESTES.md) |

## Estrutura

```
core/   Kotlin puro, testado sem Android: botão (2 de 3), filtro de monitores, decisões, proteção contra repetição
app/    Android: serviço em primeiro plano, leitura do sistema, abertura, contrato do DeskPad, telas em Compose
tools/  verificar-botao-gamer.sh: verificação física por ADB (somente leitura)
```

## Permissões

| Permissão | Para quê |
|---|---|
| Serviço em primeiro plano (`specialUse`) | Manter o observador do botão e do monitor ativo |
| Sobrepor a outros apps | Abrir o DeskPad a partir do segundo plano |
| Notificações (recomendada) | Aviso do serviço, "conecte o monitor", "toque para abrir" |
| Inicialização (opcional) | Voltar a monitorar depois de reiniciar |
| `com.deskpad.permission.CONTROL_SESSION` | Encerrar a sessão (só com a mesma assinatura do DeskPad) |

**Não usa:** acesso ao uso, acessibilidade, `QUERY_ALL_PACKAGES`. O Shizuku é só exibido (nenhuma função depende dele).

## O que o Bridge nunca faz

Não apaga dados nem altera configurações do DeskPad, não desinstala nem desativa o Game Space ou o Game Space Replacer,
não mexe em desempenho, ventoinha ou resfriamento, não altera a entrada automática do DeskPad pelo monitor e não força
a parada de nenhum app.
