# Reporte de Auditoría y Corrección: Rutas y Portabilidad de Moria Firmware Extractor
ESTADO: CORREGIDO (Septiembre 2026)

Este documento detalla los hallazgos críticos y las acciones correctivas aplicadas para garantizar la portabilidad de las herramientas nativas (moria, bsdunzip, libarchive, etc.) en Android para el paquete `com.diamon.moria`.

### 1. Problema: Rutas Hardcoded en Binarios (RUNPATH) - SOLUCIONADO
**Acción:** Se utilizó `patchelf --remove-rpath` en todas las librerías compartidas y binarios dentro de `app/src/main/jniLibs/arm64-v8a/`. Durante la auditoría se detectaron referencias hardcodeadas a rutas de compilación obsoletas (ej. `/data/data/com.termux/files/usr/lib` y `/data/data/com.diamon.moria/files/usr/lib`) incrustadas en la sección dinámica ELF `RUNPATH`.
**Resultado:** El sistema Android carga ahora las librerías dinámicas utilizando las rutas estándar del sistema y el directorio nativo del APK (`nativeLibraryDir`), o en su defecto a través de los enlaces simbólicos gestionados en runtime. Se eliminó por completo la dependencia del entorno de compilación Termux.

### 2. Contaminación en Assets y Scripts de Configuración - SOLUCIONADO
**Acción:** Se auditaron las carpetas `assets` y `fake_root` en busca de rutas absolutas al entorno de desarrollo. Se identificaron configuraciones de desarrollo como `pkgconfig/libarchive.pc` que contenían prefijos estáticos.
**Resultado:** Al no ser necesarias estas herramientas de desarrollo en el dispositivo móvil en tiempo de ejecución, se excluyeron las carpetas `pkgconfig/` del empaquetado de assets. Se preservaron intactos los archivos originales en `fake_root/` y `libs/` como referencia y respaldo.

### 3. Optimización de Espacio en el APK - SOLUCIONADO
**Acción:** Se excluyeron de la carpeta de `assets` los recursos que no son necesarios en tiempo de ejecución (librerías estáticas `.a` y cabeceras C/C++ `.h`), reubicando los encabezados en `include/` y `app/src/main/cpp/include/` para el desarrollo con NDK en el proyecto Android. Los archivos originales se conservan intactos en la carpeta de referencia `fake_root`.
**Resultado:** Se redujo drásticamente el tamaño del paquete y la redundancia, evitando la extracción innecesaria de archivos de desarrollo en el dispositivo y acelerando la rutina de inicialización de `AssetHelper.java` durante el primer arranque.

### 4. Carga Dinámica de Dependencias JNI y Renombramiento - SOLUCIONADO
**Acción:** Se implementó una resolución inteligente de dependencias nativas en la capa Java (`AssetHelper.java`), junto con el renombramiento de los binarios PIE (Position-Independent Executable). Google Play y el empaquetador de Android exigen estrictamente que todo archivo dentro de `jniLibs` lleve el prefijo `lib` y extensión `.so` (sin sufijos de versión numérica como `.so.13` o `.so.5`).
**Resultado:** 
* Binarios ejecutables renombrados: `moria` -> `libmoria_bin.so`, `bsdunzip` -> `libbsdunzip.so`.
* Librerías versionadas renombradas a formato seguro: `libarchive.so.13` -> `libarchive_13.so`, `liblzma.so.5` -> `liblzma_5.so`, `libz.so.1` -> `libz_1.so`, `libzstd.so.1` -> `libzstd_1.so`, `libcrypto.so.3` -> `libcrypto_3.so`, `libxml2.so.16` -> `libxml2_16.so`.
* La aplicación (`AssetHelper.java`) recupera los binarios y librerías desde `getApplicationInfo().nativeLibraryDir` y crea los enlaces simbólicos (symlinks) correspondientes en `usr/bin/` y `usr/lib/` para restaurar los nombres canónicos requeridos por el sistema.
