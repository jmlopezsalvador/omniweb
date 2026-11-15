import { expect, test, type Page } from '@playwright/test';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PAGE = pathToFileURL(resolve('dist/test/page.html')).href;
const BUNDLE = resolve('dist/omniweb.js');

/** Simula el puente nativo: guarda lo que el motor envía. */
async function open(page: Page, query = '') {
  await page.addInitScript(() => {
    (window as any).OmniBridge = { onCapture: (j: string) => ((window as any).__cap = JSON.parse(j)) };
  });
  await page.goto(PAGE + query);
  await page.addScriptTag({ path: BUNDLE });
}

/** Lleva la tarjeta n al centro de la pantalla y devuelve su posición central. */
async function centerCard(page: Page, n: number) {
  return page.evaluate((k) => {
    const c = document.querySelector(`.card[data-n="${k}"]`)!;
    const r = c.getBoundingClientRect();
    window.scrollTo(0, r.top + window.scrollY - 100);
    const r2 = c.querySelector('img,.bg')!.getBoundingClientRect();
    return { x: r2.left + r2.width / 2, y: r2.top + r2.height / 2 };
  }, n);
}

test('captura: el toque entrega imagen, textos y aspecto, y no navega ni activa nada', async ({ page }) => {
  await open(page);
  await page.evaluate(() => (window as any).OmniWeb.startCapture());
  const p = await centerCard(page, 4); // tarjeta de tipo picture
  await page.mouse.click(p.x, p.y);
  const cap = await page.evaluate(() => (window as any).__cap);
  expect(cap.texts).toEqual(['Título original 4', 'Fuente 4']);
  expect(cap.imageUrl).toContain('data:image/svg+xml');
  expect(cap.imageData).toContain('data:image/svg+xml');
  expect(cap.aspect).toBeGreaterThan(1);
  expect(cap.detailUrl).toMatch(/detail\.html\?n=4$/); // el enlace que envuelve la tarjeta
  expect(cap.pageUrl).toContain('page.html');
  // la tarjeta queda resaltada
  const outlined = await page.evaluate(() => document.querySelectorAll('[style*="outline"]').length);
  expect(outlined).toBe(1);
});

test('captura: también de una tarjeta con background-image', async ({ page }) => {
  await open(page);
  await page.evaluate(() => (window as any).OmniWeb.startCapture());
  const p = await centerCard(page, 3); // tipo bg
  await page.mouse.click(p.x, p.y);
  const cap = await page.evaluate(() => (window as any).__cap);
  expect(cap.texts[0]).toBe('Título original 3');
  expect(cap.imageUrl).toContain('data:image/svg+xml');
});

test('sin startCapture, el toque no captura', async ({ page }) => {
  await open(page);
  const p = await centerCard(page, 4);
  await page.mouse.click(p.x, p.y);
  expect(await page.evaluate(() => (window as any).__cap)).toBeUndefined();
});

test('efecto: sustituye imagen y TODOS los textos capturados en otra tarjeta', async ({ page }) => {
  await open(page);
  await page.evaluate(() =>
    (window as any).OmniWeb.configure({
      imageUrl: 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="red"/></svg>',
      texts: ['Predicción', 'Origen visible'],
      idleMs: 300,
    }),
  );
  for (let i = 1; i <= 8; i++) {
    await page.evaluate((v) => window.scrollTo(0, v), (900 * i) / 8);
    await page.waitForTimeout(30);
  }
  await expect.poll(() => page.evaluate(() => !!document.querySelector('[data-omni-forced]'))).toBe(true);
  const txt = await page.evaluate(() => {
    const el = document.querySelector('[data-omni-forced]')!;
    const c = el.closest('.card')!;
    return [c.querySelector('h3')!.textContent, c.querySelector('.src')!.textContent];
  });
  expect(txt).toEqual(['Predicción', 'Origen visible']);
});

