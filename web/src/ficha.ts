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

/** ¿Vale este medio como nuevo, o es de la lista de antes (que no cambió de tamaño ni estaba oculto)? */
function isNew(m: Element, r: DOMRect, before: Map<Element, number> | null, allowOld: boolean): boolean {
  if (!before || allowOld) return true;
  const a0 = before.get(m);
  // ya estaba al tocar: solo vale si ahora es mucho mayor (o estaba oculto): la web lo reutilizó para la ficha
  return !(a0 !== undefined && a0 > 0 && r.width * r.height < a0 * GROWTH);
}

/**
 * Medio principal de la ficha: el mayor medio visible en pantalla que no esté ya forzado ni sea de la lista de antes.
 * En un documento nuevo (`before` null) vale cualquiera; en una página que no se recarga (panel, SPA) solo los medios nuevos.
 * Entre varios (las fichas apilan un marcador grande detrás y la imagen real encima) gana el que SE VE: el que recibe el toque
 * en su centro; solo si ninguno lo cumple valen los que quedan tapados por una capa de la propia web.
 */
export function findFichaMedia(doc: Document, o: FichaOptions): Candidate | null {
  const win = doc.defaultView!;
  const vw = win.innerWidth;
  const vh = win.innerHeight;
  const min = (o.minFrac ?? 0.12) * vw * vh;
  let best: { m: Element; area: number; direct: boolean } | null = null;
  for (const m of allMedia(doc)) {
    if (m.closest(`[${FORCED_ATTR}]`) || m.hasAttribute(FORCED_ATTR)) continue;
    if (o.skip?.(m)) continue;
    const r = m.getBoundingClientRect();
    if (!isNew(m, r, o.before, o.allowOld)) continue;
    const area = Math.max(0, Math.min(r.right, vw) - Math.max(r.left, 0)) * Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));
    if (area < min || r.top > vh * 0.85) continue;
    // visible de verdad: lo que hay en su centro es él mismo (o algo suyo), no un panel que lo tape
    const cx = Math.min(Math.max(r.left + r.width / 2, 0), vw - 1);
    const cy = Math.min(Math.max(r.top + r.height / 2, 0), vh - 1);
    const top = doc.elementFromPoint(cx, cy);
    if (!top || !ownsPoint(m, top, r.width * r.height)) continue;
    const direct = top === m || m.contains(top);
    if (!best || (direct && !best.direct) || (direct === best.direct && area > best.area)) best = { m, area, direct };
  }
  return best ? { media: best.m, card: best.m.parentElement ?? best.m } : null;
}

function overlapArea(a: DOMRect, b: DOMRect): number {
  return Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left)) * Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
}

/**
 * Otros medios de la ficha que ocupan el MISMO hueco que el principal (el marcador de detrás y la imagen real de delante, o la
 * miniatura que sustituye a la grande cuando termina de cargar). Se sustituyen todos para que, sea cual sea la que se vea,
 * salga lo forzado. Los vecinos del carrusel (a un lado, fuera de ese hueco) no cuentan.
 */
export function findStackedMedia(
  doc: Document,
  anchor: Element,
  rect: DOMRect,
  o: { before: Map<Element, number> | null; skip?: (m: Element) => boolean },
): Element[] {
  const win = doc.defaultView!;
  const base = rect.width * rect.height;
  if (base <= 0 || !anchor.isConnected) return [];
  const out: Element[] = [];
  for (const m of allMedia(doc)) {
    if (m.closest(`[${FORCED_ATTR}]`) || m.hasAttribute(FORCED_ATTR)) continue;
    if (o.skip?.(m)) continue;
    const r = m.getBoundingClientRect();
    const area = r.width * r.height;
    if (area < base * 0.25 || area > base * 2.5) continue;
    if (!isNew(m, r, o.before, false)) continue;
    if (overlapArea(r, rect) < Math.min(area, base) * 0.6) continue;
    // del mismo componente: comparte con la imagen principal un contenedor no mucho mayor que ella (no «la página entera»;
    // así no se tocan las tarjetas de la lista que quedan detrás del panel)
    let common: Element | null = anchor.parentElement;
    while (common && !common.contains(m)) common = common.parentElement;
    if (!common || common === doc.body || common === doc.documentElement) continue;
    const cr = common.getBoundingClientRect();
    if (cr.width * cr.height > base * 4.5) continue;
    const cs = win.getComputedStyle(m);
    if (cs.display === 'none' || cs.visibility === 'hidden') continue;
    out.push(m);
  }
  return out;
}

/** ¿Se está mostrando el elemento? (conectado, con tamaño y dentro de la pantalla). */
export function isShown(el: Element): boolean {
  if (!el.isConnected) return false;
  const win = el.ownerDocument.defaultView!;
  const r = el.getBoundingClientRect();
  if (r.width < 2 || r.height < 2) return false;
  if (r.right <= 0 || r.bottom <= 0 || r.left >= win.innerWidth || r.top >= win.innerHeight) return false;
  const cs = win.getComputedStyle(el);
  return cs.display !== 'none' && cs.visibility !== 'hidden';
}
