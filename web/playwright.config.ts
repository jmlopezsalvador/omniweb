import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: 'e2e',
  timeout: 30_000,
  use: { ...devices['Pixel 7'] },
  projects: [{ name: 'chromium-movil' }],
});
