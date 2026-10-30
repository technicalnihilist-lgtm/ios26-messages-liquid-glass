package com.liquidglass.messages;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Receives real SMS while this app is the default messaging app.
 * The message is written straight into the system SMS provider, so nothing is
 * lost even if the UI is not running.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) return;

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

        ContentValues v = new ContentValues();
        v.put(Telephony.Sms.ADDRESS, address);
        v.put(Telephony.Sms.BODY, body.toString());
        v.put(Telephony.Sms.DATE, when);
        v.put(Telephony.Sms.DATE_SENT, when);
        v.put(Telephony.Sms.READ, 0);
        v.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);
        v.put(Telephony.Sms.SEEN, 0);

        try {
            ctx.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, v);
        } catch (Exception ignored) {
        }

        Intent changed = new Intent(MainActivity.ACTION_SMS_CHANGED);
        changed.setPackage(ctx.getPackageName());
        ctx.sendBroadcast(changed);
    }
}
