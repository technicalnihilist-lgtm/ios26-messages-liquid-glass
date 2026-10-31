package com.liquidglass.messages;

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

        Notify.mms(ctx);
    }

    /** Small holder so the constant is available on every API level we support. */
    static final class TelephonyConst {
        static final String WAP_PUSH_DELIVER = "android.provider.Telephony.WAP_PUSH_DELIVER";
    }
}
