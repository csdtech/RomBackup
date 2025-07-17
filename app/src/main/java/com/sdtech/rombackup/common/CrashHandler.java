package com.sdtech.rombackup.common;

import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class CrashHandler {

    public static final UncaughtExceptionHandler DEFAULT_UNCAUGHT_EXCEPTION_HANDLER = Thread.getDefaultUncaughtExceptionHandler();

    public static void init(Application app) {
        init(app, null);
    }

    public static void init(final Application app, final String crashDir) {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler(){

            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                try {
                    tryUncaughtException(thread, throwable);
                } catch(Throwable e) {
                    e.printStackTrace();
                    if(DEFAULT_UNCAUGHT_EXCEPTION_HANDLER != null)
                        DEFAULT_UNCAUGHT_EXCEPTION_HANDLER.uncaughtException(thread, throwable);
                }
            }

            private void tryUncaughtException(Thread thread, Throwable throwable) {
                final String time = new SimpleDateFormat("dd-MM-yyyy_hh-mm-ss_a").format(new Date());
                File crashFile = new File(TextUtils.isEmpty(crashDir) ? new File(app.getExternalFilesDir(null), "crash")
                : new File(crashDir), "crash_" + time + ".txt");

                String versionName = "unknown";
                long versionCode = 0;
                try { 
                    PackageInfo packageInfo = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
                    versionName = packageInfo.versionName;
                    versionCode = Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode()
                    : packageInfo.versionCode;
                } catch(PackageManager.NameNotFoundException ignored) {}

                String fullStackTrace; {
                    StringWriter sw = new StringWriter(); 
                    PrintWriter pw = new PrintWriter(sw);
                    throwable.printStackTrace(pw);
                    fullStackTrace = sw.toString();
                    pw.close();
                }

                StringBuilder sb = new StringBuilder();
                sb.append("************* Crash Head ****************\n");
                sb.append("Time Of Crash      : ").append(time).append("\n");
                sb.append("Device Manufacturer: ").append(Build.MANUFACTURER).append("\n");
                sb.append("Device Model       : ").append(Build.MODEL).append("\n");
                sb.append("Android Version    : ").append(Build.VERSION.RELEASE).append("\n");
                sb.append("Android SDK        : ").append(Build.VERSION.SDK_INT).append("\n");
                sb.append("App VersionName    : ").append(versionName).append("\n");
                sb.append("App VersionCode    : ").append(versionCode).append("\n");
                sb.append("************* Crash Head ****************\n");
                sb.append("\n").append(fullStackTrace);

                String errorLog = sb.toString();

                try {
                    writeFile(crashFile, errorLog);
                } catch(IOException ignored) {}

                gotoCrashActiviy: {
                    Intent intent = new Intent(app, CrashActivity.class);
                    intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    );
                    intent.putExtra(CrashActivity.EXTRA_CRASH_INFO, errorLog);
                    try {
                        app.startActivity(intent);
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(0);
                    } catch(ActivityNotFoundException e) {
                        e.printStackTrace();
                        if(DEFAULT_UNCAUGHT_EXCEPTION_HANDLER != null)
                            DEFAULT_UNCAUGHT_EXCEPTION_HANDLER.uncaughtException(thread, throwable);
                    }
                }

            }

            private void writeFile(File file, String content) throws IOException {
                File parentFile = file.getParentFile();
                if(parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs();
                }
                file.createNewFile();
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(content.getBytes());
                try {
                    fos.close();
                } catch(IOException e) {}
            }

        });
    }

}

