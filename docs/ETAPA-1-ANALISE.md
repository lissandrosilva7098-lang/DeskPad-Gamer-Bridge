# DeskPad Gamer Bridge — ETAPA 1: análise técnica

Data: 20/11/2026 · Alvo: REDMAGIC 11 Pro Plus, Android 16 / REDMAGIC OS

Legenda usada neste documento:

- **[COMPROVADO]**: visto no código-fonte ou na documentação oficial do Android.
- **[EMPÍRICO]**: comprovado pelo seu teste no aparelho.
- **[HIPÓTESE]**: forte indício, mas falta o teste físico.
- **[DESCARTADO]**: não serve como sinal confiável.

---

## 1. Resumo

1. O Game Space Replacer (GSR) **não lê o botão diretamente** e, na versão atual, **também não depende
   do UsageStats para abrir o app**. Ele observa, com um `ContentObserver`, a configuração do sistema
   `Settings.Global` **`gcs_need_kill_game_launcher`**, que o REDMAGIC OS altera quando o botão muda de posição.
   **[COMPROVADO]**
2. Segundo o comentário do próprio autor no código, **`0` = ligado e `1` = desligado**. O observer é chamado
   **nas duas transições**, mas o GSR simplesmente ignora o caso "desligado" (`if (isGameSwitchOn) { … }`).
   Isso explica exatamente o que você viu: abre ao ligar e não faz nada ao desligar. **[COMPROVADO no código]**
3. **O desligamento pode ser detectado.** O teste no aparelho (20/11, 02:14, seção 5.1) mostrou que **quatro
   configurações globais** mudam ao ligar e voltam ao desligar, entre elas `gcs_need_kill_game_launcher`
   (desligado=`1`, ligado=`0`). Sem Shizuku, sem acessibilidade e sem polling. **[EMPÍRICO, 1 ciclo]**
4. O **DeskPad não oferece nenhuma ação externa para encerrar a sessão**. A única ação de saída
   (`com.deskpad.app.MONITOR_EXIT`) fica num serviço **não exportado**, inacessível a outros apps (inclusive
   pelo Shizuku). Um encerramento correto exige uma pequena integração explícita no DeskPad no futuro.
   **[COMPROVADO]**
5. Descoberta importante: o **DeskPad 1.31 já tem uma integração interna com o botão Gamer** ("aprende" uma
   configuração pelo Shizuku, observa e encerra ao desligar), marcada como **[Não confirmado]** no próprio
   `FUNCIONALIDADES.md`. O comentário dele sobre o GSR está **desatualizado** (diz que o GSR só usa UsageStats).
6. O DeskPad **não deve ser aberto no monitor com `setLaunchDisplayId`**: a tela dele (`TouchpadActivity`)
   é o touchpad no celular, e é o próprio DeskPad que coloca o desktop no monitor (`Presentation`).

---

## 2. Repositório atual

`DeskPad-Gamer-Bridge` estava vazio (branch sem commits). Não há nada a preservar; o projeto será criado do zero.

---

## 3. Game Space Replacer — análise do código

Fonte analisada: `github.com/TheRealCrazyfuy/GameSpaceReplacer`, tag **1.2** (commit `58de2e4`, igual ao HEAD;
`versionName 1.2`, `targetSdk 36`, `minSdk 30`, pacote `com.abeja.gamecenterreplacer`).

> **Limite desta análise:** o download do APK publicado foi bloqueado pela rede deste ambiente
> (github.com/releases devolveu 403). Analisei o código da tag 1.2, que corresponde à última versão
> publicada, mas **não comparei o binário**. Se você quiser essa confirmação, posso analisar o APK caso
> você o coloque no repositório ou em outro lugar acessível.

> **Licença:** o repositório do GSR **não tem arquivo LICENSE**. Sem licença, o código é "todos os direitos
> reservados". **Nenhum trecho será copiado.** O Bridge será escrito do zero; usar o mesmo fato técnico
> (o nome de uma configuração do sistema e APIs públicas do Android) não é cópia de código.

### 3.1 Como identifica o acionamento do botão

Arquivo `observers/GameSwitchObserver.kt`:

- Registra um `ContentObserver` em `Settings.Global.getUriFor("gcs_need_kill_game_launcher")`.
- Em `onChange`, lê `Settings.Global.getInt(..., "gcs_need_kill_game_launcher", 0)`; comentário do autor:
  `// 0 = on, 1 = off`.
