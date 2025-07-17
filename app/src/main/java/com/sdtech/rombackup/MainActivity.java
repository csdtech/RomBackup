package com.sdtech.rombackup;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.sdtech.rombackup.common.ProgressDialog;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.sdtech.rombackup.RBConstants.*;

public class MainActivity extends Activity {

    private final int STORAGE_REQUEST_CODE = 12768;

    private ListView mPartitionList;
    private Button mStartBackup;
    private long mLastBackTime = 0;
    private boolean mBackupStart = false;

    private ArrayList<Partition> mPartitions;
    private ArrayList<Partition> mSelectedItems;
    private ArrayList<Partition> mMountedItems;
    private int mBytesPerKb;
    private PartitionAdapter mAdapter;
    private SharedPreferences mPrefs;
    private boolean settingLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPartitionList = findViewById(R.id.partitions_list);
        mStartBackup = findViewById(R.id.start_backup);
        mPartitions = new ArrayList<>();
        mSelectedItems = new ArrayList<>();
        mMountedItems = new ArrayList<>();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mBytesPerKb = mPrefs.getString(PREFS_SIZE_UNIT, "KiB").equals("KB") ? 1000 : 1024;

        mAdapter = new PartitionAdapter();
        mPartitionList.setAdapter(mAdapter);
        mStartBackup.setOnClickListener(v -> startBackup());

