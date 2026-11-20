#!/usr/bin/env bash
# DeskPad Gamer Bridge — verificação física do botão Gamer (ETAPA 1)
#
# SOMENTE LEITURA: este roteiro não altera nenhuma configuração do aparelho,
# não desinstala nada e não mexe no Game Space, no DeskPad nem no Game Space Replacer.
#
# O que ele faz, com o aparelho ligado ao computador por ADB (cabo ou depuração sem fio):
#   1. Lê todas as configurações (global, system, secure) e as propriedades do sistema
#      com o botão Gamer DESLIGADO, depois LIGADO, depois DESLIGADO de novo.
#   2. Mostra só as chaves que mudaram ao ligar e voltaram ao desligar.
#   3. Grava os eventos de entrada (getevent) e o logcat durante os dois acionamentos,
#      para procurar um sinal de hardware ou um broadcast real.
#
# Uso:  ./tools/verificar-botao-gamer.sh [pasta-de-saida]
# Depois envie a pasta gerada (ou o arquivo RESUMO.txt) para análise.

set -uo pipefail

OUT="${1:-gamer-verificacao-$(date +%Y%m%d-%H%M%S)}"
KEY="gcs_need_kill_game_launcher"
export LC_ALL=C

command -v adb >/dev/null 2>&1 || { echo "ERRO: adb não encontrado no PATH."; exit 1; }
if [ "$(adb get-state 2>/dev/null)" != "device" ]; then
  echo "ERRO: nenhum aparelho conectado por ADB (adb get-state)."; exit 1
fi
mkdir -p "$OUT"

sh_adb() { adb shell "$@" 2>/dev/null | tr -d '\r'; }

# "chave=valor" -> "chave<TAB>valor", ordenado pela chave (formato que o join entende)
to_tsv() {
  awk '{ i = index($0, "="); if (i > 1) print substr($0, 1, i - 1) "\t" substr($0, i + 1) }' "$1" |
    sort -t "$(printf '\t')" -k1,1 -u
}

snap() { # $1 = rótulo
  local ns
  for ns in global system secure; do
    sh_adb settings list "$ns" > "$OUT/$1-$ns.txt"
  done
  # [chave]: [valor]  ->  chave=valor
  sh_adb getprop | sed -n 's/^\[\(.*\)\]: \[\(.*\)\]$/\1=\2/p' > "$OUT/$1-props.txt"
  echo "$KEY=$(sh_adb settings get global "$KEY")" > "$OUT/$1-chave-gsr.txt"
  echo "   leitura '$1' guardada ($(cat "$OUT/$1-global.txt" "$OUT/$1-system.txt" "$OUT/$1-secure.txt" | wc -l | tr -d ' ') configurações)."
}

pause() { printf '\n>>> %s\n    Depois pressione Enter... ' "$1"; read -r _; }

# Chaves com valor X desligado, Y ligado e X de novo desligado (X != Y), e chaves que só existem ligado.
compare() { # $1 = sufixo (global|system|secure|props)
  local T; T="$(printf '\t')"
  to_tsv "$OUT/a-desligado-$1.txt" > "$OUT/.a"
  to_tsv "$OUT/b-ligado-$1.txt"    > "$OUT/.b"
  to_tsv "$OUT/c-desligado-$1.txt" > "$OUT/.c"
  join -t "$T" "$OUT/.a" "$OUT/.b" | join -t "$T" - "$OUT/.c" |
    awk -F "$T" -v ns="$1" '$2 == $4 && $2 != $3 { print "  [" ns "] " $1 ":  desligado=\"" $2 "\"  ligado=\"" $3 "\"" }'
  join -t "$T" -v 1 "$OUT/.b" "$OUT/.a" | join -t "$T" -v 1 - "$OUT/.c" |
    awk -F "$T" -v ns="$1" '{ print "  [" ns "] " $1 ":  só existe com o botão LIGADO (valor=\"" $2 "\")" }'
  rm -f "$OUT/.a" "$OUT/.b" "$OUT/.c"
}

echo "== DeskPad Gamer Bridge — verificação do botão Gamer (somente leitura) =="
echo "Aparelho: $(sh_adb getprop ro.product.model) / Android $(sh_adb getprop ro.build.version.release) / $(sh_adb getprop ro.build.display.id)"
echo "Saída: $OUT"
sh_adb getevent -lp > "$OUT/dispositivos-de-entrada.txt"

pause "PASSO 1: deixe o botão Gamer DESLIGADO e espere 3 segundos."
snap a-desligado

adb logcat -c >/dev/null 2>&1 || true
adb shell getevent -lt > "$OUT/getevent.txt" 2>/dev/null &
GE=$!

pause "PASSO 2: LIGUE o botão Gamer. Não toque na tela. Espere 3 segundos."
snap b-ligado

pause "PASSO 3: DESLIGUE o botão Gamer. Não toque na tela. Espere 3 segundos."
snap c-desligado

kill "$GE" >/dev/null 2>&1 || true
wait "$GE" 2>/dev/null || true
adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>/dev/null || true

{
  echo "== RESUMO =="
  echo "Aparelho: $(sh_adb getprop ro.product.model) / Android $(sh_adb getprop ro.build.version.release)"
  echo
  echo "Chave usada pelo Game Space Replacer ($KEY):"
  echo "  desligado (antes): $(cut -d= -f2- "$OUT/a-desligado-chave-gsr.txt")"
  echo "  ligado:            $(cut -d= -f2- "$OUT/b-ligado-chave-gsr.txt")"
  echo "  desligado (depois): $(cut -d= -f2- "$OUT/c-desligado-chave-gsr.txt")"
  echo
  echo "Configurações/propriedades que mudaram ao LIGAR e voltaram ao DESLIGAR:"
  for s in global system secure props; do compare "$s"; done
  echo
  echo "Eventos de chave/switch de hardware gravados (getevent, EV_SW / EV_KEY):"
  grep -E "EV_SW|EV_KEY" "$OUT/getevent.txt" | head -40 || true
  echo
  echo "Linhas do logcat que citam o Game Space ou o botão (primeiras 60):"
  grep -i -E "gcs_|gamelauncher|game_?switch|gamekey|game_key|red_?key|shoulder|slider" "$OUT/logcat.txt" | head -60 || true
} | tee "$OUT/RESUMO.txt"

echo
echo "Pronto. Envie a pasta '$OUT' (ou só '$OUT/RESUMO.txt')."