test('armado: la web no ve pointerup/touchend (como YouTube); desarmado, sí', async ({ page }) => {
  await open(page, '?navonpointer=1');
  await page.evaluate(() => (window as any).OmniWeb.startCapture());
  const p = await centerCard(page, 4);
  await page.mouse.click(p.x, p.y);
  expect(await page.evaluate(() => (window as any).__cap)).toBeTruthy();
  expect(await page.evaluate(() => (window as any).__navigated)).toBeUndefined();
  // desarmado: la web recibe el toque con normalidad
  await page.evaluate(() => (window as any).OmniWeb.endCapture());
  await page.mouse.click(p.x, p.y);
  expect(await page.evaluate(() => (window as any).__navigated)).toBe('4');
});

test('efecto con instantánea: copia la tarjeta capturada (imagen y todos sus textos) en otra', async ({ page }) => {
  await open(page);
  await page.evaluate(() => (window as any).OmniWeb.startCapture());
  const p = await centerCard(page, 4);
  await page.mouse.click(p.x, p.y);
  const cap = await page.evaluate(() => (window as any).__cap);
  expect(cap.html).toContain('Título original 4');

  // «otra visita»: página nueva, mismo motor, configurado con la instantánea
  await page.goto(PAGE);
  await page.addScriptTag({ path: BUNDLE });
  await page.evaluate(
    (c) => (window as any).OmniWeb.configure({ imageUrl: 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="red"/></svg>', html: c.html, idleMs: 300 }),
    cap,
  );
  for (let i = 1; i <= 8; i++) {
    await page.evaluate((v) => window.scrollTo(0, v), (1500 * i) / 8);
    await page.waitForTimeout(30);
  }
  await expect.poll(() => page.evaluate(() => !!document.querySelector('[data-omni-forced]'))).toBe(true);
  const r = await page.evaluate(() => {
    const el = document.querySelector('[data-omni-forced]')!;
    const c = el.closest('.card')!;
    return { h3: c.querySelector('h3')!.textContent, src: c.querySelector('.src')!.textContent, img: (el as HTMLImageElement).getAttribute('src') ?? el.getAttribute('style') };
  });
  expect(r.h3).toBe('Título original 4');
  expect(r.src).toBe('Fuente 4');
  expect(r.img).toContain('fill=');
});

test('reportScroll: avisa de la dirección del scroll a la app', async ({ page }) => {
  await page.addInitScript(() => {
    (window as any).__dirs = [];
    (window as any).OmniBridge = { onCapture() {}, onScroll: (d: string) => (window as any).__dirs.push(d) };
  });
  await page.goto(PAGE);
  await page.addScriptTag({ path: BUNDLE });
  await page.evaluate(() => (window as any).OmniWeb.configure({ imageUrl: 'data:image/png;base64,AAAA', reportScroll: true, mode: 'manual' }));
  for (let i = 1; i <= 6; i++) {
    await page.evaluate((v) => window.scrollTo(0, v), i * 120);
    await page.waitForTimeout(30);
  }
  for (let i = 5; i >= 1; i--) {
    await page.evaluate((v) => window.scrollTo(0, v), i * 100);
    await page.waitForTimeout(30);
  }
  const dirs = await page.evaluate(() => (window as any).__dirs);
  expect(dirs).toEqual(['down', 'up']); // sin repetir mientras la dirección no cambia
});

test('sin reportScroll no se avisa', async ({ page }) => {
  await page.addInitScript(() => {
    (window as any).__dirs = [];
    (window as any).OmniBridge = { onCapture() {}, onScroll: (d: string) => (window as any).__dirs.push(d) };
  });
  await page.goto(PAGE);
  await page.addScriptTag({ path: BUNDLE });
  await page.evaluate(() => (window as any).OmniWeb.configure({ imageUrl: 'data:image/png;base64,AAAA', mode: 'manual' }));
  await page.evaluate(() => window.scrollTo(0, 600));
  await page.waitForTimeout(100);
  expect(await page.evaluate(() => (window as any).__dirs)).toEqual([]);
});
