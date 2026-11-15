import { FORCED_ATTR, mediaKind, mediaUrl } from './apply';
import { isListItem, type Candidate } from './candidate';

/**
 * Ficha NATIVA: al forzar, la web abre SU ficha (panel de Google Imágenes, página de Amazon, vídeo de YouTube) y el motor
 * sustituye dentro de ella el medio principal por el que el mago capturó en la ficha durante la preparación.
 */

const SELECTOR = 'img,video,[style*="background"],[class],div,a,span';

/** Todos los medios (img/video/fondo) del documento. */
export function allMedia(doc: Document): Element[] {
  const out: Element[] = [];
  doc.body?.querySelectorAll(SELECTOR).forEach((e) => {
    if (mediaKind(e)) out.push(e);
  });
  return out;
}

function hashStr(s: string): string {
  let h = 5381;
  for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0;
  return (h >>> 0).toString(36) + ':' + s.length;
}

/**
 * Identidad de una tarjeta de la lista: el enlace que ENVUELVE al medio + una huella de su imagen. No se usa «el primer enlace
 * del contenedor»: en una rejilla ese enlace es el de otra tarjeta y varias tarjetas compartirían clave.
 */
export function cardKey(media: Element): string {
  const a = media.closest('a[href]') as HTMLAnchorElement | null;
  return (a?.href ?? '') + '|' + hashStr(mediaUrl(media) ?? '');
}

export interface CardId {
  href: string;
  url: string;
}

/** Identidad «en vivo» de una tarjeta, para comprobar que un elemento sigue siendo la misma tarjeta (no un elemento reciclado). */
export function cardId(media: Element): CardId {
  const a = media.closest('a[href]') as HTMLAnchorElement | null;
  return { href: a?.href ?? '', url: mediaUrl(media) ?? '' };
}

export function sameCard(a: CardId, b: CardId): boolean {
  return a.href && b.href ? a.href === b.href : a.url === b.url;
}

/**
 * Busca la tarjeta de la lista con esa clave (para volver a sustituirla tras una navegación). Si la clave no es única
 * (dos tarjetas iguales) NO se elige ninguna: es mejor no sustituir que sustituir otra tarjeta distinta de la tocada.
 */
export function findCardByKey(doc: Document, key: string): Candidate | null {
  if (!key) return null;
  let found: Candidate | null = null;
  for (const m of allMedia(doc).slice(0, 600)) {
    const r = m.getBoundingClientRect();
    if (r.width < 60 || r.height < 60) continue;
    if (cardKey(m) !== key) continue;
    const c = { media: m, card: m.parentElement ?? m };
    if (!isListItem(c)) continue;
    if (found) return null;
    found = c;
  }
  return found;
}

/** Área (px²) de cada medio en este instante: lo que había al tocar. Los ocultos cuentan 0. */
export function snapshotMedia(doc: Document): Map<Element, number> {
  const m = new Map<Element, number>();
  for (const e of allMedia(doc)) {
    const r = e.getBoundingClientRect();
    m.set(e, Math.max(0, r.width) * Math.max(0, r.height));
  }
  return m;
}

/** Cuánto debe crecer un medio que ya existía para considerarlo la ficha (la web reutilizó el elemento y lo amplió). */
const GROWTH = 1.6;

/** ¿Lo que hay encima en ese punto es parte del propio componente del medio (un enlace o capa suya) y no un panel ajeno? */
function ownsPoint(m: Element, top: Element, area: number): boolean {
  if (top === m || m.contains(top) || top.contains(m)) return true;
  let a: Element | null = m.parentElement;
  for (let i = 0; a && i < 4; i++, a = a.parentElement) {
    if (!a.contains(top)) continue;
    const r = a.getBoundingClientRect();
    return r.width * r.height <= area * 2.2;
  }
  return false;
}

export interface FichaOptions {
  /** Lo que había al tocar (la lista): no cuenta, salvo que el elemento haya crecido (reutilizado) o estuviera oculto. */
  before: Map<Element, number> | null;
  /** Permite elegir también medios que ya existían (la web reutilizó el elemento al abrir la ficha). */
  allowOld: boolean;
  /** Área mínima como fracción de la pantalla. */
  minFrac?: number;
  /** Medios que no valen (p. ej. los de la tarjeta de la lista ya sustituida). */
  skip?: (m: Element) => boolean;
}

/**
 * Medio principal de la ficha: el mayor medio visible en pantalla que no esté ya forzado ni sea de la lista de antes.
 * En un documento nuevo (`before` null) vale cualquiera; en una página que no se recarga (panel, SPA) solo los medios nuevos.
 */
export function findFichaMedia(doc: Document, o: FichaOptions): Candidate | null {
  const win = doc.defaultView!;
  const vw = win.innerWidth;
  const vh = win.innerHeight;
  const min = (o.minFrac ?? 0.12) * vw * vh;
  let best: { m: Element; area: number } | null = null;
  for (const m of allMedia(doc)) {
    if (m.closest(`[${FORCED_ATTR}]`) || m.hasAttribute(FORCED_ATTR)) continue;
    if (o.skip?.(m)) continue;
    const r = m.getBoundingClientRect();
    if (o.before && !o.allowOld) {
      const a0 = o.before.get(m);
      // ya estaba al tocar: solo vale si ahora es mucho mayor (o estaba oculto): la web lo reutilizó para la ficha
      if (a0 !== undefined && a0 > 0 && r.width * r.height < a0 * GROWTH) continue;
    }
    const area = Math.max(0, Math.min(r.right, vw) - Math.max(r.left, 0)) * Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));
    if (area < min || r.top > vh * 0.85) continue;
    // visible de verdad: lo que hay en su centro es él mismo (o algo suyo), no un panel que lo tape
    const cx = Math.min(Math.max(r.left + r.width / 2, 0), vw - 1);
    const cy = Math.min(Math.max(r.top + r.height / 2, 0), vh - 1);
    const top = doc.elementFromPoint(cx, cy);
    if (!top || !ownsPoint(m, top, r.width * r.height)) continue;
    if (!best || area > best.area) best = { m, area };
  }
  return best ? { media: best.m, card: best.m.parentElement ?? best.m } : null;
}
