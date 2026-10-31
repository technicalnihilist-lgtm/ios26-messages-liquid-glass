package com.liquidglass.messages;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Fallback path for when this app is NOT the default SMS app yet.
 *
 * Android delivers SMS_DELIVER only to the default app, but SMS_RECEIVED still
 * reaches any app holding RECEIVE_SMS. We use it to keep our own copy and to
 * raise a notification, so a message never silently goes missing.
 */
public class SmsReceivedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) return;

        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;

        StringBuilder body = new StringBuilder();
        String address = null;
        long when = System.currentTimeMillis();
        for (SmsMessage p : parts) {
            if (p == null) continue;
            if (address == null) address = p.getDisplayOriginatingAddress();
            String chunk = p.getMessageBody();
            if (chunk != null) body.append(chunk);
            if (p.getTimestampMillis() > 0) when = p.getTimestampMillis();
        }
        if (address == null || body.length() == 0) return;

        LocalStore.add(ctx, address, body.toString(), when);
        Notify.message(ctx, address, body.toString());

        Intent changed = new Intent(MainActivity.ACTION_SMS_CHANGED);
        changed.setPackage(ctx.getPackageName());
        ctx.sendBroadcast(changed);
    }
}
