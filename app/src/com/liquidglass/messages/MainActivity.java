package com.liquidglass.messages;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Liquid Glass Messages.
 *
 * The UI is assets/index.html. This shell gives it real SMS: it reads the system
 * SMS provider, sends through SmsManager, stores incoming texts itself (it can be
 * the default SMS app) and asks Android for the default-messaging role.
 */
public class MainActivity extends Activity {

    static final String ACTION_SMS_CHANGED = "com.liquidglass.messages.SMS_CHANGED";
    private static final String BG = "#111111";
    private static final int REQ_PERMS = 2001;
    private static final int REQ_ROLE = 2002;
    private static final int REQ_CONTACTS = 2003;

    private WebView web;
    private boolean pageLoaded;
    private boolean pendingRole;   // the role dialog waits for the permission result
    private String pendingAddress;
    private BroadcastReceiver smsChanged;
    private final Handler poll = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            js("window.__onSmsPoll && window.__onSmsPoll()");
            poll.postDelayed(this, 6000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(Color.parseColor(BG));
            w.setNavigationBarColor(Color.parseColor(BG));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View decor = w.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(Color.parseColor(BG));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(false);
        }

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setHapticFeedbackEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageLoaded = true;
                flushPendingAddress();
                pushStatus();
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");

        setContentView(web);
        handleIntent(getIntent());
        web.loadUrl("file:///android_asset/index.html");

