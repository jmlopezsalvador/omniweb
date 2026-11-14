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
- **Fase 5 (parte Android) hecha**: reset de datos y modo práctica, disparo manual oculto, bloqueo de apps externas, modo inmersivo, ajustes por sitio, gestión de sitios, imágenes en ficheros. Falta lo equivalente en iOS (Fase 4).
- **Fase 3 en curso**: YouTube y Amazon probados (sitios predefinidos `YOUTUBE`, `AMAZON`; el modo genérico basta, ver lecciones). Pendiente: Netflix e Instagram (uno a uno; exigen login).
- **Fase 2 hecha**: inicio con lista de sitios (Google Imágenes + añadir URL), «Preparar» (captura: navegar → «Capturar elemento» → tocar → guardar) y «Efecto» (abre la página capturada y fuerza imagen + todos los textos). Un forzaje por sitio.
- **Fase 1 hecha**: modo genérico probado en Google Imágenes real (emulador) + barra de URL mínima en `App.kt`. La imagen forzada sigue siendo un SVG de prueba (`SPIKE_IMAGE`); el selector real llega en la Fase 2.
- **Fase 0 hecha**: spike Android con página local (sustituye img/picture/background, título, re-render, auto/manual, reset).

## Lecciones de webs reales (Android)
- Sin `INTERNET` en el manifiesto el WebView da `net::ERR_CACHE_MISS`.
- El host inyecta el bundle 2 veces (onPageStarted + onPageFinished): el motor es idempotente (`window.OmniWeb` ya existe → no reinstala).
- Solo cuenta el scroll del documento; el de contenedores internos (p. ej. diálogo de cookies de Google) daba falsos disparos.
- Google Imágenes: la tarjeta con `<img>` no contiene el título; `applyTitleNear` sube hasta un contenedor con texto sin llegar a la rejilla (más de una imagen).
- El primer arranque del WebView en el emulador tarda ~7 s y Google pide consentimiento de cookies: el mago debe aceptarlo antes de la actuación.
- Ver el log del motor: `adb logcat -d | grep "OmniWeb\]"`.

## Modo captura y efecto (Fase 2)
- Concepto: NO se sube ninguna imagen; se fuerza un elemento que ya existe en la web (SPEC RF-02/03/03b). Un forzaje por sitio, el último capturado.
- El motor solo traga los toques cuando la captura está **armada** (botón «Capturar elemento»); si no, no se podría navegar ni cerrar cookies. `ForcerWebView(captureMode, armed, ...)`.
- Puente Android: `addJavascriptInterface(..., "OmniBridge")` → `onCapture(json)`; se ignora si `!captureMode || !armed`. iOS usará `WKScriptMessageHandler` con el mismo JSON.
- La captura guarda: imagen (data: URL; si el canvas está «tainted» por CORS la descarga Kotlin con `fetchImageDataUrl`), textos hoja de la tarjeta por posición (`findTextContainer`, misma función al capturar y al aplicar) y aspecto.
- Almacén: `ForceStore` sobre `KeyValueStore` (SharedPreferences/UserDefaults). La imagen va como data: URL en una preferencia: vale para miniaturas; si se capturan imágenes grandes (>1–2 MB) mover a ficheros.
- `navId` en `ForcerWebView`: el botón «Ir» recarga aunque la URL sea la misma.
- Pruebas en emulador con `uiautomator`: tocar por texto, p. ej. un script que lea `uiautomator dump` y haga `adb shell input tap`.
- Pendiente/limitaciones conocidas: la imagen se recorta con `object-fit: cover` al hueco de la tarjeta (no se reajusta la proporción); sin borrar/editar sitios; sin gesto oculto ni reset de trazas (Fase 5).

## Lecciones de YouTube (Fase 3)
- YouTube móvil abre el vídeo con `pointerup`/`touchend`, no con `click`: con la captura armada el motor silencia (`stopImmediatePropagation`) toda la familia touch/pointer/mouse. Test e2e con `?navonpointer=1`.
- Sin sesión, `m.youtube.com` no tiene feed de inicio («Try searching»): el mago prepara y actúa en una búsqueda (`/results?search_query=…`); el forzaje guarda esa `pageUrl`.
- YouTube mezcla plantillas de tarjeta (Shorts vertical con el título sobre la miniatura / lista con el texto a la derecha). Los textos se asignan «título primero» (hoja de mayor `font-size`) y el resto en orden (`orderLeaves`); capturar y aplicar usan el mismo orden. Sigue sin ser perfecto si las plantillas difieren mucho.
- `findTextContainer` sube hasta el contenedor MÁS ALTO que solo contenga ese medio (ignora medios <25% de su área: avatares) y no sea >4× su área.
- Límites vistos: el avatar del canal no se cambia (queda el de la tarjeta original) y chips como «♪ duración» pueden quedar sueltos. Posible mejora: capturar también imágenes pequeñas de la tarjeta.
- Teclear en el campo de URL con `adb input text` es poco fiable (se trunca/concatena): `tap`, esperar, Ctrl+A (`keycombination 113 29`), Supr (`keyevent 67`), escribir.

