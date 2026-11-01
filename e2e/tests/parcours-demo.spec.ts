import { expect, test } from '@playwright/test';

const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@rsb.local';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'AdminRsb2026';
const API_KEY = process.env.E2E_LEAD_API_KEY ?? 'dev-lead-key';

test('formulaire du site → conversion → devis → livraison', async ({ page, request }) => {
  // Une CSP trop stricte casse l'application sans faire échouer les requêtes : on la surveille pendant tout le parcours
  const cspErrors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && /Content Security Policy/i.test(m.text())) cspErrors.push(m.text());
  });

  const stamp = Date.now();
  const vessel = `MV E2E ${stamp}`;
  const email = `e2e.${stamp}@example.com`;

  await test.step('le formulaire public crée un lead', async () => {
    const res = await request.post('/api/public/leads', {
      headers: { 'X-Api-Key': API_KEY },
      data: {
        name: 'Awa Test', company: `Armement E2E ${stamp}`, email, phone: '+253 77 00 00 00', vessel, imo: '9321483',
        fuelGrade: 'VLSFO', quantityMt: 500, message: 'Demande de cotation e2e',
      },
    });
    expect(res.status()).toBe(202);
  });

  await test.step('connexion', async () => {
    await page.goto('/login');
    await page.getByLabel('E-mail professionnel').fill(ADMIN_EMAIL);
    await page.locator('input[type=password]').fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page).not.toHaveURL(/login/);
  });

  await test.step('conversion du lead en transaction', async () => {
    await page.goto('/inbox');
    await page.getByRole('row', { name: new RegExp(vessel) }).click();
    await page.getByRole('button', { name: 'Qualifier et convertir' }).click();
    await expect(page.getByText('Transaction · demande de soutage')).toBeVisible();
    await expect(page.locator('#dr-stage')).toHaveValue('QUALIFIEE');
  });

  await test.step('devis envoyé par e-mail', async () => {
    await page.getByRole('button', { name: 'Générer un devis' }).click();
    await page.getByRole('button', { name: 'Créer le devis' }).click();
    await page.getByRole('button', { name: 'Envoyer par e-mail' }).click();
    await expect(page.getByLabel('Objet')).not.toHaveValue('');
    await page.getByRole('button', { name: 'Envoyer', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Renvoyer par e-mail' })).toBeVisible({ timeout: 30_000 });
  });

  await test.step('devis accepté puis livraison', async () => {
    await page.getByRole('button', { name: 'Marquer comme accepté' }).click();
    await expect(page.getByText('Devis accepté')).toBeVisible();
    await page.getByRole('button', { name: 'Ouvrir la transaction' }).click();
    await expect(page.locator('#dr-stage')).toHaveValue('NOMINATION');
    await page.locator('#dr-stage').selectOption('LIVREE');
    await expect(page.getByText('Livraison enregistrée, contact passé en Client')).toBeVisible();
  });

  await test.step('la session survit à un rechargement (cookie de refresh)', async () => {
    await page.reload();
    await expect(page.locator('#dr-stage')).toHaveValue('LIVREE');
  });

  expect(cspErrors).toEqual([]);
});
