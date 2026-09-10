#!/bin/bash
set -euo pipefail

# ==========================================
# 0. INSTALACIÓN DE DEPENDENCIAS EN TERMUX
# ==========================================
echo "Instalando utilidades de compilación..."
pkg install -y git clang cmake ninja pkg-config binutils

# ==========================================
# 1. VARIABLES DE ENTORNO CRÍTICAS
# ==========================================
cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.moria/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"

export CC=clang
export CXX=clang++

export COMMON_FLAGS="-fPIC -Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR="
export CFLAGS="$COMMON_FLAGS"
export CXXFLAGS="$COMMON_FLAGS"

export BASE_LDFLAGS="-flto -Wl,-z,max-page-size=16384 -Wl,-z,relro,-z,now -Wl,-rpath,$APP_PREFIX/lib"
export SHARED_LDFLAGS="$BASE_LDFLAGS"

# ==========================================
# 2. DESCARGA DEL CÓDIGO FUENTE
# ==========================================
echo "Descargando libarchive para moria..."
rm -rf "$HOME/libarchive_moria"
git clone --depth 1 https://github.com/libarchive/libarchive.git "$HOME/libarchive_moria"

mkdir -p "$HOME/libarchive_moria/build"
cd "$HOME/libarchive_moria/build" || exit 1

# ==========================================
# 3. CONFIGURACIÓN CON CMAKE
# ==========================================
echo "Configurando libarchive en $APP_PREFIX..."
cmake .. \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DCMAKE_C_FLAGS="$COMMON_FLAGS" \
  -DCMAKE_CXX_FLAGS="$COMMON_FLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DENABLE_TEST=OFF \
  -DENABLE_TAR=OFF \
  -DENABLE_CPIO=OFF \
  -DENABLE_CAT=OFF \
  -DENABLE_BZip2=OFF

# ==========================================
# 4. COMPILACIÓN E INSTALACIÓN
# ==========================================
ninja -j"$(nproc)"
DESTDIR="$DESTDIR" ninja install

# ==========================================
# 5. VERIFICACIÓN FINAL
# ==========================================
echo
echo "=== libarchive instalado para moria ==="
ls -lh "$FAKE_USR/lib"/libarchive.so* || true
readelf -l "$FAKE_USR/lib/libarchive.so" | grep LOAD || true
