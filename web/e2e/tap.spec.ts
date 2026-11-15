import { expect, test, type Page } from '@playwright/test';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PAGE = pathToFileURL(resolve('dist/test/page.html')).href;
const BUNDLE = resolve('dist/omniweb.js');
const RED = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="red"/></svg>';

async function open(page: Page, cfg: object = {}, query = '?navonpointer=1') {
  await page.goto(PAGE + query);
  await page.addScriptTag({ path: BUNDLE });
  await page.evaluate((c) => (window as any).OmniWeb.configure({ imageUrl: c.img, texts: ['Predicción', 'Origen'], mode: 'tap', idleMs: 300, ...c.cfg }), { img: RED, cfg });
}

/** Lleva la tarjeta n al centro y devuelve el centro de su imagen. */
async function showCard(page: Page, n: number) {
  return page.evaluate((k) => {
    const c = document.querySelector(`.card[data-n="${k}"]`)!;
    window.scrollTo(0, c.getBoundingClientRect().top + window.scrollY - 100);
    const r = c.querySelector('img,.bg')!.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  }, n);
}

const forcedCard = (page: Page) =>
  page.evaluate(() => document.querySelector('[data-omni-forced]')?.closest('.card')?.getAttribute('data-n') ?? null);
const navigated = (page: Page) => page.evaluate(() => (window as any).__navigated ?? null);

test('armado y en reposo: el toque en una tarjeta la sustituye y NO abre la real', async ({ page }) => {
  await open(page);
  const p = await showCard(page, 5);
  await page.waitForTimeout(600); // reposo (>idleMs tras el scroll)
  await page.mouse.click(p.x, p.y);
  expect(await forcedCard(page)).toBe('5');
  expect(await navigated(page)).toBeNull(); // la página no vio el pointerup/touchend
  const txt = await page.evaluate(() => document.querySelector('.card[data-n="5"] h3')!.textContent);
  expect(txt).toBe('Predicción');
});

test('no dispara mientras se desliza: el toque que frena la inercia pasa a la web', async ({ page }) => {
  await open(page);
  const p = await showCard(page, 5);
  await page.waitForTimeout(100); // aún «deslizando» (< idleMs)
  await page.mouse.click(p.x, p.y);
  expect(await forcedCard(page)).toBeNull();
  expect(await navigated(page)).toBe('5'); // la web recibió el toque con normalidad
});

test('tras la pausa, el siguiente toque sí dispara', async ({ page }) => {
  await open(page);
  const p = await showCard(page, 4);
  await page.waitForTimeout(100);
  await page.mouse.click(p.x, p.y); // frena la lista: no cuenta
  await page.waitForTimeout(500);
  await page.mouse.click(p.x, p.y);
  expect(await forcedCard(page)).toBe('4');
});

test('un arrastre no es un toque', async ({ page }) => {
  await open(page);
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.move(p.x, p.y);
  await page.mouse.down();
  await page.mouse.move(p.x, p.y + 60, { steps: 4 });
  await page.mouse.up();
  expect(await forcedCard(page)).toBeNull();
});

test('un toque largo no dispara', async ({ page }) => {
  await open(page);
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.move(p.x, p.y);
  await page.mouse.down();
  await page.waitForTimeout(450);
  await page.mouse.up();
  expect(await forcedCard(page)).toBeNull();
});

test('desarmado no dispara; al armar con el gesto secreto, sí', async ({ page }) => {
  await open(page, { armed: false });
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  expect(await forcedCard(page)).toBeNull();
  expect(await navigated(page)).toBe('5');
  await page.evaluate(() => (window as any).OmniWeb.setArmed(true));
  expect(await page.evaluate(() => (window as any).OmniWeb.armed)).toBe(true);
  await page.mouse.click(p.x, p.y);
  expect(await forcedCard(page)).toBe('5');
});

test('desarmar de nuevo deja la web normal', async ({ page }) => {
  await open(page);
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.evaluate(() => (window as any).OmniWeb.setArmed(false));
  await page.mouse.click(p.x, p.y);
  expect(await forcedCard(page)).toBeNull();
});

test('el logo (no es de una lista) se ignora', async ({ page }) => {
  await open(page);
  await page.waitForTimeout(600);
  await page.mouse.click(40, 40); // el logo fijo arriba a la izquierda
  expect(await forcedCard(page)).toBeNull();
  expect(await page.evaluate(() => !!document.querySelector('#logo[data-omni-forced]'))).toBe(false);
});

test('solo se fuerza una vez; después el toque es normal', async ({ page }) => {
  await open(page);
  const p = await showCard(page, 5);
  await page.waitForTimeout(600);
  await page.mouse.click(p.x, p.y);
  expect(await forcedCard(page)).toBe('5');
  const q = await showCard(page, 7);
  await page.waitForTimeout(600);
  await page.mouse.click(q.x, q.y);
  expect(await forcedCard(page)).toBe('5'); // no se movió
  expect(await navigated(page)).toBe('7'); // el segundo toque llegó a la web
});

test('el modo antiguo «auto» sigue funcionando (sustituye al parar)', async ({ page }) => {
  await open(page, { mode: 'auto' }, '');
  for (let i = 1; i <= 8; i++) {
    await page.evaluate((v) => window.scrollTo(0, v), (900 * i) / 8);
    await page.waitForTimeout(30);
  }
  await expect.poll(() => forcedCard(page)).not.toBeNull();
});
