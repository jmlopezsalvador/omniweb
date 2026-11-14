import { beforeEach, describe, expect, it } from 'vitest';
import { applySnapshot, applyTexts, collectTexts, findTextContainer, imageAsDataUrl, mediaUrl, snapshotCard } from './apply';

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

/** Da caja a las hojas de texto (jsdom no calcula layout); `except` = ids/selectores que se dejan sin caja (ocultas). */
const boxLeaves = (except = '') =>
  document.querySelectorAll('body *').forEach((el) => {
    if (el.children.length === 0 && el.tagName !== 'IMG' && !(except && el.matches(except))) {
      (el as HTMLElement).getBoundingClientRect = () => ({ width: 60, height: 20, top: 0, left: 0, right: 60, bottom: 20, x: 0, y: 0 } as DOMRect);
    }
  });

describe('tarjeta con avatar y texto fuera de la miniatura (estilo YouTube)', () => {
  const rect = (el: Element, w: number, h: number) =>
    (el as HTMLElement).getBoundingClientRect = () => ({ width: w, height: h, top: 0, left: 0, right: w, bottom: h, x: 0, y: 0 } as DOMRect);

  it('incluye el texto de la fila inferior aunque haya un avatar pequeño', () => {
    document.body.innerHTML =
      '<div id="feed"><div class="card"><div class="thumb"><img class="t" src="t.jpg"><span>Título sobre la miniatura</span></div>' +
      '<div class="row"><img class="av" src="a.jpg"><span>Canal</span><span>hace 11 días</span></div></div>' +
      '<div class="card"><div class="thumb"><img class="t" src="u.jpg"><span>Otro</span></div></div></div>';
    boxLeaves();
    document.querySelectorAll('img.t').forEach((i) => rect(i, 400, 700));
    document.querySelectorAll('img.av').forEach((i) => rect(i, 36, 36));
    document.querySelectorAll('.card').forEach((i) => rect(i, 400, 800));
    document.querySelectorAll('#feed').forEach((i) => rect(i, 400, 1600));
    const img = document.querySelector('img.t')!;
    expect(collectTexts(img)).toEqual(['Título sobre la miniatura', 'Canal', 'hace 11 días']);
  });

  it('no sube a un contenedor mucho mayor que el medio', () => {
    document.body.innerHTML = '<div id="page"><div id="c"><img src="a.jpg"><p>Pie</p></div><p>Texto de toda la página</p></div>';
    boxLeaves();
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
    expect(r.targets[0].parentElement!.id).toBe('title');
    expect(document.getElementById('chip')!.textContent).toBe('46M views');
  });
});

describe('tarjeta de lista con imagen pequeña y texto al lado (estilo Amazon)', () => {
  const rect = (el: Element, w: number, h: number) =>
    ((el as HTMLElement).getBoundingClientRect = () => ({ width: w, height: h, top: 0, left: 0, right: w, bottom: h, x: 0, y: 0 } as DOMRect));

  it('sube hasta la fila completa aunque su área sea >5x la de la imagen', () => {
    document.body.innerHTML =
      '<div id="list"><div class="row"><div class="imgcol"><img class="p" src="p.jpg"></div>' +
      '<div class="txt"><h2>Auriculares X</h2><span>4,3</span><span>€</span><span>32</span></div></div>' +
      '<div class="row"><div class="imgcol"><img class="p" src="q.jpg"></div><div class="txt"><h2>Otros</h2></div></div></div>';
    boxLeaves();
    document.querySelectorAll('img.p').forEach((i) => rect(i, 144, 183));
    document.querySelectorAll('.imgcol').forEach((i) => rect(i, 168, 324));
    document.querySelectorAll('.row').forEach((i) => rect(i, 407, 324));
    rect(document.getElementById('list')!, 407, 700);
    expect(collectTexts(document.querySelector('img.p')!)).toEqual(['Auriculares X', '4,3', '€', '32']);
  });
});

describe('tarjeta profundamente anidada', () => {
  it('encuentra el texto a 12 niveles de la imagen', () => {
    let inner = '<img src="a.jpg">';
    for (let i = 0; i < 12; i++) inner = `<div>${inner}</div>`;
    document.body.innerHTML = `<div id="card">${inner}<p>Precio 29,99</p></div><div><img src="b.jpg"></div>`;
    expect(collectTexts(document.querySelector('img')!)).toEqual(['Precio 29,99']);
  });
});

describe('textos no visibles (estilo Amazon: precio accesible fuera de pantalla)', () => {
  const rect = (el: Element, w: number, h: number) =>
    ((el as HTMLElement).getBoundingClientRect = () => ({ width: w, height: h, top: 0, left: 0, right: w, bottom: h, x: 0, y: 0 } as DOMRect));

  it('ignora los textos recortados a 1px y deja el visible como título', () => {
    document.body.innerHTML =
      '<div id="card"><img src="p.jpg"><span id="off">€29.99</span><h2 id="t">Soundcore Anker P31i</h2><span id="e">€</span><span id="d">99</span></div>';
    boxLeaves('#off');
    rect(document.getElementById('off')!, 1, 1);
    rect(document.querySelector('img')!, 144, 183);
    rect(document.getElementById('card')!, 400, 250);
    (document.getElementById('t') as HTMLElement).style.fontSize = '18px';
    expect(collectTexts(document.querySelector('img')!)).toEqual(['Soundcore Anker P31i', '€', '99']);
  });
});

