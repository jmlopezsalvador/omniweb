# SPEC — OmniWeb (navegador mágico para forzar un elemento)

Repositorio: https://github.com/jmlopezsalvador/omniweb.git · Paquete: `com.omniweb` · Plataformas: Android + iOS.
Revisado el 2026-11-15 tras investigar las apps de referencia (ver §9).

## 1. Idea
Navegador con la web real cargada, en el móvil **del mago**. El mago **prepara** antes un elemento que ya existe en una web
(una foto de Google Imágenes, un vídeo, un producto…) y lo deja guardado. En la actuación abre la web **vacía** (la página
de inicio del sitio): el mago o el espectador hace **la búsqueda que quiera**, el espectador hace scroll infinito y se
detiene donde quiera; justo ahí el elemento guardado aparece integrado como contenido original. Debe funcionar en **cualquier
web** (modo genérico), con ajustes y perfiles opcionales por sitio.

## 2. Casos de uso
1. **Google Imágenes**: se abre Google Imágenes vacío, se busca cualquier cosa, el espectador hace scroll y para en una foto → sale la elegida.
2. **YouTube / Netflix**: parar en un vídeo o película → es la predicha (Netflix e Instagram exigen sesión: aparcados).
3. **Amazon / eBay**: parar en un producto concreto.
4. **Web arbitraria**: el mago añade la URL, prepara y actúa.
5. **Presets / favoritos**: varias rutinas guardadas (y algunas predefinidas) para cambiar rápido entre ellas.
6. **Modo práctica**: ensayar y resetear sin dejar trazas.

Flujo de cada rutina:
- **Preparar** (a solas): abrir el sitio → buscar lo que haga falta → *Capturar elemento* → tocarlo → guardar. Se guarda **el elemento**
  (imagen + textos + estructura de su tarjeta), **no la búsqueda**.
- **Efecto** (en público): abre el **inicio del sitio**, sin búsqueda guardada. Disparo automático al parar el scroll o manual
  con un gesto oculto.

## 3. Requisitos funcionales
### Hechos (Android; iOS escrito, pendiente de verificar)
- RF-01 Navegador in-app con barra de URL ocultable (en Preparar) y user agent móvil / escritorio por sitio.
- RF-02 Sitios y forzajes: se fuerza un elemento que ya existe en la web (nunca una imagen subida). Un forzaje por sitio.
- RF-03 Modo captura: tocar un elemento y guardar imagen (en local), textos, proporción e instantánea HTML de su tarjeta.
- RF-04 Detección de parada del scroll (300 / 450 / 600 ms + fin de inercia) y sustitución del elemento central.
- RF-05 Disparo automático o manual con gesto oculto (esquina inferior derecha, botón de volumen).
- RF-06 Borrar rastros, modo práctica y «mostrar la web real» desde el menú oculto (pulsación larga arriba-izquierda).
- RF-07 Todo local, sin cuentas ni backend.

### Cambios y nuevos (a partir del uso real y de la investigación)
- **RF-03b (corregido)** El efecto abre **la página de inicio del sitio** (`Site.startUrl`), no la página donde se capturó.
  `ForcedItem.pageUrl` deja de usarse para abrir el efecto.
- RF-08 **Más disparadores** (Inertia Pro ofrece pausa, número de deslizamientos, «scroll hasta arriba», proximidad y reloj):
  añadir *N deslizamientos* (configurable) a la pausa, el toque oculto y el volumen. Proximidad y reloj: fuera por ahora.
- RF-09 **Presets / favoritos**: varias rutinas por sitio, con nombre, y algunas de ejemplo predefinidas; elegir la activa antes de actuar.
  (AnyWeb 1 los llama «Favorites», «default ones» y «create your own effects».) Sube de prioridad: es parte del producto de referencia.
- RF-10 **Quitar banners «usar la app» / «abrir en la app»** de las webs (AnyWeb 1 los eliminó por sitio en la 1.1.3; Amazon
  los muestra con un botón «Open»). Genérico por defecto (CSS + heurística) y afinable por perfil de sitio.
- RF-11 **Estabilidad con zoom**: el forzaje debe seguir funcionando tras hacer zoom in/out (AnyWeb 1.1.2 corrigió justo eso).
- RF-12 **Tamaño correcto de la imagen forzada**: respetar la proporción del hueco y no dejarla pequeña (AnyWeb 1.2.3: «images appear small»);
  hoy se recorta con `object-fit: cover`.
- RF-13 **Editar los textos** del elemento antes de guardarlo (Inertia Pro: «Edit Mode»): opcional, después.
- RF-14 **Tabletas / iPad**: maquetación correcta (AnyWeb 1 anunció que funciona «incredible on iPad»): opcional.
- RF-15 La actuación debe poder hacerse **sin tocar la barra ni dejar pistas**: pantalla completa, sin botón de salida visible.

### Fuera de alcance
- «Google trucado» en el móvil del **espectador** con servidor propio, PIN y dominio corto (así funciona AnyWeb 2), ni la actuación
  remota (Xeno), ni el disparador por Apple Watch.

## 4. Enfoque técnico (decisiones)
- KMP + Compose; WebView nativo por plataforma (Android `WebView`; iOS `WKWebView` creado en Swift y conectado por un protocolo de Kotlin).
- **Un único bundle JS** (TypeScript → esbuild) inyectado en ambas plataformas: detector de scroll idle, `MutationObserver`,
  localizador de candidato, sustitución de imagen y **instantánea de tarjeta**, reaplicado ante re-render.
- Android: puente `OmniBridge` (`addJavascriptInterface`). iOS: `WKScriptMessageHandler` con un shim `window.OmniBridge`.
- Bloqueo de esquemas externos (`intent://`, `youtube://`…).
- Tests: Vitest (JS), Playwright (DOM contra página local), `commonTest` (Kotlin).

