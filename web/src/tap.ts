/**
 * Disparo por TOQUE. El forzaje ocurre cuando, con el truco ARMADO, el espectador pulsa un elemento con la lista en REPOSO:
 *
 *   - toque «de verdad»: corto (<300 ms) y casi sin movimiento (<10 px). Un arrastre es scroll y no cuenta.
 *   - reposo: no ha habido scroll del documento en los últimos `idleMs`. El toque que llega mientras aún hay inercia es el que
 *     frena la lista: se deja pasar a la web y NO dispara.
 *   - solo si `canTap(x, y)` dice que ahí hay un elemento válido (una tarjeta de una lista, no un logo).
 *
 * Mientras el gesto es candidato a disparo se silencia a la página (pointer/touch/mouse/click) para que no abra el elemento
 * real; si resulta ser un scroll o un toque largo se le devuelven los eventos (ya no se silencian los siguientes).
 */
export interface TapTriggerOptions {
  idleMs: number;
  /** ¿Hay en (x, y) algo que forzar? Se consulta al apoyar el dedo. */
  canTap(x: number, y: number): boolean;
  /** Se llama UNA vez en el toque válido. Devuelve true si forzó algo. */
  onTap(x: number, y: number): boolean;
  /**
   * Si devuelve true (se consulta al apoyar el dedo), el toque NO se silencia: la web lo recibe y abre su ficha de forma
   * natural, y `onTap` se llama igualmente (antes de que la web lo procese).
   */
  passThrough?(): boolean;
  now?: () => number;
}

export interface TapTrigger {
  setArmed(on: boolean): void;
  isArmed(): boolean;
  setIdleMs(ms: number): void;
}

const SWALLOWED = ['pointerdown', 'pointerup', 'touchstart', 'touchend', 'mousedown', 'mouseup', 'click'] as const;
const MAX_TAP_MS = 300;
const MAX_MOVE_PX = 10;
const TAIL_MS = 400; // los eventos «sintéticos» (mouse*, click) llegan después del pointerup

export function createTapTrigger(win: Window, opts: TapTriggerOptions): TapTrigger {
  const now = opts.now ?? (() => performance.now());
  let armed = false;
  let idleMs = opts.idleMs;
  let lastScrollAt = -Infinity;
  let down: { x: number; y: number; t: number } | null = null;
  let swallowUntil = 0;
  let swallowing = false;
  let passing = false; // este toque se deja pasar a la web (ver `passThrough`)

  const doc = win.document;

  // El scroll de la página (no el de contenedores internos) marca «deslizando»
  win.addEventListener(
    'scroll',
    (e: Event) => {
      const t = e.target as Node | null;
      if (t && t.nodeType === 1 && t !== doc.scrollingElement && t !== doc.documentElement && t !== doc.body) return;
      lastScrollAt = now();
    },
    { passive: true, capture: true },
  );

  const swallow = (e: Event) => {
    if (swallowing || now() < swallowUntil) {
      e.stopImmediatePropagation();
      if (e.type === 'click') {
        e.preventDefault();
        swallowUntil = 0; // el click es lo último de la secuencia de este toque
        swallowing = false;
      }
    }
  };
  win.addEventListener(
    'pointerdown',
    (e: PointerEvent) => {
      down = null;
      swallowing = false;
      if (!armed || !e.isPrimary || (e.pointerType === 'mouse' && e.button !== 0)) return;
      const rest = now() - lastScrollAt >= idleMs;
      if (!rest || !opts.canTap(e.clientX, e.clientY)) return;
      down = { x: e.clientX, y: e.clientY, t: now() };
      passing = !!opts.passThrough?.();
      if (passing) return;
      swallowing = true;
      swallow(e); // este mismo pointerdown también se silencia
    },
    true,
  );

  win.addEventListener(
    'pointermove',
    (e: PointerEvent) => {
      if (down && Math.hypot(e.clientX - down.x, e.clientY - down.y) > MAX_MOVE_PX) {
        down = null; // era un arrastre: scroll, no toque
        swallowing = false;
      }
    },
    { passive: true, capture: true },
  );

  const cancel = () => {
    down = null;
    swallowing = false;
  };
  win.addEventListener('pointercancel', cancel, true);

  win.addEventListener(
    'pointerup',
    (e: PointerEvent) => {
      const d = down;
      down = null;
      if (!d || !armed) {
        swallowing = false;
        return;
      }
      const quick = now() - d.t <= MAX_TAP_MS;
      const still = Math.hypot(e.clientX - d.x, e.clientY - d.y) <= MAX_MOVE_PX;
      if (!quick || !still) {
        swallowing = false;
        return;
      }
      swallowing = false;
      if (!passing) {
        swallowUntil = now() + TAIL_MS; // pointerup, touchend, mouseup y click de ESTE toque
        swallow(e);
      }
      opts.onTap(d.x, d.y);
    },
    true,
  );

  // Se registra el último: los manejadores propios de arriba (pointerdown/up) deben ejecutarse ANTES de que este
  // silenciador detenga la propagación a los siguientes (si no, stopImmediatePropagation les impediría correr).
  for (const type of SWALLOWED) win.addEventListener(type, swallow, true);

  return {
    setArmed(on) {
      armed = on;
      if (!on) cancel();
    },
    isArmed: () => armed,
    setIdleMs(ms) {
      idleMs = ms;
    },
  };
}
