const { chromium } = require('../../docs/stm-02/e2e/node_modules/playwright-core');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.goto('http://localhost:3000/login');
  await page.fill('input#institutional-email', 'student1.campx@gmail.com');
  await page.fill('input#password', 'Student@161221');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(3000);
  await page.goto('http://localhost:3000/college/documents');
  await page.waitForTimeout(2000);
  const text = await page.evaluate(() => document.body.innerText);
  console.log('Page text snippet:\n', text.substring(0, 300));
  await browser.close();
})().catch(e => {
  console.error(e);
  process.exit(1);
});
