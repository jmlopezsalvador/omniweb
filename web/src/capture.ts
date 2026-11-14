import { collectTexts, imageAsDataUrl, mediaUrl } from './apply';
import { findCandidate } from './candidate';

export interface Capture {
  imageUrl: string;
  imageData: string | null;
  texts: string[];
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
