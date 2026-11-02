# Optimizaciones de Reproducción en Jellyfin Direct Play

Este documento detalla el funcionamiento interno de las optimizaciones de reproducción incorporadas en el fork **Jellyfin Direct Play** (`org.jellyfin.androidtv.directplay`), específicamente el **Fallback de Dolby Vision Perfil 7** y el **Auto Frame Rate (AFR)** por defecto.

---

## 1. Dolby Vision Perfil 7 & 8 Fallback a HEVC / HDR10+

### Contexto y Problema Técnico
- **Dolby Vision Perfil 7 (`dvhe.07` / `dvh1.07`):** Formato estándar de discos físicos UHD Blu-Ray. Contiene una capa base (Base Layer - BL) HEVC Main 10 HDR10 / HDR10+ y una capa de mejora (Enhancement Layer - EL MEL/FEL).
- **Dolby Vision Perfil 8 (`dvhe.08` / `dvh1.08`):** Formato estándar de streaming y masterings DCI. Contiene una capa base HEVC Main 10 (8.1 es HDR10, 8.4 es HLG, 8.6 es HDR10+) junto a metadatos RPU dinámicos de Dolby Vision en el mismo flujo.

En procesadores SoC Amlogic (Xiaomi TV Box S, Homatics Box R 4K Plus, etc.):
- `c2.amlogic.hevc.decoder`: Decodificador general HEVC (admite tunneling y paso directo de paquetes SEI HDR10+).
- `c2.amlogic.dolby-vision.dvhe.decoder`: Decodificador Dolby Vision propietario.

#### El fallo en pantallas sin Dolby Vision (ej. televisores Samsung)
Cuando un reproductor entrega una pista DV Perfil 7 u 8 a una pantalla sin Dolby Vision:
1. El decodificador Amlogic DV conmuta al modo interno `AMDV_OUTPUT_MODE_HDR10`.
2. Durante esta conmutación, el controlador HDMI (`hdmitx_set_hdr10plus_pkt`) reporta `null vsif` y **descarta activamente los paquetes VSIF de metadatos HDR10+**, provocando colores apagados o pérdida de rango dinámico.

### Solución Implementada: `DirectPlayVideoRenderer` y `DolbyVisionDisplayHelper`
- **Fallback a HEVC Base Layer:** Intercepta streams DV Perfil 7 y Perfil 8 cuando el fallback está activo, reescribiendo el tipo MIME a `video/hevc` y asignando preferentemente `c2.amlogic.hevc.decoder`. El bitstream fluye intacto con sus paquetes SEI ITU-T T.35, entregando **HDR10+ o HDR10 nativo** al televisor.
- **Sincronización Total con la UI:** `PlaybackBadgesHelper` utiliza la misma lógica y detección de capacidades que el motor de vídeo. Si el fallback está activo, los badges OSD y el toast de inicio reportan el modo real que se está emitiendo (`HDR10+`, `HDR10`, `HLG`) en lugar de reclamar `Dolby Vision` falsamente.
- **Detección Dinámica (`Dv7FallbackMode.AUTO`):**
  - Si la pantalla conectada no soporta Dolby Vision por HDMI: fallback activo (reproduce y muestra `HDR10+` / `HDR10`).
  - Si la pantalla soporta Dolby Vision (ej. OLED LG, Sony): reproduce y muestra `Dolby Vision`.

### Modos Configurables
En **Ajustes → Reproducción → Avanzado → *Dolby Vision Perfil 7 Fallback***:
| Modo | Comportamiento | Uso recomendado |
| :--- | :--- | :--- |
| **Automático (`AUTO`)** *(Por defecto)* | Evalúa el EDID HDMI. Si la tele no soporta DV, mapea a HEVC (HDR10/HDR10+). Si soporta DV, reproduce en Dolby Vision nativo. | Mayoría de configuraciones domésticas. |
| **Siempre (`ALWAYS`)** | Fuerza la redirección a HEVC en todos los casos, independientemente de la tele. | Diagnóstico o preferencia personal por HDR10+. |
| **Desactivado (`DISABLED`)** | Mantiene el comportamiento upstream original de ExoPlayer sin reescritura. | Depuración de fallos de decodificador. |

---

## 2. Auto Frame Rate (AFR) por Defecto

### El Problema del Judder en Pantallas Modernas
La inmensa mayoría de contenidos cinematográficos (Blu-Ray UHD, cine digital y series) están grabados a **23.976 fps** o **24.000 fps**.
Si el dispositivo Android TV opera con una salida fija a 60.000 Hz:
- Se produce el fenómeno de **telecine 3:2 pull-down** (un fotograma se muestra durante 3 refrescos y el siguiente durante 2).
- Esto genera un micro-salto o tirón periódico perceptible especialmente en paneos de cámara lentos (*judder*).

### Configuración en Jellyfin Direct Play
En la versión upstream oficial de Jellyfin Android TV, el cambio de frecuencia de actualización viene desactivado por defecto (`DISABLED`), requiriendo que el usuario descubra y active la opción manualmente.

En **Jellyfin Direct Play**, la preferencia se establece de fábrica en:
```kotlin
var refreshRateSwitchingBehavior = enumPreference(
    "refresh_rate_switching_behavior",
    RefreshRateSwitchingBehavior.SCALE_ON_DEVICE
)
```

- **`SCALE_ON_DEVICE`:** Al iniciar la reproducción, ExoPlayer sincroniza el modo de pantalla del sistema operativo Android TV con la cadencia exacta del stream (por ejemplo conmutando la salida HDMI a `3840x2160 @ 23.976 Hz` o `24.000 Hz`), manteniendo el escalado de resolución en la GPU del reproductor para transiciones limpias y fluidas.
- **Persistencia de Elección:** Si el usuario modifica voluntariamente este ajuste en las opciones de la app (cambiando a *Escala en TV* o *Desactivado*), el valor personalizado se respeta y persiste entre actualizaciones.

