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
- Emulador: usar el AVD **`omniweb`** (Android 15, 3 GB RAM, GPU host; arranca en ~40 s): `emulator -avd omniweb -no-snapshot-load -no-audio`. El antiguo `calcu` (1,5 GB RAM, 6 GB de estado) se queda «offline» minutos o para siempre: no usarlo.
- (obsoleto) Emulador AVD `calcu` (Android 15), reutilizable. Poner la zona horaria `Europe/Madrid` (arranca en GMT). Gboard puede salir en negro (es del emulador).
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
- **Fase 6e hecha en Android (iOS escrito, sin build)**: inicio con barra inferior (Favoritos / Ajustes), cuadrícula de iconos con favicon de cada sitio. Con esto el plan 6a–6e del SPEC §10 está completo; falta VERIFICAR iOS (un solo build con 6a–6e) y probar las webs reales con el toque y la ficha.
- **Ficha nativa (sustituye a 6c)**: hecha y probada en el emulador con la página local; falta validar con Google/YouTube/Amazon reales. iOS comparte el motor y la lógica común pero NO se ha compilado.
- **Fase 6b+6d hechas en Android (pendiente de iOS)**: el efecto fuerza al TOCAR un elemento de la lista con el truco armado y la lista en reposo (`tap.ts`), activación secreta con vibración. Pendiente 6c (ficha en segunda capa) y 6e.
- **Fase 6a hecha (aspecto de navegador)**: efecto sin pantalla completa (barra de estado visible), marco decorativo (`BrowserChrome.kt`: Safari con barra inferior que se esconde al bajar en iOS, Chrome en Android), zona oculta del menú arriba-centro (pulsación larga en la barra de URL; sin marco, zona invisible 180×64). Pendientes 6b-6e en `docs/SPEC.md` §10.
- **Fase 4 (iOS) escrita, pendiente de verificar en el iPhone**: `WKWebView` en Swift + puente Kotlin (ver «iOS»). No se puede compilar en Windows: el primer build de Actions dirá si compila.
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

## iOS (Fase 4)
- **Arquitectura**: el `WKWebView` y lo del sistema viven en **Swift** (`iosApp/iosApp/OmniWebHost.swift`), que implementa el protocolo `WebViewHost` declarado en Kotlin (`shared/src/iosMain/.../IosWebHost.kt`); Compose lo embebe con `UIKitView` (`ForcerWebView.ios.kt`). Se hizo así porque no hay Mac para compilar y los enums/protocolos de WebKit son más seguros en Swift que vía cinterop. Cambiar una firma del protocolo obliga a cambiar el Swift (las etiquetas salen de los nombres de los parámetros).
- Entrada: `MainViewControllerKt.MainViewController(host: OmniWebHost())` (ContentView.swift). `iosWebHost` global guarda el host.
- El motor `web/dist/omniweb.js` se empaqueta como recurso (project.yml `buildPhase: resources`) y se inyecta con `WKUserScript` al empezar cada documento; el script de arranque (`OmniWeb.configure(...)` / `startCapture()`) al terminar el DOM. El puente `window.OmniBridge.onCapture(json)` se simula con un shim sobre `webkit.messageHandlers`. `jsQuote`/`ForceConfig.toJs()` son comunes (con tests).
- **ACTUALIZACIÓN (primera prueba en iPhone)**: embebido con `UIKitView` el scroll NO llegaba al `WKWebView` en ninguna web. Ahora el navegador es una **capa nativa SwiftUI** (`WebOverlay` en `ContentView.swift`, ZStack sobre la vista de Compose): `ForcerWebView.ios.kt` solo reserva un `Box` y avisa de su rectángulo (`layoutWebView`, px→puntos con `LocalDensity`); `WebOverlayContainer` deja pasar los toques que no son del navegador y lleva las esquinas ocultas POR ENCIMA del navegador (dentro del WKWebView, WebKit podía taparlas en modo escritorio). Un diálogo de Compose queda por debajo de la capa: por eso `covered = true` oculta el navegador mientras haya diálogo (Preparar al guardar, menú oculto del efecto).
- (histórico) **Compose queda POR DEBAJO del `UIKitView`**: no se puede superponer Compose al navegador. Por eso las esquinas ocultas del efecto son vistas nativas dentro del `OmniWebView` (`onHiddenTap` / `onHiddenLongPress`) y el botón «Inicio» de Preparar es visible (no hay tecla atrás; «atrás» en la web = gesto de borde). Los diálogos Compose sí salen por encima.
- Volumen: `AVAudioSession.outputVolume` (KVO); no hay evento con el volumen al máximo/mínimo. Datos: `WKWebsiteDataStore` + `URLCache` + cookies. Blobs: Application Support/blobs.
- No se bloquean enlaces universales (WKWebView no los abre en otra app al navegar dentro); sí los esquemas no web (`decidePolicyFor`).
- Instalar: Sideloadly/AltStore con el `.ipa` de `gh run download`. Lanzar el workflow solo con permiso del usuario.

