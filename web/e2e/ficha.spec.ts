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
  page.evaluate(() => document.querySelector('.card [data-omni-forced]')?.closest('.card')?.getAttribute('data-n') ?? null);
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
