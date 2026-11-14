import { beforeEach, describe, expect, it } from 'vitest';
import { applyTexts, collectTexts, findTextContainer, imageAsDataUrl, mediaUrl } from './apply';

const card = (n: number) =>
  `<div class="c"><a><img src="https://x.test/${n}.jpg" alt="a${n}"></a><div><div>Título ${n}</div><span>Fuente ${n}</span><button>Guardar</button></div></div>`;

beforeEach(() => {
  document.body.innerHTML = `<div id="grid">${card(1)}${card(2)}</div>`;
});

describe('textos de la tarjeta', () => {
  it('collectTexts devuelve los textos en orden y sin botones', () => {
    expect(collectTexts(document.querySelector('img')!)).toEqual(['Título 1', 'Fuente 1']);
  });

  it('applyTexts sustituye por posición y solo en su tarjeta', () => {
    const img = document.querySelector('img')!;
    const r = applyTexts(img, ['Predicción', 'Origen'])!;
    expect(r.targets.map((t) => t.textContent)).toEqual(['Predicción', 'Origen']);
    expect(document.querySelectorAll('.c')[1].textContent).toContain('Título 2');
    expect(img.getAttribute('alt')).toBe('Predicción');
  });

  it('si hay menos textos que huecos, deja el resto', () => {
    const img = document.querySelector('img')!;
    applyTexts(img, ['Solo título']);
    expect(document.querySelector('.c')!.textContent).toContain('Fuente 1');
  });

  it('no sube hasta la rejilla (varias imágenes)', () => {
    document.body.innerHTML = '<div><img src="a.jpg"><img src="b.jpg"></div>';
    expect(findTextContainer(document.querySelector('img')!)).toBeNull();
  });

  it('capturar y aplicar usan la misma estructura', () => {
    const img = document.querySelector('img')!;
    const captured = collectTexts(img);
    document.body.innerHTML = `<div id="grid">${card(7)}${card(8)}</div>`;
    const other = document.querySelector('img')!;
    const r = applyTexts(other, captured)!;
    expect(r.targets.map((t) => t.textContent)).toEqual(captured);
  });
});

describe('mediaUrl / imageAsDataUrl', () => {
  it('mediaUrl resuelve urls relativas y respeta data:', () => {
    document.body.innerHTML = '<img src="/x.jpg"><img src="data:image/png;base64,AAAA">';
    const [a, b] = Array.from(document.querySelectorAll('img'));
    expect(mediaUrl(a)).toMatch(/^https?:\/\/.*\/x\.jpg$|^about:|\/x\.jpg$/);
    expect(mediaUrl(b)).toBe('data:image/png;base64,AAAA');
  });

  it('imageAsDataUrl devuelve el propio data: URL', () => {
    document.body.innerHTML = '<img src="data:image/png;base64,AAAA">';
    expect(imageAsDataUrl(document.querySelector('img')!)).toBe('data:image/png;base64,AAAA');
  });

  it('imageAsDataUrl devuelve null si la imagen no está cargada', () => {
    document.body.innerHTML = '<img src="https://x.test/a.jpg">';
    expect(imageAsDataUrl(document.querySelector('img')!)).toBeNull();
  });
});
