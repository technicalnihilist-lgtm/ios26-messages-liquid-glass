/*
 * Liquid Glass Messages — browser test harness.
 *
 * Runs the bundled app/assets/index.html in Chromium (Playwright) with a
 * mocked Android bridge, and asserts the things that must never break:
 * SMS send, receive/poll, delete + tombstones, the default-SMS flow,
 * settings persistence, the 3D depth engine, and HTML safety.
 *
 *   node tests/ui-tests.js            # run everything
 *   CHROME_PATH=/path/to/chrome node tests/ui-tests.js
 */
const path = require('path');
const { chromium } = require('playwright');
const { mockBridgeSource } = require('./mock-bridge');

const CHROME = process.env.CHROME_PATH || '/opt/headless-shell/chrome-headless-shell';
const APP = process.env.APP_HTML || path.join(__dirname, '..', 'app', 'assets', 'index.html');

const NOW = Date.now();
const T938 = (() => { const d = new Date(); d.setHours(9, 38, 0, 0); return d.getTime(); })();

const MSGS_AANYA = [
  { id: 1, body: 'Booked! See you at 8', date: T938, out: 1 },
  { id: 2, body: 'Let me check and tell you', date: T938 + 60000, out: 0 }
];

const BASE = {
  threads: [
    { id: 11, addr: 'Aanya', snippet: 'Let me check and tell you', date: T938 + 60000, unread: 0 },
    { id: 12, addr: 'Rohan', snippet: 'Okay done', date: T938, unread: 1 },
    { id: 13, addr: 'Mom', snippet: 'Sounds great!', date: T938, unread: 0 }
  ],
  msgs: { Aanya: MSGS_AANYA.slice(), Rohan: [{ id: 21, body: 'Okay done', date: T938, out: 0 }] },
  contacts: [{ name: 'Aanya', number: 'Aanya' }, { name: 'Rohan', number: 'Rohan' }]
};

const results = [];
let failures = 0;

async function test(name, fn) {
  try {
    await fn();
    results.push(['PASS', name]);
    console.log('  ok   ' + name);
  } catch (e) {
    failures++;
    results.push(['FAIL', name, e.message]);
    console.log('  FAIL ' + name + '\n         ' + String(e.message).split('\n')[0]);
  }
}

function assert(cond, msg) { if (!cond) throw new Error(msg); }

async function open(browser, cfg, opts) {
  const ctx = await browser.newContext({
    viewport: { width: 430, height: 900 },
    deviceScaleFactor: 1,
    colorScheme: (opts && opts.dark) ? 'dark' : 'light'
  });
  const page = await ctx.newPage();
  page.setDefaultTimeout(6000);
  page.setDefaultNavigationTimeout(15000);
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  await page.addInitScript(mockBridgeSource(cfg || BASE));
  await page.goto('file://' + APP);
  await page.waitForFunction(() => typeof T !== 'undefined' && typeof cur !== 'undefined');
  await page.waitForTimeout(400);
  await page.waitForFunction(() => !document.querySelector('#app').getAnimations().length);
  await page.waitForTimeout(120);
  page.__errors = errors;
  return page;
}

