/** Detector de «scroll parado». Espera a que acabe el dedo (touch) y la inercia (los scroll siguen llegando). */
export interface IdleOptions {
  idleMs: number;
  onIdle: () => void;
}

export interface IdleDetector {
  arm(): void;
  disarm(): void;
  setIdleMs(ms: number): void;
}

export function createIdleDetector(win: Window, opts: IdleOptions): IdleDetector {
  let idleMs = opts.idleMs;
  let armed = false;
  let touching = false;
  let scrolled = false; // solo se dispara si ha habido scroll desde arm()
  let timer: ReturnType<typeof setTimeout> | undefined;

  const clear = () => {
    if (timer !== undefined) clearTimeout(timer);
    timer = undefined;
  };
  const schedule = () => {
    clear();
    if (!armed || touching || !scrolled) return;
    timer = setTimeout(() => {
      timer = undefined;
      if (armed && !touching && scrolled) {
        scrolled = false;
        opts.onIdle();
      }
    }, idleMs);
  };

  // Solo cuenta el scroll de la página; el de contenedores internos (diálogos, carruseles) no es el «scroll infinito».
  const onScroll = (e: Event) => {
    const t = e.target as Node | null;
    const d = win.document;
    if (t && t.nodeType === 1 && t !== d.scrollingElement && t !== d.documentElement && t !== d.body) return;
    scrolled = true;
    schedule();
  };
  const onTouchStart = () => {
    touching = true;
    clear();
  };
  const onTouchEnd = () => {
    touching = false;
    schedule();
  };

  const opt = { passive: true, capture: true } as const;
  // capture en document/window para cazar también el scroll de contenedores internos (no burbujea)
  win.addEventListener('scroll', onScroll, opt);
  win.addEventListener('touchstart', onTouchStart, opt);
  win.addEventListener('touchend', onTouchEnd, opt);
  win.addEventListener('touchcancel', onTouchEnd, opt);

  return {
    arm() {
      armed = true;
      scrolled = false;
    },
    disarm() {
      armed = false;
      clear();
    },
    setIdleMs(ms) {
      idleMs = ms;
    },
  };
}
