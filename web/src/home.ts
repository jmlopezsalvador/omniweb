/**
 * Atajos del «Inicio Google» de OmniWeb: sobre la web real de Google (página de inicio) se inserta, bajo el buscador, una fila
 * con los sitios de la app (donde el navegador móvil pone sus accesos directos). Tocar uno avisa a la app (`OmniBridge.onForced`
 * con `omni:launch:<id>`), que lanza el efecto de ese sitio. No se modifica nada más de la página.
 */
export interface Shortcut {
  id: string;
  name: string;
  /** data: URL del icono del sitio; si falta se pinta la inicial. */
  icon?: string;
}

export const SHORTCUTS_ID = 'omniweb-shortcuts';
export const LAUNCH_PREFIX = 'omni:launch:';

const COLORS = ['#6c5ce7', '#00a383', '#e17055', '#0984e3', '#d6336c', '#b7791f'];
const FONT = "font-family:'Google Sans',Roboto,Arial,sans-serif;";

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

/** ¿Es la página de inicio (no resultados, no otra web)? */
export function isGoogleHome(win: Window): boolean {
  const p = win.location.pathname;
  return p === '/' || p === '' || p === '/webhp';
}

export function buildShortcuts(doc: Document, list: Shortcut[], launch: (id: string) => void): HTMLElement {
  const host = doc.createElement('div');
  host.id = SHORTCUTS_ID;
  host.setAttribute(
    'style',
    `${FONT}box-sizing:border-box;display:flex;gap:2px;overflow-x:auto;margin:20px 12px 8px;padding:14px 6px 12px;` +
      'background:#fff;border-radius:24px;-webkit-overflow-scrolling:touch;text-align:center;',
  );
  for (const s of list) {
    const item = doc.createElement('div');
    item.setAttribute('data-omni-site', s.id);
    item.setAttribute('style', 'flex:0 0 76px;width:76px;cursor:pointer;-webkit-tap-highlight-color:transparent;');
    const circle = doc.createElement('div');
    circle.setAttribute(
      'style',
      'width:52px;height:52px;margin:0 auto;border-radius:50%;background:#f1f3f4;display:flex;align-items:center;justify-content:center;overflow:hidden;',
    );
    if (s.icon) {
      const img = doc.createElement('img');
      img.src = s.icon;
      img.alt = '';
      img.setAttribute('style', 'width:30px;height:30px;object-fit:contain;');
      circle.appendChild(img);
    } else {
      const ini = doc.createElement('div');
      ini.textContent = (s.name.trim()[0] ?? '?').toUpperCase();
      ini.setAttribute(
        'style',
        `${FONT}width:34px;height:34px;border-radius:50%;color:#fff;font-size:18px;font-weight:600;line-height:34px;background:${COLORS[hash(s.id) % COLORS.length]};`,
      );
      circle.appendChild(ini);
    }
    const label = doc.createElement('div');
    label.textContent = s.name;
    label.setAttribute(
      'style',
      `${FONT}margin-top:8px;padding:0 3px;font-size:12px;line-height:15px;color:#3c4043;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;`,
    );
    item.append(circle, label);
    item.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      launch(s.id);
    });
    host.appendChild(item);
  }
  return host;
}

/** Mantiene la fila de atajos bajo el buscador mientras la página sea el inicio (Google re-pinta y navega sin recargar). */
export function createShortcuts(win: Window & typeof globalThis) {
  let list: Shortcut[] = [];
  let host: HTMLElement | null = null;
  let afterForm = false;
  let timer = 0;

  const launch = (id: string) => {
    (win as unknown as { OmniBridge?: { onForced?(u: string): void } }).OmniBridge?.onForced?.(LAUNCH_PREFIX + id);
  };

  function place() {
    const doc = win.document;
    if (!isGoogleHome(win)) {
      host?.remove();
      host = null;
      return;
    }
    const q = doc.querySelector('textarea[name="q"], input[name="q"]');
    const anchor = (q?.closest('form') ?? null) as HTMLElement | null;
    if (host?.isConnected && (afterForm || !anchor)) return;
    if (!host) host = buildShortcuts(doc, list, launch);
    if (anchor?.parentElement) {
      anchor.insertAdjacentElement('afterend', host);
      afterForm = true;
    } else if (doc.body && !host.isConnected) {
      doc.body.insertBefore(host, doc.body.firstChild);
      afterForm = false;
    }
  }

  return {
    show(l: Shortcut[]) {
      list = l;
      host?.remove();
      host = null;
      win.clearInterval(timer);
      place();
      timer = win.setInterval(place, 600);
    },
    stop() {
      win.clearInterval(timer);
      host?.remove();
      host = null;
    },
  };
}
