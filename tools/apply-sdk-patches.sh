#!/usr/bin/env bash
# Aplica os patches Android (patches/NNNN-*.patch, por ordem) ao SDK ReXGlue em
# external/rexglue-sdk-0.10.
#
# Pressupoe que o SDK ja tem o overlay do projeto original aplicado
# (cp -a external/dbz3/patches/rexglue-sdk/. external/rexglue-sdk-0.10/).
# Pode ser executado sempre que houver patches novos: os que ja estao
# aplicados sao saltados.
#
# Uso (a partir da raiz do repositorio):
#   bash tools/apply-sdk-patches.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="$ROOT/external/rexglue-sdk-0.10"

if [ ! -d "$SDK/src" ]; then
  echo "ERRO: SDK nao encontrado em $SDK" >&2
  exit 1
fi

if [ ! -f "$SDK/thirdparty/sdl3/CMakeLists.txt" ]; then
  echo "ERRO: submodulos do SDK em falta. Corre:" >&2
  echo "  git -C \"$SDK\" submodule update --init --recursive" >&2
  exit 1
fi

if [ ! -f "$SDK/include/rex/dbz3_build.h" ]; then
  echo "ERRO: o overlay do projeto original nao esta aplicado ao SDK. Corre:" >&2
  echo "  cp -a \"$ROOT/external/dbz3/patches/rexglue-sdk/.\" \"$SDK/\"" >&2
  exit 1
fi

cd "$SDK"
# Registo dos patches ja aplicados. Necessario porque um patch posterior pode
# mexer nas mesmas linhas de um anterior e o teste "--reverse" deixa de o
# reconhecer como aplicado (foi o que aconteceu com o 0008 depois do 0010).
STAMP="$SDK/.dbz3-android-patches"
touch "$STAMP"
for PATCH in "$ROOT"/patches/[0-9][0-9][0-9][0-9]-*.patch; do
  NAME="$(basename "$PATCH")"
  if grep -qxF "$NAME" "$STAMP"; then
    echo "ja aplicado: $NAME"
    continue
  fi
  if patch -p1 --dry-run --reverse --force --silent < "$PATCH" > /dev/null 2>&1; then
    echo "$NAME" >> "$STAMP"
    echo "ja aplicado: $NAME"
    continue
  fi
  TEST="$(patch -p1 --dry-run --forward < "$PATCH" 2>&1 || true)"
  if ! patch -p1 --dry-run --forward --silent < "$PATCH" > /dev/null 2>&1; then
    # Todos os ficheiros dizem "previously applied": ja estava aplicado.
    FILES="$(grep -c '^checking file' <<< "$TEST" || true)"
    APPLIED="$(grep -c 'previously applied' <<< "$TEST" || true)"
    if [ "$FILES" -gt 0 ] && [ "$FILES" = "$APPLIED" ]; then
      echo "$NAME" >> "$STAMP"
      echo "ja aplicado: $NAME"
      continue
    fi
    echo "ERRO: $NAME nao aplica de forma limpa. Saida do teste:" >&2
    echo "$TEST" >&2
    exit 1
  fi
  patch -p1 --forward --silent < "$PATCH"
  echo "$NAME" >> "$STAMP"
  echo "aplicado:    $NAME"
done
echo "SDK pronto."
