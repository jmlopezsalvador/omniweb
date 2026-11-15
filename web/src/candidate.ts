import { mediaKind } from './apply';

export interface Candidate {
  media: Element;
  card: Element;
}

const MEDIA_SELECTOR = 'img,video,[style*="background"],[class],div,a,span';

function rectArea(r: DOMRect): number {
  return Math.max(0, r.width) * Math.max(0, r.height);
}

function contains(r: DOMRect, x: number, y: number): boolean {
  return x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
}

/** Medios (img/video/fondo) dentro de `root`, incluido el propio root. */
function mediaWithin(root: Element): Element[] {
  const out: Element[] = [];
  if (mediaKind(root)) out.push(root);
  root.querySelectorAll(MEDIA_SELECTOR).forEach((e) => {
    if (mediaKind(e)) out.push(e);
  });
  return out;
}

/**
 * Elemento «tarjeta» más centrado: toma lo que hay en (x, y), sube por los ancestros hasta encontrar
 * un contenedor con medios visibles y elige el medio mayor que contiene el punto (o el más cercano).
 * No sube a contenedores casi del tamaño de la pantalla (serían la página entera).
 */
export function findCandidate(doc: Document, x: number, y: number): Candidate | null {
  const win = doc.defaultView!;
  const vw = win.innerWidth;
  const vh = win.innerHeight;
  let el: Element | null = doc.elementFromPoint(x, y);
  for (let depth = 0; el && depth < 10 && el !== doc.body && el !== doc.documentElement; depth++, el = el.parentElement) {
    const r = el.getBoundingClientRect();
    if (r.width >= vw * 0.98 && r.height >= vh * 0.9 && depth > 0) break; // demasiado grande
    const medias = mediaWithin(el).filter((m) => {
      const mr = m.getBoundingClientRect();
      return mr.width >= 24 && mr.height >= 24 && mr.bottom > 0 && mr.top < vh;
    });
    if (!medias.length) continue;
    // prioridad: medios que contienen el punto (el de menor área = el más específico); si no, el más cercano al punto
    const hit = medias.filter((m) => contains(m.getBoundingClientRect(), x, y));
    let media: Element;
    if (hit.length) {
      media = hit.reduce((a, b) => (rectArea(a.getBoundingClientRect()) <= rectArea(b.getBoundingClientRect()) ? a : b));
    } else {
      const dist = (m: Element) => {
        const b = m.getBoundingClientRect();
        return Math.hypot(b.left + b.width / 2 - x, b.top + b.height / 2 - y);
      };
      media = medias.reduce((a, b) => (dist(a) <= dist(b) ? a : b));
    }
    return { media, card: el };
  }
  return null;
}

function classTokens(e: Element): string[] {
  return (typeof e.className === 'string' ? e.className : '').split(/\s+/).filter(Boolean);
}

/** Dos elementos «de la misma familia»: misma etiqueta y, si tienen clases, al menos una en común. */
function similar(a: Element, b: Element): boolean {
  if (a.tagName !== b.tagName) return false;
  const ta = classTokens(a);
  const tb = classTokens(b);
  if (!ta.length && !tb.length) return true;
  return ta.some((t) => tb.includes(t));
}

function hasMedia(e: Element): boolean {
  return !!mediaKind(e) || !!e.querySelector('img,video,[style*="background-image"]');
}

/**
 * ¿La tarjeta candidata forma parte de un LISTADO? Sube desde el medio hasta encontrar un ancestro que tenga al menos 2
 * hermanos de su misma familia con medios. Así se ignoran logos, cabeceras y banners (el logo de Google antes de buscar)
 * y solo se fuerza un resultado de verdad. Con layout, además, el medio debe tener un tamaño mínimo.
 */
export function isListItem(c: Candidate): boolean {
  const mr = c.media.getBoundingClientRect();
  if (mr.height > 0 && (mr.width < 60 || mr.height < 60)) return false;
  const body = c.media.ownerDocument.body;
  let el: Element | null = c.media;
  for (let depth = 0; el && depth < 12; depth++, el = el.parentElement) {
    const parent: Element | null = el.parentElement;
    if (!parent || parent === body) break;
    const sibs = Array.from(parent.children).filter((s) => s !== el && similar(s, el!) && hasMedia(s));
    if (sibs.length >= 2) return true;
  }
  return false;
}
