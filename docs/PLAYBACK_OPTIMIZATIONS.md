# Optimizaciones de Reproducción en Jellyfin Direct Play

Este documento detalla el funcionamiento interno de las optimizaciones de reproducción incorporadas en el fork **Jellyfin Direct Play** (`org.jellyfin.androidtv.directplay`), específicamente el **Fallback de Dolby Vision Perfil 7** y el **Auto Frame Rate (AFR)** por defecto.

---

## 1. Dolby Vision Perfil 7 Fallback a HEVC / HDR10+

### Contexto y Problema Técnico
Dolby Vision Perfil 7 (`dvhe.07` / `dvh1.07`) es el formato estándar empleado en discos físicos UHD Blu-Ray. Contiene:
- Una capa base (**Base Layer - BL**) codificada en HEVC Main 10 HDR10 / HDR10+ con mensajes SEI ITU-T T.35.
- Una capa de mejora (**Enhancement Layer - EL**) que puede ser MEL (Minimal Enhancement Layer, solo metadatos RPU de 10 bits) o FEL (Full Enhancement Layer, metadatos RPU + flujo de vídeo residual de 12 bits).

En procesadores basados en SoC Amlogic (ampliamente extendidos en cajas y sticks Android TV como Xiaomi TV Box S, Homatics Box R 4K Plus, Nokia Streaming Box, etc.), existen decodificadores hardware diferenciados:
- `c2.amlogic.hevc.decoder`: Decodificador general HEVC (admite Main 10, tunneling y paso directo de paquetes SEI HDR10+).
- `c2.amlogic.dolby-vision.dvhe.decoder`: Decodificador exclusivo para perfiles Dolby Vision. En la gran mayoría de chips Amlogic, este decodificador solo anuncia compatibilidad formal con perfiles 4, 5 y 8 (máscaras `0x10`, `0x20`, `0x100`), careciendo de perfil 7 (`0x80`).

#### El fallo en pantallas sin Dolby Vision (ej. televisores Samsung)
Cuando un reproductor entrega una pista DV Perfil 7 con tipo MIME `video/dolby-vision`:
1. El decodificador Amlogic DV arranca e inspecciona el EDID HDMI de la pantalla conectada.
2. Si el televisor es Samsung (o cualquier pantalla que carece de soporte Dolby Vision nativo pero soporta HDR10 y HDR10+), el decodificador Amlogic conmuta al modo interno `AMDV_OUTPUT_MODE_HDR10` para realizar un mapeo de tonos propietario.
3. Durante esta conmutación, el controlador HDMI (`hdmitx_set_hdr10plus_pkt`) reporta `null vsif` y **descarta activamente los paquetes VSIF de metadatos HDR10+**.
4. La reproducción sufre paradas, degradación de color o pérdida absoluta del rango dinámico dinámico en la pantalla.

### Solución Implementada: `DirectPlayVideoRenderer`
En lugar de forzar parches binarios sobre librerías externas, el fork implementa una extensión limpia sobre el pipeline de Media3 / ExoPlayer:
- **`DirectPlayVideoRenderer`:** Hereda de `MediaCodecVideoRenderer`. Intercepta la resolución del decodificador (`getDecoderInfos`) y los cambios de formato (`onInputFormatChanged`).
- **`DirectPlayRenderersFactory`:** Sustituye a `DefaultRenderersFactory`, inyectando `DirectPlayVideoRenderer` tanto en la arquitectura moderna de ExoPlayer de Jellyfin como en `VideoManager.java`.
- **Detección Dinámica (`Dv7FallbackMode.AUTO`):**
  ```kotlin
  val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
  val hdrCapabilities = display?.hdrCapabilities
  val supportsDv = hdrCapabilities?.supportedHdrTypes?.contains(
      Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION
  ) == true
  ```
  - **Si la tele NO tiene Dolby Vision (`supportsDv == false`):** El renderizador reescribe transparentemente el formato a `MimeTypes.VIDEO_H265` (`video/hevc`) y asigna `c2.amlogic.hevc.decoder`. El bitstream HEVC fluye intacto con sus NAL units SEI ITU-T T.35, logrando que el televisor Samsung active **HDR10+ nativo** sin pérdida.
  - **Si la tele SÍ tiene Dolby Vision (`supportsDv == true`):** Como en pantallas LG OLED, Sony Bravia o Philips OLED, el modo `AUTO` mantiene intacto el formato `video/dolby-vision`, entregando el stream al motor Dolby Vision nativo.

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
  - **Dolby Vision**: Identificado vía `VideoRangeType.DOVI`, `DOVI_WITH_HDR10`, `DOVI_WITH_HDR10_PLUS`, etc. Badge: `Dolby Vision`.
  - **HDR10+**: Detectado para streams `VideoRangeType.HDR10_PLUS` y combinaciones duales `DOVI_WITH_HDR10_PLUS`.
  - **HDR10**: Identificado para `VideoRangeType.HDR10` o perfiles HDR base.
  - **HLG**: Detección de emisiones con curva Hybrid Log-Gamma (`VideoRangeType.HLG`).
  - **Resolución**: Cálculo dinámico en píxeles (`4K`, `1080p`, `720p`).
* **Audio de Objeto y Alta Definición (Dolby Atmos / DTS-HD / Lossless)**:
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
El fork **Jellyfin Direct Play** reestructura el control de carga tanto en `VideoManager.java` (backend legacy) como en `ExoPlayerBackend.kt` (Media3) y `PlaybackModule.kt`, introduciendo cuatro modos seleccionables en **Ajustes → Reproducción → Avanzado → Búfer de red (RAM)**:

| Modo | Nombre en UI | Duración (Min..Max) | Límite de RAM (`targetBufferBytes`) | Priorizar tiempo sobre tamaño | Caso de uso recomendado |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AUTO** | Adaptativo (Recomendado) | 25s .. 60s | Dinámico / Automático | `true` | Ajuste dinámico inteligente para conexiones estándar y alta definición. |
| **HIGH** | Alto | 25s .. 60s | **256 MB** | `true` | Ideal para Wi-Fi de alta velocidad o remuxes 4K con picos extremos de bitrate. |
| **STANDARD** | Estándar | Por defecto ExoPlayer | Por defecto (~130 MB) | `false` | Comportamiento estándar de upstream. |
| **LOW** | Bajo | 15s .. 30s | **64 MB** | `false` | Para dispositivos Android TV con memoria RAM muy limitada. |

#### El Fix de `DefaultLoadControl`
Para evitar que ExoPlayer detenga la precarga al alcanzar el límite de bytes antes de acumular suficientes segundos de reproducción, se configura explícitamente:
- `.setTargetBufferBytes(targetBufferBytes)` (cuando aplique).
- `.setPrioritizeTimeOverSizeThresholds(true)`: Asegura que el umbral temporal (`minBufferDuration`) prevalezca sobre el límite de tamaño, evitando bloqueos de carga en streams de alto bitrate.