## Lecciones de Amazon y mejoras del motor (Fase 3)
- **Instantánea de la tarjeta** (`snapshotCard` / `applySnapshot`): al capturar se guarda el HTML interior del contenedor (sin `script`, `on*` ni `id`, con el medio principal marcado `data-omni-main`); el efecto lo reinserta en la tarjeta central con la imagen capturada. La web ya tiene el CSS, así que se ve nativa (precio, insignias, botones). Tiene PRIORIDAD sobre la asignación de textos. Si la tarjeta lleva componentes personalizados DEFINIDOS (`customElements.get`, p. ej. `ytm-*`) no se clona (se vaciarían al insertarse) y se usa la asignación de textos. Límite 200 KB.
- Textos: la unidad son **nodos de texto** (no elementos): Amazon pone `23<span>.</span>99`. Solo cuentan los visibles (caja ≥2px, sin `display:none`), así se ignoran los textos accesibles fuera de pantalla («4.6 out of 5 stars»). Título = texto de mayor `font-size` entre los largos (≥50% del más largo), porque en tiendas el precio tiene más letra que el nombre.
- `findTextContainer`: sube hasta 16 niveles (Amazon anida ~9) mientras no haya otra tarjeta (medios ≥25% de su área) y la ALTURA no supere `max(3×medio, 40% pantalla)` (en tarjetas de lista el área no sirve).
- Capturar el MISMO tipo de tarjeta donde parará el espectador (p. ej. una de la lista, no del carrusel patrocinado): con instantánea importa menos, con texto-por-posición mucho.
- Herramienta de depuración `web/tools/cdp.mjs "<js>"`: evalúa JS en el WebView del emulador por CDP (no usar Playwright: no soporta WebView). Elige el socket por el PID de `com.omniweb`.
- Los banners de consentimiento de cookies (Amazon, Google) los decide el usuario: no pulsar «Aceptar/Rechazar» por él en pruebas (el sistema lo bloquea); se puede capturar con el banner presente.
- Ayudante de pruebas de emulador: leer `uiautomator dump` y `adb shell input tap` por texto; con `PYTHONIOENCODING=utf-8` por los emojis.

## Fase 5 en Android: efecto, gestos y datos
- **Efecto** (`EffectScreen`): web a pantalla completa, inmersivo (`SystemBarsEffect(immersive)`: oculta barras y `keepScreenOn`). «Atrás» solo navega por la web, no sale. Gestos ocultos: toque en la esquina inferior DERECHA = fuerza ahora; botón de volumen = lo mismo (`VolumeKeyEffect`, `MainActivity.onKeyDown` → `VolumeKeyBus`); pulsación larga en la esquina superior IZQUIERDA = menú (reiniciar, mostrar web real sin forzar, salir). Las esquinas (`HiddenCorner`) deben TRAGARSE el contacto desde el `down`; si solo consumen el `up`, el toque llega también a la web.
- **Ajustes por sitio** (`SiteSettings`: auto/manual, `idleMs` 300/450/600, escritorio) en `ForceStore`; preparar y efecto usan los mismos (capturar con el mismo user agent que se usará). **Modo práctica**: al salir del efecto `clearBrowsingData()` (cookies, WebStorage, caché, historial). Botón «Borrar rastros» en inicio. Ojo: borrar cookies reabre los avisos de consentimiento.
- **Bloqueo de apps externas**: `shouldOverrideUrlLoading` ignora cualquier esquema que no sea http(s)/about/data/blob/file/javascript (`isExternalScheme`, con tests).
- **Almacenamiento**: `ForceStore(prefs, blobs)`. Imagen y HTML de la tarjeta van a `BlobStore` (Android `FileBlobStore`: `filesDir/blobs`); lee también el formato antiguo en preferencias. Se pueden borrar forzajes y quitar sitios añadidos (no los predefinidos).
- Expects nuevos en `Platform.kt`: `SystemBarsEffect(immersive)`, `VolumeKeyEffect`, `clearBrowsingData`. iOS los tiene como stubs: Fase 4 debe implementarlos (`WKWebsiteDataStore.removeData`, idleTimerDisabled, y un gesto alternativo al volumen: iOS no permite capturar los botones de volumen sin APIs privadas).
- Pruebas en emulador: pulsación larga con `adb shell input swipe x y x y 1500`; volumen con `keyevent 24/25`; user agent con `node tools/cdp.mjs "navigator.userAgent"`.
