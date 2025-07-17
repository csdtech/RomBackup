package com.sdtech.rombackup;

import android.app.Application;
import com.sdtech.rombackup.common.CrashHandler;


public class RomBackupApp extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.init(this);
    }
}
