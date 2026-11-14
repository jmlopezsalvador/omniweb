import { beforeEach, describe, expect, it } from 'vitest';
import { applyImage, applyTitle, FORCED_ATTR, normalizeImageUrl } from './apply';

describe('normalizeImageUrl', () => {
  it('codifica espacios, comas y comillas del cuerpo de un data: URL', () => {
    const u = normalizeImageUrl('data:image/svg+xml;utf8,<svg a="1, 2"> x</svg>');
    expect(u).toBe('data:image/svg+xml;utf8,<svg%20a=%221%2C%202%22>%20x</svg>');
    expect(u).not.toMatch(/[ "]/);
  });
  it('no toca URLs normales', () => {
    expect(normalizeImageUrl('https://a.com/x y.jpg')).toBe('https://a.com/x y.jpg');
  });
});

const NEW = 'data:image/png;base64,AAAA';

beforeEach(() => {
  document.body.innerHTML = '';
});

describe('applyImage', () => {
  it('sustituye src y elimina srcset/sizes/loading de un <img>', () => {
    document.body.innerHTML = '<img loading="lazy" src="a.jpg" srcset="a.jpg 1x, b.jpg 2x" sizes="100vw">';
    const img = document.querySelector('img')!;
    const ok = applyImage(img, NEW);
    expect(img.getAttribute('src')).toBe(NEW);
    expect(img.hasAttribute('srcset')).toBe(false);
    expect(img.hasAttribute('sizes')).toBe(false);
    expect(img.hasAttribute('loading')).toBe(false);
    expect(img.hasAttribute(FORCED_ATTR)).toBe(true);
    expect(ok()).toBe(true);
  });

  it('reescribe los <source> de un <picture>', () => {
    document.body.innerHTML = '<picture><source srcset="a.webp" sizes="50vw"><img src="a.jpg"></picture>';
    applyImage(document.querySelector('img')!, NEW);
    const s = document.querySelector('source')!;
    expect(s.getAttribute('srcset')).toBe(NEW);
    expect(s.hasAttribute('sizes')).toBe(false);
  });

  it('isApplied detecta que el framework restauró el src', () => {
    document.body.innerHTML = '<img src="a.jpg">';
    const img = document.querySelector('img')!;
    const ok = applyImage(img, NEW);
    img.setAttribute('src', 'a.jpg');
    expect(ok()).toBe(false);
  });

  it('pone el poster de un <video>', () => {
    document.body.innerHTML = '<video poster="p.jpg"></video>';
    const v = document.querySelector('video')!;
    const ok = applyImage(v, NEW);
    expect(v.getAttribute('poster')).toBe(NEW);
    expect(ok()).toBe(true);
  });

  it('sustituye background-image con !important', () => {
    document.body.innerHTML = '<div style="background-image:url(a.jpg)"></div>';
    const d = document.querySelector('div')!;
    const ok = applyImage(d, NEW);
    expect(d.style.getPropertyValue('background-image')).toContain(NEW);
    expect(d.style.getPropertyPriority('background-image')).toBe('important');
    expect(ok()).toBe(true);
  });
});

describe('applyTitle', () => {
  it('cambia el título de la tarjeta y el alt', () => {
    document.body.innerHTML = '<div><img src="a.jpg" alt="viejo"><h3>Original</h3></div>';
    const card = document.querySelector('div')!;
    expect(applyTitle(card, 'Nuevo')).toBe(true);
    expect(card.querySelector('h3')!.textContent).toBe('Nuevo');
    expect(card.querySelector('img')!.getAttribute('alt')).toBe('Nuevo');
  });

  it('devuelve false si no hay título', () => {
    document.body.innerHTML = '<div><img src="a.jpg"></div>';
    expect(applyTitle(document.querySelector('div')!, 'Nuevo')).toBe(false);
  });
});
