# HANDOFF — OmniWeb

Estado a 2026-11-16. Lo estable (arquitectura, convenciones, decisiones) está en `CLAUDE.md`; aquí solo el estado vivo.

## Qué es
App Android + iOS (Kotlin Multiplatform + Compose) tipo «navegador mágico»: el mago prepara un elemento real de una web (Google Imágenes, Amazon, YouTube…) y en la actuación, cuando el espectador desliza, para y toca un elemento de la lista, ve el elemento guardado en su lugar. Todo local. Idioma: español.

## Versión actual
- Último commit: `383d397` en `origin/main` («Ficha = página…»). Árbol: solo cambios sin commit en `CLAUDE.md` (renombrado del AVD y este handoff) y `HANDOFF.md`.
- iOS: build correcto (run 43778058377), `.ipa` en `data/ios_build/OmniWeb.ipa`. APK Android en `data/android_build/OmniWeb-debug.apk` (mismo código).
- Tests: motor 53 (Vitest) + 45 e2e (Playwright) + Gradle `:shared:testDebugUnitTest`, todos verdes.

## Hecho y verificado
- (2026-11-19) Guía de inicio rápido + pantalla de Instrucciones + icono definitivo (Android verificado en emulador: guía, Empezar, desactivar, Ajustes → Instrucciones/Guía, tecla atrás). iOS: código común + `BackPressEffect` stub + icono 1024 sin compilar ni probar.
- Android (emulador `omniweb-emu`): flujo completo en Amazon real: Preparar (lista + ficha) → «La ficha es una PÁGINA distinta» → Efecto: tocar un producto abre la página real del producto forzado; atrás devuelve la lista con la tarjeta forzada. Favoritos con marco de navegador y pulsación larga → Ajustes.
- Google Imágenes real: diagnosticado y corregido (imágenes apiladas en la ficha, tarjeta clonada que perdía el enlace), pero NO reverificado tras los últimos cambios (aviso de cookies).

## Sin verificar
- **Todo en iPhone**: Swift/Kotlin de iOS escritos a ciegas; solo se sabe que compila. Esperando feedback del usuario tras el último `.ipa`.
- Google real con la detección de ficha acelerada (50 ms) y `restoreList` con MutationObserver.
- Que en la vuelta atrás no se vea el original un instante (no medido).

## Falta / ideas
- Preguntar al usuario si «con gesto secreto» debe ser el armado por defecto en sitios nuevos (hoy: «Armado desde el inicio»).
- Re-preparar Amazon/YouTube en el móvil del usuario para que guarden `pageUrl` (los forzajes viejos no la tienen).
- Google (ficha = panel): solo se cambia la imagen, no los textos; no se puede abrir el panel por enlace.
- Amazon en modo panel/antiguo: título y precio no se sustituyen.
- Parcado: Netflix/Instagram (login), personalizar el aspecto de Favoritos desde Ajustes, icono definitivo, requisitos de tiendas (Play/App Store), manual «Cómo se usa» al día con «Ficha = página».

## Problemas abiertos / riesgos
- Si Google/Amazon cambian el DOM, pueden romperse `isListItem`, `findTextContainer`, apilado de la ficha.
- Ficha = página navega con `location.assign` a la URL capturada: si la web exige parámetros de sesión caducos, la página real puede diferir.
- El emulador se cuelga con carga alta; reiniciar con `emulator -avd omniweb-emu -no-snapshot-load -no-audio -no-window`. `calculadora-emu` es de otra sesión: no tocarlo.

## Reglas de trabajo (resumen)
No commit/push sin que lo pida; preguntar antes de cada lanzamiento de iOS Actions; ignorar `ignore/`; no pulsar avisos de cookies; escribir scripts `.py` con Write (los heredocs complejos fallan); tras cambiar `web/src` ejecutar `npm run build` y commitear `dist/`.

## Ficheros clave
- Motor: `web/src/index.ts` (orquestación: ficha, `startPage`, `restoreList`, estado en sessionStorage), `tap.ts` (gesto), `candidate.ts` (`isListItem`), `ficha.ts` (medios de la ficha, `cardKey`), `apply.ts` (sustitución, instantánea, overlay, `retargetLinks`), `capture.ts`, `home.ts`; salida `web/dist/omniweb.js`; tests `web/e2e/ficha.spec.ts`, `web/test/page.html`; depuración `web/tools/cdp.mjs`.
- App común (`shared/src/commonMain/kotlin/com/omniweb/ui/`): `App.kt` (Preparar `CaptureScreen`, `EffectScreen`), `Home.kt` (Favoritos/Sitios/Ajustes), `ForceModel.kt` (`ForceStore`, `DetailItem.pageUrl`, `detailPageUrl`), `ForcerWebView.kt` (expect + `ForceConfig.toJs`), `BrowserChrome.kt`.
- Android: `ForcerWebView.android.kt`. iOS: `ForcerWebView.ios.kt`, `IosWebHost.kt`, `iosApp/iosApp/OmniWebHost.swift`, `ContentView.swift`.
- Docs: `docs/SPEC.md`, `docs/ARQUITECTURA.md`. CI: `.github/workflows/ios.yml`.
