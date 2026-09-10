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

export COMMON_FLAGS="-fPIC -Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include"
export CFLAGS="$COMMON_FLAGS"
export CXXFLAGS="$COMMON_FLAGS"

export BASE_LDFLAGS="-flto -Wl,-z,max-page-size=16384 -Wl,-z,relro,-z,now -L$FAKE_USR/lib -Wl,-rpath,$APP_PREFIX/lib"
export EXE_LDFLAGS="-pie $BASE_LDFLAGS"
export SHARED_LDFLAGS="$BASE_LDFLAGS"

export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig"
export PKG_CONFIG_SYSROOT_DIR="$DESTDIR"

# ==========================================
# 2. DESCARGA DEL CÓDIGO FUENTE
# ==========================================
echo "Descargando código fuente de moria..."
rm -rf "$HOME/moria"
git clone --depth 1 --recurse-submodules https://github.com/nmatt0/moria.git "$HOME/moria"

mkdir -p "$HOME/moria/build"
cd "$HOME/moria/build" || exit 1

# ==========================================
# 3. CONFIGURACIÓN CON CMAKE
# ==========================================
cmake .. \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DCMAKE_C_FLAGS="$COMMON_FLAGS" \
  -DCMAKE_CXX_FLAGS="$COMMON_FLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$EXE_LDFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DCMAKE_PREFIX_PATH="$FAKE_USR" \
  -DCMAKE_FIND_ROOT_PATH="$FAKE_USR" \
  -DBUILD_TESTING=OFF

# ==========================================
# 4. COMPILACIÓN E INSTALACIÓN
# ==========================================
ninja -j"$(nproc)"
DESTDIR="$DESTDIR" ninja install

# ==========================================
# 5. VERIFICACIÓN FINAL
# ==========================================
echo
echo "=== Compilación de moria Exitosa ==="
if [ -f "$FAKE_USR/bin/moria" ]; then
    ls -lh "$FAKE_USR/bin/moria"
    readelf -d "$FAKE_USR/bin/moria" | grep -E "(NEEDED|RPATH|RUNPATH)" || true
    readelf -l "$FAKE_USR/bin/moria" | grep LOAD || true
fi
