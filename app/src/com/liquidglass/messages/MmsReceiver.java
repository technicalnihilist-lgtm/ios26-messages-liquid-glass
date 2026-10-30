package com.liquidglass.messages;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Required component for holding the default-SMS role.
 *
 * Full MMS download is out of scope for this build, so instead of dropping the
 * message silently we raise a notification: the user always knows an MMS arrived.
 */
public class MmsReceiver extends BroadcastReceiver {

    private static final String CHANNEL = "mms";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!TelephonyConst.WAP_PUSH_DELIVER.equals(action)) return;

        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Multimedia messages",
                        NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(ctx, CHANNEL)
                    : new Notification.Builder(ctx);
            b.setContentTitle("New MMS received")
             .setContentText("This build does not download MMS yet. Open your carrier's app to view it.")
             .setSmallIcon(android.R.drawable.ic_dialog_email)
             .setAutoCancel(true);
            nm.notify(4201, b.build());
        } catch (Exception ignored) {
        }
    }

    /** Small holder so the constant is available on every API level we support. */
    static final class TelephonyConst {
        static final String WAP_PUSH_DELIVER = "android.provider.Telephony.WAP_PUSH_DELIVER";
    }
}
