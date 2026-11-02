/*
 * Mocked Android bridge for the Liquid Glass Messages test harness.
 *
 * Injected into the page BEFORE app scripts run (Playwright addInitScript) so
 * that `window.Android` exists exactly like it does inside the WebView shell.
 * The mock keeps an in-memory SMS store so send / delete / tombstone / read
 * behaviour can be asserted end to end.
 */

function mockBridgeSource(cfg) {
  return `(() => {
  const cfg = ${JSON.stringify(cfg || {})};
  const store = {
    threads: JSON.parse(JSON.stringify(cfg.threads || [])),
    msgs: JSON.parse(JSON.stringify(cfg.msgs || {})),
    contacts: JSON.parse(JSON.stringify(cfg.contacts || [])),
    prefs: Object.assign({}, cfg.prefs || {}),
    deleted: [],
    calls: [],
    nextId: 90000
  };
  const key = (id, addr) => (addr && store.msgs[addr]) ? addr : String(id);
  const live = (addr) => (store.msgs[addr] || []).filter(
    m => !store.deleted.some(d => d.addr === addr && d.body === m.body));

  // survive a reload so tombstone / persistence tests can re-open the app
  const PKEY = '__liquidglass_mock';
  try {
    const saved = sessionStorage.getItem(PKEY);
    if (saved) {
      const o = JSON.parse(saved);
      store.threads = o.threads; store.msgs = o.msgs; store.prefs = o.prefs;
      store.deleted = o.deleted; store.calls = []; store.nextId = o.nextId;
    }
  } catch (e) {}
  function persist() {
    try {
      sessionStorage.setItem(PKEY, JSON.stringify({
        threads: store.threads, msgs: store.msgs, prefs: store.prefs,
        deleted: store.deleted, nextId: store.nextId
      }));
    } catch (e) {}
  }

  window.__mock = store;
  window.__mockPersist = persist;
  window.__mockCalls = (n) => store.calls.filter(c => !n || c[0] === n);
  window.__mockPush = (addr, body, out) => {
    const list = store.msgs[addr] = store.msgs[addr] || [];
    list.push({ id: store.nextId++, body, date: Date.now(), out: out ? 1 : 0 });
  };

  window.Android = {
    loadThreads() {
      store.calls.push(['loadThreads']);
      return JSON.stringify(store.threads.slice().sort((a, b) => b.date - a.date));
    },
    loadMessages(threadId, addr) {
      store.calls.push(['loadMessages', threadId, addr]);
      return JSON.stringify(live(key(threadId, addr)));
    },
    markRead(threadId) {
      store.calls.push(['markRead', threadId]);
      store.threads.forEach(t => { if (t.id === threadId) t.unread = 0; });
      persist();
    },
    hasSmsPermission() { return true; },
    isDefaultSms() { return cfg.defaultSms !== false; },
    requestSmsPermission() { store.calls.push(['requestSmsPermission']); },
    requestDefaultSms() { store.calls.push(['requestDefaultSms']); },
    sendSms(addr, body) {
      store.calls.push(['sendSms', addr, body]);
      window.__mockPush(addr, body, 1);
      persist();
      return true;
    },
    deleteMessage(id) {
      store.calls.push(['deleteMessage', id]);
      Object.keys(store.msgs).forEach(addr => {
        const m = (store.msgs[addr] || []).find(x => x.id === id);
        if (m) store.deleted.push({ addr, body: m.body });
      });
      Object.keys(store.msgs).forEach(addr => {
        store.msgs[addr] = (store.msgs[addr] || []).filter(x => x.id !== id);
      });
      persist();
    },
    deleteLocalMessage(addr, body, date) {
      store.calls.push(['deleteLocalMessage', addr, body, date]);
      store.deleted.push({ addr, body });
      store.msgs[addr] = (store.msgs[addr] || []).filter(x => x.body !== body);
      persist();
    },
    deleteThread(threadId, addr) {
      store.calls.push(['deleteThread', threadId, addr]);
      store.threads = store.threads.filter(t => !(t.id === threadId && (t.addr || '') === (addr || t.addr)));
      if (addr) delete store.msgs[addr];
      persist();
    },
    loadContacts() {
      store.calls.push(['loadContacts']);
      return JSON.stringify(store.contacts);
    },
    hasContactsPermission() { return true; },
    requestContactsPermission() { store.calls.push(['requestContactsPermission']); },
    getPref(k, def) { return Object.prototype.hasOwnProperty.call(store.prefs, k) ? String(store.prefs[k]) : (def === undefined ? '' : def); },
    setPref(k, v) { store.calls.push(['setPref', k, String(v)]); store.prefs[k] = String(v); persist(); },
    copy(t) { store.calls.push(['copy', String(t)]); }
  };
})();`;
}

module.exports = { mockBridgeSource };
