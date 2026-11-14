# OmniWeb — navegador mágico Android + iOS

App móvil para **Android y iOS** con un único código en Kotlin Multiplatform + Compose Multiplatform.
El usuario habla español: responde y comenta el código en español.

Repo: https://github.com/jmlopezsalvador/omniweb.git · paquete `com.omniweb` · fases en `docs/SPEC.md` (Android primero, iOS en Fase 4).

## Mapa de carpetas
| Carpeta | Para qué sirve |
|---|---|
| `shared/` | Módulo KMP con TODO el código: lógica pura, interfaz Compose, ajustes. `commonMain` (común), `androidMain` / `iosMain` (solo lo del sistema, `expect`/`actual`), `commonTest` (tests). |
| `app/` | Solo la envoltura Android (`MainActivity`, manifiesto, recursos, icono). |
| `iosApp/` | Envoltura iOS en SwiftUI + `project.yml` (XcodeGen genera el `.xcodeproj` en CI; no se versiona). |
| `.github/workflows/ios.yml` | Compila en un Mac de GitHub y publica un `.ipa` sin firmar. Solo `workflow_dispatch`. |
| `docs/` | `ARQUITECTURA.md` (base técnica, fuente de verdad de versiones y reglas) y `SPEC.md` (especificación funcional, se crea al inicializar). |
| `data/` | Material de referencia que aporta el usuario (capturas, icono, recursos). Se puede leer. |
| `data/android_build/` | APK descargados/generados. No se versionan. |
| `data/ios_build/` | `.ipa` descargado de Actions. No se versiona. |
| `web/` | Motor JS (TypeScript → esbuild → `web/dist/omniweb.js`, IIFE es2019) + página de pruebas `web/test/page.html`. `dist/` SÍ se versiona (Gradle lo empaqueta como assets de Android sin necesitar Node). |
| `ignore/` | **Ignorar por completo** (ver reglas). |

## Reglas para Claude
- **Ignorar por completo la carpeta `ignore/`**: no leerla, no listarla, no buscar dentro, no usarla como referencia. Excluirla
  también de búsquedas (Glob/Grep) y del control de versiones. Es el cajón del usuario para material suelto.
- La base técnica es **`docs/ARQUITECTURA.md`** (versiones, estructura, Gradle, CI de iOS, reglas de código). Si algo choca con él, consultar al usuario.
- **Antes de lanzar el workflow de iOS en GitHub Actions, preguntar siempre al usuario** (cuesta cuota de macOS; se acumulan mejoras).
  Por defecto se lanza **sin tests** (el usuario prueba en su iPhone); solo con tests si él lo pide.
- No inventar funcionalidad: la fuente de verdad es `docs/SPEC.md` y lo que pida el usuario. Ante la duda, preguntar.
- **No hacer commit ni push sin que el usuario lo pida.** No commitear si la compilación falla.
- Datos solo en local (SharedPreferences / UserDefaults y ficheros internos). Sin red ni cuentas salvo que el SPEC lo diga.
- Mantener este `CLAUDE.md` al día: al cambiar de forma relevante la app, el flujo o las decisiones, actualizarlo en la misma tarea.

## Qué hace la app
Navegador con WebView real: el mago configura web objetivo + una imagen (y título opcional); en la actuación, al parar el scroll del espectador (auto o gesto oculto) un bundle JS compartido (TypeScript→esbuild, en `web/`) sustituye el elemento más centrado por la imagen forzada. Modo genérico + perfiles por sitio. Todo local, sin cuentas. Detalle y decisiones técnicas en `docs/SPEC.md`.

## Reglas de código para que compile en iOS
- En `commonMain`: nada de `java.*` ni `android.*`. Usar `kotlinx-datetime` (0.6.1), `kotlin.math.*` y, si hace falta aritmética
  grande, `bignum` (`com.ionspin.kotlin:bignum:0.3.10`). Sin `String.format`, `Locale`, `SimpleDateFormat`, `Thread`, `synchronized`.
- Sin recursos de Android (`R.string`) en común; textos en el código.
- Iconos dibujados con `Canvas`/`Path` de Compose (no `material-icons-extended`).
- Lógica de dominio pura y sin UI, con tests en `commonTest` (y reloj inyectable si depende del tiempo).
- Lo del sistema (sensores, vibración, galería, portapapeles…): `expect` en `commonMain/.../ui/Platform.kt` + `actual` en `androidMain`/`iosMain`.
- `Platform.ios.kt` lleva `@file:OptIn(ExperimentalForeignApi::class)` (cinterop de Kotlin/Native).
- El fallo de iOS solo se ve en Actions (~20 min): revisar con cuidado antes de lanzarlo.
- Con scripts: en esta máquina los heredocs de Bash con contenido complejo fallan; escribe los `.py` con la herramienta Write y ejecútalos.

