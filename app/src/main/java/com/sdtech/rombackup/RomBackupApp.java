package com.sdtech.rombackup;

import android.app.Application;

public class RomBackupApp extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        RBCrashHandler.init(this);
    }
}
