# Contrato de sessão: DeskPad ↔ Gamer Bridge

**Situação: o DeskPad publicado ainda NÃO implementa este contrato.** A mudança já está pronta como patch em [`deskpad-patch/deskpad-1.32-controle-de-sessao.patch`](../deskpad-patch/deskpad-1.32-controle-de-sessao.patch) (DeskPad 1.32), mas **não foi aplicada nem enviada ao repositório do DeskPad**. Este documento define o que precisa ser adicionado ao
DeskPad (em outra etapa, só com a sua autorização) para o Bridge poder encerrar a sessão desktop ao desligar o botão
Gamer. O Bridge já está pronto para usá-lo e **só o usa quando detecta, no aparelho, que o DeskPad instalado o oferece**.
Até lá, a tela do Bridge mostra "Encerramento indisponível".

## Por que é preciso

O DeskPad 1.31 não tem nenhuma entrada externa para encerrar a sessão. A saída correta dele (`exitByGamer()` /
`exitManually()` → `finish()` da `TouchpadActivity`) só é chamada por dentro, e o `MonitorWatchService`, que tem a ação
`MONITOR_EXIT`, não é exportado. Forçar a parada mataria o processo sem desmontar o desktop corretamente.

## Segurança

- O DeskPad declara uma permissão **`signature`**: o Android só a concede a apps assinados com **a mesma chave** do
  DeskPad. Nenhum outro app instalado consegue encerrar ou consultar a sessão.
- O receptor é exportado, mas protegido por essa permissão.
- A ação faz só o que o usuário faria tocando em "Sair do DeskPad": não apaga dados, não altera configurações e não
  mexe na entrada automática pelo monitor.

## Constantes (iguais às de `core/.../DeskPadContract.kt`)

| Item | Valor |
|---|---|
| Permissão | `com.deskpad.permission.CONTROL_SESSION` (`protectionLevel="signature"`) |
| Consultar | `com.deskpad.app.action.QUERY_SESSION` → resultCode `1` aberto, `2` fechado |
| Encerrar | `com.deskpad.app.action.END_SESSION` → resultCode `1` encerrada, `2` não havia sessão |
| Extra | `com.deskpad.app.extra.REASON` (`gamer_switch_off` ou `bridge_test`) |
| Sem resposta | resultCode `0` (valor inicial do broadcast ordenado) |

O Bridge envia **broadcasts ordenados explícitos** (`setPackage("com.deskpad.app")`) e lê o `resultCode`.

## Como aplicar o patch no DeskPad

O patch muda 4 arquivos do DeskPad 1.31: cria `SessionControlReceiver.kt`, declara a permissão e o receptor no manifesto, sobe a versão para 1.32 (versionCode 33) e anota a novidade no `FUNCIONALIDADES.md`. Foi verificado só quanto a aplicar sem conflito; **não foi compilado** (o ambiente não tem o Android SDK), então o DeskPad precisa compilar no CI dele antes de ser usado.

Com o `DeskPad-projeto.zip` do repositório Deskpad-NOVO:

```bash
unzip DeskPad-projeto.zip           # cria a pasta DeskPad/
patch -p1 < deskpad-1.32-controle-de-sessao.patch
rm DeskPad-projeto.zip && zip -qr DeskPad-projeto.zip DeskPad && rm -r DeskPad
# enviar o novo DeskPad-projeto.zip ao repositório (o CI do DeskPad gera o APK 1.32)
```

## O que o patch adiciona (resumo)

`AndroidManifest.xml`:

```xml
<permission
    android:name="com.deskpad.permission.CONTROL_SESSION"
    android:label="Controlar a sessão desktop do DeskPad"
    android:protectionLevel="signature" />

<receiver
    android:name=".SessionControlReceiver"
    android:exported="true"
    android:permission="com.deskpad.permission.CONTROL_SESSION">
    <intent-filter>
        <action android:name="com.deskpad.app.action.QUERY_SESSION" />
        <action android:name="com.deskpad.app.action.END_SESSION" />
    </intent-filter>
</receiver>
```

`SessionControlReceiver.kt` (esboço; usa o que o DeskPad já tem):

```kotlin
class SessionControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.deskpad.app.action.QUERY_SESSION" ->
                resultCode = if (TouchpadActivity.desktopUp) 1 else 2
            "com.deskpad.app.action.END_SESSION" -> {
                val reason = intent.getStringExtra("com.deskpad.app.extra.REASON") ?: "?"
                Diag.log("gamer", "Pedido do Gamer Bridge para encerrar a sessão ($reason)")
                // exitFromGamer() devolve false quando a tela do DeskPad não existe
                resultCode = if (TouchpadActivity.alive && TouchpadActivity.exitFromGamer()) 1 else 2
            }
        }
    }
}
```

## Assinatura dos dois apps

A permissão `signature` só funciona se o Bridge e o DeskPad forem assinados com a **mesma chave**:

1. No repositório **DeskPad-Gamer-Bridge**, em Settings → Secrets and variables → Actions, crie os mesmos segredos que
   o DeskPad usa: `DESKPAD_KEYSTORE_BASE64`, `DESKPAD_KEYSTORE_PASSWORD`, `DESKPAD_KEY_ALIAS`, `DESKPAD_KEY_PASSWORD`.
2. O GitHub Actions passa a gerar o Bridge assinado (variante `release`) e grava o certificado em `assinatura.txt`,
   dentro do artefato. Compare o SHA-256 com o do DeskPad.
3. Ordem de instalação: com as assinaturas iguais, o Android concede a permissão quando os dois estão instalados. Se o
   Bridge mostrar "o Android não deu a permissão" mesmo com a mesma chave, reinstale o Bridge depois do DeskPad.

Sem os segredos, o Bridge é gerado como versão de teste (debug), com outra chave: tudo funciona, **menos** o encerramento.