        new RootTask(false).execute();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            if(System.currentTimeMillis() - mLastBackTime <= 3000) {
                return super.onKeyDown(keyCode, event);
            } else {
                showToast("Press back again to exit");
                mLastBackTime = System.currentTimeMillis();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    private boolean canWriteSd() {
        return Build.VERSION.SDK_INT < 23
            ? true
            : (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    public void onRequestPermissionsResult(
        int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == STORAGE_REQUEST_CODE
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if(mBackupStart) {
                startBackup();
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if(settingLaunched && mAdapter.getCount() > 0) {
            ArrayList<Partition> mounted = new ArrayList<>();
            for(Partition partition : mPartitions) {
                if(hideMounted() && isMounted(partition)) {
                    mounted.add(partition);
                }
            }
            if(hideMounted() && mounted.size() > 0) {
                for(Partition partition : mounted) {
                    mAdapter.remove(partition);
                    if(mSelectedItems.contains(partition)) {
                        mSelectedItems.remove(partition);
                    }
                    if(!mMountedItems.contains(partition)) {
                        mMountedItems.add(partition);
                    }
                }
            }
            if(!hideMounted() && mMountedItems.size() > 0) {
                for(Partition partition : mMountedItems) {
                    if(!mPartitions.contains(partition)) {
                        mPartitions.add(partition);
                    }
                    if(partition.isChecked() && !mSelectedItems.contains(partition)) {
                        mSelectedItems.add(partition);
                    }
                }
                mAdapter.sort();
            }
            mBytesPerKb = mPrefs.getString(PREFS_SIZE_UNIT, "KiB").equals("KB") ? 1000 : 1024;
            mAdapter.notifyDataSetChanged();
            settingLaunched = false;
            mStartBackup.setVisibility(mSelectedItems.size() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.about_app) {
            startActivity(new Intent(this, AboutActivity.class));
        } else if(item.getItemId() == R.id.app_preference) {
            startActivity(new Intent(this, SettingActivity.class));
            settingLaunched = true;
        }
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        return super.onOptionsItemSelected(item);
    }

    private void requestWriteSd() {
        requestPermissions(
            new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
            },
            STORAGE_REQUEST_CODE);
    }

    private void startBackup() {
        mBackupStart = true;
        if(!canWriteSd()) {
            requestWriteSd();
        } else {
            new RootTask(true).execute();
        }
    }
    private void compressBackups(String backupPath) throws IOException {
        if(tarFormat()) {
            RootWorker.command(COMPRESS_TAR + " \"" + backupPath + "\"").waitToFininsh();
        } else {
            RootWorker.command(COMPRESS_ZIP + " \"" + backupPath + "\"").waitToFininsh();
        }                       
        if(deleteFolder()) {
            RootWorker.command("rm -rf \"" + backupPath + "\"").waitToFininsh();
        }
    }
    private void checkMountedItems() throws IOException {
        List<String> output = RootWorker.command("mount").getOutput();
        for(String line : output) {
            for(Partition p : mPartitions) {
                if(line.contains(p.getPath())) {
                    mMountedItems.add(p);
                }
            }
        }
        if(hideMounted()) {
            for(Partition p : mMountedItems) {
                if(mPartitions.contains(p)) {
                    mPartitions.remove(p);
                }
            }
        }
    }


    private void showDialog(String title, String message, boolean exit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setOnDismissListener(
        (dia) -> {
            if(exit) {
                System.exit(0);
            }
        });
        builder.setPositiveButton(
            exit ? "Exit" : "Close",
            (d, b) -> {
            if(exit) {
                System.exit(0);
            } else {
                d.dismiss();
            }
        });
        builder.show();
    }

    private boolean hideMounted() {
        return !mPrefs.getBoolean(PREFS_SHOW_MOUNTED, false);
    }

    private boolean isMounted(Partition p) {
        return mMountedItems.contains(p);
    }

    private boolean showSize() {
        return mPrefs.getBoolean(PREFS_SHOW_SIZE, true);
    }

    private boolean remountRO() {
        return mPrefs.getBoolean(PREFS_REMOUNT_RO, false);
    }

    private boolean formatSize() {
        return mPrefs.getBoolean(PREFS_READABLE_SIZE, true);
    }

    private boolean autoCompress() {
        return mPrefs.getBoolean(PREFS_AUTO_COMPRESS, false);
    }

    private boolean tarFormat() {
        return mPrefs.getBoolean(PREFS_TAR_GZ, false);
    }

    private boolean deleteFolder() {
        return mPrefs.getBoolean(PREFS_DELETE_FOLDER, true);
    }

    private void findPartitions() throws IOException  {
        List<String> list = RootWorker.command(FIND_PARTITIONS).getOutput();
        for(String line : list) {
            if(line.contains("->")) {
                String[] items = line.replace(" ->", "").split("\\s");
                String name = items[items.length - 2];
                String path = items[items.length - 1];
                if(!name.isEmpty() && !path.isEmpty()) {
                    if(name.startsWith(".")) {
                        name = name.substring(1);
                    }
                    Partition item = new Partition(name, path, 0);

                    mPartitions.add(item);
                }
            }
        }
        checkMountedItems();
        findPartitionSize();
    }

    private void findPartitionSize() throws NumberFormatException, IOException {
        final ArrayList<Partition> allParts = new ArrayList<>();
        allParts.addAll(mPartitions);
        if(hideMounted()) {
            allParts.addAll(mMountedItems);
        }
        for(Partition part : allParts) {
            long size = Long.parseLong(RootWorker.command(FIND_PARTITION_SIZE + " " + part.getPath())
                .getOutput()
                .get(0));
            part.setSize(size);
            part.setStatus(isMounted(part) ? "Mounted | Ready" : "Ready");
        }
        allParts.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // close our shell
        try {
            RootWorker.closeShell();
        } catch(IOException ignored) {}
    }

    private void setupFiles() throws IOException {
        final File bin = new File(getFilesDir(), "bin");
        if(bin.exists() && bin.list() != null && bin.list().length >= 3) {
            return;
        }
        final File archbin = new File(getFilesDir(), RomBackupUtils.getArchName() + "/bin");
        RomBackupUtils.deCompressZip(getAssets().open("rbdata"), getFilesDir());
        if(archbin.exists()) {
            bin.mkdirs();
            for(File file : archbin.listFiles()) {
                File fbin = new File(bin, file.getName());
                file.renameTo(fbin);
                fbin.setExecutable(true);
            }
            File scripts = new File(getFilesDir(), "scripts");
            for(File f : scripts.listFiles()) {
                f.setExecutable(true);
            }
        }
    }

    private class PartitionAdapter extends ArrayAdapter<Partition> {

        PartitionAdapter() {
            super(MainActivity.this, R.layout.partition_item, mPartitions);
        }

        @Override
        public int getCount() {
            return mPartitions.size();
        }

        @Override
        public Partition getItem(int pos) {
            return mPartitions.get(pos);
        }

        public void sort() {
            Comparator<Partition> c = (first,second) -> first.getName().compareTo(second.getName());

            sort(c);
        }

        @Override
        public View getView(int pos, View view, ViewGroup parent) {
            Partition item = getItem(pos);
            if(view == null) {
                view = getLayoutInflater().inflate(R.layout.partition_item, parent, false);
            }
            ImageView partitionIcon = view.findViewById(R.id.partition_icon);
            TextView partitionName = view.findViewById(R.id.partition_name);
            CheckBox partitionSelector = view.findViewById(R.id.partition_selector);
            TextView partitionSize = view.findViewById(R.id.partition_size);
            TextView partitionStatus = view.findViewById(R.id.partition_status);
            item.setOnCheckedListener(
            (b) -> {
                notifyDataSetChanged();
                if(!mSelectedItems.contains(item)) {
                    if(b) {
                        mSelectedItems.add(item);
                    }
                } else if(!b) {
                    mSelectedItems.remove(item);
                }
                mStartBackup.setVisibility(
                    mSelectedItems.size() > 0 ? View.VISIBLE : View.GONE);
            });
            partitionName.setText(item.getName());
            if(showSize()) {
                partitionSize.setText(
                    formatSize()
                    ? RomBackupUtils.readableFileSize(item.getSize(), mBytesPerKb)
                    : NumberFormat.getInstance().format(item.getSize()));
            } else {
                partitionSize.setText("unknown".toUpperCase());
            }
            partitionStatus.setText(item.getStatus());
            partitionSelector.setChecked(item.isChecked());
            view.setOnClickListener(b -> item.toggle());
            return view;
        }
    }

    private class Partition implements Checkable {

        private String mTitle;
        private String mPath;
        private long mSize;
        private boolean mChecked = false;
        private String mStatus;
        private CheckedListener mListener;

        Partition(String name, String path, long size) {
            mTitle = name;
            mPath = path;
            mStatus = "Not Ready";
            mSize = size;
        }

        public long getSize() {
            return mSize;
        }

        public String getPath() {
            return mPath;
        }

        public String getName() {
            return mTitle;
        }

        public void setName(String name) {
            mTitle = name;
        }

        public void setPath(String path) {
            this.mPath = path;
        }

        public void setSize(long size) {
            this.mSize = size;
        }

        public String getStatus() {
            return this.mStatus;
        }

        public void setStatus(String status) {
            this.mStatus = status;
        }

        @Override
        public void toggle() {
            setChecked(!mChecked);
        }

        @Override
        public void setChecked(boolean flag) {
            mChecked = flag;
            if(mListener != null) {
                mListener.onChecked(flag);
            }
        }

        public void setOnCheckedListener(CheckedListener listener) {
            mListener = listener;
        }

        @Override
        public boolean isChecked() {
            return mChecked;
        }
    }

    private interface CheckedListener {
        void onChecked(boolean checked);
    }

    private class RootTask extends AsyncTask<Void, String, Void> {
        private ProgressDialog dialog;
        private boolean mBackup;
        private String backupPath;

        public RootTask(boolean backup) {
            this.mBackup = backup;
        }

        @Override
        protected void onPreExecute() {
            dialog = new ProgressDialog(MainActivity.this);
            dialog.setMessage("Checking Root");
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected void onPostExecute(Void arg0) {
            dialog.dismiss();
        }

        @Override
        protected void onProgressUpdate(String... args) {
            String message = args[0];
            if(message.equals("ROOTERROR")) {
            	String details = args[1];
                showDialog("Root Error", details, true);
            } else if(message.equals("ERROR")) {
                String details = args[1];
                showDialog("Internal Error", details, false);
            } else if(message.equals("BACKUPDONE")) {
            	showDialog(
                    "Backup Completed",
                    String.format(
                        "%s %s saved to %s.",
                        mSelectedItems.size(),
                        mSelectedItems.size() > 1 ? "Partitions were" : "Partition was",
                        backupPath),
                    false);

                ArrayList<Partition> temp_selected = new ArrayList<>();
                for(Partition p : mSelectedItems) {
                    p.setStatus("Saved");
                    temp_selected.add(p);
                }
                for(Partition p : temp_selected) {
                    p.setChecked(false);
                }
                mSelectedItems.clear();
                mStartBackup.setVisibility(mSelectedItems.size() > 0 ? View.VISIBLE : View.GONE);
            } else if(message.equals("SETUPDONE")) {
            	mAdapter.sort();
            } else {
                dialog.setMessage(message);
            }
        }

        @Override
        protected Void doInBackground(Void... args) {
            try {
                if(mBackup) {
                    publishProgress("Making backup");
                    File sdcard = Environment.getExternalStorageDirectory();
                    long freeSpace = sdcard.getUsableSpace();
                    long backupSize = 0;
                    String backupName = Build.DEVICE + "-Backup";
                    backupPath = new File(sdcard, "RomBackup/" + backupName).getPath();
                    File backupDir = new File(backupPath);
                    if(backupDir.isDirectory()) {
                        int count = 0;
                        do{
                            backupDir = new File(backupPath + ++count);
                        }while(backupDir.isDirectory());
                    }
                    backupPath = backupDir.getPath();
                    final List<String> backup_commands = new ArrayList<>();
                    for(Partition p : mSelectedItems) {
                        backupSize += p.getSize();
                        if(remountRO() && isMounted(p)) {
                            backup_commands.add(
                                String.format(
                                    "%s/%s mount -o remount ro %s", FILES_PATH, "bin/busybox", p.getPath()));
                        }
                        backup_commands.add(
                            String.format(
                                "%s/%s dd if=%s 'of=%s/%s.%s'", FILES_PATH, "bin/busybox",
                                p.getPath(), backupPath, p.getName(), "img"));
                    }
                    // leave atleast 50MB of free space
                    if(backupSize > (freeSpace - (50L * mBytesPerKb * mBytesPerKb))) {
                        publishProgress("ERROR",
                            String.format(
                                "Not enought space on device to backup selected %s.\nPlease free up space and try again.\nBackup Size: %s\nFree Space: %s",
                                mSelectedItems.size() >= 2 ? "partitions" : "partition",
                                RomBackupUtils.readableFileSize(backupSize, mBytesPerKb),
                                RomBackupUtils.readableFileSize(
                                    freeSpace - (50L * mBytesPerKb * mBytesPerKb), mBytesPerKb)));

                        return null;
                    }
                    new File(backupPath).mkdirs();

                    RootWorker.command(backup_commands.toArray(new String[0])).waitToFininsh();
                    if(autoCompress()) {
                        publishProgress("Compressing Backup");
                        compressBackups(backupPath);
                    }
                    publishProgress("BACKUPDONE");
                } else {
                    publishProgress("Initializing tools");
                    setupFiles();
                    publishProgress("Checking Root");
                    if(RootWorker.rootGranted()) {
                        publishProgress("Searching partitions");
                        findPartitions();
                        publishProgress("SETUPDONE");
                    } else {
                        publishProgress("ROOTERROR", RootWorker.rootFound() ? "You will not grant the root access and this app cannot work without root access." : "Your device seem to be not rooted and this app can not work without root.");
                    }
                }     
            } catch(IOException | NumberFormatException e) {
                publishProgress("ERROR", "Internal Error occured while performing root task because:\n\n" + RomBackupUtils.getStackTrace(e));
            }
            return null;
        }
    }
}