## 5. Fases
- **0–2** hechas (spike, modo genérico en Google, captura + sitios + efecto).
- **3** YouTube y Amazon hechos; Netflix e Instagram aparcados por el login.
- **4** iOS escrito y compilado en Actions; **bloqueo de scroll** detectado en la primera prueba en iPhone (ver §8).
- **5** Parte Android hecha (gestos ocultos, rastros, ajustes, bloqueo de apps externas).
- **6 (nueva)** Alinear con el producto de referencia: RF-03b, RF-08…RF-12 (ver §3) y corregir lo de iOS.

## 6. Restricciones
- Marca y assets propios; no copiar código, textos ni imágenes de AnyWeb, Inertia Pro ni otras. Solo se usan sus descripciones públicas para entender el efecto.
- Solo se modifica el renderizado local en el dispositivo del mago; sin recolección de datos.
- Commits pequeños; al cerrar cada fase, resumen y propuesta de la siguiente.

## 7. Preguntas abiertas
- Cómo reacciona **exactamente** AnyWeb 1 al forzar (¿instantáneo al parar, o necesita un toque?, ¿hay un paso previo de zoom?): las fuentes
  públicas no lo detallan; lo ideal es que lo describas tú si la has usado.
- ¿Quieres RF-08 (N deslizamientos) y RF-09 (presets) ya, o primero cerrar iOS?
- Icono: provisional (globo); sustituir por `data/icon.png`. Idiomas de la interfaz: español.

## 8. Estado y problemas conocidos (2026-11-15)
- **iOS (primera prueba en iPhone)**: el scroll no funcionaba en NINGUNA web (Google, YouTube, Amazon, Wikipedia). Corrección aplicada: el navegador pasa a una capa nativa SwiftUI fuera de Compose (pendiente de verificar en el iPhone). Detalle original:: en Google no se puede hacer scroll de los resultados ni en Preparar ni en el efecto. Sin
  causa confirmada: hipótesis (a) el diálogo de cookies de Google bloquea el scroll; (b) el `UIKitView` dentro de Compose no
  entrega bien los gestos al `WKWebView`; (c) el motor JS o los gestos nativos interfieren. Pendiente de diagnosticar.
- **Efecto abre la búsqueda guardada** → corregido: el efecto abre `Site.startUrl` (RF-03b hecho). Rediseño visual hecho (tema oscuro propio).
- Android: verificado en emulador (ver CLAUDE.md). Netflix e Instagram aparcados.

## 9. Investigación de la app de referencia (2026-11-15)
Fuentes públicas (fichas de App Store y tiendas de magia). **Son textos comerciales: casi no explican la mecánica**; lo que sigue
distingue lo que dicen de lo que se infiere.

**AnyWeb v1 (Magic Pro Ideas; equipo de magos argentinos)** — «un navegador mágico» que se usa **en el móvil del mago**.
- Dicen: forzar elecciones en otras webs y apps «con simples toques en pantalla»; «imita los sitios en tiempo real»; ejemplos: Google, Google Maps,
  Amazon, Netflix, Instagram, IMDB «o cualquier otro»; «sin técnicas, la app lo hace todo»; se pueden «crear tus propios efectos».
- Notas de versión (pistas de funciones reales): favoritos (y algunos por defecto) y opción de arrancar en «Favorites Setup»;
  ocultar la barra de direcciones; quitar los banners «usa la app» de varias webs; «triggering forces now more stable»; arreglo
  de zoom in/out antes de forzar; arreglo de imágenes que salían pequeñas; funciona en iPad; sin datos recogidos; iOS 15.6+; hay versión Android.
- Modelo: de pago (59,99 USD en la ficha; unos 40 USD en tiendas de magia) con código de desbloqueo; clasificación 16+/17+ por «acceso web sin restricciones».
- No dicen: cómo se prepara el efecto, cuándo aparece el elemento, si hay gestos ocultos ni si funciona sin conexión.

**Inertia Pro** — «forzar cualquier cosa en cualquier web mientras el espectador mira el móvil»; va integrado en un navegador.
- Disparadores de la versión 2: **número de deslizamientos, proximidad, scroll hasta arriba, Apple Watch**; «Preset Force»:
  dejar la posición del forzaje preparada y abrir el navegador cuando empieza la actuación; interfaz del navegador automática según la web.
- Extras de pago: **modo edición** (cambiar texto de cualquier web) y uso en un móvil prestado («Momentum»); actuación remota con «Xeno».
- Sin límite de sitios.

**AnyWeb 2** — producto distinto («no es una actualización»): un **Google trucado en el móvil del espectador** (dominio corto + PIN),
hasta **9 imágenes**, detecta más de 40 idiomas; único rastro: la dirección en el historial. Exige servidor propio.

**Qué aprendemos para OmniWeb**
1. El producto de referencia es, como OmniWeb, un navegador en el móvil del mago con sitios y favoritos: vamos bien encaminados.
2. Lo que falta respecto a ellas: más disparadores (RF-08), favoritos/presets (RF-09), quitar banners de «usar la app» (RF-10),
   estabilidad con zoom (RF-11) y tamaño correcto de la imagen (RF-12).
3. Estas apps están publicadas en las tiendas (16+/17+ por acceso web), así que publicar una app de este tipo parece viable;
   corrijo así mi cautela anterior sobre la revisión de Apple (sigue siendo una decisión que se verifica al enviar).

Fuentes: ficha de App Store de AnyWeb (id1491262721) y de Anyweb 2 (id1555576782); ficha de Inertia Pro (id1540387087);
reseña de Ning Cai (2020); tiendas Magic Supply y Tricksupply. Otras tiendas dieron error 403/429 o piden login y no se leyeron.
