#!/usr/bin/env bash
# Teste A/B de opcoes do runtime (cvars) com captura de log e de ecra.
#
# Uso (a partir da raiz do repositorio, com o aparelho ligado por adb):
#   bash tools/gpu-ab.sh <nome> [cvar=valor ...]
#
# Exemplos:
#   bash tools/gpu-ab.sh nativo-base
#   bash tools/gpu-ab.sh nativo-snorm snorm16_render_target_full_range=false
#   bash tools/gpu-ab.sh turnip-base            (com o Turnip ativo no launcher)
#
# O que faz:
#   1. escreve as cvars em files/cvars.txt (vazio = sem mudancas) e fecha o jogo;
#   2. abre o jogo e espera que chegues ao sitio a comparar (ex. a mesma luta);
#   3. ao carregares Enter tira uma captura de ecra e guarda o logcat inteiro;
#   4. tudo fica em perf/gpu/<nome>/ (screenshot.png, logcat.txt, cvars.txt, props.txt).
set -euo pipefail

PKG="com.dfdx047.dragonfury"
NAME="${1:?uso: gpu-ab.sh <nome> [cvar=valor ...]}"
shift || true
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/perf/gpu/$NAME"
APPDIR="/sdcard/Android/data/$PKG/files"
mkdir -p "$OUT"

printf '%s\n' "$@" > "$OUT/cvars.txt"
adb push "$OUT/cvars.txt" "$APPDIR/cvars.txt" > /dev/null
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -n "$PKG/.game.GameActivity" -a com.dfdx047.dragonfury.PLAY > /dev/null
echo "Jogo aberto com: ${*:-(sem cvars)}"
read -r -p "Chega ao sitio a comparar e carrega Enter para capturar... " _

adb exec-out screencap -p > "$OUT/screenshot.png"
adb logcat -d -v threadtime > "$OUT/logcat.txt"
{
  adb shell getprop ro.product.model
  adb shell getprop ro.soc.model
  adb shell getprop ro.hardware.vulkan
  adb shell ls "$APPDIR/driver" 2>/dev/null || true
} > "$OUT/props.txt"
grep -E "dbz3: tiron|VK_|Vulkan|vulkan|driver|error|warn" "$OUT/logcat.txt" | head -400 > "$OUT/resumo.txt" || true

# Volta ao normal: sem cvars de teste.
adb shell rm -f "$APPDIR/cvars.txt"
echo "Guardado em $OUT"
