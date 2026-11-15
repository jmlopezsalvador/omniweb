import { expect, test, type Page } from '@playwright/test';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PAGE = pathToFileURL(resolve('dist/test/page.html')).href;
const BUNDLE = resolve('dist/omniweb.js');
const RED = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="red"/></svg>';
const BLUE = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="blue"/></svg>';

const baseCfg = {
  imageUrl: RED,
  texts: ['Lista forzada', 'Fuente lista'],
  mode: 'tap',
  idleMs: 300,
  detail: { imageUrl: BLUE, texts: ['Ficha forzada', 'Fuente ficha'] },
};

/** Abre la página con el motor ya configurado (en CADA carga, como hace la app). */
async function open(page: Page, query: string, cfg: object = {}) {
  await page.addInitScript({ path: BUNDLE });
  await page.addInitScript((c) => {
    document.addEventListener('DOMContentLoaded', () => (window as any).OmniWeb.configure(c));
  }, { ...baseCfg, ...cfg });
  await page.goto(PAGE + query);
}

async function showCard(page: Page, n: number) {
  return page.evaluate((k) => {
    const c = document.querySelector(`.card[data-n="${k}"]`)!;
    window.scrollTo(0, c.getBoundingClientRect().top + window.scrollY - 100);
    const r = c.querySelector('img,.bg')!.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  }, n);
}

const forcedCard = (page: Page) =>
  page.evaluate(() => document.querySelector('.card [data-omni-forced]')?.closest('.card[data-n]')?.getAttribute('data-n') ?? null);
const heroBlue = (page: Page) => page.evaluate(() => (document.querySelector<HTMLImageElement>('#hero')?.getAttribute('src') ?? '').includes('blue'));

