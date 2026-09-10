# Nombres Nuevos vs Antiguos (Binarios Ejecutables) - Moria Firmware Extractor

De acuerdo a la verificación del archivo `REPORTE_ANALISIS_DEPENDENCIAS.md` y a la separación estricta entre binarios (ejecutables) y librerías compartidas (`.so`), a continuación se presenta la relación de los binarios reales de la aplicación.

Google Play requiere que todo archivo dentro de `jniLibs/arm64-v8a/` tenga el formato `lib<nombre>.so`. Por ello, los binarios han sido renombrados en la carpeta, y el código de la app (`AssetHelper.java`) se encarga de extraerlos/enlazarlos en las rutas exactas requeridas en el runtime de la aplicación en tiempo de ejecución.

## 1. Mapeo de Binarios Ejecutables

| Nombre Antiguo (Original) | Nombre Nuevo (Google Play) en `arm64-v8a` | Ruta exacta reconstruida en App (`files/usr`) |
|---|---|---|
| `moria` | `libmoria_bin.so` | `usr/bin/moria` |
| `bsdunzip` | `libbsdunzip.so` | `usr/bin/bsdunzip` |

---

## 2. Dependencias de los Binarios y Librerías

Todas las dependencias listadas han sido verificadas dentro de `jniLibs/arm64-v8a/`. Las librerías que originalmente tenían versionado (como `.so.13`, `.so.5`, `.so.1`, `.so.3`) han sido renombradas con sufijos seguros como `_13.so`, `_5.so`, etc., y la app restaura sus SONAMEs originales como enlaces simbólicos en `usr/lib/`.

| Binario / Librería | Dependencias Exigidas (DT_NEEDED) | Estado en `arm64-v8a` / App |
|---|---|---|
| **`moria`** | `libz.so.1`, `liblzma.so.5`, `liblz4.so`, `libzstd.so.1`, `libc.so`, `libc++_shared.so`, `libdl.so`, `libm.so` | ✅ Todas las dependencias presentes. |
| **`bsdunzip`** | `libz.so.1`, `liblzma.so.5`, `liblz4.so`, `libzstd.so.1`, `libcrypto.so.3`, `libiconv.so`, `libcharset.so`, `libxml2.so.16`, `libacl.so`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. |
| **`libarchive.so.13`** | `libz.so.1`, `liblzma.so.5`, `liblz4.so`, `libzstd.so.1`, `libcrypto.so.3`, `libiconv.so`, `libcharset.so`, `libxml2.so.16`, `libacl.so`, `libdl.so`, `libc.so` | ✅ Todas las dependencias presentes. |

---

## 3. Estado de Archivos en `assets` vs `fake_root`

1. **Binarios y Librerías (.so):** Ubicados en `app/src/main/jniLibs/arm64-v8a/`. Durante la ejecución de la app, se enlazan (symlinks) a la jerarquía esperada (`usr/bin`, `usr/lib`).
2. **Documentación / Man pages:** Ubicadas en `app/src/main/assets/data/data/com.diamon.moria/files/usr/share/man/`.
3. **Archivos de desarrollo (Headers .h y librerías estáticas .a):** Cabeceras C/C++ ubicadas en `include/` y `app/src/main/cpp/include/` para compilación NDK. Los originales se mantienen de respaldo en `fake_root`.
