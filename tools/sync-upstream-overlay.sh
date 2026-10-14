#!/usr/bin/env bash
# Traz para o SDK (external/rexglue-sdk-0.10) os ficheiros do overlay do projeto
# original (external/dbz3/patches/rexglue-sdk/) que mudaram depois de o SDK ter
# sido preparado e que os patches Android NAO tocam. Assim nao se perde nenhum
# patch Android e nao e preciso recriar o SDK do zero.
#
# Os ficheiros do overlay que os patches Android modificam (cache de texturas
# Vulkan, cache de texturas comum, driver de input SDL) NAO sao copiados: a versao
# nova do original tem de ser portada como um patch novo em patches/.
#
# Idempotente: so copia o que for diferente.
#
# Uso (a partir da raiz do repositorio, depois de atualizar o external/dbz3):
#   bash tools/sync-upstream-overlay.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="$ROOT/external/rexglue-sdk-0.10"
OVERLAY="$ROOT/external/dbz3/patches/rexglue-sdk"

if [ ! -d "$OVERLAY" ] || [ ! -d "$SDK/src" ]; then
  echo "ERRO: falta $OVERLAY ou $SDK" >&2
  exit 1
fi

# Seguros (v1.4.5.1): nenhum patch Android lhes toca.
SAFE=(
  include/rex/dbz3_build.h                       # selo do runtime 1.4.5.1
  src/filesystem/afs.cpp                         # mods: ordem estavel, "<mod>.disabled", .install_*
  src/filesystem/devices/host_path_entry.cpp
  src/filesystem/virtual_file_system.cpp         # mount mais especifico (regioes/mods)
  src/input/input_system.cpp                     # fallback XInput->SDL (so Windows)
  src/input/mnk/mnk_input_driver.cpp             # teclas combinadas (testes)
)

# Ficam de fora (conflito com patches Android; portar a mao se forem precisos):
#   src/graphics/vulkan/texture_cache.cpp, include/rex/graphics/vulkan/texture_cache.h,
#   src/graphics/pipeline/texture/cache.cpp, include/rex/graphics/pipeline/texture/cache.h,
#   src/graphics/dbz3_texture_pack.cpp/.h (a API nova so serve com a cache Vulkan nova),
#   src/input/sdl/sdl_input_driver.cpp/.h (patch 0014), d3d12/* (nao existe no Android).

for F in "${SAFE[@]}"; do
  SRC="$OVERLAY/$F"
  DST="$SDK/$F"
  if [ ! -f "$SRC" ]; then
    echo "sem origem:  $F"
    continue
  fi
  if [ -f "$DST" ] && cmp -s "$SRC" "$DST"; then
    echo "igual:       $F"
    continue
  fi
  mkdir -p "$(dirname "$DST")"
  cp "$SRC" "$DST"
  echo "atualizado:  $F"
done
echo "Overlay sincronizado (so os ficheiros seguros)."
