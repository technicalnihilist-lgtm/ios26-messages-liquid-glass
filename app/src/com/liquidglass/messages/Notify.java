package com.liquidglass.messages;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** One place for the notification channels this app uses. */
final class Notify {

    static final String MSG_CHANNEL = "messages";
    static final String MMS_CHANNEL = "mms";

    private Notify() {}

    // both receivers can see the same text; never notify twice for one SMS
    private static String lastKey;
    private static long lastAt;
    // creating a channel is idempotent but not free: do it once per process.
    // One flag per channel: a shared flag meant the second channel was never
    // created, and posting to a channel that does not exist throws on O+ - so
    // either the MMS or the SMS notification silently never appeared.
    private static boolean msgChannelMade;
    private static boolean mmsChannelMade;

    static void message(Context ctx, String from, String body) {
        try {
            String key = (from == null ? "" : from) + '\u0000' + (body == null ? "" : body);
            long now = System.currentTimeMillis();
            if (key.equals(lastKey) && now - lastAt < 4000L) return;
            lastKey = key;
            lastAt = now;
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !msgChannelMade) {
                NotificationChannel ch = new NotificationChannel(MSG_CHANNEL, "Messages",
                        NotificationManager.IMPORTANCE_HIGH);
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
                msgChannelMade = true;
            }

            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.setAction(Intent.ACTION_SENDTO);
            open.setData(android.net.Uri.parse("smsto:" + from));
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            // one request code per conversation: a shared code made every
            // notification reuse the last intent, so tapping an older one opened
            // the wrong chat
            PendingIntent pi = PendingIntent.getActivity(ctx, idFor(from), open, flags);

            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(ctx, MSG_CHANNEL)
                    : new Notification.Builder(ctx);
            b.setContentTitle(from == null ? "New message" : from)
             .setContentText(body)
             .setStyle(new Notification.BigTextStyle().bigText(body))
             .setSmallIcon(android.R.drawable.ic_dialog_email)
             .setContentIntent(pi)
             .setAutoCancel(true);
            nm.notify(idFor(from), b.build());
        } catch (Exception ignored) {}
    }

    /**
     * One notification per conversation, so a chatty sender replaces their old
     * notification instead of piling up a new one per message in the shade.
     */
    static int idFor(String from) {
        int h = from == null ? 0 : from.hashCode();
        return 0x4D530000 | (h & 0xffff);
    }

    /** Reading a conversation in the app clears its notification. */
    static void clear(Context ctx, String from) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(idFor(from));
        } catch (Exception ignored) {}
    }

    static void mms(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !mmsChannelMade) {
                NotificationChannel ch = new NotificationChannel(MMS_CHANNEL, "Multimedia messages",
                        NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(ch);
                mmsChannelMade = true;
            }
            // without a content intent a tap on this notification did nothing at all
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(ctx, MMS_CHANNEL)
                    : new Notification.Builder(ctx);
            b.setContentTitle("New MMS received")
             .setContentText("This build does not download MMS yet.")
             .setSmallIcon(android.R.drawable.ic_dialog_email)
             .setContentIntent(PendingIntent.getActivity(ctx, 4201, open, flags))
             .setAutoCancel(true);
            nm.notify(4201, b.build());
        } catch (Exception ignored) {}
    }
}