## Rendimiento y comprobaciones (emulador)
- Arranque en frío de la build debug en el emulador: ~1,5 s y ~55 fotogramas saltados en el primer fotograma, con o sin datos (coste base de Compose/JIT; en release baja). Medir con `adb shell am start -W` y `logcat | grep "Skipped"`.
- La pantalla de inicio NO debe leer imágenes: usa `ForceStore.summary` (solo preferencias). `load` (imagen + HTML, MB) solo al entrar al efecto.
- WebView: el motor se inyecta en `onPageStarted` (idempotente) y la configuración, que puede llevar MB, solo en `onPageFinished`.
- Si Google «se ve pequeño»: mirar «· escritorio» en la tarjeta del sitio (Ajustes → Web de escritorio). En escritorio el viewport es 980 px al ~42 %; en móvil 412 px. Dejar los ajustes de prueba como estaban al terminar.
- Sembrar forzajes pesados para pruebas de rendimiento: ver el script `seed.py` de la sesión (blobs en `files/blobs` + `shared_prefs/omniweb.xml` vía `run-as`).

## Diseño (tema)
- `Theme.kt`: `OmniTheme` oscuro propio (paleta violeta/turquesa, tipografías y formas). El tema por defecto era claro sobre fondo negro: texto negro sobre negro en campos y títulos. El contenedor raíz es un `Surface` (fija también el color del texto) y `MainActivity` usa `SystemBarStyle.dark` (iconos claros en la barra de estado).
- Inicio: tarjetas por sitio (avatar con inicial, estado «Preparado · «título»», pastillas Automático/Móvil/450 ms, botones Preparar/Efecto y menú ⋮ dibujado con Canvas). Colores explícitos para campos de texto: no usar `Color.Black` de fondo ni `Text` sin tema.
- El efecto abre `Site.startUrl` (inicio del sitio, sin búsqueda); Amazon pasó de `/s?k=auriculares` a `https://www.amazon.es`. `ForcedItem.pageUrl` ya no se usa para abrir.
- Android: la primera vez que se entra en inmersivo el sistema muestra «Viewing full screen / Got it» y se traga el primer gesto.

## Marco de navegador (Fase 6a)
- Decorativo: `BrowserTopBar` (candado + dominio vía `displayHost`; en Android, pestañas y ⋮) y `BrowserBottomBar` (solo iOS). La URL sale del `WebViewClient`/KVO (`onPageInfo`); la dirección del scroll, del motor JS (`config.reportScroll` → `OmniBridge.onScroll`).
- `SiteSettings.chrome` (por sitio, por defecto activado) y conmutable en directo desde el menú oculto. Quitado el modo inmersivo: `SystemBarsEffect(immersive)` solo mantiene la pantalla encendida. iOS: `UIStatusBarHidden: false`, sin `.statusBarHidden(true)`.
- iOS: zonas ocultas nativas en `WebOverlayContainer` (menú arriba-centro 180×64, disparo abajo-derecha 72×72). Android: `HiddenCorner` en Compose.
- Orden de parámetros de `ForceConfig`: `reportScroll` va al FINAL (los tests usan argumentos posicionales).

## Efecto por toque, armado y vibración (6b + 6d)
- Motor: `mode:'tap'` + `armed`. `tap.ts` = máquina de estados: armado → (sin scroll ≥ `idleMs`) reposo → toque corto (<300 ms, <10 px). El toque que frena la inercia NO cuenta. Solo se fuerza si `isListItem(candidato)` (≥2 hermanas de la misma familia con medios y medio ≥60 px): el logo de Google antes de buscar se ignora. Se silencia pointer/touch/mouse/click del toque candidato; si resulta ser un arrastre o toque largo se devuelven los eventos. API: `OmniWeb.setArmed(bool)`, `OmniWeb.armed`. Los modos `auto`/`manual` heredados siguen (tests).
- OJO al registrar listeners: los manejadores propios de `pointerdown/up` van ANTES del silenciador genérico (`stopImmediatePropagation` les impediría ejecutarse). Ventana de silenciado tras forzar: 400 ms o hasta el `click`.
- App: `SiteSettings.auto` = «Armado desde el inicio» (true) o «con gesto secreto» (false). `EffectScreen.armedNow` conmuta con volumen / esquina inferior derecha y llama a `vibrateFeedback(armed)` (Android `Vibrator`, permiso VIBRATE; iOS `UINotificationFeedbackGenerator` en Swift). Ajuste general «Vibración al armar» (`ForceStore.haptics`).
- `ForcerWebView.effectArmed` sustituye a `triggerNonce`. Las actuals usan la config vigente (`rememberUpdatedState`) en cada carga.
- Prueba determinista en emulador: añadir un sitio `file:///android_asset/test/page.html` (página de pruebas con un «logo» fijo que NO es de la lista).

