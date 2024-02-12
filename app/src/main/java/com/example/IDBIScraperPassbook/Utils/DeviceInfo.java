package com.example.IDBIScraperPassbook.Utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

public class DeviceInfo {

    public static String getModelNumber() {
        return Build.MODEL;
    }

    public static String generateSecureId(Context context) {
        return getAndroidId(context);
    }

    @SuppressLint("HardwareIds")
    private static String getAndroidId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

}