---

## 3. Badges Técnicos Dinámicos en ExoPlayer OSD

### Arquitectura de `PlaybackBadgesHelper`
En versiones anteriores, el OSD de reproducción únicamente presentaba el badge genérico de Dolby Vision si estaba presente. En Jellyfin Direct Play se incorpora **`PlaybackBadgesHelper`** para poblar dinámicamente tanto la interfaz de Leanback (`CustomPlaybackOverlayFragment` / `vlc_player_interface.xml`) como Compose (`VideoPlayerHeader`):

* **Vídeo (HDR / Rango Dinámico & Resolución)**:
  - **Exclusividad de modo HDR**: Se garantiza que el OSD reporte exclusivamente el modo HDR activo (`Dolby Vision` > `HDR10+` > `HLG` > `HDR10`), evitando la duplicidad de badges simultáneos (como `DV` y `HDR10+` a la vez) en streams de perfil dual (perfil 7 u 8).
  - **Toast flotante al inicio**: Al arrancar la reproducción, se presenta durante 3.5 segundos en la esquina superior derecha una píldora con el modo activo (`DOLBY VISION`, `HDR10+`, etc.) que se desvanece suavemente (*fade out*), emulando las notificaciones nativas de las Smart TVs. Oculto para SDR.
  - **Audio de Objeto y Alta Definición (Dolby Atmos / DTS-HD / Lossless)**:
  - **Dolby Atmos prioritario**: Si el stream contiene metadatos Atmos en perfil o título, se genera un badge resaltado con diseño de acento (`#00A4DC`), especializando según el contenedor base: `TrueHD Atmos` (Lossless), `DD+ Atmos` (Streaming/E-AC-3) o `Dolby Atmos`.
  - **Codecs HD**: Identificación de `TrueHD`, `DTS:X`, `DTS-HD MA`, `DTS`, `DD+`, `Dolby Digital`, `FLAC`, `AAC`, `OPUS`.
  - **Canales**: Mapeo estricto del diseño acústico (`7.1`, `5.1(side)`, `5.1`, `2.0`).
* **Sincronización Dinámica**: Los badges se recalculan automáticamente en tiempo real tanto al abrir el OSD como al conmutar entre pistas de audio multicanal.

---

## 4. Flujo Interactivo de Permiso de Fuentes Desconocidas

Al solicitar actualizar la aplicación desde el menú *Acerca de* (`SettingsAboutScreen`):
- Se verifica `packageManager.canRequestPackageInstalls()`.
- Si el permiso no está otorgado, la aplicación lanza inmediatamente el intent interactivo del sistema Android TV (`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`) apuntando al paquete `package:org.jellyfin.androidtv.directplay`.
- Mediante un observador de ciclo de vida (`ON_RESUME`), cuando el usuario concede el permiso y regresa a Jellyfin, la comprobación, descarga e instalación se reanuda de manera transparente y automática.


---

## 5. Optimización de Búfer de Red en RAM (`DefaultLoadControl`)

### Contexto y Problema Técnico
En ExoPlayer (y Media3), la gestión por defecto del búfer (`DefaultLoadControl`) dimensiona el asignador de muestras en función del conjunto de pistas activas y aplica límites de memoria derivados de perfiles genéricos (~130 MB). Al reproducir contenidos de streaming o archivos locales con bitrates muy elevados (remuxes 4K UHD Blu-ray con picos superiores a 80-100 Mbps), el búfer por defecto se agota con facilidad o se detiene prematuramente al alcanzar el límite estricto de bytes antes de cumplir los umbrales de duración temporal, provocando paradas e interrupciones constantes ("hipo" o re-buffering).

### Solución Implementada
El fork **Jellyfin Direct Play** reestructura el control de carga tanto en `VideoManager.java` (backend legacy) como en `ExoPlayerBackend.kt` (Media3) y `PlaybackModule.kt`, introduciendo cinco modos seleccionables en **Ajustes → Reproducción → Avanzado → Búfer de red (RAM)**:

| Modo | Nombre en UI | Duración (Min..Max) | Límite de RAM (`targetBufferBytes`) | Priorizar tiempo sobre tamaño | Caso de uso recomendado |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AUTO** | Adaptativo | 25s .. 60s | Dinámico (hasta 256 MB) | `true` | Ajuste dinámico inteligente para la mayoría de streams. |
| **AGGRESSIVE** | Agresivo | 60s .. 120s | **512 MB** | `true` | Dispositivos con 3 GB de RAM o más (filtrado dinámicamente en UI). Máximo colchón contra hipo. |
| **HIGH** | Alto | 25s .. 60s | **256 MB** | `true` | Ideal para Wi-Fi o remuxes 4K de alto bitrate. |
| **STANDARD** | Estándar | Por defecto ExoPlayer | Por defecto (~130 MB) | `false` | Comportamiento estándar de upstream. |
| **LOW** | Bajo | 15s .. 30s | **64 MB** | `false` | Para dispositivos con poca memoria libre. |

#### El Fix de `DefaultLoadControl`
Para evitar que ExoPlayer detenga la precarga al alcanzar el límite de bytes antes de acumular suficientes segundos de reproducción, se configura explícitamente:
- `.setTargetBufferBytes(targetBufferBytes)` (cuando aplique).
- `.setPrioritizeTimeOverSizeThresholds(true)`: Asegura que el umbral temporal (`minBufferDuration`) prevalezca sobre el límite de tamaño, evitando bloqueos de carga en streams de alto bitrate.
