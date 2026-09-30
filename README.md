# BrayanEmu v0.7

Esta versión añade gestión real de archivos desde Android:

- Selector de carpeta de ROMs.
- Escaneo recursivo de ROMs.
- Detección básica por extensión.
- Selector de carpeta de BIOS.
- Cálculo MD5 en el propio teléfono.
- Verificación de nombre + hash para referencias PS1.
- Pantalla de estado para BIOS reconocidas/no reconocidas.
- Arquitectura preparada para añadir cores nativos.

## Importante

El proyecto **no incluye BIOS propietarias ni juegos**.

La documentación de Libretro explica que las BIOS son archivos de firmware que el usuario debe proporcionar y que nombre, ubicación y hash deben coincidir con lo requerido por el core.

Esta v0.7 todavía **no incluye un core nativo de emulación compilado**. La siguiente fase debe añadir bibliotecas libretro/otros cores para los sistemas elegidos. No se presenta el catálogo como si fuera un motor de emulación.

## Compilación

El workflow de GitHub Actions de esta carpeta compila el APK debug y lo publica como artifact.
