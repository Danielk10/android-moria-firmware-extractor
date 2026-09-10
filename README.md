# Moria Firmware Extractor

<p align="center">
  <img src="logo.png" width="160" alt="Moria Firmware Extractor Logo">
</p>

Scripts de compilación cruzada y aplicación para **Moria** (extractor y analizador forense de firmware) y su dependencia **libarchive** con interfaz táctil y consola de terminal integrada.

## Descripción

Este repositorio contiene los scripts necesarios para compilar desde el código fuente:

- **libarchive**: Biblioteca multiplataforma para lectura y escritura de archivos comprimidos y empaquetados.
- **Moria**: Herramienta de extracción y análisis de firmware embebido.

Ambos binarios se compilan con un prefijo de instalación exclusivo para la aplicación `com.diamon.moria`, garantizando que las bibliotecas y ejecutables no interfieran con otros paquetes del sistema.

## Características

- **Aislamiento por paquete Android**: Cada binario se instala bajo `/data/data/com.diamon.moria/files/usr`
- **Alineación de 16 KB**: Compatible con los requisitos de memoria de Android 15+
- **RPATH exclusivo**: Los binarios buscan sus bibliotecas únicamente en la ruta del paquete
- **Hardening completo**: Stack protector, FORTIFY_SOURCE, RELRO y BIND_NOW
- **Optimización LTO**: Link-Time Optimization para binarios más pequeños y rápidos
- **Compilación nativa en Termux**: Sin necesidad de NDK externo

## Contenido del Repositorio

| Archivo | Descripción |
|---------|-------------|
| `build_libarchive_moria_custom.sh` | Compila e instala libarchive en `fake_root` con el prefijo del paquete Moria |
| `build_moria_custom.sh` | Compila Moria enlazando contra la libarchive local aislada |

## Requisitos Previos

- Dispositivo Android con [Termux](https://termux.dev/) instalado
- Conexión a internet para descargar el código fuente
- Paquetes de Termux: `git`, `clang`, `cmake`, `ninja`, `pkg-config`, `binutils` (se instalan automáticamente)

## Uso

### Orden de ejecución

Es obligatorio respetar el orden secuencial. Primero se compila la biblioteca y luego la herramienta:

```bash
chmod 700 build_libarchive_moria_custom.sh build_moria_custom.sh

# Paso 1: Compilar libarchive
./build_libarchive_moria_custom.sh

# Paso 2: Compilar Moria
./build_moria_custom.sh
```

### Resultado

Al finalizar, los binarios se encontrarán en:

```
$HOME/fake_root/data/data/com.diamon.moria/files/usr/
├── bin/
│   └── moria
├── lib/
│   ├── libarchive.so
│   └── pkgconfig/
└── include/
    └── archive.h
```

## Variable Clave

Si necesitas cambiar el identificador del paquete Android, modifica esta variable en **ambos** scripts:

```bash
export APP_PREFIX=/data/data/com.diamon.moria/files/usr
```

## Proyectos Originales

| Proyecto | Autor | Repositorio | Licencia |
|----------|-------|-------------|----------|
| **Moria** | [nmatt0](https://github.com/nmatt0) | [github.com/nmatt0/moria](https://github.com/nmatt0/moria) | Consultar repositorio original |
| **libarchive** | [libarchive contributors](https://github.com/libarchive) | [github.com/libarchive/libarchive](https://github.com/libarchive/libarchive) | BSD-2-Clause |

## Autor

**Daniel Elias Diamon Vazquez** — [danielpdiamon@gmail.com](mailto:danielpdiamon@gmail.com)

## Licencia

Este proyecto está licenciado bajo la **Licencia Apache 2.0**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

Los scripts de compilación contenidos en este repositorio son obras originales. Las herramientas y bibliotecas que compilan pertenecen a sus respectivos autores y se distribuyen bajo sus propias licencias.