describe('título entre textos largos (precio con letra grande)', () => {
  it('el descuento grande no es el título: lo es el nombre del producto', () => {
    document.body.innerHTML = '<div><img src="a.jpg"><span id="d">-57%</span><span id="n">Soundcore Anker P31i Noise Cancelling</span><span id="m">4.6</span></div>';
    (document.getElementById('d') as HTMLElement).style.fontSize = '28px';
    (document.getElementById('n') as HTMLElement).style.fontSize = '16px';
    (document.getElementById('m') as HTMLElement).style.fontSize = '14px';
    expect(collectTexts(document.querySelector('img')!)).toEqual(['Soundcore Anker P31i Noise Cancelling', '-57%', '4.6']);
  });
});

describe('texto mezclado con etiquetas (precio de Amazon)', () => {
  const rect = (el: Element, w: number, h: number) =>
    ((el as HTMLElement).getBoundingClientRect = () => ({ width: w, height: h, top: 0, left: 0, right: w, bottom: h, x: 0, y: 0 } as DOMRect));

  it('captura y reemplaza cada fragmento, incluido el texto que comparte elemento con hijos', () => {
    document.body.innerHTML =
      '<div id="card"><img src="p.jpg"><h2>Auriculares Pro</h2><span id="p"><span>€</span>23<span>.</span>99</span></div>';
    rect(document.querySelector('img')!, 100, 100);
    rect(document.getElementById('card')!, 300, 200);
    document.querySelectorAll('h2,span').forEach((e) => rect(e, 50, 20));
    expect(collectTexts(document.querySelector('img')!)).toEqual(['Auriculares Pro', '€', '23', '.', '99']);
    applyTexts(document.querySelector('img')!, ['Predicción', '$', '10', ',', '50']);
    expect(document.getElementById('p')!.textContent).toBe('$10,50');
  });
});

describe('instantánea de la tarjeta', () => {
  const card = (n: number, extra = '') =>
    `<div class="c"><a href="/p/${n}" onclick="steal()" id="a${n}"><img src="https://x.test/${n}.jpg"></a><div><h2>Título ${n}</h2><span class="badge">Best Seller</span>${extra}<span>€${n}</span></div><script>alert(1)</script></div>`;

  beforeEach(() => {
    document.body.innerHTML = `<div id="grid">${card(1)}${card(2)}</div>`;
  });

  it('copia el interior de la tarjeta, limpio de scripts, on* e ids, con el medio marcado', () => {
    const html = snapshotCard(document.querySelector('img')!)!;
    expect(html).toContain('Título 1');
    expect(html).toContain('Best Seller');
    expect(html).toContain('data-omni-main');
    expect(html).not.toMatch(/script|onclick|id=/);
  });

  it('el efecto sustituye el interior de OTRA tarjeta por la instantánea con la imagen capturada', () => {
    const html = snapshotCard(document.querySelectorAll('img')[0])!;
    const target = document.querySelectorAll('img')[1];
    const r = applySnapshot(target, html, 'data:image/png;base64,AAAA')!;
    const c2 = document.querySelectorAll('.c')[1];
    expect(r.container).toBe(c2);
    expect(c2.textContent).toContain('Título 1');
    expect(c2.textContent).not.toContain('Título 2');
    expect(c2.querySelector('img')!.getAttribute('src')).toBe('data:image/png;base64,AAAA');
    expect(r.isApplied()).toBe(true);
    // la primera tarjeta no se toca
    expect(document.querySelectorAll('.c')[0].textContent).toContain('Título 1');
  });

  it('el aspecto de la tarjeta capturada se conserva aunque la de destino tenga otra estructura', () => {
    document.body.innerHTML = `<div id="grid">${card(1)}<div class="c"><a><img src="b.jpg"></a><p>Texto distinto</p><i>1</i><i>2</i><i>3</i><i>4</i></div></div>`;
    const html = snapshotCard(document.querySelectorAll('img')[0])!;
    applySnapshot(document.querySelectorAll('img')[1], html, 'data:image/png;base64,AAAA');
    const c2 = document.querySelectorAll('.c')[1];
    expect(c2.textContent).toContain('€1');
    expect(c2.textContent).not.toContain('Texto distinto');
  });

  it('no clona tarjetas con componentes personalizados definidos (se vaciarían al insertarse)', () => {
    if (!customElements.get('ytm-thing')) customElements.define('ytm-thing', class extends HTMLElement {});
    document.body.innerHTML = '<div id="grid"><div class="c"><img src="a.jpg"><ytm-thing>Hola</ytm-thing></div><div class="c"><img src="b.jpg"></div></div>';
    expect(snapshotCard(document.querySelector('img')!)).toBeNull();
  });
});

describe('elementos con guion no definidos (inertes)', () => {
  it('sí se clona una tarjeta con <w-debug-content> sin definir', () => {
    document.body.innerHTML = '<div id="g"><div class="c"><img src="a.jpg"><w-debug-content></w-debug-content><span>Hola</span></div><div class="c"><img src="b.jpg"></div></div>';
    expect(snapshotCard(document.querySelector('img')!)).toContain('Hola');
  });
});
