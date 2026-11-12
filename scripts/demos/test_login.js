const { chromium } = require('../../docs/stm-02/e2e/node_modules/playwright-core');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.goto('http://localhost:3000/login');
  await page.fill('input#institutional-email', 'campxsync@gmail.com');
  await page.fill('input#password', 'Nira@161208');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(3000);
  console.log('Logged in URL:', page.url());
  await page.goto('http://localhost:3000/college/documents');
  await page.waitForTimeout(3000);
  console.log('Documents page URL:', page.url());
  const reqText = await page.locator('#tab-stage-requirements').textContent();
  console.log('Requirements tab text:', reqText.trim());
  const rows = await page.locator('table tbody tr').count();
  console.log('Table rows visible:', rows);
  await browser.close();
})().catch(e => {
  console.error(e);
  process.exit(1);
});
