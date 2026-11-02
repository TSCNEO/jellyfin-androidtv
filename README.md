<h1 align="center">Jellyfin Direct Play (Android TV)</h1>
<h3 align="center">Fork independiente optimizado para Direct Play nativo y Remuxes UHD / DCP</h3>

<p align="center">
  <a href="https://github.com/TSCNEO/jellyfin-androidtv/releases/latest">
    <img src="https://img.shields.io/github/v/release/TSCNEO/jellyfin-androidtv?style=flat-square&color=00a4dc&label=latest%20release" alt="Latest Release" />
  </a>
  <a href="https://github.com/TSCNEO/jellyfin-androidtv/actions/workflows/build-apk.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/TSCNEO/jellyfin-androidtv/build-apk.yml?style=flat-square&label=build%20%26%20verify" alt="CI Status" />
  </a>
  <img src="https://img.shields.io/badge/package-org.jellyfin.androidtv.directplay-informational?style=flat-square" alt="Package ID" />
  <img src="https://img.shields.io/badge/license-GPL--2.0-blue?style=flat-square" alt="License" />
</p>

---

## 🚀 Instalación Rápida

Puedes instalar **Jellyfin Direct Play** en cualquier Android TV, Google TV o Amazon Fire TV sin necesidad de ordenador:

### Vía Downloader App (AFTVnews)
Abre la aplicación **Downloader** en tu televisor o dispositivo e introduce directamente:
- **Código Downloader:** `6511579`
- **Short URL:** `aftv.news/6511579`

### Enlace Permanente de GitHub
Si utilizas navegador o gestor de descargas, este enlace redirige **siempre** al APK de la última release disponible:
```text
https://github.com/TSCNEO/jellyfin-androidtv/releases/latest/download/jellyfin-directplay.apk
```

### Vía ADB (Red local o USB)
```bash
adb connect <IP_DISPOSITIVO>:5555
adb install -r jellyfin-directplay.apk
```

---

## 🎯 ¿Por qué este fork? (Motivación)

### 1. El problema con la aplicación oficial de Upstream
La aplicación oficial de Jellyfin para Android TV está diseñada con un enfoque conservador de compatibilidad universal, orientado a evitar fallos en televisores antiguos o decodificadores de gama baja. Para ello, el cliente anuncia perfiles de reproducción restrictivos que el servidor interpreta al negociar el stream:
- **Límites de HEVC por defecto (Level 5.1):** Contenidos máster de alta tasa de transferencia, remuxes cinematográficos UHD o ripeos DCP codificados en **HEVC Level 6.1 o 6.2** disparan una transcodificación innecesaria en el servidor Jellyfin.
- **Vetos por bitrate o perfiles HDR:** Ficheros con picos superiores a 100 Mbps o con capas Dolby Vision (perfiles 7 u 8) provocan frecuentemente que el servidor intente recomprimir el vídeo a H.264 / SDR, consumiendo ingentes recursos de CPU/GPU y destruyendo la fidelidad visual y los metadatos dinámicos.
- **Conversión forzada de audios sin pérdida:** Pistas TrueHD Atmos o DTS-HD MA se ven forzadas a conversión cuando el receptor AV o barra de sonido conectada al dispositivo admite passthrough nativo completo sin pérdida.

### 2. La realidad del hardware moderno
Reproductores multimedia actuales (como **Homatics Box R 4K Plus**, **Nvidia Shield Pro**, **Google TV Streamer** o dispositivos con chips Amlogic S905X4 / Realtek) cuentan con aceleración por hardware completa capaz de decodificar vídeo 4K HEVC Level 6.2 a alto bitrate y realizar bitstream passthrough de TrueHD Atmos y DTS-HD sin transcodificar absolutamente nada.

### 3. Qué aporta Jellyfin Direct Play
- **Force Direct Play por defecto:** Omite las restricciones artificiales del cliente para reproducción bajo demanda interna. El stream original intacto se entrega directamente al decodificador hardware (`MediaCodec` / ExoPlayer).
- **Coexistencia en paralelo (`org.jellyfin.androidtv.directplay`):** Identificador de paquete desacoplado. Permite tener instalada la app oficial de Jellyfin y Jellyfin Direct Play simultáneamente en la misma tele sin conflictos ni colisiones.
- **In-App Auto-Updater integrado:** Inspirado en la arquitectura de Bambi, la aplicación consulta la API de GitHub Releases. Cuando hay una nueva versión, te avisa en **Ajustes -> Quienes somos** y se actualiza a sí misma con un solo clic vía `PackageInstaller`.
- **Firma criptográfica persistente:** Compilación y firma automatizada en GitHub Actions con un keystore de producción seguro. Esto garantiza que las actualizaciones internas se instalen sobre la app existente sin errores de firma (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Identificación propia en el servidor:** El cliente se reporta como `Jellyfin Direct Play`, permitiendo al administrador auditar y diferenciar de inmediato las sesiones directas de las oficiales en el panel de control.
- **Sincronización automatizada con Upstream:** Workflow diario en GitHub Actions (`sync-upstream.yml`) que sigue las ramas estables oficiales (`release-0.20.z` o superior), garantizando estabilidad sin absorber cambios inestables de `master`.

---

## 🛠️ Estructura de Versiones

El versionado de este fork sigue una convención clara y determinista:
```text
<upstreamVersion>-directplay.<iteracion>
```
- Ejemplo: `0.20.0-beta.4-directplay.2`
- El código numérico de versión de Android (`versionCode`) se incrementa de forma monótona:
  `versionCode = baseCode * 100 + iteracion` (ej: `20000402`), garantizando actualizaciones automáticas válidas para el gestor de paquetes de Android.

---

## 📦 Compilación y Desarrollo

Para compilar el proyecto localmente requieres JDK 21 y Android SDK:

```bash
# Ejecutar tests unitarios
./gradlew test

# Compilar versión debug
./gradlew assembleDebug

# Compilar versión release firmada (requiere variables de entorno de keystore)
export KEYSTORE_FILE="/ruta/a/release.keystore"
export KEYSTORE_PASSWORD="password"
export SIGNING_KEY_ALIAS="alias"
export SIGNING_KEY_PASSWORD="password"
./gradlew assembleRelease
```

Consulta la [Guía de Mantenimiento y Firma](docs/MAINTENANCE.md) para más detalles sobre CI/CD y secretos de GitHub Actions.

---

## 📄 Licencia y Reconocimientos

Este proyecto es un fork de [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv), distribuido bajo la licencia **GNU General Public License v2.0**.  
Mantenido por [TSCNEO](https://github.com/TSCNEO).  
Agradecimientos especiales al equipo de desarrollo de [Jellyfin](https://jellyfin.org) por la base del cliente Android TV.
