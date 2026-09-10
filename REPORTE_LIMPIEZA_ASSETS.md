# Reporte de Limpieza de Assets y Corrección de Rutas
Paquete: `com.diamon.moria` (Moria Firmware Extractor)

Este reporte documenta los archivos y carpetas dentro de `app/src/main/assets/data/data/com.diamon.moria/files/usr` que no son necesarios para la ejecución de los binarios en la arquitectura arm64-v8a del proyecto Android y que han sido optimizados para reducir significativamente el peso del APK y mejorar el rendimiento de la aplicación.

### 1. Archivos y Carpetas Optimizados / Reubicados

**A. Cabeceras y Archivos C/C++ (NDK)**
*   **Ruta Original:** `usr/include/`
*   **Reubicación:** `include/` y `app/src/main/cpp/include/`
*   **Razón de exclusión en assets:** Solo son requeridos para la compilación C/C++ con el NDK; no se necesitan en runtime por el APK. Los originales se mantienen intactos en `fake_root`.

**B. Librerías Estáticas (.a) (NDK)**
*   **Ruta Original:** `usr/lib/*.a` (`libarchive.a`)
*   **Razón de exclusión en assets:** Las librerías estáticas son artefactos de enlazado para compilación NDK, no para ejecución en dispositivo.

**C. Archivos de Configuración de Desarrollo (PkgConfig)**
*   **Ruta:** `usr/lib/pkgconfig/` (`libarchive.pc`)
*   **Razón:** Pkg-config no se ejecuta en el dispositivo Android para lanzar la aplicación.

### 2. Archivos Conservados en Assets
*   **`usr/share/man/`**: Páginas de documentación y manuales disponibles para consulta o soporte de las herramientas empaquetadas.

### Resumen del Impacto
*   **Velocidad de Extracción:** El archivo `AssetHelper.java` procesa rápidamente los recursos esenciales en el primer arranque.
*   **Portabilidad:** Eliminadas rutas absolutas de desarrollo a Termux mediante `patchelf --remove-rpath`.
