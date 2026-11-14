import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createIdleDetector } from './idle';

describe('createIdleDetector', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  const scroll = () => window.dispatchEvent(new Event('scroll'));
  const touch = (t: string) => window.dispatchEvent(new Event(t));

  it('no dispara sin scroll previo', () => {
    const onIdle = vi.fn();
    const d = createIdleDetector(window, { idleMs: 300, onIdle });
    d.arm();
    vi.advanceTimersByTime(2000);
    expect(onIdle).not.toHaveBeenCalled();
    d.disarm();
  });

  it('ignora el scroll de contenedores internos', () => {
    const onIdle = vi.fn();
    const d = createIdleDetector(window, { idleMs: 300, onIdle });
    d.arm();
    const inner = document.createElement('div');
    document.body.appendChild(inner);
    inner.dispatchEvent(new Event('scroll'));
    vi.advanceTimersByTime(2000);
    expect(onIdle).not.toHaveBeenCalled();
    d.disarm();
  });

  it('dispara una vez tras idleMs sin scroll', () => {
    const onIdle = vi.fn();
    const d = createIdleDetector(window, { idleMs: 300, onIdle });
    d.arm();
    scroll();
    vi.advanceTimersByTime(299);
    expect(onIdle).not.toHaveBeenCalled();
    vi.advanceTimersByTime(2);
    expect(onIdle).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(2000);
    expect(onIdle).toHaveBeenCalledTimes(1);
    d.disarm();
  });

  it('la inercia (scrolls seguidos) reinicia la espera', () => {
    const onIdle = vi.fn();
    const d = createIdleDetector(window, { idleMs: 300, onIdle });
    d.arm();
    for (let i = 0; i < 5; i++) {
      scroll();
      vi.advanceTimersByTime(200);
    }
    expect(onIdle).not.toHaveBeenCalled();
    vi.advanceTimersByTime(150);
    expect(onIdle).toHaveBeenCalledTimes(1);
    d.disarm();
  });

  it('no dispara mientras el dedo sigue apoyado', () => {
    const onIdle = vi.fn();
    const d = createIdleDetector(window, { idleMs: 300, onIdle });
    d.arm();
    touch('touchstart');
    scroll();
    vi.advanceTimersByTime(5000);
    expect(onIdle).not.toHaveBeenCalled();
    touch('touchend');
    vi.advanceTimersByTime(301);
    expect(onIdle).toHaveBeenCalledTimes(1);
    d.disarm();
  });

  it('disarm cancela el disparo pendiente', () => {
    const onIdle = vi.fn();
    const d = createIdleDetector(window, { idleMs: 300, onIdle });
    d.arm();
    scroll();
    d.disarm();
    vi.advanceTimersByTime(1000);
    expect(onIdle).not.toHaveBeenCalled();
  });
});
