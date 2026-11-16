/** Sustitución de imagen/título sobre un elemento ya localizado. Sin dependencias de layout (testable con jsdom). */

export const FORCED_ATTR = 'data-omni-forced';

export type MediaKind = 'img' | 'video' | 'bg';

/**
 * Las URL `data:` con espacios, comas o comillas rompen `srcset` (que separa por comas/espacios) y `url("...")`.
 * Se codifican esos caracteres (salvo la coma que separa la cabecera del cuerpo, y el base64, que no los lleva).
 */
export function normalizeImageUrl(url: string): string {
  if (!url.startsWith('data:')) return url;
  const i = url.indexOf(',');
  if (i < 0) return url;
  const body = url.slice(i + 1).replace(/ /g, '%20').replace(/,/g, '%2C').replace(/"/g, '%22').replace(/\n/g, '%0A');
  return url.slice(0, i + 1) + body;
}

export function mediaKind(el: Element): MediaKind | null {
  const tag = el.tagName.toLowerCase();
  if (tag === 'img') return 'img';
  if (tag === 'video') return 'video';
  if (tag === 'svg' || tag === 'canvas') return null;
  const bg = (el.ownerDocument.defaultView?.getComputedStyle(el).backgroundImage ?? '') || '';
  if (bg && bg !== 'none' && /url\(/.test(bg)) return 'bg';
  return null;
}

/** Aplica la imagen. Devuelve una función `isApplied()` que dice si el elemento sigue mostrando la imagen forzada. */
export function applyImage(el: Element, imageUrl: string): () => boolean {
  const kind = mediaKind(el);
  if (kind === 'img') {
    const img = el as HTMLImageElement;
    const pic = img.parentElement?.tagName.toLowerCase() === 'picture' ? img.parentElement : null;
    pic?.querySelectorAll('source').forEach((s) => {
      s.setAttribute('srcset', imageUrl);
      s.removeAttribute('sizes');
    });
    img.removeAttribute('srcset');
    img.removeAttribute('sizes');
    img.removeAttribute('loading'); // evita que el lazy-loading la deje en blanco
    img.setAttribute('src', imageUrl);
    img.style.setProperty('object-fit', 'cover');
    el.setAttribute(FORCED_ATTR, '1');
    return () => img.getAttribute('src') === imageUrl && !img.hasAttribute('srcset');
  }
  if (kind === 'video') {
    const v = el as HTMLVideoElement;
    v.setAttribute('poster', imageUrl);
    v.pause?.();
    v.style.setProperty('object-fit', 'cover');
    el.setAttribute(FORCED_ATTR, '1');
    return () => v.getAttribute('poster') === imageUrl;
  }
  const h = el as HTMLElement;
  const set = () => {
    h.style.setProperty('background-image', `url("${imageUrl}")`, 'important');
    h.style.setProperty('background-size', 'cover', 'important');
    h.style.setProperty('background-position', 'center', 'important');
  };
  set();
  el.setAttribute(FORCED_ATTR, '1');
  return () => h.style.getPropertyValue('background-image').includes(imageUrl);
}

const TITLE_SELECTOR = 'h1,h2,h3,h4,h5,h6,[role="heading"],[class*="title" i],[class*="name" i],figcaption';

/** Busca el texto «título» de una tarjeta y lo cambia. Devuelve true si cambió algo. */
export function applyTitle(card: Element, title: string): boolean {
  const target = Array.from(card.querySelectorAll(TITLE_SELECTOR)).find(
    (n) => !n.querySelector(TITLE_SELECTOR) && (n.textContent ?? '').trim().length > 0,
  );
  if (!target) return false;
  target.textContent = title;
  card.querySelectorAll('img').forEach((i) => i.hasAttribute('alt') && i.setAttribute('alt', title));
  return true;
}

/**
 * Título de una tarjeta cuando no hay ancestro claro: sube desde el medio hasta un contenedor con texto propio
 * (sin llegar a uno que contenga varias imágenes = la rejilla entera) y cambia el primer texto «hoja» en orden de documento.
 * Devuelve el contenedor y el elemento cambiado, o null.
 */
export function applyTitleNear(media: Element, title: string): { container: Element; target: Element } | null {
  let c: Element | null = media.parentElement;
  for (let depth = 0; c && depth < 6 && c !== c.ownerDocument.body; depth++, c = c.parentElement) {
    if (c.querySelectorAll('img,video').length > 1) return null; // ya es la rejilla
    const heading = Array.from(c.querySelectorAll(TITLE_SELECTOR)).find(
      (n) => !n.contains(media) && !n.querySelector(TITLE_SELECTOR) && (n.textContent ?? '').trim().length > 0,
    );
    const leaf =
      heading ??
      Array.from(c.querySelectorAll('*')).find(
        (n) =>
          n.children.length === 0 &&
          !n.contains(media) &&
          !/^(script|style|svg|path|button|img|video|source)$/i.test(n.tagName) &&
          (n.textContent ?? '').trim().length >= 3,
      );
    if (leaf) {
      leaf.textContent = title;
      c.querySelectorAll('img').forEach((i) => i.hasAttribute('alt') && i.setAttribute('alt', title));
      return { container: c, target: leaf };
    }
  }
  return null;
}

const NON_TEXT = /^(script|style|svg|path|button|img|video|source|noscript|textarea|input|select)$/i;

/** ¿Se ve el elemento? (descarta textos «solo para lectores de pantalla», recortados a 1px, display:none, invisibles). */
function isVisible(n: Element): boolean {
  const r = n.getBoundingClientRect();
  if (r.width < 2 || r.height < 2) return false;
  const st = n.ownerDocument.defaultView!.getComputedStyle(n);
  return st.display !== 'none' && st.visibility !== 'hidden' && st.opacity !== '0';
}

/**
 * Unidades de texto de un contenedor: NODOS DE TEXTO no vacíos, en orden de documento. Se usan nodos y no elementos
 * porque muchas webs mezclan texto y etiquetas (`<span>23<span>.</span>99</span>`, Amazon). Si la página tiene layout
 * (el medio mide algo) solo cuentan los visibles; sin layout (jsdom) no se filtra.
 */
export function textLeaves(container: Element, media: Element): Text[] {
  const layout = media.getBoundingClientRect().height > 0;
  const doc = container.ownerDocument;
  const walker = doc.createTreeWalker(container, 4 /* NodeFilter.SHOW_TEXT */);
  const out: Text[] = [];
  for (let n = walker.nextNode(); n; n = walker.nextNode()) {
    const t = n as Text;
    const el = t.parentElement;
    if (!el || (t.nodeValue ?? '').trim().length < 1) continue;
    if (media.contains(t) || NON_TEXT.test(el.tagName) || el.closest('button,[role="button"],noscript,script,style')) continue;
    if (layout && !isVisible(el)) continue;
    out.push(t);
  }
  return out;
}

function area(e: Element): number {
  const r = e.getBoundingClientRect();
  return Math.max(0, r.width) * Math.max(0, r.height);
}

/**
 * ¿Hay en `c` otro medio «significativo» (otra tarjeta)? Se ignoran los pequeños (avatares, iconos: < 25% del medio
 * objetivo). Cuenta img/video y elementos con background-image en línea.
 */
function hasOtherMedia(c: Element, media: Element): boolean {
  const a = area(media);
  return Array.from(c.querySelectorAll('img,video,[style*="background-image"]')).some(
    (m) => m !== media && !media.contains(m) && !m.contains(media) && !(a > 0 && area(m) < a * 0.25),
  );
}

/**
 * Contenedor «tarjeta» de un medio con sus textos: sube mientras el ancestro siga conteniendo solo ese medio (más
 * avatares/iconos pequeños) y no sea mucho más alto que él, y se queda con el MÁS ALTO que tenga texto (título, canal, fecha…).
 * Capturar y aplicar usan EXACTAMENTE esta función para que los índices coincidan.
 */
export function findTextContainer(media: Element): { container: Element; leaves: Text[] } | null {
  const mh = media.getBoundingClientRect().height;
  const vh = media.ownerDocument.defaultView!.innerHeight;
  let best: { container: Element; leaves: Text[] } | null = null;
  let c: Element | null = media.parentElement;
  for (let depth = 0; c && depth < 16 && c !== c.ownerDocument.body; depth++, c = c.parentElement) {
    if (hasOtherMedia(c, media)) break; // ya es la rejilla
    // Demasiado alto para ser «la tarjeta». Se mide la ALTURA, no el área: en tarjetas de lista (Amazon, YouTube) la
    // imagen es pequeña y el texto va al lado, y el área de la tarjeta puede ser >5× la de la imagen.
    if (mh > 0 && c.getBoundingClientRect().height > Math.max(mh * 3, vh * 0.4)) break;
    const leaves = textLeaves(c, media);
    if (leaves.length) best = { container: c, leaves };
  }
  return best;
}

const textOf = (n: Text) => (n.nodeValue ?? '').trim();

/**
 * Orden de asignación de textos: primero el TÍTULO y después el resto en orden de documento. Así el título cae siempre en
 * el hueco del título aunque la tarjeta de destino tenga otra plantilla (YouTube mezcla miniaturas verticales y listas).
 * Título = el texto de mayor tamaño de letra ENTRE LOS LARGOS (al menos la mitad que el más largo): en tiendas el
 * precio/descuento tiene más letra que el nombre del producto, pero es corto. A igualdad, el de más texto, y luego el primero.
 */
export function orderLeaves(leaves: Text[]): Text[] {
  if (leaves.length < 2) return leaves;
  const fs = (n: Text) => parseFloat(n.ownerDocument.defaultView!.getComputedStyle(n.parentElement!).fontSize) || 0;
  const maxLen = Math.max(...leaves.map((n) => textOf(n).length));
  let best = -1;
  leaves.forEach((n, i) => {
    if (textOf(n).length < maxLen / 2) return;
    const b = leaves[best];
    if (best < 0 || fs(n) > fs(b) || (fs(n) === fs(b) && textOf(n).length > textOf(b).length)) best = i;
  });
  return [leaves[best], ...leaves.filter((_, i) => i !== best)];
}

export function collectTexts(media: Element): string[] {
  return orderLeaves(findTextContainer(media)?.leaves ?? []).map(textOf);
}

/** Sustituye los textos de la tarjeta (título primero, luego el resto en orden). Devuelve contenedor y nodos cambiados. */
export function applyTexts(media: Element, texts: string[]): { container: Element; targets: Text[] } | null {
  const r = findTextContainer(media);
  if (!r) return null;
  const targets: Text[] = [];
  orderLeaves(r.leaves).forEach((leaf, i) => {
    if (i < texts.length) {
      leaf.nodeValue = texts[i];
      targets.push(leaf);
    }
  });
  r.container.querySelectorAll('img').forEach((img) => img.hasAttribute('alt') && texts[0] && img.setAttribute('alt', texts[0]));
  return { container: r.container, targets };
}


const MAX_SNAPSHOT = 200_000;
export const MAIN_ATTR = 'data-omni-main';

/** Ruta de índices (entre hijos elemento) desde `root` hasta `el`. */
function indexPath(root: Element, el: Element): number[] {
  const path: number[] = [];
  for (let n: Element | null = el; n && n !== root; n = n.parentElement) {
    path.unshift(Array.prototype.indexOf.call(n.parentElement!.children, n));
  }
  return path;
}

/**
 * Instantánea de la tarjeta de un medio: HTML interior de su contenedor, sin scripts, manejadores `on*` ni ids, con el medio
 * principal marcado. La web ya tiene el CSS de la tarjeta, así que reinsertarla se ve nativa. Devuelve null si el contenedor
 * lleva componentes personalizados DEFINIDOS (`ytm-…`: se re-renderizan al insertarse y quedarían vacíos; los no definidos son inertes) o es enorme.
 */
export function snapshotCard(media: Element): string | null {
  const r = findTextContainer(media);
  if (!r) return null;
  const root = r.container;
  const registry = media.ownerDocument.defaultView!.customElements;
  if ([root, ...Array.from(root.querySelectorAll('*'))].some((e) => e.tagName.includes('-') && registry.get(e.tagName.toLowerCase()))) return null;
  const clone = root.cloneNode(true) as Element;
  const main = path2(clone, indexPath(root, media));
  if (!main) return null;
  main.setAttribute(MAIN_ATTR, '1');
  (main as HTMLElement).style?.removeProperty('outline'); // resaltado del modo captura
  main.removeAttribute(FORCED_ATTR);
  clone.querySelectorAll('script,noscript').forEach((e) => e.remove());
  clone.querySelectorAll('*').forEach((e) => {
    for (const a of Array.from(e.attributes)) if (/^on/i.test(a.name) || a.name === 'id') e.removeAttribute(a.name);
  });
  const html = clone.innerHTML;
  return html.length <= MAX_SNAPSHOT ? html : null;
}

function path2(root: Element, path: number[]): Element | null {
  let n: Element | undefined = root;
  for (const i of path) n = n?.children[i];
  return n ?? null;
}

/** Pone la instantánea dentro de `container` y la imagen capturada en su medio principal. */
export function applySnapshotAt(container: Element, html: string, imageUrl: string): { main: Element; isApplied: () => boolean } | null {
  container.innerHTML = html;
  const main = container.querySelector(`[${MAIN_ATTR}]`);
  if (!main) return null;
  main.removeAttribute(MAIN_ATTR);
  return { main, isApplied: applyImage(main, imageUrl) };
}

/** Localiza el contenedor de `media` y le aplica la instantánea. */
export function applySnapshot(media: Element, html: string, imageUrl: string): { container: Element; main: Element; isApplied: () => boolean } | null {
  const r = findTextContainer(media);
  if (!r) return null;
  const s = applySnapshotAt(r.container, html, imageUrl);
  return s ? { container: r.container, ...s } : null;
}

/**
 * «Ficha = página»: todos los enlaces de la tarjeta (y el que la envuelve, si lo hay) apuntan a `href`. Así la tarjeta sustituida
 * abre DE VERDAD la página del elemento forzado y no hay que sustituir nada dentro de ella.
 */
export function retargetLinks(container: Element, href: string) {
  const set = (a: Element) => {
    if (a.getAttribute('href') !== href) a.setAttribute('href', href);
    a.removeAttribute('target');
  };
  container.querySelectorAll('a[href]').forEach(set);
  const outer = container.closest('a[href]');
  if (outer) set(outer);
}

export const OVERLAY_ATTR = 'data-omni-overlay';

/** Los hijos originales de la tarjeta se quedan en su sitio pero transparentes: siguen recibiendo el toque. */
function hideOriginals(container: Element, overlay: Element | null) {
  for (const ch of Array.from(container.children)) {
    if (ch === overlay) continue;
    const h = ch as HTMLElement;
    if (h.style.getPropertyValue('opacity') !== '0') h.style.setProperty('opacity', '0', 'important');
  }
}

/** La capa de instantánea que cuelga de `container`, si está. */
export function findOverlay(container: Element): Element | null {
  return Array.from(container.children).find((c) => c.hasAttribute(OVERLAY_ATTR)) ?? null;
}

/** ¿Hay algún hijo original sin ocultar (la web repintó la tarjeta)? */
export function overlayStale(container: Element): boolean {
  const ov = findOverlay(container);
  if (!ov) return true;
  return Array.from(container.children).some((c) => c !== ov && (c as HTMLElement).style?.getPropertyValue('opacity') !== '0');
}

/**
 * Variante «sobrepuesta» de la instantánea: en vez de VACIAR la tarjeta (se pierden los manejadores de la web: el enlace
 * original, la apertura de su ficha), la tarjeta original se queda intacta pero transparente y encima se pinta la instantánea con
 * la imagen capturada, sin recibir toques. El toque atraviesa la capa y la web abre su ficha como siempre.
 * La capa lleva la clase de la tarjeta (el CSS de la web se le aplica) y ocupa exactamente su caja.
 */
export function applySnapshotOverlayAt(container: Element, html: string, imageUrl: string): { main: Element; isApplied: () => boolean } | null {
  const doc = container.ownerDocument;
  const host = container as HTMLElement;
  findOverlay(container)?.remove();
  const o = doc.createElement('div');
  o.setAttribute(OVERLAY_ATTR, '1');
  if (typeof host.className === 'string') o.className = host.className;
  o.innerHTML = html;
  const main = o.querySelector(`[${MAIN_ATTR}]`);
  if (!main) return null;
  main.removeAttribute(MAIN_ATTR);
  o.style.cssText =
    'position:absolute!important;left:0!important;top:0!important;right:auto!important;bottom:auto!important;width:100%!important;' +
    'height:100%!important;margin:0!important;box-sizing:border-box!important;transform:none!important;pointer-events:none!important;';
  o.querySelectorAll('*').forEach((e) => (e as HTMLElement).style?.setProperty('pointer-events', 'none', 'important'));
  if (doc.defaultView!.getComputedStyle(host).position === 'static') host.style.setProperty('position', 'relative');
  hideOriginals(container, null);
  container.appendChild(o);
  return { main, isApplied: applyImage(main, imageUrl) };
}

/** Si la web repinta la tarjeta, vuelve a ocultar los hijos nuevos (sin tocar la capa). */
export function reHideOriginals(container: Element) {
  hideOriginals(container, findOverlay(container));
}

export function applySnapshotOverlay(
  media: Element,
  html: string,
  imageUrl: string,
): { container: Element; main: Element; isApplied: () => boolean } | null {
  const r = findTextContainer(media);
  if (!r) return null;
  const s = applySnapshotOverlayAt(r.container, html, imageUrl);
  return s ? { container: r.container, ...s } : null;
}

/** URL de la imagen que muestra un medio (img/video poster/background-image), absoluta. */
export function mediaUrl(el: Element): string | null {
  const kind = mediaKind(el);
  let u: string | null = null;
  if (kind === 'img') u = (el as HTMLImageElement).currentSrc || el.getAttribute('src');
  else if (kind === 'video') u = el.getAttribute('poster');
  else if (kind === 'bg') {
    const m = /url\((['"]?)(.*?)\1\)/.exec(el.ownerDocument.defaultView!.getComputedStyle(el).backgroundImage);
    u = m ? m[2] : null;
  }
  if (!u) return null;
  try {
    return u.startsWith('data:') ? u : new URL(u, el.ownerDocument.baseURI).href;
  } catch {
    return u;
  }
}

/** Intenta obtener los bytes de la imagen ya cargada (canvas). Devuelve null si el canvas queda «tainted» por CORS. */
export function imageAsDataUrl(el: Element): string | null {
  const u = mediaUrl(el);
  if (u?.startsWith('data:')) return u;
  if (el.tagName.toLowerCase() !== 'img') return null;
  const img = el as HTMLImageElement;
  if (!img.complete || !img.naturalWidth) return null;
  try {
    const c = el.ownerDocument.createElement('canvas');
    c.width = img.naturalWidth;
    c.height = img.naturalHeight;
    c.getContext('2d')!.drawImage(img, 0, 0);
    return c.toDataURL('image/jpeg', 0.92);
  } catch {
    return null;
  }
}
