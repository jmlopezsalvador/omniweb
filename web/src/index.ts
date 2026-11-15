import {
  applyImage,
  applySnapshot,
  applySnapshotAt,
  applySnapshotOverlay,
  applySnapshotOverlayAt,
  applyTexts,
  applyTitleNear,
  findOverlay,
  FORCED_ATTR,
  normalizeImageUrl,
  overlayStale,
  reHideOriginals,
} from './apply';
import { createCaptureMode } from './capture';
import { findCandidate, isListItem, type Candidate } from './candidate';
import { cardId, cardKey, findCardByKey, findFichaMedia, findStackedMedia, isShown, sameCard, snapshotMedia } from './ficha';
import { createShortcuts, type Shortcut } from './home';
import { createIdleDetector } from './idle';
import { createTapTrigger } from './tap';

/** Lo capturado en la FICHA del objeto (la página o el panel que abre la web al tocar un elemento de la lista). */
export interface OmniDetail {
  imageUrl: string;
  texts?: string[];
  html?: string;
}

export interface OmniConfig {
  imageUrl: string;
  title?: string;
  /** Textos de la tarjeta capturada (por posición). Si hay, sustituyen a `title`. */
  texts?: string[];
  /** Instantánea HTML de la tarjeta capturada; tiene prioridad sobre `texts`. */
  html?: string;
  idleMs?: number;
  /** Avisar a la app de la dirección del scroll (barra inferior decorativa que se esconde al bajar). */
  reportScroll?: boolean;
  /**
   * 'tap' (el efecto real): con el truco armado, fuerza al TOCAR un elemento de la lista en reposo.
   * 'auto' (heredado): al parar el scroll. 'manual': solo con OmniWeb.trigger().
   */
  mode?: 'tap' | 'auto' | 'manual';
  /**
   * Ficha capturada. Si hay, el toque NO se silencia: la web abre su ficha de forma natural; el motor sustituye la tarjeta de
   * la lista (por detrás) y el medio principal de la ficha cuando aparece (también si es otra página: el estado se guarda).
   */
  detail?: OmniDetail;
  /** Modo 'tap': ¿empieza armado? Si no, hay que llamar a `OmniWeb.setArmed(true)` (activación secreta). */
  armed?: boolean;
  /** «Inicio Google»: si hay, la página no se fuerza; se muestra bajo el buscador la fila de sitios de la app. */
  shortcuts?: Shortcut[];
  /** Identifica una sesión del efecto: al cambiar (p. ej. «Reiniciar») se olvida lo ya forzado. */
  session?: number;
}

/** Qué se pone en lugar de un elemento: la tarjeta de la lista o lo capturado en la ficha. */
interface Content {
  imageUrl: string;
  texts?: string[];
  html?: string;
  title?: string;
}

interface Forced {
  media: Element;
  card: Element;
  isApplied: () => boolean;
  content: Content;
  texts?: string[];
  textEls?: Node[];
  html?: string;
  observer: MutationObserver;
  docObserver?: MutationObserver;
  alive: boolean;
  /** La instantánea va SOBREPUESTA a la tarjeta original (que sigue viva y transparente): ver `applySnapshotOverlayAt`. */
  overlay?: boolean;
}

/** Estado que sobrevive a una navegación (sessionStorage): lo ya forzado no se repite al cargarse otra página. */
interface State {
  session: number | null;
  key: string;
  used: boolean;
  listKey?: string;
  fichaUntil?: number;
  fichaDone?: boolean;
  /** Dirección (sin #) de la página donde se tocó: una ficha que es OTRA página se espera solo si la URL es distinta. */
  fromHref?: string;
}

const STATE_KEY = 'omniweb.state';
const LIST_DELAY_MS = 350; // la web procesa el toque antes de que sustituyamos la tarjeta por detrás
const FICHA_WINDOW_MS = 10000; // tiempo máximo esperando a que aparezca la ficha
const NAV_GRACE_MS = 60000; // una página cargada tras tocar (la ficha) tiene ventana propia si llega dentro de este tiempo
const SHOWN_GRACE_MS = 500; // la ficha forzada puede dejar de verse este rato (la web cambia de elemento) antes de darla por cerrada

