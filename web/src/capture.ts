import { collectTexts, imageAsDataUrl, mediaUrl, snapshotCard } from './apply';
import { findCandidate } from './candidate';

/**
 * URL de la «ficha» del elemento: el enlace que lo envuelve (o el primero de su tarjeta). Se descartan los que no llevan a
 * otra página (`#`, `javascript:`, la propia página). null si no hay.
 */
export function detailHref(media: Element): string | null {
  const doc = media.ownerDocument;
  const here = doc.location?.href.split('#')[0];
  const ok = (a: Element | null): string | null => {
    if (!a) return null;
    const raw = a.getAttribute('href') ?? '';
    if (!raw || raw.startsWith('#') || /^javascript:/i.test(raw)) return null;
    const abs = (a as HTMLAnchorElement).href;
    return abs && abs.split('#')[0] !== here && /^https?:|^file:/i.test(abs) ? abs : null;
  };
  const own = ok(media.closest('a[href]'));
  if (own) return own;
  let el: Element | null = media.parentElement;
  for (let d = 0; el && d < 6 && el !== doc.body; d++, el = el.parentElement) {
    const found = ok(el.querySelector('a[href]'));
    if (found) return found;
  }
  return null;
}

export interface Capture {
  imageUrl: string;
  imageData: string | null;
  texts: string[];
  /** Instantánea HTML de la tarjeta (o null si no es clonable). */
  html: string | null;
  /** Página de la ficha del elemento (ver `detailHref`). */
  detailUrl: string | null;
  pageUrl: string;
  aspect: number;
}

export interface CaptureMode {
  start(): void;
  stop(): void;
}

const OUTLINE = '4px solid #00e676';

/** Modo captura: el siguiente toque NO activa la web; resalta la tarjeta tocada y entrega sus datos al host. */
export function createCaptureMode(win: Window, onCapture: (c: Capture) => void): CaptureMode {
  const doc = win.document;
  let on = false;
  let marked: HTMLElement | null = null;

  const unmark = () => {
    if (marked) marked.style.removeProperty('outline');
    marked = null;
  };

  const onClick = (e: Event) => {
    if (!on) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    const m = e as MouseEvent;
    const cand = findCandidate(doc, m.clientX, m.clientY);
    if (!cand) return;
    const url = mediaUrl(cand.media);
    if (!url) return;
    unmark();
    marked = cand.media as HTMLElement;
    marked.style.setProperty('outline', OUTLINE);
    const r = cand.media.getBoundingClientRect();
    onCapture({
      imageUrl: url,
      imageData: imageAsDataUrl(cand.media),
      texts: collectTexts(cand.media),
      html: snapshotCard(cand.media),
      detailUrl: detailHref(cand.media),
      pageUrl: win.location.href,
      aspect: r.height ? r.width / r.height : 1,
    });
  };

  // Muchas webs (YouTube móvil) navegan con pointerup/touchend y no con click: con la captura armada silenciamos toda la
  // familia de eventos del toque para que la página no los vea. El click sintetizado lo tratamos arriba.
  const swallow = (e: Event) => {
    if (on) e.stopImmediatePropagation();
  };
  for (const t of ['touchstart', 'touchend', 'touchcancel', 'pointerdown', 'pointerup', 'pointercancel', 'mousedown', 'mouseup']) {
    win.addEventListener(t, swallow, true);
  }
  // capture=true y a nivel de window: llegamos antes que los manejadores de la página
  win.addEventListener('click', onClick, true);

  return {
    start() {
      on = true;
    },
    stop() {
      on = false;
      unmark();
    },
  };
}
