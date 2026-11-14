# SPEC — OmniWeb (navegador mágico para forzar una imagen)

Repositorio: https://github.com/jmlopezsalvador/omniweb.git · Paquete: `com.omniweb` · Plataformas: Android + iOS (Android primero).

## 1. Idea
Navegador con la web real cargada. El mago configura de antemano la **web objetivo** y la **imagen a forzar**. En la actuación
el espectador hace scroll infinito y para donde quiera; la imagen forzada aparece justo ahí, integrada como contenido original
(Google Imágenes, YouTube, Netflix, Amazon, Instagram…). Debe funcionar en **cualquier web** (modo genérico), con perfiles
opcionales por sitio.

## 2. Casos de uso
1. Google Imágenes: parar en cualquier foto → sale la elegida.
2. Netflix / YouTube: parar en una película/vídeo → es la predicha.
3. Amazon / eBay: parar en un producto concreto.
4. Web arbitraria (preparada o añadida en el momento).
5. Varios presets (forzajes) guardados.
6. Modo práctica: ensayar y resetear sin dejar trazas.

## 3. Requisitos funcionales (MVP)
- RF-01 Navegador in-app con barra de URL ocultable y user agent móvil configurable.
- RF-02 **Sitios** y **forzajes**. El mago tiene una lista de webs añadidas (Google Imágenes, YouTube…). Un forzaje = sitio + elemento
  capturado (no se sube ninguna imagen desde la galería: **se fuerza un elemento que ya existe en la propia web**).
- RF-03 **Modo captura** (preparación): el mago abre un sitio, navega y busca normalmente (p. ej. un término en Google Imágenes) y
  toca el elemento que quiere forzar entre los resultados. La app guarda del elemento: la imagen (descargada y cacheada en local),
  los textos visibles de su tarjeta (título, fuente…), proporción y tamaño. Queda guardado y listo para el efecto.
- RF-03b **Modo efecto**: el mago entra, elige el sitio y la app abre la web ya preparada; al parar el espectador el scroll se
  sustituye el elemento central por el capturado (imagen + textos), con el aspecto de contenido original.
- RF-04 Modo actuación: detecta parada del scroll (debounce configurable 300–600 ms + fin de inercia) y sustituye el elemento más
  centrado por la imagen, adaptando tamaño. Opcional: cambiar título/texto asociado.
- RF-05 Disparo **automático** al parar o **manual** con gesto oculto (toque en esquina, botón de volumen); configurable por forzaje.
- RF-06 Reset limpio: borrar historial, cookies y caché; botón para volver a la web real.
- RF-07 Persistencia local, sin cuentas ni backend.

## 4. Enfoque técnico (decisiones)
- KMP + Compose; WebView nativo por plataforma vía `expect/actual` (Android `WebView`, iOS `WKWebView`).
- **Un único bundle JS** (TypeScript → esbuild → un `.js` en recursos) inyectado en ambas plataformas:
  detector de scroll idle, `MutationObserver`, localizador de candidato (`elementFromPoint` + ancestro «tarjeta»),
  sustitución de `src`/`srcset`/`<picture><source>`/`background-image`/`poster`, reaplicado ante re-render.
- CSP: el JS inyectado no está sujeto a la CSP de script; el riesgo es `img-src`. Android: `shouldInterceptRequest` sirve la imagen
  desde una URL del mismo host. iOS: `data:` URL; plan B overlay nativo.
- Desactivar universal links / esquemas no http(s).
- Tests: Vitest (JS) y Playwright (DOM contra página local); `commonTest` para lógica Kotlin.

## 5. Fases
- **0** Spike Android con página HTML local de scroll infinito.
- **1** Modo genérico en Google Imágenes.
- **2** Modo captura (tocar un elemento de la web y guardarlo) + lista de sitios y forzajes + modo efecto.
- **3** Perfiles YouTube, Netflix, Amazon, Instagram.
- **4** Versión iOS (build en Actions, con permiso del usuario).
- **5** Pulido: gestos ocultos, borrado de trazas, rendimiento.
- Fuera de alcance: modo para el móvil del espectador con servidor propio.

## 6. Restricciones
- Marca y assets propios; nada de apps existentes (AnyWeb, Inertia Pro).
- Solo se modifica el renderizado local; sin recolección de datos; cumplir políticas de Play Store / App Store.
- Commits pequeños; al cerrar cada fase, resumen y propuesta de la siguiente.

## 7. Preguntas abiertas
- Icono: provisional (globo de navegador generado); sustituir por `data/icon.png` cuando el usuario lo aporte.
- Idiomas de la interfaz (se asume español).