test('panel (misma página): el toque llega a la web, la ficha sale con lo capturado y la lista queda sustituida detrás', async ({ page }) => {
  await open(page, '?panel=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  // la web abrió SU panel (el toque no se silenció)
  await expect(page.locator('#panel')).toBeVisible();
  expect(await page.evaluate(() => (window as any).__linkClicked)).toBe('detail.html?n=5');
  // el motor sustituye el medio principal de la ficha y sus textos
  await expect.poll(() => heroBlue(page)).toBe(true);
  await expect(page.locator('#ptitle')).toHaveText('Ficha forzada');
  // y la tarjeta de la lista, por detrás
  await expect.poll(() => forcedCard(page)).toBe('5');
  expect(await page.evaluate(() => document.querySelector('.card[data-n="5"] h3')!.textContent)).toBe('Lista forzada');
  // las miniaturas de «relacionados» no se tocan
  expect(await page.evaluate(() => document.querySelector('#panel img.rel')!.hasAttribute('data-omni-forced'))).toBe(false);
});

test('panel: cerrar la ficha deja la lista sustituida y un segundo toque NO vuelve a forzar nada', async ({ page }) => {
  await open(page, '?panel=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await expect.poll(() => heroBlue(page)).toBe(true);
  await page.click('#close');
  await expect(page.locator('#panel')).toHaveCount(0);
  expect(await forcedCard(page)).toBe('5'); // «al volver, el elemento está sustituido»
  const q = await showCard(page, 7);
  await page.waitForTimeout(600);
  await page.mouse.click(q.x, q.y);
  await expect(page.locator('#panel')).toBeVisible(); // la web abre su ficha de verdad (otra)…
  await page.waitForTimeout(900);
  expect(await heroBlue(page)).toBe(false); // …pero ya no se fuerza
  expect(await forcedCard(page)).toBe('5');
});

test('con la lista aún deslizándose el toque no cuenta (pasa a la web y no arma la ficha)', async ({ page }) => {
  await open(page, '?panel=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(100);
  await page.mouse.click(p.x, p.y);
  await expect(page.locator('#panel')).toBeVisible();
  await page.waitForTimeout(900);
  expect(await heroBlue(page)).toBe(false);
  expect(await forcedCard(page)).toBeNull();
});

test('otra página: la ficha cargada tras navegar sale forzada y al volver la lista sigue sustituida', async ({ page }) => {
  await open(page, '?realnav=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await page.waitForURL(/detail\.html\?n=5/);
  await expect.poll(() => heroBlue(page), { timeout: 5000 }).toBe(true);
  await expect(page.locator('#m')).toContainText('Ficha forzada');
  await page.goBack();
  await page.waitForURL(/page\.html/);
  await expect.poll(() => forcedCard(page), { timeout: 8000 }).toBe('5');
  // la lista restaurada tampoco permite forzar otra tarjeta
  const q = await showCard(page, 8);
  await page.waitForTimeout(600);
  await page.mouse.click(q.x, q.y);
  await page.waitForURL(/detail\.html\?n=8/);
  await page.waitForTimeout(900);
  expect(await heroBlue(page)).toBe(false);
});

test('otra sesión (Reiniciar) olvida lo forzado y vuelve a armar', async ({ page }) => {
  await open(page, '?panel=1', { session: 1 });
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await expect.poll(() => heroBlue(page)).toBe(true);
  await page.click('#close');
  await page.evaluate((c) => (window as any).OmniWeb.configure(c), { ...baseCfg, session: 2 });
  const q = await showCard(page, 7);
  await page.waitForTimeout(600);
  await page.mouse.click(q.x, q.y);
  await expect.poll(() => heroBlue(page)).toBe(true);
  await expect.poll(() => forcedCard(page)).not.toBeNull();
});

test('sin ficha capturada el toque se silencia y solo se sustituye la lista (comportamiento clásico)', async ({ page }) => {
  await open(page, '?panel=1', { detail: undefined });
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  expect(await forcedCard(page)).toBe('5');
  await expect(page.locator('#panel')).toHaveCount(0); // la web no vio el toque
});

test('panel que ya existe oculto y se reutiliza: también se sustituye la ficha', async ({ page }) => {
  await open(page, '?panel=1&reuse=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await expect(page.locator('#panel')).toBeVisible();
  await expect.poll(() => heroBlue(page)).toBe(true);
  await expect(page.locator('#ptitle')).toHaveText('Ficha forzada');
  await expect.poll(() => forcedCard(page)).toBe('5');
});

test('una capa transparente de la web sobre la imagen de la ficha no impide sustituirla', async ({ page }) => {
  await open(page, '?panel=1&overlay=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await expect(page.locator('#panel')).toBeVisible();
  await expect.poll(() => heroBlue(page)).toBe(true);
});

const forcedHref = (page: Page) =>
  page.evaluate(() => document.querySelector('.card [data-omni-forced]')?.closest('.card[data-n]')?.querySelector('a')?.getAttribute('href') ?? null);

test('si la web recoloca la rejilla al apoyar el dedo, se sustituye la tarjeta TOCADA y no la que queda debajo al soltar', async ({ page }) => {
  await open(page, '?panel=1&shift=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await expect.poll(() => forcedCard(page)).not.toBeNull();
  expect(await forcedCard(page)).toBe('5');
});

test('si la web recicla la tarjeta al abrir la ficha, se sustituye la tarjeta tocada (por su identidad), no la que ocupa su sitio', async ({ page }) => {
  await open(page, '?panel=1&recycle=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await expect.poll(() => forcedHref(page)).not.toBeNull();
  expect(await forcedHref(page)).toBe('detail.html?n=5');
});

// Instantánea HTML de una tarjeta (la que guarda la app al preparar): el enlace es el del elemento CAPTURADO (n=999).
const SNAP = '<a href="detail.html?n=999"><img data-omni-main="1" src="x"></a><h3>t</h3><div class="src">f</div>';
const backBlue = (page: Page) => page.evaluate(() => (document.querySelector<HTMLImageElement>('#back')?.getAttribute('src') ?? '').includes('blue'));

test('ficha apilada (marcador grande detrás y la imagen real llega tarde encima): se sustituyen las dos', async ({ page }) => {
  await open(page, '?panel=1&stack=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await expect(page.locator('#panel')).toBeVisible();
  await expect.poll(() => heroBlue(page), { timeout: 4000 }).toBe(true); // la imagen real, la que se VE
  expect(await backBlue(page)).toBe(true);
});

test('la tarjeta sustituida con instantánea sigue abriendo la ficha de la web, y esa ficha sale forzada otra vez', async ({ page }) => {
  await open(page, '?panel=1', { html: SNAP, texts: undefined });
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await expect.poll(() => heroBlue(page)).toBe(true);
  await page.click('#close');
  await expect(page.locator('#panel')).toHaveCount(0);
  await expect.poll(() => forcedCard(page)).toBe('5');
  // lo que se ve es lo forzado…
  expect(await page.evaluate(() => document.querySelector('.card[data-n="5"] [data-omni-overlay] h3, .card[data-n="5"] h3')?.textContent)).toBeTruthy();
  // …y al tocarla la web abre SU ficha (el enlace original no se ha perdido)
  await page.evaluate(() => ((window as any).__linkClicked = null));
  const q = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(q.x, q.y);
  await expect(page.locator('#panel')).toBeVisible();
  expect(await page.evaluate(() => (window as any).__linkClicked)).toBe('detail.html?n=5');
  await expect.poll(() => heroBlue(page)).toBe(true); // coherente con lo que se ve en la lista
});

test('panel reutilizado: al cerrar la ficha se suelta y otra ficha distinta ya no se fuerza', async ({ page }) => {
  await open(page, '?panel=1&reuse=1');
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  await expect.poll(() => heroBlue(page)).toBe(true);
  await page.click('#close');
  await page.waitForTimeout(1500);
  const q = await showCard(page, 7);
  await page.waitForTimeout(600);
  await page.mouse.click(q.x, q.y);
  await expect(page.locator('#panel')).toBeVisible();
  await page.waitForTimeout(1200);
  expect(await heroBlue(page)).toBe(false);
});

test('tarjetas muy anidadas (Amazon: la imagen está ~14 niveles por debajo de la tarjeta de la lista) también se reconocen', async ({ page }) => {
  await open(page, '?panel=1&deep=1', { detail: undefined });
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  expect(await forcedCard(page)).toBe('5');
});

test('un logo suelto entre bloques de la página no cuenta como lista aunque haya varios bloques con imágenes', async ({ page }) => {
  await open(page, '?panel=1');
  await page.evaluate(() => {
    document.body.insertAdjacentHTML(
      'afterbegin',
      '<header style="height:300px"><div class="blk" style="height:300px"><img id="hdr" alt="x" style="width:120px;height:120px" src="data:image/svg+xml;utf8,<svg xmlns=%27http://www.w3.org/2000/svg%27 width=%2710%27 height=%2710%27/>"></div></header>' +
        '<section style="height:900px"><div class="blk" style="height:900px"><img alt="y" style="width:100px;height:100px" src="data:image/svg+xml;utf8,<svg xmlns=%27http://www.w3.org/2000/svg%27 width=%2710%27 height=%2710%27/>"></div></section>',
    );
    window.scrollTo(0, 0);
  });
  await page.waitForTimeout(600);
  const r = await page.evaluate(() => document.querySelector('#hdr')!.getBoundingClientRect().toJSON());
  await page.mouse.click(r.x + r.width / 2, r.y + r.height / 2);
  expect(await forcedCard(page)).toBeNull();
});
