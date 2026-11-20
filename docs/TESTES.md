# Testes do DeskPad Gamer Bridge

## 1. Testes automáticos (rodam no código, sem aparelho)

Rodam no GitHub Actions a cada envio (`:core:test`). Cobrem as regras, com os valores reais medidos no aparelho:

| Arquivo | O que comprova |
|---|---|
| `SwitchStateMachineTest` | Valores medidos (desligado `0/0/1`, ligado `1/1/0`); a leitura inicial não dispara ação; ciclo ligar→desligar→ligar; eventos repetidos não repetem a ação; uma chave sozinha não basta e duas de três bastam; valores estranhos ou ausentes são ignorados. |
| `DisplayFilterTest` | O HDMI (`flags=0x8b`) é monitor; a tela do celular e as janelas virtuais do DeskPad (`deskpad-*`, `flags=0x5f80`) não são; prefere um monitor ligado; nunca usa ID fixo. |
| `DecisionsTest` | Ligar com monitor abre; ligar sem monitor não abre e avisa; desligar só encerra se o estado anterior era "ligado", se a opção estiver ativa e se o DeskPad oferecer o encerramento; abrir ao conectar o monitor só com o botão ligado e a opção ativa; proteção contra abertura repetida (uma por vez e 4 s de intervalo). |

**Não são testados automaticamente** (dependem do Android real): o observador das configurações, o DisplayManager, a
abertura em segundo plano, as notificações, o serviço e a inicialização após reiniciar.

## 2. Testes no REDMAGIC 11 Pro Plus (dependem do aparelho físico)

### Preparação

1. Instale o APK do artefato do GitHub Actions.
2. **Pause o serviço do Game Space Replacer** no próprio app dele (não precisa desinstalar). Senão ele também abre o
   DeskPad ao ligar o botão e não dá para saber quem abriu.
3. Deixe **desligada** a integração Gamer interna do DeskPad (Configurações → Tela e desempenho → Entrada automática →
   Botão físico Gamer).
4. No Bridge, em **Permissões**: conceda "Sobrepor a outros apps" e Notificações e tire a otimização de bateria.
5. Em **Configurações**, confira que o app de destino é o DeskPad.
6. Em **Início**, toque em **Iniciar integração**.
7. Mantenha o botão Gamer configurado para abrir o Game Space (exigência do REDMAGIC OS).

Depois de cada teste, abra **Diagnóstico → Copiar** e guarde o registro.

### Cenários

| # | Teste | Resultado esperado | Registro esperado |
|---|---|---|---|
| 1 | Ligar o botão com o monitor conectado | O DeskPad abre praticamente na hora e o desktop vai para o monitor | `Botão Gamer LIGADO`, `abrir DeskPad: pedido de abertura entregue` |
| 2 | Desligar o botão | Sem o contrato no DeskPad: nada é encerrado e o motivo fica registrado. Com o contrato e a opção ativa: o DeskPad encerra a sessão e você volta ao Android normal | `Botão Gamer DESLIGADO`, `Nada a fazer: … não oferece encerramento externo` (ou `sessão desktop encerrada`) |
| 3 | Ligar de novo | Abre de novo, sem duplicar a tela | Igual ao 1 |
| 4 | Desconectar o monitor (botão ligado) | O próprio DeskPad fecha o desktop; o Bridge só registra | `Monitor externo desconectado` |
| 5 | Reconectar o monitor | Com "Abrir ao conectar o monitor" desligado (padrão): o Bridge não faz nada. Ligado: abre o DeskPad | `Monitor externo conectado` e `Nada a fazer: …` ou `abrir DeskPad` |
| 6 | Ligar o botão sem monitor | Não abre o DeskPad; aparece o aviso "Conecte o monitor externo" | `botão ligado sem monitor externo: não abro sessão desktop` |
| 7 | Abertura repetida (ligar/desligar/ligar rápido, ou "Testar abertura" várias vezes) | Uma tela só do DeskPad; pedidos com menos de 4 s de intervalo são ignorados | `Abertura repetida ignorada` |
| 8 | Reiniciar o aparelho com "Iniciar após reiniciar" ligado | O aviso "Gamer Bridge ativo" volta sozinho e o botão funciona | `Aparelho reiniciado: iniciando o monitoramento` |
| 9 | Encerrar o DeskPad à mão e depois ligar/desligar o botão | Ligar abre de novo; desligar não trava nada | `Botão Gamer LIGADO`… |

### Também observar

- **Latência**: no registro, as linhas `zte_keypad_slide_on_or_off: 0 → 1 (+N ms)` mostram em que ordem e com que
  intervalo as três configurações mudam. Isso confirma se a regra "2 de 3" não atrasa a abertura.
- **Calibração**: depois do teste 2, o Diagnóstico deve mostrar "ligado: visto · desligado: visto".
- **Valores ao vivo**: com o Diagnóstico aberto, os valores das chaves mudam ao mexer no botão, mesmo com o
  monitoramento pausado.
