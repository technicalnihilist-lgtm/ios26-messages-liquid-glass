package com.liquidglass.messages;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Telephony;
import android.telephony.SmsManager;

import java.util.ArrayList;

/**
 * Lets the system send a text on our behalf (quick reply from a notification).
 * Required component for holding the default-SMS role.
 */
public class HeadlessSmsSendService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null) {
                Uri data = intent.getData();
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (data != null && text != null) {
                    String addr = data.getSchemeSpecificPart();
                    if (addr != null && !addr.isEmpty()) {
                        SmsManager sm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                ? getSystemService(SmsManager.class)
                                : SmsManager.getDefault();
                        if (sm != null) {
                            ArrayList<String> parts = sm.divideMessage(text);
                            sm.sendMultipartTextMessage(addr, null, parts, null, null);
                            // record it, exactly like the in-app send path does, so a
                            // quick reply does not vanish from the conversation
                            long now = System.currentTimeMillis();
                            Uri stored = null;
                            try {
                                ContentValues v = new ContentValues();
                                v.put(Telephony.Sms.ADDRESS, addr);
                                v.put(Telephony.Sms.BODY, text);
                                v.put(Telephony.Sms.DATE, now);
                                v.put(Telephony.Sms.READ, 1);
                                v.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT);
                                stored = getContentResolver().insert(Telephony.Sms.CONTENT_URI, v);
                            } catch (Exception ignored) {}
                            if (stored == null) LocalStore.addOut(this, addr, text, now);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }
}
