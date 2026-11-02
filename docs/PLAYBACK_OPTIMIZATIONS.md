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
