import { expect, test, type Page } from '@playwright/test';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PAGE = pathToFileURL(resolve('dist/test/page.html')).href;
const BUNDLE = resolve('dist/omniweb.js');
const FORCED = 'data:image/svg+xml;utf8,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="220"><rect width="400" height="220" fill="red"/><text x="200" y="125" font-size="40" text-anchor="middle" fill="white">FORZADA</text></svg>',
);

async function setup(page: Page, query = '', cfg: object = {}) {
  await page.goto(PAGE + query);
  await page.addScriptTag({ path: BUNDLE });
  await page.evaluate((c) => (window as any).OmniWeb.configure({ imageUrl: c.img, idleMs: 300, ...c.cfg }), { img: FORCED, cfg });
}

/** Qué hay forzado y a qué distancia del centro de la pantalla está. */
async function state(page: Page) {
  return page.evaluate(() => {
    const el = document.querySelector('[data-omni-forced]');
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { cy: r.top + r.height / 2, vh: window.innerHeight, tag: el.tagName, card: el.closest('.card')?.getAttribute('data-n') };
  });
}

async function scrollLikeUser(page: Page, y: number) {
  // varios pasos para imitar inercia
  for (let i = 1; i <= 8; i++) {
    await page.evaluate((v) => window.scrollTo(0, v), (y * i) / 8);
    await page.waitForTimeout(30);
  }
}

test('no sustituye nada antes de que haya scroll', async ({ page }) => {
  await setup(page);
  await page.waitForTimeout(800);
  expect(await state(page)).toBeNull();
});

for (const [name, y] of [['img lazy+srcset', 0], ['picture', 1], ['background-image', 2]] as const) {
  test(`al parar el scroll sustituye el elemento central (${name})`, async ({ page }) => {
    await setup(page);
    // las tarjetas miden ~300 px: apuntamos a que la tarjeta de tipo `y` quede en el centro
    await page.evaluate(() => window.scrollTo(0, 100));
    const target = await page.evaluate((k) => {
      const cards = Array.from(document.querySelectorAll('.card')).filter((c) => +c.getAttribute('data-n')! % 3 === k && +c.getAttribute('data-n')! > 6);
      const r = cards[0].getBoundingClientRect();
      return r.top + window.scrollY + r.height / 2 - window.innerHeight / 2;
    }, y);
    await scrollLikeUser(page, target);
    await expect.poll(() => state(page), { timeout: 5000 }).not.toBeNull();
    const s = (await state(page))!;
    expect(Math.abs(s.cy - s.vh / 2)).toBeLessThan(160);
  });
}

test('la imagen forzada se ve realmente (img)', async ({ page }) => {
  await setup(page);
  await scrollLikeUser(page, 900);
  await expect.poll(() => state(page)).not.toBeNull();
  const ok = await page.evaluate(() => {
    const el = document.querySelector('[data-omni-forced]') as HTMLElement;
    return el instanceof HTMLImageElement ? el.complete && el.naturalWidth > 0 : getComputedStyle(el).backgroundImage.includes('FORZADA') || getComputedStyle(el).backgroundImage.includes('url');
  });
  expect(ok).toBe(true);
});

test('cambia también el título de la tarjeta', async ({ page }) => {
  await setup(page, '', { title: 'Predicción' });
  await scrollLikeUser(page, 900);
  await expect.poll(() => state(page)).not.toBeNull();
  const s = (await state(page))!;
  const t = await page.locator(`.card[data-n="${s.card}"] h3`).textContent();
  expect(t).toBe('Predicción');
});

test('sobrevive a un framework que restaura el src (re-render)', async ({ page }) => {
  await setup(page, '?rerender=1');
  await scrollLikeUser(page, 900);
  await expect.poll(() => state(page)).not.toBeNull();
  const n = (await state(page))!.card;
  await page.waitForTimeout(1200); // varios ciclos de «re-render»
  const src = await page.evaluate((k) => {
    const c = document.querySelector(`.card[data-n="${k}"]`)!;
    const img = c.querySelector('img');
    return img ? img.getAttribute('src') : (c.querySelector('.bg') as HTMLElement).style.backgroundImage;
  }, n);
  expect(decodeURIComponent(src!)).toContain('FORZADA');
});

test('modo manual: no dispara solo, sí con trigger()', async ({ page }) => {
  await setup(page, '', { mode: 'manual' });
  await scrollLikeUser(page, 900);
  await page.waitForTimeout(900);
  expect(await state(page)).toBeNull();
  await page.evaluate(() => (window as any).OmniWeb.trigger());
  expect(await state(page)).not.toBeNull();
});

test('reset permite forzar de nuevo en otra posición', async ({ page }) => {
  await setup(page);
  await scrollLikeUser(page, 900);
  await expect.poll(() => state(page)).not.toBeNull();
  const first = (await state(page))!.card;
  await page.evaluate(() => (window as any).OmniWeb.reset());
  await scrollLikeUser(page, 2000);
  await expect.poll(async () => (await state(page))?.card).not.toBe(first);
});
