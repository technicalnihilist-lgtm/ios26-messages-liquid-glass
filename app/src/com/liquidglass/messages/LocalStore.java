package com.liquidglass.messages;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final String DELETED = "deleted_sms";
    private static final String READ_AT = "read_at";
    private static final int MAX = 800;

    /**
     * Tombstones indexed by address+body. Rebuilding this from JSON for every
     * message made loading a long thread quadratic; the index is built once and
     * thrown away whenever a new tombstone is written.
     */
    private static Map<String, long[]> delIndex = null;

    private LocalStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized void add(Context ctx, String addr, String body, long when) {
        if (addr == null || body == null || body.isEmpty()) return;
        if (isDeleted(ctx, addr, body, when)) return;
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

    /** Remember a deletion so the message can never be merged back in. */
    static synchronized void tombstone(Context ctx, String addr, String body, long when) {
        if (addr == null || body == null) return;
        try {
            SharedPreferences p = prefs(ctx);
            JSONArray arr = new JSONArray(p.getString(DELETED, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (addr.equals(o.optString("addr")) && body.equals(o.optString("body"))
                        && Math.abs(when - o.optLong("date")) < 180000L) {
                    return;
                }
            }
            JSONObject o = new JSONObject();
            o.put("addr", addr);
            o.put("body", body);
            o.put("date", when);
            arr.put(o);
            while (arr.length() > 3000) arr.remove(0);
            p.edit().putString(DELETED, arr.toString()).apply();
            delIndex = null;
        } catch (Exception ignored) {}
    }

    private static Map<String, long[]> delIndex(Context ctx) {
        if (delIndex != null) return delIndex;
        Map<String, List<Long>> tmp = new HashMap<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString(DELETED, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String key = o.optString("addr") + '\u0000' + o.optString("body");
                List<Long> dates = tmp.get(key);
                if (dates == null) {
                    dates = new ArrayList<>();
                    tmp.put(key, dates);
                }
                dates.add(o.optLong("date"));
            }
        } catch (Exception ignored) {}
        Map<String, long[]> idx = new HashMap<>();
        for (Map.Entry<String, List<Long>> e : tmp.entrySet()) {
            List<Long> v = e.getValue();
            long[] a = new long[v.size()];
            for (int i = 0; i < a.length; i++) a[i] = v.get(i);
            idx.put(e.getKey(), a);
        }
        delIndex = idx;
        return idx;
    }

    static synchronized boolean isDeleted(Context ctx, String addr, String body, long when) {
        if (addr == null || body == null) return false;
        long[] dates = delIndex(ctx).get(addr + '\u0000' + body);
        if (dates == null) return false;
        for (long d : dates) {
            if (Math.abs(when - d) < 180000L) return true;
        }
        return false;
    }

    /** Remember when a conversation was last opened, so local copies stop looking unread. */
    static synchronized void markRead(Context ctx, String addr) {
        if (addr == null || addr.isEmpty()) return;
        try {
            SharedPreferences p = prefs(ctx);
            JSONObject o = new JSONObject(p.getString(READ_AT, "{}"));
            o.put(addr, System.currentTimeMillis());
            p.edit().putString(READ_AT, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    static synchronized long readAt(Context ctx, String addr) {
        if (addr == null || addr.isEmpty()) return 0L;
        try {
            return new JSONObject(prefs(ctx).getString(READ_AT, "{}")).optLong(addr, 0L);
        } catch (Exception e) {
            return 0L;
        }
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
