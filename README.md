# Moria Firmware Extractor

<p align="center">
  <img src="logo.png" width="160" alt="Moria Firmware Extractor Logo">
</p>

[![Android](https://img.shields.io/badge/Android-6.0%20(API%2023)%20a%20Android%2017%20(API%2037)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![ABI](https://img.shields.io/badge/ABI-arm64--v8a-0091EA?logo=arm&logoColor=white)](https://developer.android.com/ndk/guides/abis)
[![NDK](https://img.shields.io/badge/NDK-r30--rc1-4CAF50?logo=android&logoColor=white)](https://developer.android.com/ndk)
[![AGP](https://img.shields.io/badge/AGP-9.2.1-blue?logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![16 KB Pages](https://img.shields.io/badge/16%20KB%20Pages-Compatible-success?logo=android)](https://developer.android.com/guide/practices/page-sizes)
[![Moria](https://img.shields.io/badge/moria-v__upstream-orange?logo=github)](https://github.com/nmatt0/moria)
[![libarchive](https://img.shields.io/badge/libarchive-integrado-blue)](https://github.com/libarchive/libarchive)
[![Licencia](https://img.shields.io/badge/Licencia-Apache--2.0-blue)](./LICENSE)

Aplicación Android de alto rendimiento para **identificación forense, extracción profunda, desempaquetado de contenedores y análisis de entropía** de imágenes de firmware y dispositivos IoT/embebidos sin requerir privilegios de superusuario (`root`), potenciada por los motores nativos **moria** y **libarchive** compilados para arquitectura **ARM64 (`arm64-v8a`)**.

> **Nombre visible de la app:** Moria Firmware Extractor  
> **Identificador de paquete:** `com.diamon.moria`  
> **Versión actual:** `1.0.0` (Código de versión: `1`)  
> **Rango de soporte Android:** API 23 a API 37 (Android 6.0 a Android 17+)  
> **Arquitectura objetivo:** `arm64-v8a` (con alineación de página a 16 KB para Android 15+)  
> **Autor:** [Danielk10](https://github.com/Danielk10)

---

## 1) Objetivo y Alcance del Proyecto

**Moria Firmware Extractor** traslada el poder del análisis forense de bajo nivel directamente a dispositivos móviles Android. Identifica las estructuras embebidas dentro de paquetes e imágenes de firmware (sistemas de archivos, kernels, cargadores de arranque, archivos comprimidos, certificados y claves criptográficas) y las desempaqueta de forma recursiva sin requerir herramientas externas ni permisos de `root` o `sudo`.

### Características destacadas del motor Moria
- **Identificación estructural de primera clase:** Genera un árbol de hallazgos con offsets exactos en hexadecimal, tipos detectados y porcentaje de confianza.
- **Desempaquetado recursivo in-process:** Si dentro de un volumen UBI se aloja un contenedor SquashFS comprimido con gzip, Moria lo desempaca capa tras capa automáticamente.
- **Determinismo absoluto:** La misma imagen de entrada produce siempre los mismos resultados reproducibles sin desempates aleatorios.
- **Seguridad en entradas hostiles:** Todas las lecturas realizan verificación estricta de límites (*bounds-checking*), las escrituras usan llamadas `openat` con `O_NOFOLLOW` para prevenir escapes de enlaces simbólicos o ataques de *directory traversal*, y la descompresión cuenta con salvaguardas estrictas frente a bombas de descompresión (*zip bombs*).
- **Salida dual (Humana / JSON):** Salida legible en consola y modo JSON (`-j`) estructurado para scripts y agentes automatizados.

### Pipeline Forense: Moria + Mithril
El ecosistema de seguridad forense se complementa en dos fases esenciales:
```text
                      ┌─────────────────────────────────────────┐
                      │    Imagen de Firmware (firmware.bin)    │
                      └────────────────────┬────────────────────┘
                                           │
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │        Moria Firmware Extractor         │
                      │       (Extracción y Carving)            │
                      │     moria -e firmware.bin               │
                      └────────────────────┬────────────────────┘
                                           │ Genera directorio:
                                           │ firmware.bin.extracted/
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │        Mithril Firmware Scanner         │
                      │ (Análisis de Secretos, SBOM, CVE, Lic)  │
                      │   mithril firmware.bin.extracted/       │
                      └─────────────────────────────────────────┘
```
1. **Moria** realiza la identificación estructural, disección, tallado de bytes (*carving*) y descompresión del sistema de archivos.
2. [Mithril](https://github.com/nmatt0/mithril) ([android-mithril-firmware-scanner](https://github.com/Danielk10/android-mithril-firmware-scanner)) analiza el contenido extraído en busca de secretos, credenciales expuestas, SBOM, CVEs y auditoría de licencias.

---

## 2) Capacidades de Extracción y Formatos Soportados

El motor de Moria integrado desempaca en proceso y sin dependencias externas:

| Categoría | Formatos y Tecnologías Soportadas |
|---|---|
| **Sistemas de Archivos** | SquashFS, ext2/ext3/ext4, F2FS, FAT12/FAT16/FAT32, exFAT, NTFS, HFS+/HFSX, XFS, btrfs, JFFS2, UBI/UBIFS, romfs, YAFFS2, cramfs, EROFS |
| **Archivos y Paquetes** | ZIP, tar, cpio, ISO 9660, Android sparse, Android boot images (`boot.img`) |
| **Kernels y Cargadores** | U-Boot uImage, U-Boot FIT, flujos comprimidos independientes (gzip, xz, zstd, lz4) |
| **Firmwares Propietarios** | RAE Systems / Honeywell RFP (tabla de secciones con descompresión LZARI de cada bloque), contenedores vendor IoT |
| **Base de Firmas Embebida** | • `signatures/`: Firmas base con validación estructural C++ (CRCs y punteros de bloque).<br>• `signatures-firmware/`: Magics de contenedores firmware de fabricantes cargados por defecto.<br>• `signatures-generated/`: ~2.500 firmas derivadas de la base de datos mágica de `file(1)` habilitadas con `--broad`. |

---

## 3) Arquitectura de la Aplicación y Funcionalidades de la UI

La aplicación combina botones táctiles de ejecución rápida con una terminal interactiva tipo UNIX:

```text
┌─────────────────────────────────────────────────────────────┐
│                 Moria Firmware Extractor                    │
├─────────────────────────────────────────────────────────────┤
│ [ Seleccionar Firmware ] -> Target: firmware.bin (12.4 MB)  │
├─────────────────────────────────────────────────────────────┤
│ [ Identificar ]           [ Extraer (-e) ]                  │
│ [ Carve (-c) ]            [ Entropía (-E) ]                 │
│ [ Listar (-l) ]           [ Profundo (-A) ]                 │
│ [x] JSON (-j)             [ ] Amplio (--broad)              │
├─────────────────────────────────────────────────────────────┤
│ Consola de Terminal UNIX / Sandbox                          │
│ $ moria -e "firmware.bin"                                   │
│ [0x00000000] u-boot-legacy-image (100%)                    │
│ [0x00040000] squashfs-v4-le (100%) -> extracted/            │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ [▲] [▼] [◀] [▶] [TAB] [CLEAR] [PASTE] [COPY] [ABORT]        │
├─────────────────────────────────────────────────────────────┤
│ [ moria -e firmware.bin                               ] [▶] │
└─────────────────────────────────────────────────────────────┘
```

### Funcionalidades clave de la interfaz
1. **Acciones directas:**
   - **Identificar:** Inspección estructural inmediata sin modificar archivos.
   - **Extraer (`-e`):** Desempaqueta en carpetas `0x<offset>-<tipo>/` y genera un `manifest.json`.
   - **Carve (`-c`):** Extrae los rangos de bytes directos a carpetas `.carved/` sin parseo.
   - **Entropía (`-E`):** Escaneo de densidad para resaltar secciones cifradas o comprimidas.
   - **Listar (`-l`):** Muestra los miembros de archivos (tar, cpio, zip) sin descomprimirlos.
   - **Profundo (`-A`):** Escaneo exhaustivo del espacio completo de la imagen.
2. **Barra de navegación de terminal:**
   - Historial de comandos (`Arriba` / `Abajo`).
   - Movimiento de cursor (`Izquierda` / `Derecha`).
   - Inserción de tabulador (4 espacios) para estructuración de comandos.
   - Limpieza de pantalla (`Clear`), pegado desde portapapeles (`Paste`), copiado rápido de logs (`Copy`).
   - Botón de parada de emergencia (`Abort`) que destruye de forma segura el proceso nativo si una tarea se extiende.
3. **Consola tipo UNIX con comandos sandbox:**
   - Comandos internos: `ls`, `cd`, `cat`, `touch`, `mkdir`, `rm`, `cp`, `echo`, `pwd`, `clear`, `help`.
   - Intérprete shell `/system/bin/sh` activado automáticamente cuando se usan tuberías (`|`), redirecciones (`>`, `<`) o cadenas de comandos (`&&`, `;`).
4. **Gestión de almacenamiento SAF y Exportación:**
   - Integración nativa con el *Storage Access Framework* (SAF) para importar firmwares desde cualquier proveedor (Drive, SD, descargas).
   - Exportación directa en un clic de los firmwares extraídos a `Downloads/Moria_Firmware/` cumpliendo los estándares de *Scoped Storage* de Android 10 a Android 17+.

---

## 4) Rutas del Runtime Nativo y Aislamiento

Para garantizar que los binarios nativos encuentren sus librerías dinámicas y activos compartidos sin colisionar con otros paquetes de Android ni requerir acceso root, se implementa una arquitectura de enlaces de runtime:

### Rutas del entorno
- **Ruta de compilación y empaquetado (Assets):**
  ```text
  app/src/main/assets/data/data/com.diamon.moria/files/usr
  ```
- **Ruta física en tiempo de ejecución (Sandbox Android):**
  ```text
  /data/user/0/com.diamon.moria/files/usr
  ```
- **Directorio de ejecutables (`usr/bin`):**
  Enlaces simbólicos creados por `AssetHelper.java` hacia los binarios empaquetados en `nativeLibraryDir`.
- **Directorio de librerías (`usr/lib`):**
  Enlaces simbólicos con los SONAMEs originales (`libarchive.so.13`, `liblzma.so.5`, `libzstd.so.1`, etc.).
- **Variables de entorno inyectadas:**
  `LD_LIBRARY_PATH` apunta a `nativeLibraryDir` y `usr/lib`; `PATH` incluye `usr/bin` y el entorno del sistema.

---

## 5) Estructura del Repositorio

```text
├── app/                                    # Módulo principal de la aplicación Android
│   ├── build.gradle                        # Configuración Gradle (SDK 37, NDK r30, ABI arm64-v8a)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         # Declaración de actividades y permisos SAF
│       │   ├── assets/                     # Man pages (usr/share/man) y Política de Privacidad HTML
│       │   ├── cpp/                        # Código C++ JNI y CMakeLists.txt
│       │   ├── java/com/diamon/moria/      # Lógica en Java (MainActivity, TerminalExecutor, FileManager, AssetHelper)
│       │   ├── jniLibs/arm64-v8a/          # Binarios nativos y librerías compartidas (.so)
│       │   └── res/                        # Recursos de UI, layouts, temas y strings (EN principal, ES secundario)
├── fake_root/                              # Directorio de instalación intermedia para empaquetado
├── libs/                                   # Copias de referencia de librerías nativas compiladas
├── analyze_deps.py                         # Script utilitario para analizar dependencias DT_NEEDED
├── setup-sdk.sh                            # Script automatizado para descargar y configurar Android SDK/NDK
├── build_libarchive_moria_custom.sh        # Script de compilación aislada de libarchive en Termux
├── build_moria_custom.sh                  # Script de compilación de Moria enlazado con libarchive
├── NOMBRES_BINARIOS_VS_ANTIGUOS.md         # Documentación de mapeo de nombres de binarios para Google Play
├── REPORTE_ANALISIS_DEPENDENCIAS.md        # Reporte de dependencias ELF de los binarios
├── REPORTE_AUDITORIA_RUTAS_PORTABILIDAD.md # Auditoría de rutas y portabilidad del runtime
├── REPORTE_LIMPIEZA_ASSETS.md              # Reporte de optimización de assets
├── logo.png                                # Logotipo oficial de la aplicación
└── LICENSE                                 # Licencia Apache 2.0 del proyecto
```

---

## 6) Binarios y Dependencias Nativas

### Binarios principales en `jniLibs/arm64-v8a/`
Google Play exige que todo archivo binario dentro de un APK mantenga la nomenclatura `lib<nombre>.so`. En tiempo de inicio, `AssetHelper` genera los enlaces simbólicos ejecutables correspondientes:

| Binario en `jniLibs` | Enlace generado en Runtime | Función principal |
|---|---|---|
| `libmoria_bin.so` | `usr/bin/moria` | Motor central de identificación, extracción y análisis de firmware |
| `libbsdunzip.so` | `usr/bin/bsdunzip` | Descompresor universal complementario de libarchive |

### Librerías compartidas enlazadas
- **`libarchive.so` / `libarchive.so.13`**: Motor universal de descompresión y lectura de múltiples formatos de archivo.
- **`libz.so.1`**: Algoritmo Deflate / compresión gzip.
- **`liblzma.so.5`**: Soporte para formatos XZ y algoritmos LZMA/LZMA2.
- **`liblz4.so`**: Descompresor ultrarrápido LZ4.
- **`libzstd.so.1`**: Algoritmo Zstandard (ZSTD) de alta compresión.
- **`libcrypto.so.3`**: Funciones criptográficas y hashes de OpenSSL 3.
- **`libxml2.so.16` / `libiconv.so` / `libcharset.so`**: Procesamiento de texto, codificaciones y metadatos XML.
- **`libacl.so`**: Soporte para listas de control de acceso en sistemas POSIX.

> **Compatibilidad con Android 15+:** Todos los binarios y librerías nativas han sido compilados con `-Wl,-z,max-page-size=16384`, garantizando compatibilidad total con dispositivos de páginas de memoria de **16 KB**.

---

## 7) Comandos de Consola y Ejemplos de Uso

Desde la caja de comandos de la terminal se pueden ejecutar todas las funciones nativas de `moria` combinadas con utilidades del sistema:

```bash
# 1. Identificar estructuras embebidas en un firmware
moria firmware.bin

# 2. Extraer recursivamente todos los sistemas de archivos y contenedores
moria -e firmware.bin

# 3. Extraer a un directorio específico
moria -e -C /ruta/salida firmware.bin

# 4. Obtener reporte detallado en formato JSON (ideal para automatización)
moria -j firmware.bin

# 5. Cortar (carving) de bloques de bytes en bruto sin parseo
moria -c firmware.bin

# 6. Análisis de entropía para detectar regiones comprimidas o cifradas
moria -E firmware.bin

# 7. Listar contenidos de un archivo sin extraer
moria --list archivo.tar

# 8. Cargar más de 2.500 firmas genéricas adicionales
moria --broad firmware.bin

# 9. Tuberías y filtros de shell combinados
moria firmware.bin | grep -i squashfs
```

---

## 8) Compilación y Configuración

### Configuración del SDK/NDK
El proyecto incluye un script automatizado para preparar las herramientas oficiales de Android (SDK 37, Build-Tools 37.0.0, CMake 4.1.2, NDK r30):

```bash
chmod +x setup-sdk.sh
./setup-sdk.sh
```

### Compilación con Gradle Wrapper
Una vez configurado el SDK, se puede compilar la APK de release o depuración:

```bash
# Compilación de la APK de Release
./gradlew assembleRelease

# Compilación de la APK de Depuración
./gradlew assembleDebug
```

La APK resultante se generará en el directorio configurado (`/tmp/moria/outputs/apk/release/` o `app/build/outputs/apk/`).

### Compilación nativa en Termux (para regenerar binarios)
Si se desea recompilar los binarios nativos directamente en un dispositivo Android con Termux:

```bash
chmod 700 build_libarchive_moria_custom.sh build_moria_custom.sh

# Paso 1: Compilar e instalar libarchive aislado
./build_libarchive_moria_custom.sh

# Paso 2: Compilar Moria enlazando con la libarchive local
./build_moria_custom.sh
```

---

## 9) Autor, Licencias y Créditos

### Autor de la Aplicación Android
- **Danielk10** — [GitHub: @Danielk10](https://github.com/Danielk10) — [danielpdiamon@gmail.com](mailto:danielpdiamon@gmail.com)

### Licencia del Proyecto Android
Este proyecto está licenciado bajo la **Licencia Apache 2.0**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

### Proyectos Originales y Componentes de Terceros

| Proyecto / Componente | Autor / Organización | Repositorio Oficial | Licencia |
|---|---|---|---|
| **Moria CLI** | [Matt Brown (nmatt0)](https://github.com/nmatt0) | [github.com/nmatt0/moria](https://github.com/nmatt0/moria) | **Licencia MIT** |
| **libarchive** | [libarchive contributors](https://github.com/libarchive) | [github.com/libarchive/libarchive](https://github.com/libarchive/libarchive) | **Licencia BSD 2-Clause** |
| **bsdunzip** | libarchive contributors | [github.com/libarchive/libarchive](https://github.com/libarchive/libarchive) | **Licencia BSD 2-Clause** |
| **OpenSSL (libcrypto)** | The OpenSSL Project | [openssl.org](https://www.openssl.org) | **Apache 2.0** |
| **Zstandard (libzstd)** | Meta Platforms / Yann Collet | [facebook.github.io/zstd](https://facebook.github.io/zstd/) | **BSD 3-Clause** |
| **LZ4** | Yann Collet | [lz4.org](https://lz4.org) | **BSD 2-Clause** |
| **XZ / liblzma** | Lasse Collin & Tukaani Project | [tukaani.org/xz](https://tukaani.org/xz/) | **Dominio Público / LGPL 2.1** |
| **zlib** | Jean-loup Gailly & Mark Adler | [zlib.net](https://zlib.net) | **Licencia zlib** |
| **libxml2** | GNOME Project | [gitlab.gnome.org/GNOME/libxml2](https://gitlab.gnome.org/GNOME/libxml2) | **Licencia MIT** |
| **ICU** | Unicode, Inc. | [icu.unicode.org](https://icu.unicode.org) | **Unicode / ICU License** |
| **libiconv / libcharset** | GNU Project | [gnu.org/software/libiconv](https://gnu.org/software/libiconv/) | **LGPL 2.1** |
| **libacl** | Silicon Graphics & SuSE | [savannah.nongnu.org/projects/acl](https://savannah.nongnu.org/projects/acl) | **LGPL 2.1** |