- Ler essa chave **não exige nenhuma permissão**. O GSR usa `targetSdk 36` e funciona no seu
  REDMAGIC 11 Pro Plus, então o Android 16 do aparelho permite essa leitura a apps comuns. **[EMPÍRICO]**

Histórico: até a 1.1 (jan/2025) o GSR consultava o UsageStats a cada 1 s procurando o Game Space
(`cn.nubia.gamelauncher`) na frente. O commit `d58ea09` (13/03/2026, "add observer for game switch, fixes delay
on target app launch") trocou isso pelo observer da chave. **Por isso ele abre praticamente na hora.**

O nome da chave ("precisa matar o game launcher") é coerente com a semântica: ao **desligar** o botão,
o sistema marca `1` para encerrar o Game Space; ao **ligar**, volta para `0`.

### 3.2 Como usa o "Acesso ao uso" (UsageStats)

Só na opção **"relançar o app de destino"**: um laço com `delay(1000)` chama `queryEvents` numa janela de 5 s
e, se o último `MOVE_TO_FOREGROUND` for `cn.nubia.gamelauncher`, reabre o app de destino. O próprio autor
deixou `// TODO: get rid of this delay while not tanking the battery life`. **Para a abertura principal, o
UsageStats não é necessário.**

### 3.3 Serviço em segundo plano

- `BackgroundService`: serviço em primeiro plano, notificação `IMPORTANCE_MIN`, `START_STICKY`,
  `foregroundServiceType="mediaPlayback"` (o app não toca mídia: o tipo não corresponde ao uso).
- `BootReceiver`: em `BOOT_COMPLETED`, chama `startForegroundService` se a opção estiver ligada.
- Problemas encontrados (que o Bridge vai evitar):
  1. Cada `startForegroundService` chama `onStartCommand` de novo, que **cria outro observer e outra
     corrotina sem remover os anteriores**. O app chama isso de três lugares diferentes. Resultado possível:
     vários observers ativos → **aberturas repetidas** do app de destino.
  2. `onDestroy` usa `gameSwitchObserver` (`lateinit`) e pode travar se o serviço morrer antes de registrá-lo.
  3. No Android 15+, um `BOOT_COMPLETED` **não pode iniciar serviço em primeiro plano do tipo `mediaPlayback`**
     (regra oficial do Android 15). A "inicialização após reiniciar" do GSR provavelmente falha no Android 16.
     **[COMPROVADO na documentação; não testado no aparelho]**

### 3.4 Como "redireciona" o Game Space

Ele **não bloqueia nem intercepta** o Game Space. O sistema abre o Game Space normalmente e o GSR, logo em
seguida, chama `startActivity(getLaunchIntentForPackage(alvo) + FLAG_ACTIVITY_NEW_TASK)` **a partir do serviço
em segundo plano**, ficando por cima. O Android 10+ proíbe abrir telas em segundo plano; o GSR passa porque o
usuário concedeu **"Sobrepor a outros apps" (`SYSTEM_ALERT_WINDOW`)**, que é uma das exceções oficiais.
**[COMPROVADO + EMPÍRICO]**

Antes de abrir, ele confere de novo se a chave vale `0` (comentário: "check if the competitive key is on too").

O README do GSR avisa: no Android 15+ o botão precisa estar configurado para **abrir o Game Space**, e as
"permissões restritas" precisam ser liberadas manualmente (Configurações → Apps → GSR → ⋮).

### 3.5 Desligamento

O GSR **recebe** o aviso de desligamento (o observer dispara a cada mudança da chave) e o **descarta**.
Não há no código nenhum broadcast, intent ou serviço proprietário da REDMAGIC. **[COMPROVADO]**

---

## 4. DeskPad — análise (somente leitura, nada foi alterado)

Fonte: `Deskpad-NOVO/DeskPad-projeto.zip` (pacote `com.deskpad.app`, versão **1.31**, `targetSdk 34`, usa Shizuku 13.1.5).

### 4.1 Como o DeskPad abre

| Componente | Exportado | Papel |
|---|---|---|
| `MainActivity` (`Theme.NoDisplay`, LAUNCHER) | **sim** | trampolim: abre `TouchpadActivity` e fecha |
| `TouchpadActivity` (`singleTask`, paisagem) | não | o touchpad, **no celular** |
| `DesktopPresentation` (`Presentation`) | — | o desktop, **no monitor** |
| `MonitorWatchService` | não | entrada automática pelo monitor + botão Gamer interno |

Consequências para o Bridge:

- O único ponto de entrada externo é o **intent de lançamento** (`MainActivity`).
- `TouchpadActivity` é `singleTask`: abrir de novo **reaproveita a mesma instância** (não duplica).
- **Não se deve usar `setLaunchDisplayId(monitor)` para o DeskPad**: isso colocaria o touchpad no monitor.
  O Bridge abre o DeskPad na tela do celular, e o DeskPad leva o desktop para o monitor sozinho.
  `setLaunchDisplayId` fica disponível só como opção para **outros** apps de destino.

### 4.2 Como o DeskPad encerra

- Saída correta existente: `TouchpadActivity.exitManually()` / `exitByGamer()` → `finish()` (o DeskPad desmonta o
  desktop e salva o estado normalmente).
- Acesso de fora: **nenhum**. `ACTION_EXIT` (`com.deskpad.app.MONITOR_EXIT`) só existe no `MonitorWatchService`,
  que é `exported="false"`. O Android não deixa outro app (nem o usuário `shell` do Shizuku) iniciar um serviço não
  exportado.
- Alternativas pelo Shizuku e por que **não** servem como encerramento de sessão:
  - `am force-stop` / `am stop-app`: matam o processo sem passar pelo `finish()`. Rejeitados (você mesmo pediu para
    não usar como primeira opção).
  - Tecla HOME/VOLTAR simulada: só tira o DeskPad da frente ou depende do que estiver focado. Não é encerramento.

### 4.3 Integração interna do DeskPad 1.31 com o botão Gamer

`GamerSwitch.kt` + `MonitorWatchService.kt`: com o Shizuku, tira 3 "fotos" de todas as configurações
(desligado/ligado/desligado), deixa você escolher a chave que mudou, observa-a com `ContentObserver` (mais uma
verificação a cada 2 s), confirma por 1,2 s e então abre (se faltar) ou encerra a sessão com `exitByGamer()`.

- Está marcada como **[Não confirmado]** no `FUNCIONALIDADES.md`.
- Se o aprendizado dela for feito, deve encontrar justamente `global:gcs_need_kill_game_launcher` (seria uma
  segunda confirmação independente).
- **Risco de conflito:** se o DeskPad (interno), o GSR e o Bridge estiverem ativos ao mesmo tempo, os três reagem
  ao mesmo botão. O `singleTask` evita telas duplicadas, mas haverá pedidos de abertura repetidos.

### 4.4 Evidências do aparelho (Diagnóstico do DeskPad, 20/11, 00:38–00:50)

Registro enviado por você. **Ele não contém nenhum acionamento do botão Gamer** (não há nenhuma linha `[gamer]`),
então não responde à questão do desligamento. Mas confirma pontos importantes para o projeto:

1. **Aparelho:** `Android 16 (SDK 36) nubia NX809J`. Tela do celular `id=0` 2688x1216; monitor
   `"Tela HDMI"` 1920x1080, `flags=0x8b`. **[EMPÍRICO]**
2. **O ID do monitor muda a cada conexão** (205, 207, 208, 209/210, 211). Às 00:43:02, **dois displays
   apareceram juntos** (209 e 210) numa única conexão. O Bridge nunca vai guardar um ID fixo; ele confere
   "existe monitor externo agora?" no momento em que o botão é ligado.
3. **As janelas do DeskPad criam displays virtuais** (`deskpad-ZArchiver`, `deskpad-ChatGPT`, `flags=0x5f80`),
   e eles disparam o `DisplayListener` como se fossem monitores. Eles **não têm `FLAG_PRIVATE` (0x4)**, então
   esse filtro não basta. Filtro do Bridge: exigir `FLAG_PRESENTATION` (0x8, presente no HDMI e ausente nos
   virtuais) e ignorar nomes `deskpad-*`. **[EMPÍRICO]**
4. **A entrada automática do próprio DeskPad falha neste aparelho.** Em 4 de 4 conexões:
   "abrindo o DeskPad" e, cerca de 4 s depois, "o Android não abriu o DeskPad: aviso para abrir com um toque".
   O desktop só subiu quando você abriu à mão (por exemplo, às 00:40:36). O DeskPad **não tem**
   `SYSTEM_ALERT_WINDOW`; o GSR, que abre o DeskPad com sucesso, tem. Isso reforça usar no Bridge
   o caminho comprovado pelo GSR e **conferir se a abertura realmente aconteceu**, com o aviso de toque como
   alternativa. **[EMPÍRICO]** (As linhas do registro diferem um pouco do código do zip analisado, então a versão
   instalada pode não ser exatamente a mesma.)
5. **O DeskPad já fecha sozinho ao desconectar o monitor** ("Monitor continua desconectado: fechando a tela do
   DeskPad no celular"). O Bridge não vai interferir nisso.
6. **A integração Gamer interna do DeskPad não está ativa** (nenhuma linha `[gamer]`). Hoje não há conflito com ela.
7. O REDMAGIC OS injeta código de jogos nos apps (`GameActivityStub`, `Macro-Helper: not in game list` no logcat do
   ChatGPT). Isso não é um sinal do botão e não será usado.

---

## 5. Investigação do desligamento — todas as possibilidades

| Mecanismo | Situação | Observação |
|---|---|---|
| `Settings.Global` (`gcs_need_kill_game_launcher` e outras 2 chaves) | **[EMPÍRICO]** | Confirmado no aparelho: ver 5.1. Sem permissão, sem polling, sem Shizuku. |
| Outras chaves (global/system/secure) e `getprop` | a verificar | O roteiro compara tudo antes/depois. Propriedades (`getprop`) não têm observer: só serviriam com polling, por isso ficam como último recurso. |
| Broadcasts / intents / serviços da REDMAGIC | **sem evidência** | Nada no GSR nem no DeskPad. Não vou supor nenhum. O roteiro grava o logcat durante os acionamentos para procurar algo real. Além disso, desde o Android 8 a maioria dos broadcasts implícitos não chega a receivers do manifesto. |
| Game Space perder o foco / UsageStats | **[DESCARTADO]** como prova | Como você observou, o próprio app de destino assume o primeiro plano. Serve no máximo como sinal auxiliar no diagnóstico. |
| Abertura/encerramento da interface do Game Space | **[DESCARTADO]** como prova | Mesmo motivo; além disso, o usuário pode fechar o Game Space à mão. |
| Acessibilidade | **[DESCARTADO]** | Não recebe eventos de chave tipo *switch* (EV_SW), exige "configuração restrita" para apps instalados por APK, e é invasiva. Não é necessária. |
| Shizuku + `getevent` | candidato de reserva | Se o botão aparecer como dispositivo de entrada (EV_SW/EV_KEY), seria um sinal de hardware real. Exige Shizuku sempre ativo e um processo `getevent` permanente. Só se a chave falhar. |
| Temporizador | **[DESCARTADO]** | Proibido por você, e não é detecção. |

### 5.1 Resultado do teste físico (20/11, 02:13–02:14)

Teste feito com o aprendizado do DeskPad: 3 leituras de todas as configurações pelo Shizuku, sem nenhuma
alteração. Resultado: **5 configurações mudaram ao ligar e voltaram ao desligar**.

| Configuração | Desligado | Ligado | Uso no Bridge |
|---|---|---|---|
| `global:zte_keypad_slide_on_or_off` | `0` | `1` | **sinal** (o nome descreve a chave deslizante física) |
| `global:cc_nubia_game_key` | `0` | `1` | **sinal** |
| `global:gcs_need_kill_game_launcher` | `1` | `0` | **sinal** (o mesmo do GSR; a hipótese foi confirmada) |
| `global:game_subagent_scene` | `0` | `1` | só no diagnóstico (o nome indica "cena de jogo", que pode mudar por outros motivos) |
| `secure:default_home` | `com.teslacoilsw.launcher` | `cn.nubia.gamelauncher` | **nunca** usada nem alterada (muda se você trocar de launcher) |

Conclusões:

- O desligamento **gera um sinal real e específico**: três chaves independentes voltam ao valor de "desligado".
  **[EMPÍRICO]**
- Com o botão ligado, **o Game Space passa a ser a tela inicial (launcher)**. Ao desligar, o sistema devolve o
  launcher normal (Nova). Consequências:
  - Ao encerrar o DeskPad depois do desligamento, você volta ao Android normal **sem que o Bridge precise mexer no
    launcher**.
  - Se você sair do DeskPad à mão com o botão ainda ligado, cai no Game Space. É o comportamento do sistema, e o
    Bridge não interfere.
- O `getevent -lp` do aprendizado foi cortado no primeiro dispositivo (um mouse Bluetooth). Não é preciso
  investigar eventos de hardware: as configurações já bastam.

### 5.2 Regra de detecção do Bridge

- Observa as **três chaves de sinal** com `ContentObserver` (nenhum polling).
- **Estado = maioria (2 de 3)**. Isso protege contra uma chave mudar por outro motivo. O GSR, por exemplo, cita uma
  "tecla competitiva" que também mexe em `gcs_need_kill_game_launcher`. O custo é esperar a segunda chave mudar,
  o que deve levar milissegundos. O Diagnóstico registra a ordem e o tempo de cada chave para confirmar isso.
- Só age em **transições confirmadas** (desligado→ligado, ligado→desligado). Repetições e valores inesperados são
  ignorados e registrados.
- O encerramento automático só fica disponível depois que o **próprio Bridge** tiver visto as duas transições no
  seu aparelho. O teste acima foi um único ciclo, feito por outro app.

---

## 6. Limitações do Android 16 relevantes

| Tema | Regra | Como o Bridge lida |
|---|---|---|
| Abrir tela em segundo plano | Proibido desde o Android 10, com exceções oficiais; uma delas é `SYSTEM_ALERT_WINDOW` concedido | Pede "Sobrepor a outros apps" (mesmo caminho comprovado pelo GSR). Se o Android recusar, mostra notificação "toque para abrir" e registra o erro. |
| Serviço em primeiro plano | Android 14+: tipo obrigatório e coerente com o uso | Tipo `specialUse` com justificativa (não `mediaPlayback`). |
| Serviço a partir do `BOOT_COMPLETED` | Android 15+: proibido para `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone` | `specialUse` não está nessa lista. Mesmo assim, será testado no aparelho. |
| Notificações | Android 13+: `POST_NOTIFICATIONS` em tempo de execução | Pede; sem ela o serviço roda, mas o aviso fica oculto. |
| Visibilidade de pacotes | Android 11+ | `<queries>` para apps com ícone de inicialização, **sem** `QUERY_ALL_PACKAGES`. |
| Configurações restritas | Apps instalados por APK precisam de liberação manual de algumas permissões (o README do GSR cita isso no Android 15+) | A tela de permissões explica o passo "⋮ → Permitir configurações restritas". |
| Leitura de `Settings` | Android 12+ restringe chaves ocultas declaradas no framework | A chave do REDMAGIC é lida hoje por um app com `targetSdk 36` (GSR) **[EMPÍRICO]**. Uma atualização do sistema pode mudar isso; o Bridge detecta e avisa. |
| Economia de bateria do REDMAGIC OS | Mata apps em segundo plano | Pede para ignorar a otimização de bateria (tela oficial do Android, sem permissão extra). |

---

## 7. Verificação física necessária agora (somente leitura)

Antes de implementar a detecção do desligamento, preciso do resultado deste teste no seu aparelho.

**Opção A — roteiro automático (computador com ADB):**

```bash
./tools/verificar-botao-gamer.sh
```

Siga os 3 passos (desligado → ligado → desligado) e me envie o arquivo `RESUMO.txt` gerado.

**Opção B — comandos manuais (ADB ou terminal com Shizuku/`rish`):**

```bash
settings get global gcs_need_kill_game_launcher   # com o botão DESLIGADO
settings get global gcs_need_kill_game_launcher   # com o botão LIGADO
settings get global gcs_need_kill_game_launcher   # com o botão DESLIGADO de novo
```

Resultado esperado se a hipótese estiver certa: `1`, `0`, `1`.

Faça o teste com o botão configurado para **abrir o Game Space** (exigência do REDMAGIC OS no Android 15+).
O roteiro não altera nada no aparelho.

---

## 8. Plano de implementação (ETAPA 2), sujeito à sua aprovação

### 8.1 Permissões

| Permissão | Necessária? | Motivo |
|---|---|---|
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | **sim** | Manter o processo vivo para o observer da chave e do monitor. |
| `SYSTEM_ALERT_WINDOW` (Sobrepor a outros apps) | **sim** | Exceção oficial para abrir o DeskPad em segundo plano. |
| `POST_NOTIFICATIONS` | recomendada | Aviso do serviço e "toque para abrir" se a abertura falhar. |
| `RECEIVE_BOOT_COMPLETED` | só se a opção estiver ligada | Iniciar após reiniciar. |
| Ignorar otimização de bateria | recomendada | O REDMAGIC OS mata serviços. |
| `PACKAGE_USAGE_STATS` | **não** | A chave do sistema substitui o UsageStats. |
| `QUERY_ALL_PACKAGES` | **não** | `<queries>` basta. |
| Acessibilidade | **não** | Ver seção 5. |
| Shizuku | **opcional** | Nenhuma função comprovada precisa dele. A tela mostra o estado; ele só será usado se o teste exigir o plano de reserva (`getevent`). |
| Monitor externo (`DisplayManager`) | sem permissão | API pública. |

### 8.2 Arquitetura (Kotlin, Jetpack Compose, Material 3, pt-BR)

- `switch/GamerSwitchMonitor`: observa a chave com `ContentObserver` (sem polling); máquina de estados que só age em
  **transições reais** (desconhecido/desligado → ligado, ligado → desligado), ignora eventos repetidos e valores
  inesperados, e registra tudo. Chave e valores ficam configuráveis caso uma atualização do REDMAGIC OS os mude.
- `display/ExternalDisplayMonitor`: `DisplayManager` + `DisplayListener`; considera monitor externo um display
  público, diferente do 0, ligado, ignorando displays virtuais do DeskPad (`deskpad-*`).
- `launch/TargetLauncher`: intent de lançamento do app escolhido, `FLAG_ACTIVITY_NEW_TASK` (+ `SINGLE_TOP`), uma
  abertura por vez e intervalo mínimo entre aberturas; display 0 para o DeskPad; `setLaunchDisplayId` opcional
  para outros apps; se o Android recusar, notificação "toque para abrir".
- `session/SessionCloser`: hoje informa "o DeskPad não oferece encerramento externo". Ver decisão 2.
- `service/BridgeService`: serviço `specialUse`, idempotente (registrar o observer só uma vez), `START_STICKY`,
  recuperação após o sistema matar o processo; `BootReceiver` opcional.
- `log/EventLog`: registro em arquivo com tamanho limitado (eventos do botão, monitor, aberturas, erros).
- Telas: **Início** (botão Gamer, monitor, Shizuku, app de destino, serviço, botões Iniciar/Pausar, Testar abertura,
  Permissões), **Configurações**, **Permissões**, **Diagnóstico** (registros e **valor ao vivo da chave**, para
  você comprovar o desligamento no próprio celular).

### 8.3 Compilação (ETAPA 3)

Gradle (Kotlin DSL), `compileSdk/targetSdk 36`, `minSdk 30`, GitHub Actions gerando o APK como artefato.
Para que as atualizações instalem por cima sem desinstalar, o APK precisa de **uma chave de assinatura fixa**
(segredo do GitHub, como o DeskPad já faz); sem ela, uso a chave de depuração, que muda a cada máquina.

### 8.4 Testes (ETAPA 4)

- **No código (CI):** testes unitários da máquina de estados do botão, do filtro de monitores, da proteção contra
  abertura repetida e da persistência das configurações.
- **Só no aparelho:** os 9 cenários que você listou (ligar com monitor, desligar, religar, desconectar/reconectar o
  monitor, ligar sem monitor, abertura repetida, reiniciar, encerrar o DeskPad à mão).

---

## 9. Decisões que dependem de você

1. **Teste físico da seção 7**: é o que decide se o encerramento automático é possível.
2. **Como encerrar o DeskPad.** Não existe hoje nenhum caminho externo correto. Opções:
   - **(A, recomendada)** No futuro, quando você autorizar, adicionar ao DeskPad um receptor **protegido por
     permissão de assinatura** (só apps assinados com a mesma chave, ou seja, o Bridge, podem chamar) que execute a
     mesma saída de `exitByGamer()`. O Bridge só ativaria o encerramento quando detectasse esse receptor instalado.
     Exige assinar o Bridge com a mesma chave do DeskPad.
   - **(B)** Usar a integração interna que o DeskPad 1.31 já tem para o encerramento (configurando nela a chave
     `gcs_need_kill_game_launcher`) e deixar o Bridge só com a abertura e o diagnóstico.
   - **(C)** Por enquanto, só abertura + detecção e registro do desligamento, sem encerrar.
3. **Convivência durante os testes:** o GSR continua instalado, como você pediu. Para não haver aberturas em
   dobro, recomendo **pausar o serviço do GSR pelo próprio app dele** enquanto testa o Bridge (o Bridge nunca mexe
   no GSR) e deixar a integração Gamer interna do DeskPad desligada.
