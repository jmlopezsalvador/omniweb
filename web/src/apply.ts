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

const NON_TEXT = /^(script|style|svg|path|button|img|video|source|noscript)$/i;

/** Textos «hoja» (sin hijos elemento) de un contenedor, en orden de documento, sin contar el propio medio. */
export function textLeaves(container: Element, media: Element): Element[] {
  return Array.from(container.querySelectorAll('*')).filter(
    (n) =>
      n.children.length === 0 &&
      !n.contains(media) &&
      !NON_TEXT.test(n.tagName) &&
      !n.closest('button,[role="button"]') &&
      (n.textContent ?? '').trim().length >= 1,
  );
}

/**
 * Contenedor «tarjeta» de un medio con sus textos: sube hasta el primer ancestro con texto, sin llegar a uno que
 * contenga varias imágenes (la rejilla). Capturar y aplicar usan EXACTAMENTE esta función para que los índices coincidan.
 */
export function findTextContainer(media: Element): { container: Element; leaves: Element[] } | null {
  let c: Element | null = media.parentElement;
  for (let depth = 0; c && depth < 6 && c !== c.ownerDocument.body; depth++, c = c.parentElement) {
    if (c.querySelectorAll('img,video').length > 1) return null;
    const leaves = textLeaves(c, media);
    if (leaves.length) return { container: c, leaves };
  }
  return null;
}

export function collectTexts(media: Element): string[] {
  return (findTextContainer(media)?.leaves ?? []).map((n) => (n.textContent ?? '').trim());
}

/** Sustituye los textos de la tarjeta, uno a uno por posición. Devuelve contenedor y elementos cambiados. */
export function applyTexts(media: Element, texts: string[]): { container: Element; targets: Element[] } | null {
  const r = findTextContainer(media);
  if (!r) return null;
  const targets: Element[] = [];
  r.leaves.forEach((leaf, i) => {
    if (i < texts.length) {
      leaf.textContent = texts[i];
      targets.push(leaf);
    }
  });
  r.container.querySelectorAll('img').forEach((img) => img.hasAttribute('alt') && texts[0] && img.setAttribute('alt', texts[0]));
  return { container: r.container, targets };
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
