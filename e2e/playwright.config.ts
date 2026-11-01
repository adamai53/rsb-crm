import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: 'tests',
  timeout: 90_000,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'https://localhost',
    // Certificat auto-signé de la stack de test
    ignoreHTTPSErrors: true,
    // Chrome déjà installé en local (E2E_CHANNEL=chrome), Chromium de Playwright en CI
    channel: process.env.E2E_CHANNEL,
    trace: 'retain-on-failure',
  },
});
