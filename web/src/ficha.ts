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

/** Identidad estable de una tarjeta de la lista: el enlace que la envuelve o, si no, la URL de su imagen. */
export function cardKey(media: Element): string {
  const a = (media.closest('a[href]') ?? media.parentElement?.querySelector('a[href]')) as HTMLAnchorElement | null;
  return a?.href || mediaUrl(media) || '';
}

/** Busca la tarjeta de la lista con esa clave (para volver a sustituirla tras una navegación). */
export function findCardByKey(doc: Document, key: string): Candidate | null {
  if (!key) return null;
  for (const m of allMedia(doc).slice(0, 400)) {
    const r = m.getBoundingClientRect();
    if (r.width < 60 || r.height < 60) continue;
    if (cardKey(m) !== key) continue;
    const c = { media: m, card: m.parentElement ?? m };
    if (isListItem(c)) return c;
  }
  return null;
}

export interface FichaOptions {
  /** Medios que ya estaban al tocar (la lista): no cuentan, salvo que se permitan los antiguos. */
  before: WeakSet<Element> | null;
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
    if (o.before && !o.allowOld && o.before.has(m)) continue;
    if (o.skip?.(m)) continue;
    const r = m.getBoundingClientRect();
    const area = Math.max(0, Math.min(r.right, vw) - Math.max(r.left, 0)) * Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));
    if (area < min || r.top > vh * 0.85) continue;
    // visible de verdad: lo que hay en su centro es él mismo (o algo suyo), no un panel que lo tape
    const cx = Math.min(Math.max(r.left + r.width / 2, 0), vw - 1);
    const cy = Math.min(Math.max(r.top + r.height / 2, 0), vh - 1);
    const top = doc.elementFromPoint(cx, cy);
    if (!top || !(top === m || m.contains(top) || top.contains(m))) continue;
    if (!best || area > best.area) best = { m, area };
  }
  return best ? { media: best.m, card: best.m.parentElement ?? best.m } : null;
}
