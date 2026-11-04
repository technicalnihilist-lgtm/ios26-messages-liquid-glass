/*
 * Re-renders every screenshot in screenshots/ from the bundled HTML, with a
 * mocked Android bridge, at the same 430x900 @3x the README expects.
 *
 *   node tests/screenshots.js
 */
const path = require('path');
const { chromium } = require('playwright');
const { mockBridgeSource } = require('./mock-bridge');

const CHROME = process.env.CHROME_PATH || '/opt/headless-shell/chrome-headless-shell';
const APP = process.env.APP_HTML || path.join(__dirname, '..', 'app', 'assets', 'index.html');
const OUT = path.join(__dirname, '..', 'screenshots');

const TODAY = (() => { const d = new Date(); d.setHours(9, 38, 0, 0); return d.getTime(); })();
const NOON = (() => { const d = new Date(); d.setHours(11, 53, 0, 0); return d.getTime(); })();
const YESTERDAY = TODAY - 86400000;

const AANYA = [
  { id: 1, body: 'Booked! See you at 8', date: TODAY, out: 1 },
  { id: 2, body: 'Let me check and tell you', date: TODAY + 60000, out: 0 }
];
const PHONE3 = [
  { id: 31, body: 'Hey! Are we still on for tonight?', date: TODAY, out: 0 },
  { id: 32, body: 'Booked the corner table, 8 pm', date: TODAY + 60000, out: 1 },
  { id: 33, body: 'See you at 8 then', date: TODAY + 120000, out: 0 }
];
const LONG = [
  ['in', 'Hey! Are we still on for tonight?'],
  ['out', 'Yes, booked the corner table'],
  ['in', 'Perfect. 8 pm?'],
  ['out', '8 pm works'],
  ['in', "I'll be there by 7:50"],
  ['out', 'See you then'],
  ['in', 'Bringing the blue notebook'],
  ['out', 'Good, I need the notes'],
  ['in', "Done, it's in my bag"],
  ['out', "Fine, I'll wait outside"],
  ['in', "There's a table by the window"],
  ['out', 'Perfect, I love that one'],
  ['in', "I'll order the usual"],
  ['out', 'Make it two']
].map((m, i) => ({ id: 100 + i, body: m[1], date: TODAY + i * 60000, out: m[0] === 'out' ? 1 : 0 }));

const THREADS3 = [
  { id: 11, addr: 'Aanya', snippet: 'Let me check and tell you', date: TODAY + 60000, unread: 0 },
  { id: 12, addr: 'Rohan', snippet: 'Okay done', date: TODAY, unread: 0 },
  { id: 13, addr: 'Mom', snippet: 'Sounds great!', date: TODAY, unread: 0 }
];
const MSGS3 = {
  Aanya: AANYA,
  Rohan: [{ id: 21, body: 'Okay done', date: TODAY, out: 0 }],
  Mom: [{ id: 22, body: 'Sounds great!', date: TODAY, out: 0 }]
};
const CONTACTS = [
  { name: 'Aanya Sharma', number: '+91 98765 43210' },
  { name: 'Kabir Mehta', number: '+91 91234 56789' },
  { name: 'Mom', number: '+91 90000 11111' },
  { name: 'Priya Nair', number: '+91 93333 22222' },
  { name: 'Rohan Verma', number: '+91 95555 44444' }
];

async function settle(p, ms) {
  await p.waitForTimeout(ms || 900);
  await p.waitForFunction(() => !document.querySelector('#app').getAnimations().length, null, { timeout: 5000 }).catch(() => {});
}

// freeze the ambient wallpaper drift so every run produces the same frame
async function freeze(p) {
  await p.evaluate(() => {
    // a real phone has no mouse: drop the parallax a stray pointer at the
    // viewport corner would otherwise leave on the wallpaper and bars
    if (typeof applyParallax === 'function') applyParallax(0, 0);
    document.querySelectorAll('.blob').forEach(b => b.getAnimations().forEach(a => {
      try { a.pause(); a.currentTime = 0; } catch (e) {}
    }));
    document.querySelectorAll('.typing i').forEach((el, i) => el.getAnimations().forEach(a => {
      try { a.pause(); a.currentTime = 120 + i * 150; } catch (e) {}
    }));
  });
  await p.waitForTimeout(120);
}