        smsChanged = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                js("window.__onSmsChanged && window.__onSmsChanged()");
            }
        };
        IntentFilter f = new IntentFilter(ACTION_SMS_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsChanged, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(smsChanged, f);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        // the page may still be loading: onPageFinished will pick the address up
        if (pageLoaded) flushPendingAddress();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        pushStatus();
        // the page may have been reloaded while we were away
        js("window.__onSmsChanged && window.__onSmsChanged()");
        if (pageLoaded) flushPendingAddress();
        poll.removeCallbacks(pollTask);
        poll.postDelayed(pollTask, 2500);
    }

    @Override
    protected void onPause() {
        poll.removeCallbacks(pollTask);
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        pageLoaded = false;
        if (smsChanged != null) {
            try { unregisterReceiver(smsChanged); } catch (Exception ignored) {}
        }
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- intents

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_SENDTO.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                String scheme = data.getScheme() == null ? "" : data.getScheme().toLowerCase();
                if (scheme.startsWith("sms") || scheme.startsWith("mms")) {
                    String addr = data.getSchemeSpecificPart();
                    if (addr != null) {
                        int q = addr.indexOf('?');
                        if (q >= 0) addr = addr.substring(0, q);
                        pendingAddress = addr.trim();
                    }
                }
            }
        }
    }

    private void flushPendingAddress() {
        if (!pageLoaded) return;               // the intent would be lost in a page that is not up yet
        if (pendingAddress == null || pendingAddress.isEmpty()) return;
        String addr = pendingAddress;
        pendingAddress = null;
        js("window.__composeTo && window.__composeTo(" + JSONObject.quote(addr) + ")");
    }

    // ------------------------------------------------------------- js helpers

    private void js(final String code) {
        if (web == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try { web.evaluateJavascript(code, null); } catch (Exception ignored) {}
            }
        });
    }

    private void pushStatus() {
        js("window.__onSmsStatus && window.__onSmsStatus()");
    }

    // ----------------------------------------------------------- role + perms

    private boolean hasSmsPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return checkSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        ArrayList<String> ask = new ArrayList<>();
        String[] wanted = {
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.RECEIVE_MMS,
                android.Manifest.permission.RECEIVE_WAP_PUSH
        };
        for (String p : wanted) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) ask.add(p);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ask.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        if (ask.isEmpty()) {
            pushStatus();
            if (pendingRole) { pendingRole = false; askDefaultRole(); }
            return;
        }
        requestPermissions(ask.toArray(new String[0]), REQ_PERMS);
    }

    private boolean hasContactsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void askContactsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasContactsPermission()) {
            js("window.__onContactsReady && window.__onContactsReady()");
            return;
        }
        requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        pushStatus();
        js("window.__onSmsStatus && window.__onSmsStatus()");
        if (code == REQ_CONTACTS) {
            js("window.__onContactsReady && window.__onContactsReady()");
        }
        if (pendingRole) {
            pendingRole = false;
            if (hasSmsPermissions()) askDefaultRole();
        }
    }

    // -------------------------------------------------------------- contacts

    private String contactsJson() {
        JSONArray out = new JSONArray();
        if (!hasContactsPermission()) return out.toString();
        Cursor c = null;
        Map<String, JSONObject> seen = new LinkedHashMap<>();
        try {
            c = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (c != null) {
                while (c.moveToNext() && seen.size() < 1000) {
                    String name = c.getString(0);
                    String number = c.getString(1);
                    if (number == null || number.trim().isEmpty()) continue;
                    String key = number.replaceAll("[^0-9+]", "");
                    if (key.length() < 6 || seen.containsKey(key)) continue;
                    JSONObject o = new JSONObject();
                    o.put("name", name == null || name.trim().isEmpty() ? number : name);
                    o.put("number", number);
                    seen.put(key, o);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        for (JSONObject o : seen.values()) out.put(o);
        return out.toString();
    }

    // --------------------------------------------------------------- prefs

    private SharedPreferences prefs() {
        return getSharedPreferences("liquidglass", Context.MODE_PRIVATE);
    }

    boolean isDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = getSystemService(RoleManager.class);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_SMS);
        }
        return getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this));
    }

    private void askDefaultRole() {
        if (isDefaultSmsApp()) {
            js("window.__onSmsStatus && window.__onSmsStatus()");
            return;
        }
        // Android will not put the role dialog over a permission dialog, so ask
        // for the permissions first and continue once the user has answered.
        if (!hasSmsPermissions()) {
            pendingRole = true;
            askPermissions();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), REQ_ROLE);
                }
            } else {
                Intent i = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                startActivityForResult(i, REQ_ROLE);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ROLE) {
            pushStatus();
            js("window.__onSmsChanged && window.__onSmsChanged()");
        }
    }

    // -------------------------------------------------------------- SMS store

    private String threadsJson() {
        Map<Long, JSONObject> seen = new LinkedHashMap<>();
        Cursor c = null;
        try {
            c = getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    new String[]{Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                            Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ},
                    null, null, Telephony.Sms.DATE + " DESC");
            if (c != null) {
                // newest first: 300 conversations are enough, and stopping after a
                // few thousand rows keeps the query cheap on a phone with 100k texts
                int scanned = 0;
                while (c.moveToNext() && seen.size() < 300 && scanned++ < 5000) {
                    long threadId = c.getLong(1);
                    if (seen.containsKey(threadId)) continue;
                    String addr = c.getString(2);
                    if (addr == null || addr.isEmpty()) addr = "(unknown)";
                    JSONObject o = new JSONObject();
                    o.put("id", threadId);
                    o.put("addr", addr);
                    o.put("snippet", c.getString(3) == null ? "" : c.getString(3));
                    o.put("date", c.getLong(4));
                    o.put("unread", c.getInt(6) == 0 ? 1 : 0);
                    seen.put(threadId, o);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }

        // fold in everything we received ourselves - this is what makes new texts appear
        try {
            JSONArray local = LocalStore.all(this);
            for (int i = 0; i < local.length(); i++) {
                JSONObject m = local.optJSONObject(i);
                if (m == null) continue;
                String addr = m.optString("addr");
                long when = m.optLong("date");
                if (LocalStore.isDeleted(this, addr, m.optString("body"), when)) continue;
                JSONObject existing = null;
                for (JSONObject o : seen.values()) {
                    if (addr.equals(o.optString("addr"))) { existing = o; break; }
                }
                boolean fresh = when > LocalStore.readAt(this, addr);
                if (existing == null) {
                    JSONObject o = new JSONObject();
                    o.put("id", LocalStore.syntheticThreadId(addr));
                    o.put("addr", addr);
                    o.put("snippet", m.optString("body"));
                    o.put("date", when);
                    o.put("unread", fresh ? 1 : 0);
                    seen.put(o.optLong("id"), o);
                } else if (when >= existing.optLong("date")) {
                    existing.put("snippet", m.optString("body"));
                    existing.put("date", when);
                    if (fresh) existing.put("unread", 1);
                }
            }
        } catch (Exception ignored) {}

        List<JSONObject> list = new ArrayList<>(seen.values());
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Long.compare(b.optLong("date"), a.optLong("date"));
            }
        });
        JSONArray out = new JSONArray();
        for (JSONObject o : list) out.put(o);
        return out.toString();
    }

    private String messagesJson(long threadId, String addr) {
        List<JSONObject> all = new ArrayList<>();

        if (threadId > 0) {
            Cursor c = null;
            try {
                c = getContentResolver().query(Telephony.Sms.CONTENT_URI,
                        new String[]{Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE},
                        Telephony.Sms.THREAD_ID + "=?", new String[]{String.valueOf(threadId)},
                        Telephony.Sms.DATE + " ASC");
                if (c != null) {
                    while (c.moveToNext()) {
                        String body = c.getString(1) == null ? "" : c.getString(1);
                        long when = c.getLong(2);
                        if (LocalStore.isDeleted(this, addr, body, when)) continue;
                        int type = c.getInt(3);
                        JSONObject o = new JSONObject();
                        o.put("id", c.getLong(0));
                        o.put("body", body);
                        o.put("date", when);
                        // outbox / queued / failed are still our own messages, not theirs
                        o.put("out", (type == Telephony.Sms.MESSAGE_TYPE_SENT
                                || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
                                || type == Telephony.Sms.MESSAGE_TYPE_QUEUED
                                || type == Telephony.Sms.MESSAGE_TYPE_FAILED) ? 1 : 0);
                        all.add(o);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
        }

        // our own copies (they also cover the "not the default app yet" case)
        try {
            JSONArray local = LocalStore.forAddress(this, addr);
            for (int i = 0; i < local.length(); i++) {
                JSONObject m = local.optJSONObject(i);
                if (m == null) continue;
                String body = m.optString("body");
                long when = m.optLong("date");
                if (LocalStore.isDeleted(this, addr, body, when)) continue;
                boolean dup = false;
                for (JSONObject o : all) {
                    if (body.equals(o.optString("body")) && Math.abs(when - o.optLong("date")) < 180000L) {
                        dup = true;
                        break;
                    }
                }
                if (dup) continue;
                JSONObject o = new JSONObject();
                o.put("id", 0);
                o.put("body", body);
                o.put("date", when);
                o.put("out", m.optInt("out", 0));
                all.add(o);
            }
        } catch (Exception ignored) {}

        Collections.sort(all, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Long.compare(a.optLong("date"), b.optLong("date"));
            }
        });

        JSONArray out = new JSONArray();
        for (JSONObject o : all) out.put(o);
        return out.toString();
    }

    private String addrForThread(long threadId) {
        if (threadId <= 0) return null;
        Cursor c = null;
        try {
            c = getContentResolver().query(Telephony.Sms.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS},
                    Telephony.Sms.THREAD_ID + "=?", new String[]{String.valueOf(threadId)}, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private void markThreadRead(long threadId, String addr) {
        if (threadId == 0 && (addr == null || addr.isEmpty())) return;
        if (addr != null && !addr.isEmpty()) {
            LocalStore.markRead(this, addr);
            Notify.clear(this, addr);   // reading it here clears its notification
        }
        if (threadId <= 0) return;
        try {
            ContentValues v = new ContentValues();
            v.put(Telephony.Sms.READ, 1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) v.put(Telephony.Sms.SEEN, 1);
            getContentResolver().update(Telephony.Sms.CONTENT_URI, v,
                    Telephony.Sms.THREAD_ID + "=?", new String[]{String.valueOf(threadId)});
        } catch (Exception ignored) {}
    }

    private boolean sendSms(String addr, String body) {
        if (addr == null || addr.isEmpty() || body == null || body.isEmpty()) return false;
        try {
            SmsManager sm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
            if (sm == null) return false;
            ArrayList<String> parts = sm.divideMessage(body);
            sm.sendMultipartTextMessage(addr, null, parts, null, null);

            // Keep our own record of the outgoing text. The provider only accepts
            // this write while we hold the default-SMS role, so fall back to our
            // own store: otherwise the bubble vanishes on the next reload.
            long now = System.currentTimeMillis();
            Uri stored = null;
            try {
                ContentValues v = new ContentValues();
                v.put(Telephony.Sms.ADDRESS, addr);
                v.put(Telephony.Sms.BODY, body);
                v.put(Telephony.Sms.DATE, now);
                v.put(Telephony.Sms.READ, 1);
                v.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT);
                stored = getContentResolver().insert(Telephony.Sms.CONTENT_URI, v);
            } catch (Exception ignored) {}
            if (stored == null) LocalStore.addOut(this, addr, body, now);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteThread(long threadId, String addr) {
        if (threadId <= 0) {
            // a conversation that only exists in our own store (or has no provider id yet)
            if (addr == null || addr.isEmpty()) return;
            try {
                JSONArray local = LocalStore.forAddress(this, addr);
                for (int i = 0; i < local.length(); i++) {
                    JSONObject m = local.optJSONObject(i);
                    if (m == null) continue;
                    LocalStore.tombstone(this, addr, m.optString("body"), m.optLong("date"));
                }
            } catch (Exception ignored) {}
            LocalStore.removeAddress(this, addr);
            return;
        }
        tombstoneThread(threadId, addr);
        try {
            getContentResolver().delete(Telephony.Sms.CONTENT_URI,
                    Telephony.Sms.THREAD_ID + "=?", new String[]{String.valueOf(threadId)});
        } catch (Exception ignored) {}
    }

    private void deleteMessage(long id) {
        if (id <= 0) return;
        // remember what we are deleting, so the backup store cannot bring it back
        try {
            Cursor c = getContentResolver().query(
                    Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, String.valueOf(id)),
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                    null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    LocalStore.tombstone(this, c.getString(0), c.getString(1), c.getLong(2));
                }
                c.close();
            }
        } catch (Exception ignored) {}
        try {
            getContentResolver().delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, String.valueOf(id)),
                    null, null);
        } catch (Exception ignored) {}
    }

    /** Tombstone every message we know about in this thread before removing it. */
    private void tombstoneThread(long threadId, String addr) {
        try {
            Cursor c = getContentResolver().query(Telephony.Sms.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                    Telephony.Sms.THREAD_ID + "=?", new String[]{String.valueOf(threadId)}, null);
            if (c != null) {
                while (c.moveToNext()) {
                    LocalStore.tombstone(this, c.getString(0), c.getString(1), c.getLong(2));
                }
                c.close();
            }
        } catch (Exception ignored) {}
        try {
            JSONArray local = LocalStore.forAddress(this, addr);
            for (int i = 0; i < local.length(); i++) {
                JSONObject m = local.optJSONObject(i);
                if (m == null) continue;
                LocalStore.tombstone(this, addr, m.optString("body"), m.optLong("date"));
            }
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------ bridge

    private class Bridge {

        @JavascriptInterface
        public void copy(String text) {
            if (text == null) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("message", text));
        }

        @JavascriptInterface
        public boolean hasSmsPermission() {
            return hasSmsPermissions();
        }

        @JavascriptInterface
        public boolean isDefaultSms() {
            return isDefaultSmsApp();
        }

        @JavascriptInterface
        public void requestSmsPermission() {
            runOnUiThread(new Runnable() {
                @Override public void run() { askPermissions(); }
            });
        }

        @JavascriptInterface
        public void requestDefaultSms() {
            runOnUiThread(new Runnable() {
                @Override public void run() { askDefaultRole(); }
            });
        }

        @JavascriptInterface
        public String loadThreads() {
            return threadsJson();
        }

        @JavascriptInterface
        public String loadMessages(long threadId, String addr) {
            return messagesJson(threadId, addr);
        }

        @JavascriptInterface
        public void markRead(long threadId) {
            markThreadRead(threadId, threadId > 0 ? addrForThread(threadId) : null);
        }

        /** Synthetic (negative) thread ids carry no address, so the page passes it. */
        @JavascriptInterface
        public void markRead(long threadId, String addr) {
            String resolved = addr;
            if ((resolved == null || resolved.isEmpty()) && threadId > 0) {
                try { resolved = addrForThread(threadId); } catch (Exception ignored) {}
            }
            markThreadRead(threadId, resolved);
        }

        @JavascriptInterface
        public boolean sendSms(String addr, String body) {
            return MainActivity.this.sendSms(addr, body);
        }

        @JavascriptInterface
        public void deleteThread(long threadId, String addr) {
            String resolved = addr;
            if (resolved == null || resolved.isEmpty()) {
                try { resolved = addrForThread(threadId); } catch (Exception ignored) {}
            }
            MainActivity.this.deleteThread(threadId, resolved);
        }

        @JavascriptInterface
        public void deleteMessage(long id) {
            MainActivity.this.deleteMessage(id);
        }

        /** For messages that only exist in our own store (no provider id). */
        @JavascriptInterface
        public void deleteLocalMessage(String addr, String body, long date) {
            LocalStore.tombstone(MainActivity.this, addr, body, date);
        }

        @JavascriptInterface
        public String loadContacts() {
            return contactsJson();
        }

        @JavascriptInterface
        public boolean hasContactsPermission() {
            return MainActivity.this.hasContactsPermission();
        }

        @JavascriptInterface
        public void requestContactsPermission() {
            runOnUiThread(new Runnable() {
                @Override public void run() { askContactsPermission(); }
            });
        }

        @JavascriptInterface
        public String getPref(String key, String def) {
            try { return prefs().getString(key, def); } catch (Exception e) { return def; }
        }

        @JavascriptInterface
        public void setPref(String key, String value) {
            try { prefs().edit().putString(key, value).apply(); } catch (Exception ignored) {}
        }
    }

    /**
     * Hardware back: let the page decide first (close menu -> sheet -> thread ->
     * close app). Only when the page has nothing to undo do we leave the app.
     */
    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript(
                "(function(){try{return window.__androidBack?!!window.__androidBack():false}catch(e){return false}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (!"true".equals(value)) moveTaskToBack(true);
                    }
                });
    }
}
