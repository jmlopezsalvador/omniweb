import { applyImage, applyTitle, FORCED_ATTR, normalizeImageUrl } from './apply';
import { findCandidate } from './candidate';
import { createIdleDetector } from './idle';

export interface OmniConfig {
  imageUrl: string;
  title?: string;
  idleMs?: number;
  /** 'auto': al parar el scroll. 'manual': solo con OmniWeb.trigger(). */
  mode?: 'auto' | 'manual';
}

interface Forced {
  media: Element;
  card: Element;
  isApplied: () => boolean;
  title?: string;
  observer: MutationObserver;
}

function install(win: Window & typeof globalThis) {
  const doc = win.document;
  let cfg: OmniConfig | null = null;
  let forced: Forced | null = null;
  let guard = false; // evita bucles con nuestro propio MutationObserver

  const idle = createIdleDetector(win, { idleMs: 450, onIdle: () => cfg?.mode !== 'manual' && trigger() });

  function reapply() {
    if (!forced || !cfg || guard) return;
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
      if (forced.title) applyTitle(forced.card, forced.title);
      setTimeout(() => (guard = false), 0);
    }
  }

  function trigger(): boolean {
    if (!cfg || forced) return false;
    const c = findCandidate(doc, win.innerWidth / 2, win.innerHeight / 2);
    if (!c) return false;
    guard = true;
    const isApplied = applyImage(c.media, cfg.imageUrl);
    if (cfg.title) applyTitle(c.card, cfg.title);
    const observer = new MutationObserver(() => reapply());
    observer.observe(c.card, { attributes: true, childList: true, subtree: true, characterData: true });
    forced = { media: c.media, card: c.card, isApplied, title: cfg.title, observer };
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

  const api = {
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
