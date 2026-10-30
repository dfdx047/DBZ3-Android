#!/usr/bin/env bash
# Codigo recompilado do Budokai 1 (external/dbz1/generated), a partir do yae1_xenon.xex do disco da HD Collection.
#
# O executavel do jogador (nunca no git) vai para external/dbz1/yae1_xenon.xex; o manifesto e a config (funcoes que a
# analise nao separa sozinha, tools/b1-missing-funcs.py) estao em external/dbz1 e sao copiadas de tools/b1/.
# O codegen e o do SDK no PC (REXGLUE, por omissao ~/AndroidStudioProjects/rexglue-sdk/out/linux-amd64/Release/rexglue).
#
# Uso: bash tools/codegen-b1.sh [<yae1_xenon.xex>]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DST="$ROOT/external/dbz1"
REXGLUE="${REXGLUE:-$HOME/AndroidStudioProjects/rexglue-sdk/out/linux-amd64/Release/rexglue}"
mkdir -p "$DST"
cp "$ROOT/tools/b1/dbz1_manifest.toml" "$ROOT/tools/b1/dbz1_config.toml" "$DST/"
if [ -n "${1:-}" ]; then cp "$1" "$DST/yae1_xenon.xex"; fi
SHA="$(sha256sum "$DST/yae1_xenon.xex" | cut -c1-16)"
if [ "$SHA" != "52bce2009c62df9a" ]; then
  echo "ERRO: $DST/yae1_xenon.xex nao e o do disco USA (sha256 $SHA...)" >&2
  exit 1
fi
cd "$DST"
"$REXGLUE" codegen dbz1_manifest.toml