## Compilar y probar (Windows)
- Kotlin 2.0.21, Compose Multiplatform 1.7.1, AGP 8.7.3, Gradle 8.9, JDK 17, minSdk 26, target/compileSdk 35, iOS mínimo 15.0.
- `JAVA_HOME` apunta al JDK 17: `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot`.
- SDK de Android en `C:\Users\jmlop\AppData\Local\Android\Sdk`: crear `local.properties` con `sdk.dir=...` (ignorado por git).
- `./gradlew.bat :shared:testDebugUnitTest assembleDebug` → APK en `app/build/outputs/apk/debug/app-debug.apk`.
  Copiar el APK a `data/android_build/` si se quiere conservar.
- Emulador AVD `calcu` (Android 15), reutilizable. Poner la zona horaria `Europe/Madrid` (arranca en GMT). Gboard puede salir en negro (es del emulador).
- `gh` en Windows: `C:\Program Files\GitHub CLI\gh.exe` (hace falta `gh auth login` una vez).
- En Windows los destinos de iOS se ignoran (`kotlin.native.ignoreDisabledTargets=true`); Android compila y testea igual.

## Flujo de trabajo
1. Desarrollar y probar en Android/JVM en local (emulador).
2. Commit y push cuando el usuario lo pida.
3. **Preguntar antes de lanzar Actions** y acumular mejoras: `gh workflow run ios.yml --ref main` (opción `-f tests=true` solo si se pide).
4. Descargar el artefacto a `data/ios_build/`: `gh run download <id> -n <Nombre>-ios-sin-firmar -D data/ios_build`.
5. Instalar el `.ipa` con Sideloadly/AltStore (Apple ID gratis; caduca a los 7 días; activar Modo desarrollador en el iPhone).

## Lecciones de iOS ya aprendidas
- `gradlew` debe estar en git con permiso de ejecución (`git update-index --chmod=+x gradlew`).
- En iOS, `UIDevice.proximityState` se lee por sondeo; apagar y reactivar la monitorización deja el estado pegado. Mantenerla activa.
- Barra de estado e indicador de inicio se ocultan desde SwiftUI (`ContentView.swift`); el bloqueo automático con `idleTimerDisabled`.

## Instrucciones para el usuario (si la app las necesita)
Si la app lleva una página de «Instrucciones» de cara al usuario final, mantenerla al día en la misma tarea en que se cambie la función.

## Motor JS (`web/`)
- Comandos (en `web/`): `npm run build` (regenera `dist/`), `npm test` (Vitest+jsdom), `npm run e2e` (Playwright, Chromium móvil contra `dist/test/page.html`), `npm run typecheck`.
- **Tras cambiar `web/src`, ejecutar `npm run build`** y commitear `dist/`; si no, la app usa el bundle viejo.
- API en la página: `OmniWeb.configure({imageUrl,title,idleMs,mode})`, `trigger()`, `reset()`, `disarm()`.
- Kotlin: `expect fun ForcerWebView(url, config)` (commonMain); Android inyecta el bundle en `onPageStarted/onPageFinished`; iOS pendiente (Fase 4).
- Lección: las URL `data:` con espacios/comas rompen `srcset`; el motor las normaliza (`normalizeImageUrl`).
- Probar en emulador: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, abrir y hacer swipe; captura con `adb exec-out screencap -p`.

## Estado de fases
- **Fase 1 hecha (pendiente de commit)**: modo genérico probado en Google Imágenes real (emulador) + barra de URL mínima en `App.kt`. La imagen forzada sigue siendo un SVG de prueba (`SPIKE_IMAGE`); el selector real llega en la Fase 2.
- **Fase 0 hecha**: spike Android con página local (sustituye img/picture/background, título, re-render, auto/manual, reset).

## Lecciones de webs reales (Android)
- Sin `INTERNET` en el manifiesto el WebView da `net::ERR_CACHE_MISS`.
- El host inyecta el bundle 2 veces (onPageStarted + onPageFinished): el motor es idempotente (`window.OmniWeb` ya existe → no reinstala).
- Solo cuenta el scroll del documento; el de contenedores internos (p. ej. diálogo de cookies de Google) daba falsos disparos.
- Google Imágenes: la tarjeta con `<img>` no contiene el título; `applyTitleNear` sube hasta un contenedor con texto sin llegar a la rejilla (más de una imagen).
- El primer arranque del WebView en el emulador tarda ~7 s y Google pide consentimiento de cookies: el mago debe aceptarlo antes de la actuación.
- Ver el log del motor: `adb logcat -d | grep "OmniWeb\]"`.
