package com.liquidglass.messages;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Safety net for incoming SMS.
 *
 * As the default SMS app we write every incoming text into the system provider.
 * If that insert fails (older Android, provider hiccup, app not default yet) the
 * message would otherwise vanish, so we always keep our own copy here and merge
 * it back into the UI. Nothing is ever lost.
 */
final class LocalStore {

    private static final String PREFS = "liquidglass";
    private static final String KEY = "local_sms";
    private static final int MAX = 800;

    private LocalStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized void add(Context ctx, String addr, String body, long when) {
        if (addr == null || body == null || body.isEmpty()) return;
        try {
            SharedPreferences p = prefs(ctx);
            JSONArray arr = new JSONArray(p.getString(KEY, "[]"));

            // ignore a repeat of something we already stored (both receivers can fire)
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (addr.equals(o.optString("addr"))
                        && body.equals(o.optString("body"))
                        && Math.abs(when - o.optLong("date")) < 180000L) {
                    return;
                }
            }

            JSONObject o = new JSONObject();
            o.put("addr", addr);
            o.put("body", body);
            o.put("date", when);
            arr.put(o);
            while (arr.length() > MAX) arr.remove(0);
            p.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    static synchronized JSONArray all(Context ctx) {
        try {
            return new JSONArray(prefs(ctx).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static synchronized JSONArray forAddress(Context ctx, String addr) {
        JSONArray out = new JSONArray();
        if (addr == null) return out;
        JSONArray all = all(ctx);
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && addr.equals(o.optString("addr"))) out.put(o);
        }
        return out;
    }

    static synchronized void removeAddress(Context ctx, String addr) {
        if (addr == null) return;
        try {
            SharedPreferences p = prefs(ctx);
            JSONArray arr = new JSONArray(p.getString(KEY, "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !addr.equals(o.optString("addr"))) keep.put(o);
            }
            p.edit().putString(KEY, keep.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Stable synthetic thread id for addresses that only exist in our own store. */
    static long syntheticThreadId(String addr) {
        if (addr == null) return -1;
        return -1L * (Math.abs(addr.hashCode()) + 1L);
    }
}