const SCENES = [
  {
    file: '01-home.png',
    cfg: { threads: [], msgs: {} },
    run: async p => {
      await p.evaluate(() => { document.querySelector('#app').classList.remove('on'); cur = null; });
    }
  },
  {
    file: '02-messages.png',
    cfg: { threads: [], msgs: {} },
    run: async () => {}
  },
  {
    file: '03-thread.png',
    cfg: { threads: [{ id: 11, addr: 'Aanya', snippet: '', date: TODAY, unread: 0 }], msgs: { Aanya: [] } },
    run: async p => { await openThreadByIndex(p, 0); }
  },
  {
    file: '04-typing.png',
    cfg: { threads: [{ id: 11, addr: 'Aanya', snippet: 'Booked! See you at 8', date: TODAY, unread: 0 }], msgs: { Aanya: [AANYA[0]] } },
    run: async p => {
      await openThreadByIndex(p, 0);
      await p.evaluate(() => { T[cur].typing = true; renderThread(); });
    }
  },
  {
    file: '05-reply.png',
    cfg: { threads: [{ id: 11, addr: 'Aanya', snippet: 'Let me check and tell you', date: TODAY, unread: 0 }], msgs: { Aanya: AANYA } },
    run: async p => { await openThreadByIndex(p, 0); }
  },
  {
    file: '06-dark.png',
    cfg: { threads: [{ id: 11, addr: 'Aanya', snippet: 'Let me check and tell you', date: TODAY, unread: 0 }], msgs: { Aanya: AANYA }, prefs: { theme: 'dark' } },
    run: async p => { await openThreadByIndex(p, 0); }
  },
  {
    file: '07-settings.png',
    cfg: { threads: THREADS3, msgs: MSGS3, defaultSms: false },
    run: async p => {
      await p.evaluate(() => { closeSheets(); nowTimes(); });
      await p.waitForTimeout(400);
      await p.evaluate(() => sheet('#sSet'));
    }
  },
  {
    file: '08-new-message.png',
    cfg: { threads: [], msgs: {} },
    run: async p => {
      await p.evaluate(() => { const n = document.querySelector('#newName'); n.value = 'Aanya'; n.dispatchEvent(new Event('input')); });
      await p.evaluate(() => sheet('#sNew'));
    }
  },
  {
    file: '09-context-menu.png',
    cfg: { threads: [{ id: 11, addr: 'Aanya', snippet: 'Let me check and tell you', date: TODAY, unread: 0 }], msgs: { Aanya: AANYA } },
    run: async p => {
      await openThreadByIndex(p, 0);
      await p.evaluate(() => openCtx(document.querySelectorAll('#msgs .bub')[1]));
    }
  },
  {
    file: '10-row-menu.png',
    cfg: { threads: THREADS3, msgs: MSGS3 },
    run: async p => {
      await p.evaluate(() => nowTimes());
      await p.waitForTimeout(300);
      await p.evaluate(() => openRowCtx(document.querySelectorAll('#rows .row')[2]));
    }
  },
  {
    file: '11-inbox.png',
    cfg: { threads: THREADS3, msgs: MSGS3 },
    run: async p => { await p.evaluate(() => nowTimes()); }
  },
  {
    file: '12-default-app.png',
    cfg: {
      threads: [
        { id: 41, addr: '+91 98765 43210', snippet: 'See you at 8 then', date: NOON, unread: 1 },
        { id: 42, addr: '+91 98123 45678', snippet: 'Sent the design file', date: YESTERDAY, unread: 0 }
      ],
      msgs: { '+91 98765 43210': PHONE3 },
      defaultSms: false
    },
    run: async p => {
      await p.waitForTimeout(1600);           // the app offers the default role itself
      await p.evaluate(() => { if (!document.querySelector('#sDefault').classList.contains('on')) sheet('#sDefault'); });
    }
  },
  {
    file: '13-contacts.png',
    cfg: {
      threads: [{ id: 41, addr: '+91 98765 43210', snippet: 'See you at 8 then', date: TODAY, unread: 0 }],
      msgs: { '+91 98765 43210': PHONE3 },
      contacts: CONTACTS
    },
    run: async p => {
      await openThreadByIndex(p, 0);
      await p.evaluate(() => { renderContacts(''); sheet('#sContacts'); });
    }
  },
  {
    file: '14-depth.png',
    cfg: { threads: [{ id: 41, addr: '+91 98765 43210', snippet: 'Make it two', date: TODAY, unread: 0 }], msgs: { '+91 98765 43210': LONG } },
    run: async p => { await openThreadByIndex(p, 0); }
  }
];

async function openThreadByIndex(p, i) {
  await p.click(`#rows .row[data-i="${i}"]`);
  await p.waitForTimeout(900);
  await p.evaluate(() => requestAnimationFrame(depthScroll));
  await p.waitForTimeout(200);
}

(async () => {
  const browser = await chromium.launch({
    executablePath: CHROME,
    args: ['--no-sandbox', '--allow-file-access-from-files', '--force-color-profile=srgb', '--font-render-hinting=none']
  });

  for (const scene of SCENES) {
    const ctx = await browser.newContext({ viewport: { width: 430, height: 900 }, deviceScaleFactor: 3, colorScheme: 'light' });
    const p = await ctx.newPage();
    p.setDefaultTimeout(6000);
    const errs = [];
    p.on('pageerror', e => errs.push(String(e)));
    await p.addInitScript(mockBridgeSource(scene.cfg));
    await p.goto('file://' + APP);
    await settle(p, 1300);
    // "Now" is what the list shows right after a text is sent
    await p.evaluate(() => { window.nowTimes = () => { T.forEach(t => t.t = 'Now'); renderRows(); }; });
    await scene.run(p);
    await settle(p, 900);
    await freeze(p);
    await p.screenshot({ path: path.join(OUT, scene.file) });
    console.log((errs.length ? 'ERRORS ' : 'ok     ') + scene.file + (errs.length ? ' :: ' + errs.join(' | ') : ''));
    await ctx.close();
  }

  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
