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

  const res = await page.evaluate(async () => {
    const r = await fetch('/api/college/documents/verifications');
    const data = await r.json();
    return { status: r.status, data };
  });

  console.log('Queue API status:', res.status);
  console.log('Queue API full data:', res.data);

  await browser.close();
})().catch(e => {
  console.error(e);
  process.exit(1);
});
