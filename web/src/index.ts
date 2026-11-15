import { applyImage, applySnapshot, applySnapshotAt, applyTexts, applyTitleNear, FORCED_ATTR, normalizeImageUrl } from './apply';
import { createCaptureMode } from './capture';
import { findCandidate } from './candidate';
import { createIdleDetector } from './idle';

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
  /** 'auto': al parar el scroll. 'manual': solo con OmniWeb.trigger(). */
  mode?: 'auto' | 'manual';
}

interface Forced {
  media: Element;
  card: Element;
  isApplied: () => boolean;
  texts?: string[];
  textEls?: Node[];
  html?: string;
  observer: MutationObserver;
}

function install(win: Window & typeof globalThis) {
  // El host puede inyectar el bundle varias veces (onPageStarted + onPageFinished): una sola instancia.
  if ((win as unknown as { OmniWeb?: unknown }).OmniWeb) return;
  const doc = win.document;
  let cfg: OmniConfig | null = null;
  let forced: Forced | null = null;
  let guard = false; // evita bucles con nuestro propio MutationObserver

  const idle = createIdleDetector(win, { idleMs: 450, onIdle: () => cfg?.mode !== 'manual' && trigger() });

  function reapply() {
    if (!forced || !cfg || guard) return;
    if (forced.html && forced.card.isConnected && !forced.card.contains(forced.media)) {
      // el framework repintó el interior de la tarjeta: volvemos a poner la instantánea
      guard = true;
      const s = applySnapshotAt(forced.card, forced.html, cfg.imageUrl);
      if (s) {
        forced.media = s.main;
        forced.isApplied = s.isApplied;
      }
      setTimeout(() => (guard = false), 0);
      return;
    }
    if (!forced.media.isConnected) {
      // el framework ha repintado: buscamos de nuevo el elemento más centrado
      forced.observer.disconnect();
      forced = null;
      trigger();
      return;
    }
    if (!forced.isApplied()) {
      guard = true;
      forced.isApplied = applyImage(forced.media, cfg.imageUrl);
      setTimeout(() => (guard = false), 0);
    }
    if (forced.texts && forced.textEls) {
      const texts = forced.texts;
      const stale = forced.textEls.some((el, i) => el.textContent !== texts[i]);
      if (stale) {
        guard = true;
        forced.textEls.forEach((el, i) => (el.textContent = texts[i]));
        setTimeout(() => (guard = false), 0);
      }
    }
  }

  function trigger(): boolean {
    if (!cfg || forced) return false;
    const c = findCandidate(doc, win.innerWidth / 2, win.innerHeight / 2);
    if (!c) {
      console.log('[OmniWeb] sin candidato en el centro');
      return false;
    }
    guard = true;
    if (cfg.html) {
      const snap = applySnapshot(c.media, cfg.html, cfg.imageUrl);
      if (snap) {
        const observer = new MutationObserver(() => reapply());
        observer.observe(snap.container, { attributes: true, childList: true, subtree: true, characterData: true });
        forced = { media: snap.main, card: snap.container, isApplied: snap.isApplied, html: cfg.html, observer };
        console.log('[OmniWeb] forzado (instantánea)', snap.main.tagName, Math.round(snap.container.getBoundingClientRect().height) + 'px');
        setTimeout(() => (guard = false), 0);
        return true;
      }
    }
    const isApplied = applyImage(c.media, cfg.imageUrl);
    let root: Element = c.card;
    let texts: string[] | undefined;
    let textEls: Node[] | undefined;
    if (cfg.texts?.length) {
      const t = applyTexts(c.media, cfg.texts);
      if (t) {
        root = t.container;
        texts = cfg.texts;
        textEls = t.targets;
      }
    } else if (cfg.title) {
      const t = applyTitleNear(c.media, cfg.title);
      if (t) {
        root = t.container;
        texts = [cfg.title];
        textEls = [t.target];
      }
    }
    const observer = new MutationObserver(() => reapply());
    observer.observe(root, { attributes: true, childList: true, subtree: true, characterData: true });
    forced = { media: c.media, card: root, isApplied, texts, textEls, observer };
    console.log('[OmniWeb] forzado', c.media.tagName, (c.media as HTMLElement).className || '', 'en tarjeta', c.card.tagName, Math.round(c.card.getBoundingClientRect().height) + 'px');
    setTimeout(() => (guard = false), 0);
    // si el padre desaparece (re-render), observamos el documento para volver a colocar
    new MutationObserver((_, self) => {
      if (!forced) return self.disconnect();
      if (!forced.media.isConnected) reapply();
    }).observe(doc.body, { childList: true, subtree: true });
    return true;
  }

  function reset() {
    forced?.observer.disconnect();
    forced = null;
    doc.querySelectorAll(`[${FORCED_ATTR}]`).forEach((e) => e.removeAttribute(FORCED_ATTR));
    idle.arm();
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
      cfg = { mode: 'auto', idleMs: 450, ...c, imageUrl: normalizeImageUrl(c.imageUrl) };
      idle.setIdleMs(cfg.idleMs!);
      forced = null;
      idle.arm();
    },
    trigger,
    reset,
    disarm() {
      cfg = null;
      idle.disarm();
    },
    get forced() {
      return !!forced;
    },
  };
  (win as unknown as { OmniWeb: typeof api }).OmniWeb = api;
}

install(window as Window & typeof globalThis);
