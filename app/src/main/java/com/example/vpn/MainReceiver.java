package com.example.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MainReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final String TAG = "KavoshNetworkManager.Receiver";
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
            Log.i(TAG, "onReceive: " + intent.getAction());
            Intent mIntent = new Intent(context, MainService.class);
            context.startForegroundService(mIntent);
        }
    }
}
