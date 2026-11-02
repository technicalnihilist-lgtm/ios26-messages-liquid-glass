package com.liquidglass.messages;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
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
