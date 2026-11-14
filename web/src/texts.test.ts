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

describe('tarjeta con avatar y texto fuera de la miniatura (estilo YouTube)', () => {
  const rect = (el: Element, w: number, h: number) =>
    (el as HTMLElement).getBoundingClientRect = () => ({ width: w, height: h, top: 0, left: 0, right: w, bottom: h, x: 0, y: 0 } as DOMRect);

  it('incluye el texto de la fila inferior aunque haya un avatar pequeño', () => {
    document.body.innerHTML =
      '<div id="feed"><div class="card"><div class="thumb"><img class="t" src="t.jpg"><span>Título sobre la miniatura</span></div>' +
      '<div class="row"><img class="av" src="a.jpg"><span>Canal</span><span>hace 11 días</span></div></div>' +
      '<div class="card"><div class="thumb"><img class="t" src="u.jpg"><span>Otro</span></div></div></div>';
    document.querySelectorAll('img.t').forEach((i) => rect(i, 400, 700));
    document.querySelectorAll('img.av').forEach((i) => rect(i, 36, 36));
    document.querySelectorAll('.card').forEach((i) => rect(i, 400, 800));
    document.querySelectorAll('#feed').forEach((i) => rect(i, 400, 1600));
    const img = document.querySelector('img.t')!;
    expect(collectTexts(img)).toEqual(['Título sobre la miniatura', 'Canal', 'hace 11 días']);
  });

  it('no sube a un contenedor mucho mayor que el medio', () => {
    document.body.innerHTML = '<div id="page"><div id="c"><img src="a.jpg"><p>Pie</p></div><p>Texto de toda la página</p></div>';
    rect(document.querySelector('img')!, 100, 100);
    rect(document.querySelector('#c')!, 100, 150);
    rect(document.querySelector('#page')!, 100, 2000);
    expect(collectTexts(document.querySelector('img')!)).toEqual(['Pie']);
  });
});

describe('el título va al hueco del título aunque cambie la plantilla', () => {
  const fs = (el: Element, px: string) => ((el as HTMLElement).style.fontSize = px);

  it('captura primero el texto de mayor tamaño', () => {
    document.body.innerHTML = '<div><img src="a.jpg"><span id="v">46M views</span><span id="t">Título</span><span id="c">Canal</span></div>';
    fs(document.getElementById('v')!, '12px');
    fs(document.getElementById('t')!, '20px');
    fs(document.getElementById('c')!, '12px');
    expect(collectTexts(document.querySelector('img')!)).toEqual(['Título', '46M views', 'Canal']);
  });

  it('en otra plantilla el título captura cae en el hueco grande', () => {
    document.body.innerHTML =
      '<div><img src="a.jpg"><span id="chip">4K</span><span id="views">1M views</span><span id="title">Otro título largo</span><span id="ch">Otro canal</span></div>';
    fs(document.getElementById('chip')!, '10px');
    fs(document.getElementById('views')!, '12px');
    fs(document.getElementById('title')!, '20px');
    fs(document.getElementById('ch')!, '12px');
    const r = applyTexts(document.querySelector('img')!, ['Predicción', '46M views', 'Miautuber'])!;
    expect(document.getElementById('title')!.textContent).toBe('Predicción');
    expect(r.targets[0].id).toBe('title');
    expect(document.getElementById('chip')!.textContent).toBe('46M views');
  });
});
