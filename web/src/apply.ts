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
