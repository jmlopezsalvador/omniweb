// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { buildShortcuts, createShortcuts, isGoogleHome, SHORTCUTS_ID } from './home';

describe('atajos del inicio Google', () => {
  it('construye una entrada por sitio con icono o inicial', () => {
    const host = buildShortcuts(document, [{ id: 'a', name: 'Amazon', icon: 'data:image/png;base64,AA' }, { id: 'y', name: 'YouTube' }], () => {});
    expect(host.children.length).toBe(2);
    expect(host.querySelector('img')).not.toBeNull();
    expect(host.children[1].textContent).toContain('Y');
    expect(host.children[1].textContent).toContain('YouTube');
  });

  it('tocar un atajo lanza su id', () => {
    const launch = vi.fn();
    const host = buildShortcuts(document, [{ id: 'a', name: 'Amazon' }], launch);
    (host.firstElementChild as HTMLElement).click();
    expect(launch).toHaveBeenCalledWith('a');
  });

  it('reconoce el inicio y no los resultados', () => {
    expect(isGoogleHome({ location: { pathname: '/' } } as Window)).toBe(true);
    expect(isGoogleHome({ location: { pathname: '/search' } } as Window)).toBe(false);
  });

  it('se inserta tras el formulario del buscador y avisa a la app', () => {
    document.body.innerHTML = '<div id="w"><form><textarea name="q"></textarea></form><p id="after">x</p></div>';
    const onForced = vi.fn();
    (window as unknown as { OmniBridge: unknown }).OmniBridge = { onForced };
    const row = createShortcuts(window as Window & typeof globalThis);
    row.show([{ id: 'yt', name: 'YouTube' }]);
    const host = document.getElementById(SHORTCUTS_ID)!;
    expect(host.previousElementSibling?.tagName).toBe('FORM');
    (host.firstElementChild as HTMLElement).click();
    expect(onForced).toHaveBeenCalledWith('omni:launch:yt');
    row.stop();
    expect(document.getElementById(SHORTCUTS_ID)).toBeNull();
  });

  it('sin buscador (aún) va arriba del todo', () => {
    document.body.innerHTML = '<p>hola</p>';
    const row = createShortcuts(window as Window & typeof globalThis);
    row.show([{ id: 'yt', name: 'YouTube' }]);
    expect(document.body.firstElementChild?.id).toBe(SHORTCUTS_ID);
    row.stop();
  });
});
