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

> **Nota (2026-11-15, tras la prueba en iPhone):** RF-04, RF-05 y RF-15 quedan **redefinidos en el §10** (efecto = pausa + toque sobre un elemento de la lista, no sustitución al parar; la pantalla del efecto ya no es pantalla completa).

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

## 10. Efecto fiel y aspecto de navegador (decisiones del 2026-11-15)

Estado de partida: en iPhone ya funciona (scroll y sustitución). Lo que se pide ahora es que **se parezca de verdad a un navegador** y que
el efecto **ocurra como lo haría una pulsación real** y no antes.

### 10.1 Aspecto de navegador (look & feel)
- **RF-20** El efecto y Preparar **no van a pantalla completa**: se ve la **barra de estado del sistema** (hora, batería) y el contenido queda por debajo.
  Se quita el modo inmersivo y el ocultar la barra de estado/indicador de inicio (iOS: `UIStatusBarHidden`, `.statusBarHidden(true)`, `persistentSystemOverlays`).
- **RF-21** **Marco de navegador decorativo** en el efecto, con el estilo de cada sistema (Safari en iOS, Chrome en Android):
  - **Barra superior** con la URL real de la página (solo el dominio, candado) y el título; no hace falta que funcione.
  - **Barra inferior** que **aparece y desaparece al hacer scroll** (como Safari): atrás, adelante, compartir, favoritos, pestañas. Decorativa;
    «atrás» puede funcionar de verdad.
- **RF-22** **Activar/desactivar el marco** (por sitio y desde el menú oculto en directo). Con el marco apagado se ve solo la web bajo la barra de estado.
- **RF-23** Respetar zonas seguras (notch/isla dinámica) y teclado; la web nunca debe quedar pegada al borde superior.

### 10.2 Cómo ocurre el efecto (modelo de estados)
Sustituye a «sustituir al parar el scroll» (RF-04) y al disparo inmediato (RF-05).

```
 DESARMADO ──(activación secreta)──▶ ARMADO ──(scroll)──▶ DESLIZANDO ──(sin scroll ≥ idleMs)──▶ REPOSO
                 ▲                                              ▲                                    │
                 └───────── (tras el disparo: vuelve a normal) ─┴──── (nuevo scroll vuelve a DESLIZANDO)
                                                                                    toque válido ◀───┘ DISPARO
```
- **RF-24** El forzaje se dispara con un **toque** (tap corto, <10 px de movimiento y <300 ms) sobre un **elemento de la lista** estando en **REPOSO**:
  el espectador desliza, **para**, y **pulsa** como si eligiera uno.
- **RF-25** Condiciones de tiempo:
  - *Deslizando*: llegan eventos de scroll con menos de `idleMs` entre ellos (300/450/600 ms, ya configurable).
  - *Reposo*: ha pasado `idleMs` desde el último scroll. **Un toque que llega mientras aún hay inercia no cuenta** (es el toque que frena la lista): se deja pasar y el siguiente toque ya puede disparar.
  - No hay caducidad del reposo; cualquier scroll nuevo vuelve a *Deslizando*. Un solo disparo por carga de página; después todo se comporta como una web normal.
- **RF-26** *(redefinido tras probar con Google Imágenes)* Al disparar con una ficha capturada: (1) **el toque llega a la web**, que abre **su propia ficha** de forma natural
  (panel de Google Imágenes, página de Amazon, vídeo de YouTube); (2) **por detrás** se sustituye la tarjeta tocada de la lista (un instante después, cuando la web ya procesó el toque);
  (3) cuando aparece la ficha, el motor sustituye en ella su **medio principal** (y sus textos) por lo capturado en Preparar. Sin ficha capturada: el toque se silencia y solo se sustituye la lista (comportamiento anterior, RF-28).
- **RF-27** **Ficha** = lo que la web muestra al abrir un elemento: se detecta como el **mayor medio visible nuevo** (que no estaba en la lista); si la ficha es otra página, el estado se conserva entre páginas (sessionStorage) y al volver a la lista la tarjeta sigue sustituida.
  No se abre ninguna segunda capa ni se guarda URL: la ficha es siempre la nativa de la web. En la ficha se sustituyen imagen y textos sobre los elementos vivos (sin clonar HTML, para no matar sus botones).
- **RF-28** Si no se capturó ficha, se sustituye solo la lista y no se abre nada.
- **RF-38** **Una sola vez**: tras forzar, la web se comporta con normalidad aunque repinte la lista o se reenvíe la configuración; solo «Reiniciar» (o una configuración nueva) lo rearma.
- **RF-35** **Solo elementos de una lista**: se ignoran logos, cabeceras y cualquier cosa que no sea una tarjeta repetida (p. ej. el logo de Google antes de buscar).
  Criterio genérico: la tarjeta tiene hermanas con la misma estructura (≥2) y un medio de tamaño mínimo. Si el toque no cae en una tarjeta válida, la web se comporta con normalidad.