const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  const browser = await chromium.launch({
    executablePath: CHROME,
    args: ['--no-sandbox', '--allow-file-access-from-files', '--force-color-profile=srgb']
  });

  console.log('\n— boot & list —');
  await test('app boots and renders the conversation list', async () => {
    const p = await open(browser);
    const rows = await p.$$eval('#rows .row', els => els.length);
    assert(rows === 3, 'expected 3 rows, got ' + rows);
    assert(await p.$eval('#app', el => el.classList.contains('on')), 'app not shown');
    assert(p.__errors.length === 0, 'console errors: ' + p.__errors.join(' | '));
    await p.context().close();
  });

  await test('empty store shows the empty state', async () => {
    const p = await open(browser, { threads: [], msgs: {} });
    const txt = await p.$eval('#rows', el => el.textContent.trim());
    assert(/No Conversations/.test(txt), 'empty state missing: ' + txt);
    await p.context().close();
  });

  console.log('\n— threads & messages —');
  await test('opening a thread renders its messages in order with correct sides', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(500);
    const bubs = await p.$$eval('#msgs .bub', els => els.map(e => [e.className, e.textContent.trim()]));
    assert(bubs.length === 2, 'expected 2 bubbles, got ' + bubs.length);
    assert(/me/.test(bubs[0][0]) && bubs[0][1] === 'Booked! See you at 8', 'first bubble wrong: ' + bubs[0]);
    assert(/th/.test(bubs[1][0]) && bubs[1][1] === 'Let me check and tell you', 'second bubble wrong: ' + bubs[1]);
    const title = await p.$eval('#ttl span', el => el.textContent.trim());
    assert(title === 'Aanya', 'title wrong: ' + title);
    await p.context().close();
  });

  await test('thread header shows the real date, not a hardcoded time', async () => {
    const p = await open(browser, {
      threads: [{ id: 11, addr: 'Aanya', snippet: 'old', date: T938 - 6 * 86400000, unread: 0 }],
      msgs: { Aanya: [{ id: 1, body: 'old', date: T938 - 6 * 86400000, out: 0 }] }
    });
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(450);
    const stamp = await p.$eval('#msgs .stamp', el => el.textContent.trim());
    assert(/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun) /.test(stamp), 'stamp does not reflect the date: ' + stamp);
    const today = await p.evaluate(() => { const d = new Date(); return /Today/.test(document.querySelector('#msgs .stamp').textContent); });
    assert(today === false, 'an old thread is still stamped Today');
    await p.context().close();
  });

  await test('opening a thread marks it read', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(400);
    const calls = await p.evaluate(() => window.__mockCalls('markRead'));
    assert(calls.length >= 1, 'markRead never called');
    await p.context().close();
  });

  console.log('\n— sending —');
  await test('sending a text goes through the native bridge', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(400);
    await p.fill('#txt', 'On my way');
    await p.click('#send');
    await p.waitForTimeout(300);
    const calls = await p.evaluate(() => window.__mockCalls('sendSms'));
    assert(calls.length === 1 && calls[0][1] === 'Aanya' && calls[0][2] === 'On my way',
      'sendSms wrong: ' + JSON.stringify(calls));
    const last = await p.$$eval('#msgs .bub', els => els[els.length - 1].textContent.trim());
    assert(last === 'On my way', 'optimistic bubble missing: ' + last);
    assert(await p.$eval('#txt', el => el.value) === '', 'input not cleared');
    await p.context().close();
  });

  await test('empty input does not send', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(400);
    await p.fill('#txt', '   ');
    await p.click('#send');
    await p.waitForTimeout(200);
    const calls = await p.evaluate(() => window.__mockCalls('sendSms'));
    assert(calls.length === 0, 'blank message was sent');
    await p.context().close();
  });

  console.log('\n— delete & tombstones —');
  await test('long-press a bubble and delete it (tombstoned)', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(450);
    const box = await p.$eval('#msgs .bub', el => {
      const r = el.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
    });
    await p.mouse.move(box.x, box.y);
    await p.mouse.down();
    await p.waitForTimeout(600);
    await p.mouse.up();
    await p.waitForTimeout(450);
    assert(await p.$eval('#ctx', el => el.classList.contains('on')), 'context menu did not open');
    await p.click('#ctx .ci[data-a="del"]');
    await p.waitForTimeout(1600);
    const calls = await p.evaluate(() => window.__mockCalls());
    const del = calls.filter(c => c[0] === 'deleteMessage' || c[0] === 'deleteLocalMessage');
    assert(del.length === 1, 'delete never reached the bridge: ' + JSON.stringify(calls));
    const n = await p.$$eval('#msgs .bub', els => els.length);
    assert(n === 1, 'bubble not removed from the DOM, count=' + n);
    // reload: the deleted message must not come back
    await p.reload();
    await p.waitForTimeout(900);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(450);
    const after = await p.$$eval('#msgs .bub', els => els.map(e => e.textContent.trim()));
    assert(!after.includes('Booked! See you at 8'), 'deleted message came back: ' + JSON.stringify(after));
    await p.context().close();
  });

  await test('long-press a conversation and delete the thread', async () => {
    const p = await open(browser);
    const box = await p.$eval('#rows .row[data-i="0"]', el => {
      const r = el.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
    });
    await p.mouse.move(box.x, box.y);
    await p.mouse.down();
    await p.waitForTimeout(600);
    await p.mouse.up();
    await p.waitForTimeout(400);
    assert(await p.$eval('#ctx', el => el.classList.contains('on')), 'row menu did not open');
    await p.click('#ctx .ci[data-a="del"]');
    await p.waitForTimeout(1600);
    const calls = await p.evaluate(() => window.__mockCalls('deleteThread'));
    assert(calls.length === 1, 'deleteThread never called: ' + JSON.stringify(calls));
    const rows = await p.$$eval('#rows .row', els => els.length);
    assert(rows === 2, 'row not removed, count=' + rows);
    await p.context().close();
  });

  await test('copy puts the bubble text on the clipboard', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(450);
    const box = await p.$eval('#msgs .bub', el => {
      const r = el.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
    });
    await p.mouse.move(box.x, box.y);
    await p.mouse.down();
    await p.waitForTimeout(600);
    await p.mouse.up();
    await p.waitForTimeout(400);
    await p.click('#ctx .ci[data-a="copy"]');
    await p.waitForTimeout(500);
    const calls = await p.evaluate(() => window.__mockCalls('copy'));
    assert(calls.length === 1 && calls[0][1] === 'Booked! See you at 8',
      'copy wrong: ' + JSON.stringify(calls));
    await p.context().close();
  });

  console.log('\n— receive / poll —');
  await test('an incoming message appears and does not yank a scrolled-up reader', async () => {
    const many = [];
    for (let i = 0; i < 40; i++) many.push({ id: 100 + i, body: 'message ' + i, date: T938 + i * 60000, out: i % 2 });
    const p = await open(browser, {
      threads: [{ id: 11, addr: 'Aanya', snippet: 'message 39', date: T938 + 39 * 60000, unread: 0 }],
      msgs: { Aanya: many }
    });
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(500);
    await p.evaluate(() => { document.querySelector('#tscroll').scrollTop = 0; });
    await p.waitForTimeout(200);
    await p.evaluate(() => {
      window.__mockPush('Aanya', 'a brand new one', 0);
      window.__mock.threads[0].snippet = 'a brand new one';
      window.__mock.threads[0].date = Date.now();
      window.__onSmsPoll();
    });
    await p.waitForTimeout(400);
    const top = await p.evaluate(() => document.querySelector('#tscroll').scrollTop);
    assert(top < 60, 'reader was yanked to the bottom (scrollTop=' + top + ')');
    await p.context().close();
  });

  await test('an incoming message shows up when at the bottom', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(450);
    await p.evaluate(() => {
      window.__mockPush('Aanya', 'ping from the network', 0);
      window.__onSmsChanged();
    });
    await p.waitForTimeout(400);
    const txt = await p.$$eval('#msgs .bub', els => els.map(e => e.textContent.trim()));
    assert(txt.includes('ping from the network'), 'incoming message missing: ' + JSON.stringify(txt));
    await p.context().close();
  });

  console.log('\n— safety —');
  await test('message text is HTML-escaped (no injection through the bridge)', async () => {
    const evil = '<img src=x onerror="window.__xss=1">AT&T <b>bold</b>';
    const p = await open(browser, {
      threads: [{ id: 11, addr: 'Aanya', snippet: evil, date: T938, unread: 0 }],
      msgs: { Aanya: [{ id: 1, body: evil, date: T938, out: 0 }] }
    });
    const injected = await p.$$eval('#rows img', els => els.length);
    assert(injected === 0, 'markup from an SMS reached the DOM');
    const snip = await p.$eval('#rows .rt p', el => el.textContent);
    assert(snip === evil, 'snippet text mangled: ' + snip);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(450);
    const injected2 = await p.$$eval('#msgs .bub img, #msgs .bub b', els => els.length);
    assert(injected2 === 0, 'markup from an SMS reached the thread DOM');
    const shown = await p.$eval('#msgs .bub', el => el.textContent.trim());
    assert(shown === evil, 'text was mangled: ' + shown);
    assert(await p.evaluate(() => window.__xss) === undefined, 'injected script ran');
    await p.context().close();
  });

  await test('a contact name cannot inject markup either', async () => {
    const p = await open(browser, {
      threads: [], msgs: {},
      contacts: [{ name: '<img src=x onerror="window.__xss=2">', number: '123456' }]
    });
    await p.click('#compose');
    await p.waitForTimeout(400);
    await p.fill('#newName', '90000');
    await p.click('#startChat');
    await p.waitForTimeout(700);
    await p.click('#plus');
    await p.waitForTimeout(700);
    const injected = await p.$$eval('#clist img', els => els.length);
    assert(injected === 0, 'contact name markup reached the DOM');
    await p.context().close();
  });

  console.log('\n— settings & persistence —');
  await test('glass sliders persist their value', async () => {
    const p = await open(browser);
    await p.click('#edit');
    await p.waitForTimeout(450);
    await p.evaluate(() => {
      const r = document.querySelector('#r1');
      r.value = '55';
      r.dispatchEvent(new Event('input', { bubbles: true }));
      const b = document.querySelector('#r2');
      b.value = '40';
      b.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await p.waitForTimeout(700);
    const prefs = await p.evaluate(() => window.__mock.prefs);
    assert(prefs.ga === '55', 'transparency not saved: ' + JSON.stringify(prefs));
    assert(prefs.blur === '40', 'blur not saved: ' + JSON.stringify(prefs));
    await p.context().close();
  });

  await test('saved settings are restored on the next launch', async () => {
    const p = await open(browser, Object.assign({}, BASE, {
      prefs: { ga: '55', blur: '40', bounce: '0', theme: 'dark', d3: '1', depth: '8', par: '2', tilt: '9' }
    }));
    const v = await p.evaluate(() => ({
      ga: getComputedStyle(document.documentElement).getPropertyValue('--ga').trim(),
      theme: document.documentElement.dataset.theme,
      bounce: S.on,
      depth: D.strength,
      tilt: D.tilt
    }));
    assert(v.ga === '0.55', '--ga not restored: ' + v.ga);
    assert(v.theme === 'dark', 'theme not restored: ' + v.theme);
    assert(v.bounce === false, 'bounce flag not restored');
    assert(Math.abs(v.depth - 0.8) < 1e-6, 'depth not restored: ' + v.depth);
    assert(Math.abs(v.tilt - 0.9) < 1e-6, 'tilt not restored: ' + v.tilt);
    await p.context().close();
  });

  await test('dark mode toggle persists', async () => {
    const p = await open(browser);
    await p.click('#edit');
    await p.waitForTimeout(450);
    await p.click('#td');
    await p.waitForTimeout(300);
    const prefs = await p.evaluate(() => window.__mock.prefs);
    assert(prefs.theme === 'dark', 'theme not saved: ' + JSON.stringify(prefs));
    await p.context().close();
  });

  await test('default-SMS prompt appears when the role is not held', async () => {
    const p = await open(browser, Object.assign({}, BASE, { defaultSms: false }));
    await p.waitForTimeout(1200);
    const on = await p.$eval('#sDefault', el => el.classList.contains('on'));
    assert(on, 'default-app sheet never appeared');
    await p.click('#sDefault #setDefault');
    await p.waitForTimeout(1100);
    const calls = await p.evaluate(() => window.__mockCalls('requestDefaultSms'));
    assert(calls.length === 1, 'requestDefaultSms not called');
    await p.context().close();
  });

  await test('no default-SMS prompt when the role is already held', async () => {
    const p = await open(browser);
    await p.waitForTimeout(1300);
    const on = await p.$eval('#sDefault', el => el.classList.contains('on'));
    assert(!on, 'default-app sheet shown even though the role is held');
    await p.context().close();
  });

  console.log('\n— navigation, depth & performance —');
  await test('hardware back unwinds menu -> sheet -> thread -> app', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(500);
    assert(await p.evaluate(() => window.__androidBack()) === true, 'back did not consume the thread');
    await p.waitForTimeout(600);
    assert(await p.evaluate(() => cur) === null, 'thread still open after back');
    await p.click('#edit');
    await p.waitForSelector('#sSet.on', { timeout: 4000 });
    await p.waitForTimeout(250);
    assert(await p.evaluate(() => window.__androidBack()) === true, 'back did not consume the sheet');
    await p.waitForTimeout(500);
    assert(!(await p.$eval('#sSet', el => el.classList.contains('on'))), 'sheet still open');
    assert(await p.evaluate(() => window.__androidBack()) === true, 'back did not consume the app');
    await p.waitForTimeout(900);
    assert(!(await p.$eval('#app', el => el.classList.contains('on'))), 'app still open');
    await p.context().close();
  });

  await test('3D depth still applies to bubbles', async () => {
    const p = await open(browser, {
      threads: [{ id: 11, addr: 'Aanya', snippet: 'hi', date: T938, unread: 0 }],
      msgs: { Aanya: MSGS_AANYA }
    });
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(600);
    const z = await p.$$eval('#msgs .bub', els => els.map(e => e.style.getPropertyValue('--bz')));
    assert(z.length === 2 && z.every(v => /px$/.test(v)), 'no depth on bubbles: ' + JSON.stringify(z));
    const any = z.some(v => parseFloat(v) > 0);
    assert(any, 'depth resolved to zero for every bubble: ' + JSON.stringify(z));
    await p.context().close();
  });

  await test('search filters the list', async () => {
    const p = await open(browser);
    await p.fill('#q', 'roh');
    await p.waitForTimeout(250);
    const rows = await p.$$eval('#rows .row', els => els.length);
    assert(rows === 1, 'search filter wrong, rows=' + rows);
    await p.context().close();
  });

  await test('compose creates a new conversation', async () => {
    const p = await open(browser);
    await p.click('#compose');
    await p.waitForTimeout(400);
    await p.fill('#newName', '+91 90000 11111');
    await p.click('#startChat');
    await p.waitForTimeout(700);
    assert(await p.evaluate(() => cur) !== null, 'new chat did not open');
    const title = await p.$eval('#ttl span', el => el.textContent.trim());
    assert(title === '+91 90000 11111', 'wrong title: ' + title);
    await p.context().close();
  });

  await test('contacts sheet opens a thread for the tapped contact', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]');
    await p.waitForTimeout(500);
    await p.click('#plus');
    await p.waitForTimeout(600);
    const n = await p.$$eval('#clist .row', els => els.length);
    assert(n === 2, 'contacts not listed, n=' + n);
    await p.click('#clist .row[data-num="Rohan"]');
    await p.waitForTimeout(800);
    const title = await p.$eval('#ttl span', el => el.textContent.trim());
    assert(title === 'Rohan', 'wrong thread opened: ' + title);
    await p.context().close();
  });

  await test('a 300-message thread renders and scrolls within budget', async () => {
    const big = [];
    for (let i = 0; i < 300; i++) big.push({ id: 500 + i, body: 'bulk message number ' + i, date: T938 + i * 1000, out: i % 2 });
    const p = await open(browser, {
      threads: [{ id: 11, addr: 'Aanya', snippet: 'bulk', date: T938, unread: 0 }],
      msgs: { Aanya: big }
    });
    const t = await p.evaluate(() => {
      const t0 = performance.now();
      cur = 0; T[0].m = []; loadMsgs(0); renderThread();
      return performance.now() - t0;
    });
    assert(t < 900, 'render too slow: ' + t.toFixed(0) + 'ms');
    const scroll = await p.evaluate(() => {
      const t0 = performance.now();
      for (let i = 0; i < 30; i++) depthScroll();
      return (performance.now() - t0) / 30;
    });
    console.log('         depthScroll ' + scroll.toFixed(2) + 'ms/frame over 300 bubbles, render ' + t.toFixed(0) + 'ms');
    assert(scroll < 12, 'depthScroll too slow: ' + scroll.toFixed(2) + 'ms');
    await p.context().close();
  });

  await test('no uncaught errors during a full interactive pass', async () => {
    const p = await open(browser);
    await p.click('#rows .row[data-i="0"]'); await p.waitForTimeout(450);
    await p.fill('#txt', 'hello'); await p.click('#send'); await p.waitForTimeout(600);
    await p.click('#back'); await p.waitForTimeout(500);
    await p.click('#edit'); await p.waitForTimeout(400);
    await p.click('#sSet [data-close]'); await p.waitForTimeout(500);
    await p.click('#compose'); await p.waitForTimeout(300);
    await p.click('#sNew [data-close]'); await p.waitForTimeout(400);
    await p.click('#rows .row[data-i="0"]'); await p.waitForTimeout(500);
    await p.click('#plus'); await p.waitForTimeout(500);
    await p.click('#sContacts [data-close]'); await p.waitForTimeout(400);
    await p.click('#back'); await p.waitForTimeout(700);
    await p.click('#closeApp'); await p.waitForTimeout(700);
    assert(p.__errors.length === 0, 'console errors: ' + p.__errors.join(' | '));
    await p.context().close();
  });

  await browser.close();

  console.log('\n' + '='.repeat(52));
  console.log(`${results.length - failures}/${results.length} passed`);
  if (failures) {
    console.log('\nFailures:');
    results.filter(r => r[0] === 'FAIL').forEach(r => console.log(' - ' + r[1] + ': ' + r[2]));
    process.exit(1);
  }
})().catch(e => { console.error(e); process.exit(1); });