const stripHash = (u: string) => u.split('#')[0];

function install(win: Window & typeof globalThis) {
  // El host puede inyectar el bundle varias veces (onPageStarted + onPageFinished): una sola instancia.
  if ((win as unknown as { OmniWeb?: unknown }).OmniWeb) return;
  const doc = win.document;
  let cfg: OmniConfig | null = null;
  let forced: Forced | null = null; // tarjeta de la lista
  let fichaForced: Forced | null = null; // medio principal de la ficha
  let fichaExtras: Forced[] = []; // otros medios apilados en el mismo hueco (marcador de detrás, imagen real de delante…)
  let sweepTimer: number | undefined;
  let guard = false; // evita bucles con nuestro propio MutationObserver
  // Modo 'tap': el truco se hace UNA vez. Aunque la web repinte la lista (y `forced` se pierda), la app reenvíe la
  // configuración o se cargue otra página, no se vuelve a forzar en otra tarjeta; solo `reset()` o una configuración
  // distinta (o otra sesión) lo rearman.
  let st: State = { session: null, key: '', used: false };
  let configured = false; // ya se configuró en este documento
  let fichaTimer: number | undefined;
  let restoreTimer: number | undefined;

  const saveState = () => {
    try {
      win.sessionStorage.setItem(STATE_KEY, JSON.stringify(st));
    } catch {
      /* sin sessionStorage: solo vale dentro de este documento */
    }
  };
  const loadState = (): State | null => {
    try {
      return JSON.parse(win.sessionStorage.getItem(STATE_KEY) || 'null');
    } catch {
      return null;
    }
  };

  const listContent = (): Content => ({ imageUrl: cfg!.imageUrl, texts: cfg!.texts, html: cfg!.html, title: cfg!.title });
  const fichaContent = (): Content => ({ imageUrl: cfg!.detail!.imageUrl, texts: cfg!.detail!.texts, html: cfg!.detail!.html });

  const shortcutRow = createShortcuts(win);
  const idle = createIdleDetector(win, { idleMs: 450, onIdle: () => (cfg?.mode === 'auto' || cfg?.mode === undefined) && trigger() });

  // La tarjeta se decide al APOYAR el dedo: si la web reacciona antes de soltar (abre su ficha en pointerdown/touchstart, o
  // recoloca la rejilla), lo que haya bajo el dedo al soltar ya no es la tarjeta que se tocó.
  let pressed: { x: number; y: number; c: Candidate } | null = null;

  // Disparo por toque (modo 'tap')
  const tap = createTapTrigger(win, {
    idleMs: 450,
    canTap: (x, y) => {
      if (!cfg || cfg.mode !== 'tap' || forced || st.used) return false;
      const c = findCandidate(doc, x, y);
      pressed = c && isListItem(c) ? { x, y, c } : null;
      return !!pressed;
    },
    passThrough: () => !!cfg?.detail,
    onTap: (x, y) => {
      const p = pressed;
      pressed = null;
      const c = p && p.x === x && p.y === y && p.c.media.isConnected ? p.c : findCandidate(doc, x, y);
      if (!c) return false;
      if (cfg?.detail) {
        startFicha(c);
        return true;
      }
      return forceAt(c);
    },
  });

  function kill(f: Forced | null) {
    if (!f) return;
    f.alive = false;
    f.observer.disconnect();
    f.docObserver?.disconnect();
    if (f === forced) forced = null;
    if (f === fichaForced) fichaForced = null;
  }

  function reapply(f: Forced) {
    if (!f.alive || !cfg || guard) return;
    if (f.html && f.card.isConnected && f.overlay) {
      const ov = findOverlay(f.card);
      if (!ov || !ov.contains(f.media)) {
        // la web repintó la tarjeta y se llevó nuestra capa: se vuelve a poner
        guard = true;
        const s = applySnapshotOverlayAt(f.card, f.html, f.content.imageUrl);
        if (s) {
          f.media = s.main;
          f.isApplied = s.isApplied;
        }
        setTimeout(() => (guard = false), 0);
        return;
      }
      if (overlayStale(f.card)) {
        // hijos nuevos de la tarjeta (los pone la web): también transparentes
        guard = true;
        reHideOriginals(f.card);
        setTimeout(() => (guard = false), 0);
      }
    } else if (f.html && f.card.isConnected && !f.card.contains(f.media)) {
      // el framework repintó el interior de la tarjeta: volvemos a poner la instantánea
      guard = true;
      const s = applySnapshotAt(f.card, f.html, f.content.imageUrl);
      if (s) {
        f.media = s.main;
        f.isApplied = s.isApplied;
      }
      setTimeout(() => (guard = false), 0);
      return;
    }
    if (!f.media.isConnected) {
      // el framework ha repintado (o se cerró la ficha)
      const wasList = f === forced;
      kill(f);
      if (cfg.mode !== 'tap') trigger(); // en modo toque no se re-fuerza en otro sitio
      else if (wasList && st.used && st.listKey) restoreList(st.listKey); // la MISMA tarjeta, localizada por su identidad
      return;
    }
    if (!f.isApplied()) {
      guard = true;
      f.isApplied = applyImage(f.media, f.content.imageUrl);
      setTimeout(() => (guard = false), 0);
    }
    if (f.texts && f.textEls) {
      const texts = f.texts;
      const stale = f.textEls.some((el, i) => el.textContent !== texts[i]);
      if (stale) {
        guard = true;
        f.textEls.forEach((el, i) => (el.textContent = texts[i]));
        setTimeout(() => (guard = false), 0);
      }
    }
  }

  /** Fuerza en el elemento central de la pantalla (modos 'auto' y 'manual'). */
  function trigger(): boolean {
    if (!cfg || forced) return false;
    const c = findCandidate(doc, win.innerWidth / 2, win.innerHeight / 2);
    if (!c) {
      console.log('[OmniWeb] sin candidato en el centro');
      return false;
    }
    return forceAt(c);
  }

  /** Sustituye `c` por `content` (instantánea o imagen + textos) y la vigila por si la web repinta. */
  function substitute(c: Candidate, content: Content, overlay = false): Forced | null {
    guard = true;
    const release = () => setTimeout(() => (guard = false), 0);
    if (content.html) {
      const snap = overlay ? applySnapshotOverlay(c.media, content.html, content.imageUrl) : applySnapshot(c.media, content.html, content.imageUrl);
      if (snap) {
        const f: Forced = {
          media: snap.main,
          card: snap.container,
          isApplied: snap.isApplied,
          html: content.html,
          content,
          overlay,
          alive: true,
          observer: new MutationObserver(() => reapply(f)),
        };
        f.observer.observe(snap.container, { attributes: true, childList: true, subtree: true, characterData: true });
        console.log('[OmniWeb] forzado (instantánea)', snap.main.tagName, Math.round(snap.container.getBoundingClientRect().height) + 'px');
        release();
        return f;
      }
    }
    const isApplied = applyImage(c.media, content.imageUrl);
    let root: Element = c.card;
    let texts: string[] | undefined;
    let textEls: Node[] | undefined;
    if (content.texts?.length) {
      const t = applyTexts(c.media, content.texts);
      if (t) {
        root = t.container;
        texts = content.texts;
        textEls = t.targets;
      }
    } else if (content.title) {
      const t = applyTitleNear(c.media, content.title);
      if (t) {
        root = t.container;
        texts = [content.title];
        textEls = [t.target];
      }
    }
    const f: Forced = { media: c.media, card: root, isApplied, content, texts, textEls, alive: true, observer: new MutationObserver(() => reapply(f)) };
    f.observer.observe(root, { attributes: true, childList: true, subtree: true, characterData: true });
    console.log('[OmniWeb] forzado', c.media.tagName, (c.media as HTMLElement).className || '', 'en tarjeta', c.card.tagName, Math.round(c.card.getBoundingClientRect().height) + 'px');
    release();
    // si el padre desaparece (re-render), observamos el documento para volver a colocar
    f.docObserver = new MutationObserver(() => {
      if (f.alive && !f.media.isConnected) reapply(f);
    });
    f.docObserver.observe(doc.body, { childList: true, subtree: true });
    return f;
  }

  /** Modo clásico (sin ficha): sustituye la tarjeta `c` de la lista y se silencia el toque. */
  function forceAt(c: Candidate): boolean {
    if (!cfg || forced || (st.used && cfg.mode === 'tap')) return false;
    if (cfg.mode === 'tap') {
      st.used = true;
      st.listKey = cardKey(c.media);
      saveState();
    }
    const f = substitute(c, listContent());
    if (!f) return false;
    forced = f;
    return true;
  }

  /**
   * Con ficha: el toque llega a la web (abre su ficha). Aquí se sustituye la tarjeta de la lista por detrás, un instante
   * después, y se espera a que aparezca el medio principal de la ficha para sustituirlo también.
   */
  function startFicha(c: Candidate) {
    if (!cfg || st.used) return;
    st.used = true;
    st.listKey = cardKey(c.media);
    st.fichaDone = false;
    st.fichaUntil = Date.now() + FICHA_WINDOW_MS;
    st.fromHref = stripHash(win.location.href);
    saveState();
    const before = snapshotMedia(doc);
    const key = st.listKey;
    const id0 = cardId(c.media);
    win.setTimeout(() => {
      if (!cfg || forced) return;
      // Si la web ha reciclado/repintado la rejilla al abrir la ficha, el elemento tocado puede haber pasado a ser OTRA tarjeta:
      // solo se sustituye si sigue siendo la misma; si no, se busca por su identidad (o no se sustituye nada).
      if (c.media.isConnected && sameCard(cardId(c.media), id0)) forced = substitute(c, listContent(), true);
      else if (key) restoreList(key);
    }, LIST_DELAY_MS);
    watchFicha(before, st.fichaUntil);
  }

  /** Espera (hasta `until`) a que aparezca el medio principal de la ficha y lo sustituye. `before` = lo que había en la lista. */
  function watchFicha(before: Map<Element, number> | null, until: number) {
    win.clearInterval(fichaTimer);
    const href0 = win.location.href;
    const t0 = Date.now();
    let lastPick: Element | null = null;
    fichaTimer = win.setInterval(() => {
      if (!cfg?.detail || fichaForced || Date.now() > until) {
        win.clearInterval(fichaTimer);
        return;
      }
      // Solo hace falta el body: Amazon sirve la página por trozos y `readyState` puede seguir en «loading» mucho rato
      // con la ficha ya visible. (Si la imagen aún se está pintando, el sondeo exige dos lecturas iguales.)
      if (!doc.body) return;
      // si la web reutiliza los elementos al abrir la ficha, tras un rato se admiten también los que ya existían
      const allowOld = before !== null && win.location.href !== href0 && Date.now() - t0 > 1200;
      const c = findFichaMedia(doc, { before, allowOld, skip: (m) => !!forced && forced.card.contains(m) });
      if (!c) {
        lastPick = null;
        return;
      }
      if (c.media !== lastPick) {
        lastPick = c.media; // debe mantenerse dos sondeos seguidos (la ficha termina de pintarse)
        return;
      }
      win.clearInterval(fichaTimer);
      const rect = c.media.getBoundingClientRect();
      fichaForced = substitute(c, fichaContent());
      st.fichaDone = true;
      saveState();
      holdFicha(rect, before);
    }, 150);
  }

  /** Suelta lo forzado en la ficha (se cerró): si la web reutiliza esos elementos para otra ficha, no se vuelve a forzar. */
  function endFicha() {
    win.clearInterval(sweepTimer);
    for (const f of [fichaForced, ...fichaExtras]) {
      if (!f) continue;
      f.media.removeAttribute(FORCED_ATTR);
      kill(f);
    }
    fichaForced = null;
    fichaExtras = [];
  }

  /**
   * Mientras la ficha está abierta: sustituye también los medios apilados en su mismo hueco (la web pone un marcador grande
   * detrás y la imagen real llega más tarde y lo tapa), vuelve a buscar el principal si la web se lo llevó, y detecta que la
   * ficha se cerró para soltarla.
   */
  function holdFicha(rect: DOMRect, before: Map<Element, number> | null) {
    win.clearInterval(sweepTimer);
    const until = Date.now() + FICHA_WINDOW_MS;
    let lastShown = Date.now();
    const skip = (m: Element) => !!forced && forced.card.contains(m);
    sweepTimer = win.setInterval(() => {
      if (!cfg?.detail) {
        win.clearInterval(sweepTimer);
        return;
      }
      fichaExtras = fichaExtras.filter((f) => f.alive);
      const live = [fichaForced, ...fichaExtras].filter((f): f is Forced => !!f && f.alive);
      const now = Date.now();
      if (live.some((f) => isShown(f.media))) lastShown = now;
      else if (now - lastShown > SHOWN_GRACE_MS) return endFicha(); // la ficha se cerró: no se fuerza otra distinta
      if (now > until) return;
      if (!fichaForced) {
        const c = findFichaMedia(doc, { before, allowOld: false, skip });
        if (c) {
          rect = c.media.getBoundingClientRect();
          fichaForced = substitute(c, fichaContent());
        }
      }
      const anchor = fichaForced?.media ?? null;
      for (const m of anchor ? findStackedMedia(doc, anchor, rect, { before, skip }) : []) {
        const x = substitute({ media: m, card: m.parentElement ?? m }, { imageUrl: fichaContent().imageUrl });
        if (x) fichaExtras.push(x);
      }
    }, 150);
  }

  // Tocar la tarjeta ya sustituida: la web abre SU ficha (la tarjeta original sigue viva debajo) y esa ficha sale forzada también,
  // para que lo que se ve en la lista y en la ficha sea coherente. Solo vale para la tarjeta forzada: otra se comporta normal.
  let reDown: { x: number; y: number; t: number } | null = null;
  win.addEventListener(
    'pointerdown',
    (e: PointerEvent) => {
      reDown = e.isPrimary ? { x: e.clientX, y: e.clientY, t: performance.now() } : null;
    },
    true,
  );
  win.addEventListener(
    'pointerup',
    (e: PointerEvent) => {
      const d = reDown;
      reDown = null;
      if (!d || !cfg?.detail || !st.used || !forced || fichaForced) return;
      if (performance.now() - d.t > 300 || Math.hypot(e.clientX - d.x, e.clientY - d.y) > 10) return;
      const t = e.target as Node | null;
      if (!t || !forced.card.contains(t)) return;
      st.fichaDone = false;
      st.fichaUntil = Date.now() + FICHA_WINDOW_MS;
      saveState();
      watchFicha(snapshotMedia(doc), st.fichaUntil);
    },
    true,
  );

  /** Tras una navegación (volver a la lista): vuelve a sustituir la tarjeta que se tocó, identificada por su enlace/imagen. */
  function restoreList(key: string) {
    win.clearInterval(restoreTimer);
    let tries = 0;
    restoreTimer = win.setInterval(() => {
      if (!cfg || forced || ++tries > 25) {
        win.clearInterval(restoreTimer);
        return;
      }
      if (!doc.body) return;
      const c = findCardByKey(doc, key);
      if (c) {
        win.clearInterval(restoreTimer);
        forced = substitute(c, listContent(), !!cfg.detail);
      }
    }, 200);
  }

  function reset() {
    kill(forced);
    endFicha();
    win.clearInterval(fichaTimer);
    win.clearInterval(restoreTimer);
    st = { session: st.session, key: st.key, used: false };
    saveState();
    doc.querySelectorAll(`[${FORCED_ATTR}]`).forEach((e) => e.removeAttribute(FORCED_ATTR));
    if (cfg?.mode !== 'tap') idle.arm();
  }

  const capture = createCaptureMode(win, (c) => {
    const bridge = (win as unknown as { OmniBridge?: { onCapture(json: string): void } }).OmniBridge;
    bridge?.onCapture(JSON.stringify(c));
  });

  // Dirección del scroll hacia la app. Solo con `reportScroll`; ignora el scroll de contenedores internos.
  let lastY = win.scrollY;
  let lastDir = '';
  const emitDir = (dir: string) => {
    if (dir === lastDir) return;
    lastDir = dir;
    (win as unknown as { OmniBridge?: { onScroll?(d: string): void } }).OmniBridge?.onScroll?.(dir);
  };
  win.addEventListener(
    'scroll',
    (e: Event) => {
      if (!cfg?.reportScroll) return;
      const t = e.target as Node | null;
      if (t && t.nodeType === 1 && t !== doc.scrollingElement && t !== doc.documentElement && t !== doc.body) return;
      const y = win.scrollY;
      if (y < 40) {
        lastY = y;
        emitDir('up'); // arriba del todo: la barra inferior se muestra
        return;
      }
      const d = y - lastY;
      if (Math.abs(d) < 14) return;
      lastY = y;
      emitDir(d > 0 ? 'down' : 'up');
    },
    { passive: true, capture: true },
  );

  const api = {
    startCapture() {
      api.disarm();
      capture.start();
    },
    endCapture() {
      capture.stop();
    },
    configure(c: OmniConfig) {
      if (c.shortcuts) {
        shortcutRow.show(c.shortcuts);
        return;
      }
      cfg = { mode: 'auto', idleMs: 450, ...c, imageUrl: normalizeImageUrl(c.imageUrl) };
      if (cfg.detail) cfg.detail = { ...cfg.detail, imageUrl: normalizeImageUrl(cfg.detail.imageUrl) };
      idle.setIdleMs(cfg.idleMs!);
      tap.setIdleMs(cfg.idleMs!);
      const key = [
        cfg.imageUrl.length + ':' + cfg.imageUrl.slice(-64),
        cfg.html?.length ?? 0,
        (cfg.texts ?? []).join('\u0001'),
        cfg.title ?? '',
        cfg.detail ? cfg.detail.imageUrl.length + ':' + cfg.detail.imageUrl.slice(-64) + ':' + (cfg.detail.html?.length ?? 0) : '',
      ].join('|');
      const session = cfg.session ?? null;
      if (configured && st.key === key && st.session === session) {
        // la misma configuración otra vez en este documento (la app la manda al empezar a pintar y al terminar de cargar):
        // solo se refresca el armado; no se tocan los forzados ni los vigilantes en marcha
        if (cfg.mode === 'tap') tap.setArmed(cfg.armed !== false);
        return;
      }
      configured = true;
      forced = null;
      fichaForced = null;
      fichaExtras = [];
      win.clearInterval(sweepTimer);
      if (st.key !== key || st.session !== session) {
        // primera configuración de este documento (o una distinta): ¿lo que se guardó antes vale para esta?
        const prev = loadState();
        if (prev && prev.key === key && prev.session === session) {
          st = prev;
          if (st.used && cfg.mode === 'tap') {
            if (st.listKey) restoreList(st.listKey);
            // La ficha es OTRA página: ventana propia desde que carga ésta (la carga puede tardar más que lo que quedaba de la
            // ventana del toque). No se hace al volver a la lista (misma URL que la del toque).
            const now = Date.now();
            const since = now - ((st.fichaUntil ?? 0) - FICHA_WINDOW_MS);
            if (cfg.detail && !st.fichaDone && st.fichaUntil && since < NAV_GRACE_MS && stripHash(win.location.href) !== st.fromHref) {
              st.fichaUntil = Math.max(st.fichaUntil, now + FICHA_WINDOW_MS);
              saveState();
              watchFicha(null, st.fichaUntil);
            }
          }
        } else {
          st = { session, key, used: false };
          saveState();
        }
      }
      if (cfg.mode === 'tap') {
        idle.disarm();
        tap.setArmed(cfg.armed !== false);
      } else {
        tap.setArmed(false);
        idle.arm();
      }
    },
    trigger,
    /** Activación secreta (modo 'tap'): armar o desarmar el truco. */
    setArmed(on: boolean) {
      tap.setArmed(on);
    },
    get armed() {
      return tap.isArmed();
    },
    reset,
    disarm() {
      cfg = null;
      shortcutRow.stop();
      win.clearInterval(sweepTimer);
      win.clearInterval(fichaTimer);
      win.clearInterval(restoreTimer);
      idle.disarm();
      tap.setArmed(false);
    },
    get forced() {
      return !!forced || !!fichaForced;
    },
  };
  (win as unknown as { OmniWeb: typeof api }).OmniWeb = api;
}

install(window as Window & typeof globalThis);