## Ficha NATIVA + forzar dentro (sustituye a la segunda capa de 6c)
- Decisión del usuario tras probar Google Imágenes: la ficha NO se abre desde una URL guardada (en Google es un panel sin URL y salía la web de origen de la imagen). La web abre SU ficha al tocar y el motor sustituye dentro.
- Preparar en dos pasos (`CaptureScreen`): 1) «Capturar elemento» de la lista; el diálogo ofrece «Capturar su ficha» (desarma la captura para poder abrirla), «Guardar solo la lista» o «Elegir otro»; 2) entrar en la ficha como el espectador y «Capturar ficha» sobre su imagen principal («Sin ficha» / «Otro» en la barra). `ForcedItem.detail: DetailItem(imageData, texts, html)`; la app guarda `html = null` en la ficha: clonar el HTML de un panel/página deja sin vida sus botones (el «Cerrar» dejó de funcionar en la prueba), así que se sustituyen imagen y textos sobre los elementos vivos. Claves del almacén `f.<id>.d.*` y blobs `f.<id>.d.image/html`.
- Motor (`index.ts` + `ficha.ts`): con `cfg.detail` el toque se deja PASAR (`tap.passThrough`; `onTap` se llama antes de que la web lo procese); `startFicha` sustituye la tarjeta de la lista 350 ms después y `watchFicha` sondea (150 ms, hasta 10 s) el mayor medio visible NUEVO (que no estaba en la lista; tras 1,2 s con la URL cambiada admite también los antiguos) estable dos sondeos, y lo sustituye. Ranuras: `forced` (lista) y `fichaForced` (ficha), cada una con su `Content` y su observer.
- **Una sola vez** (`st.used`): vale aunque la web repinte la lista, se reenvíe la config o se cargue otra página. El estado `{session,key,used,listKey,fichaUntil,fichaDone}` se guarda en `sessionStorage` (`omniweb.state`): en una ficha que es OTRA página el motor la espera y la sustituye, y al volver a la lista `restoreList` vuelve a sustituir la tarjeta tocada (`cardKey` = enlace o URL de la imagen). `ForceConfig.session = navId`: «Reiniciar» lo rearma.
- Sin ficha capturada se mantiene el comportamiento clásico (toque silenciado, solo se sustituye la lista).
- Se retiró `DetailLayer`, «Fijar ficha» y `detailUrl` del modelo. Quedan inertes (sin uso) `OmniBridge.onForced`, el parámetro `layer` y las capas de iOS en Swift; `Capture.detailUrl` se sigue recibiendo pero no se guarda.
- Pruebas: `web/e2e/ficha.spec.ts` (panel en la misma página con `?panel=1`, navegación real con `?realnav=1`, reinicio de sesión, modo clásico). En la página de pruebas el enlace envuelve solo la imagen: tocar el título no abre nada.
- Límites/riesgos sin validar en webs reales: la detección del medio principal (YouTube: la ficha es un vídeo, solo se sustituye lo que sea imagen/póster; títulos y descripción fuera del contenedor del medio no se reemplazan) y la vuelta a la lista en navegaciones completas (Amazon).

## Inicio con pestañas y favicons (6e)
- `Home.kt`: `HomeScreen` con `BottomTabs` (Favoritos: `LazyVerticalGrid` 3 columnas con `SiteTile` + «Añadir»; Ajustes: modo práctica, vibración, rastros, cómo se usa). Tocar un icono abre la ficha de acciones (Preparar / Efecto / Ajustes). Iconos de pestaña dibujados con `Canvas` (estrella y engranaje).
- `Favicons.kt`: **no se incluyen logos de terceros**. `rememberFavicon` descarga `<origen>/apple-touch-icon.png` y, si no, `/favicon.ico` DIRECTAMENTE del dominio del sitio (sin servicios intermedios), lo guarda en `BlobStore` (`fav.<id>`; «none» si no hay, para no reintentar) y lo decodifica con `expect fun decodeImageBitmap` (Android `BitmapFactory`; iOS Skia `Image.makeFromEncoded`, sin compilar aún). Si no hay icono, inicial sobre color.
- Cuidado con los `
` en cadenas Kotlin al escribir ficheros con scripts: usar cadenas crudas (`"""..."""`) para textos de varias líneas.