### 10.3 Preparar: qué se guarda
> **Estado:** Android hecho y probado en emulador (ficha nativa); iOS reutiliza el mismo motor y la misma lógica común (sin build todavía).
- **RF-29** Preparar en **dos pasos**: (1) *Capturar elemento* de la lista (imagen, textos, instantánea de la tarjeta); (2) opcional, **entrar en su ficha** como lo haría el espectador y pulsar
  *Capturar ficha* sobre su imagen principal (se guardan imagen y textos). «Sin ficha» guarda solo la lista. Ya no se guarda la URL de la ficha.
- **RF-30** En el efecto el sitio abre su **inicio vacío** (ya hecho); la búsqueda la hace el mago o el espectador.

### 10.4 Activación secreta y demostración
- **RF-31** Flujo de actuación: el mago puede **demostrar primero con la web real** (busca, desliza, abre un objeto, lo cierra) con el efecto **DESARMADO**, y **arma el truco en secreto**
  después. Ajuste por sitio: «Armado desde el inicio» / «Armar con gesto». La **activación secreta** es: botón de volumen, toque en una zona oculta (abajo-derecha)
  o pulsación en la zona del menú. El disparo inmediato anterior pasa a ser una opción avanzada.
- **RF-32** **Zona oculta del menú**: **arriba en el centro**, grande (≈160×64 pt), y coincide con la **barra de URL decorativa**: una pulsación larga ahí abre el menú.
  Con el marco apagado la zona sigue existiendo, invisible. Se retira la esquina superior izquierda.

- **RF-36** **Vibración al armar**: cuando el mago activa el modo forzaje (le da la orden en secreto de que **la siguiente pulsación que no sea un scroll fuerce**),
  el móvil **vibra una vez** como confirmación. Si vuelve a dar la orden y **desarma**, vibra con un patrón distinto (dos pulsos cortos). No vibra si el efecto
  arranca ya armado. Ajuste general «Vibración al armar» (activado por defecto). Android: `Vibrator` (permiso `VIBRATE`); iOS: háptico del sistema.
- **RF-37** «Pulsación que no es scroll» = toque corto (<10 px y <300 ms) **con la lista en reposo** (sin scroll desde hace `idleMs`): el toque que frena la inercia no cuenta.
- **Ajuste «Armado»** (sustituye a Automático/Manual): *Desde el inicio* (el efecto ya está armado, no vibra) o *Con gesto secreto* (hay que armarlo; vibra al hacerlo).

### 10.5 La app (fuera del efecto)
> **Estado 6e (Android hecho):** barra inferior Favoritos/Ajustes y favicons descargados del propio sitio. iOS escrito, sin verificar.
- **RF-33** **Barra de navegación inferior** en la app, con pestañas. La primera, **Favoritos**, es como la página de inicio de un navegador: **cuadrícula de iconos** de los sitios con su logo.
  Otras pestañas previstas: Rastros/Modo práctica, Ajustes generales, Ayuda.
- **RF-34** Logos de los sitios: **no se incluyen logos de terceros en la app** (requisito §6 y marcas registradas). Se usará el **favicon que publica cada sitio** (lo que hace cualquier navegador), descargado en el
  dispositivo desde el propio sitio y guardado en local; mientras tanto, inicial sobre color.

### 10.6 Orden de trabajo propuesto
1. **6a** Aspecto de navegador (RF-20…23) y zona oculta arriba-centro (RF-32). Es independiente y se ve enseguida.
2. **6b** Modelo de estados y toque (RF-24, 25, 35) con sustitución por detrás; tests Playwright de tiempos (inercia, doble toque, scroll nuevo).
3. **6c** Ficha (RF-26, 27, 29): primero en segunda capa; **sustituida por la ficha nativa + forzar dentro** tras probar con Google Imágenes.
4. **6d** Activación secreta y «armar con gesto» (RF-31).
5. **6e** Barra inferior de la app, favoritos y favicons (RF-33, 34).

### 10.7 Riesgos a validar
- **Ficha de Google Imágenes**: es un panel dentro de la misma página sin URL propia, por eso la ficha es la nativa de la web (RF-26). Pendiente validar la detección del medio principal en Google, YouTube y Amazon reales (en YouTube la ficha es un vídeo: solo se sustituye lo que sea imagen/póster).
- **Webs que reaccionan a `pointerdown`/`touchend`** (YouTube): hay que interceptar toda la familia de eventos al disparar, como ya se hace en la captura.
- **Marco decorativo con barra inferior que se esconde**: necesita saber la dirección del scroll (nuevo mensaje del motor JS a la app).
- iOS: sin botón de volumen fiable con el volumen al máximo/mínimo; la zona oculta es el gesto de referencia.
