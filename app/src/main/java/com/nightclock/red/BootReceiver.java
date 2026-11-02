package com.nightclock.red;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the charger watcher after a reboot or an app update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ChargeService.start(context);
        }
    }
}
