# Guía de Mantenimiento y Firma: Jellyfin Direct Play

Este documento describe la arquitectura técnica, gestión de firmas criptográficas en GitHub Actions, sistema de actualización interna (in-app updater) y sincronización con upstream de **Jellyfin Direct Play**.

---

## 1. Identificador y Convivencia Paralela

- **Application ID de Distribución:** `org.jellyfin.androidtv.directplay`
- **Application ID de Depuración:** `org.jellyfin.androidtv.directplay.debug`
- **Nombre de Aplicación:** `Jellyfin Direct Play`
- **Namespace Interno:** `org.jellyfin.androidtv` (conservado para compatibilidad con el código base upstream).
- **Autoridades de ContentProvider:**
  - `org.jellyfin.androidtv.directplay.content`
  - `org.jellyfin.androidtv.directplay.integration.provider.ImageProvider`
  - `org.jellyfin.androidtv.directplay.acra`
  - `org.jellyfin.androidtv.directplay.androidx-startup`

> **Nota importante:** Todas las autoridades de búsqueda y proveedores de contenido están vinculadas dinámicamente a `applicationId`. Esto garantiza que **Jellyfin Direct Play** puede instalarse simultáneamente junto a la versión oficial de Jellyfin Android TV en cualquier televisor o dispositivo sin conflictos de manifiesto (`INSTALL_FAILED_CONFLICTING_PROVIDER`).

---

## 2. Generación de la Clave Privada (Keystore) y GitHub Secrets

Para que las actualizaciones internas funcionen sin requerir desinstalación ni provocar fallos de firma (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), **la misma clave de firma debe utilizarse permanentemente**.

### Paso 1: Generar el archivo keystore

Ejecuta el siguiente comando en una máquina local segura (no en el runner ni en el repositorio):

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias jellyfin_directplay \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "<TU_CONTRASENA_SEGURA>" \
  -keypass "<TU_CONTRASENA_SEGURA>" \
  -dname "CN=Jellyfin DirectPlay, OU=Homelab, O=TSCNEO, L=Madrid, ST=Madrid, C=ES"
```

Guarda `release.keystore` y las contraseñas en un gestor seguro (Keepass, 1Password, etc.). **Nunca** agregues el archivo `.keystore` al control de versiones Git.

### Paso 2: Convertir el Keystore a Base64

```bash
base64 -w 0 release.keystore > release.keystore.base64
```
(En macOS usa `base64 -b 0 release.keystore > release.keystore.base64` o `cat release.keystore | base64`).

### Paso 3: Configurar los Secretos en GitHub

En tu repositorio de GitHub (`TSCNEO/jellyfin-androidtv`), accede a **Settings** -> **Secrets and variables** -> **Actions** y crea los siguientes 4 secretos:

| Nombre del Secreto | Descripción | Valor de Ejemplo |
| :--- | :--- | :--- |
| `ANDROID_KEYSTORE_BASE64` | Contenido en Base64 de `release.keystore` | `MIIKbgIBAzCCCW8GCSqGSIb3DQE...` |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña de acceso al almacén de claves | `<TU_CONTRASENA_SEGURA>` |
| `ANDROID_KEY_ALIAS` | Alias de la clave dentro del almacén | `jellyfin_directplay` |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave privada | `<TU_CONTRASENA_SEGURA>` |

### Comportamiento cuando faltan los secretos
Si los secretos no están configurados, el flujo de compilación `.github/workflows/build-apk.yml` **no fallará**. Compilará automáticamente una versión de desarrollo (`Jellyfin-DirectPlay-<version>-debug.apk`) emitiendo un aviso descriptivo en el log de GitHub Actions indicando qué secretos faltan.

---

## 3. Esquema de Versionado y VersionCode Creciente

Android exige que cada actualización tenga un `versionCode` numéricamente superior al anterior.

El cálculo en `app/build.gradle.kts` sigue la regla:
```kotlin
val baseVersionName = project.getVersionName() // Ej: "0.20.0-beta.4"
val forkRevision = project.getProperty("fork.revision")?.toInt() ?: 1
versionName = "$baseVersionName-fork.$forkRevision"

val baseCode = getVersionCode(baseVersionName) // Ej: 200004
versionCode = baseCode * 100 + forkRevision   // Ej: 20000401
```

- Si upstream sube de `beta.4` a `0.20.0`, el `baseCode` aumenta automáticamente.
- Si sacas mejoras del fork sobre la misma versión base de Jellyfin, basta con definir `-Pfork.revision=2` para incrementar el `versionCode` (de `20000401` a `20000402`).

---

## 4. In-App Updater (Patrón Bambi)

El sistema de actualización interna (`org.jellyfin.androidtv.update`) incluye:

1. **`UpdateRepository` / `UpdateRepositoryImpl`:**
   - Consulta el endpoint oficial de GitHub Releases: `https://api.github.com/repos/TSCNEO/jellyfin-androidtv/releases/latest`.
   - Comprueba diferencias de versión (`isNewer`) considerando números mayores, menores, parches, betas y revisiones del fork.
   - Descarga con buffer de 64 KiB emitiendo el progreso en porcentaje.
   - Abre una sesión con `PackageInstaller` (`MODE_FULL_INSTALL`) y solicita la instalación del sistema.
2. **`UpdateInstallReceiver`:**
   - Captura el resultado de la sesión de instalación (`STATUS_PENDING_USER_ACTION`, `STATUS_SUCCESS`, `STATUS_FAILURE`).
   - Lanza el diálogo de confirmación del sistema si es requerido.
3. **Interfaz en Ajustes -> Acerca de:**
   - Botón directo "Buscar actualizaciones". Muestra dinámicamente si la app está al día, si hay una versión disponible con su peso, el porcentaje de descarga o avisos si falta conceder permiso de orígenes desconocidos (`REQUEST_INSTALL_PACKAGES`).

---

## 5. Sincronización Automática con Upstream (`sync-upstream.yml`)

El workflow `.github/workflows/sync-upstream.yml`:

- Se ejecuta automáticamente cada día a las **04:00 UTC** y también vía **workflow_dispatch**.
- Inspecciona las ramas activas en `jellyfin/jellyfin-androidtv`.
- Ignora cualquier rama o versión inferior a `0.20` (nunca sincroniza `0.19.z` ni toca `master`).
- Localiza la última rama estable `release-0.20.z` (o futuras `release-0.21.z`).
- Si detecta nuevos commits:
  1. Fusiona upstream en `main`.
  2. Ejecuta la suite de pruebas unitarias (`./gradlew test`).
  3. Si las pruebas pasan, sube a `main` e inicia la compilación de la nueva release.
  4. Si se produce un conflicto o fallo de tests, abre un Pull Request / Issue automáticamente detallando el conflicto para resolución manual, sin romper la rama principal.
