package com.sdtech.rombackup;

import android.os.Build;
import static com.sdtech.rombackup.RBConsonants.*;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipOutputStream;

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
    
    private boolean settingLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPartitionList = findViewById(R.id.partitions_list);
        mStartBackup = findViewById(R.id.start_backup);
        mPartitions = new ArrayList<>();
        mSelectedItems = new ArrayList<>();
        mMountedItems= new ArrayList<>();
        mBytesPerKb = PreferenceManager.getDefaultSharedPreferences(this).getInt(PREFS_BYTES_SIZE, 1024);
        
        mAdapter = new PartitionAdapter();
        mPartitionList.setAdapter(mAdapter);
        mStartBackup.setOnClickListener((v)->startBackup());
        
        if (RootWorker.rootGranted()) {
            setupFiles();
            findPartitions();
        } else {
            showDialog("Sorry!",RootWorker.rootFound() ? "You will not grant the root access and this app cannot work without root access." : "Your device seem to be not rooted and this app can not work without root.",true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            if (System.currentTimeMillis() - mLastBackTime <= 3000) {
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
        return Build.VERSION.SDK_INT < 23 ? true : (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (mBackupStart) {
                startBackup();
            }
        }
    }
    
    private void saveLog(Throwable cause) {
    	try {
    		RomBackupUtils.saveLog(new File(getExternalFilesDir(null),"crashes").getPath(),cause);
    	} catch(Exception ignored) {
    		
    	}
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if(settingLaunched && mAdapter.getCount() > 0) {
            ArrayList<Partition> mounted = new ArrayList<>();
            for(Partition partition : mPartitions) {
            	if (hideMounted() && isMounted(partition)) {
                    sendLog("partition " + partition.getName() + " is mounted. removing");
                    mounted.add(partition);
                }
            }
            if(hideMounted()&&mounted.size()>0){
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
            if(!hideMounted()&&mMountedItems.size()>0){
                for(Partition partition : mMountedItems) {
                    if(!mPartitions.contains(partition)) {
                    	mPartitions.add(partition);
                    }
                    if(partition.isChecked()&&!mSelectedItems.contains(partition)){
                        mSelectedItems.add(partition);
                    }
                }
                mAdapter.sort();
            }
        	mAdapter.notifyDataSetChanged();
            settingLaunched=false;
            mStartBackup.setVisibility(mSelectedItems.size() > 0 ? View.VISIBLE : View.GONE);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==R.id.about_app) {
        	startActivity(new Intent(this,AboutActivity.class));
        } else if(item.getItemId()==R.id.app_preference){
        	startActivity(new Intent(this,SettingActivity.class));
            settingLaunched=true;
        }
        overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
        return super.onOptionsItemSelected(item);
    }
    
    
    private void requestWriteSd() {
        requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_REQUEST_CODE);
    }

    private void startBackup() {
        mBackupStart = true;
        if (!canWriteSd()) {
            requestWriteSd();
        } else {
            File sdcard = Environment.getExternalStorageDirectory();
            long freeSpace = sdcard.getUsableSpace();
            long backupSize = 0;
            String backupName = Build.DEVICE+"_"+RomBackupUtils.getFormattedTime();
            String backupPath = new File(sdcard,"Rom Backup/"+backupName).getPath();
            final List<String> backup_commands = new ArrayList<>();
            for(Partition p : mSelectedItems) {
            	backupSize+=p.getSize();
                backup_commands.add(String.format("cat %s > '%s/%s.%s'",p.getPath(),backupPath,p.getName(),"img"));
            }
            //leave atleast 50MB of free space
            if(backupSize > (freeSpace - (50 * mBytesPerKb * mBytesPerKb))) {
            	showDialog(String.format("Not enought space on device to backup selected partitions.\nPlease free up space and try again.\nBackup Size: %s\nFree Space: %s",RomBackupUtils.readableFileSize(backupSize,mBytesPerKb),RomBackupUtils.readableFileSize(freeSpace,mBytesPerKb)),false);
                return;
            }
            try {
                new File(backupPath).mkdirs();
                ExecutorService mExecutor = Executors.newSingleThreadExecutor();
                Future result = mExecutor.submit(()->{
                    try{
                        RootWorker.command(backup_commands.toArray(new String[0]));
                    } catch (Exception e) {
                        sendLog(RomBackupUtils.getStackTrace(e));
                    }
                });
                result.get();
                showDialog("Backup Completed",String.format("%s %s is saved to %s.",mSelectedItems.size(),mSelectedItems.size() > 1 ? "Partitions" : "Partition",backupPath),false);
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
                if(autoCompress()) {
                	if(tarFormat()) {
                		ExecutorService mExecutor2 = Executors.newSingleThreadExecutor();
                        Future result2 = mExecutor2.submit(()->{
                            try{
                                RootWorker.command(COMPRESS_TAR + " \"" + backupPath + "\"");
                            } catch (Exception e) {
                                sendLog(RomBackupUtils.getStackTrace(e));
                            }
                        });
                        result2.get();
                        if(deleteFolder()) {
                            RootWorker.command("rm -rf \"" + backupPath +"\"");
                        }
                	} else {
                		ExecutorService mExecutor2 = Executors.newSingleThreadExecutor();
                        Future result2 = mExecutor2.submit(()->{
                            try{
                                RootWorker.command(COMPRESS_ZIP + " \"" + backupPath + "\"");
                            } catch (Exception e) {
                                sendLog(RomBackupUtils.getStackTrace(e));
                            }
                        });
                        result2.get();
                        if(deleteFolder()) {
                            RootWorker.command("rm -rf \"" + backupPath +"\"");
                        }
                	}
                }
            } catch (Exception e) {
                saveLog(e);
            }
        }
    }
    private void checkMountedItems() throws Exception {
        if(!hideMounted()) {
        	return;
        }
    	ExecutorService mExecutor2 = Executors.newSingleThreadExecutor();
        Future result2 = mExecutor2.submit(()->{
            try{
               List<String> output = RootWorker.command("mount").getOutput();
                for(String line : output) {
                    for(Partition p : mPartitions) {
                    	if(line.contains(p.getPath())) {
                    		mMountedItems.add(p);
                    	}
                    }
                }
                for(Partition p : mMountedItems) {
                	if(mPartitions.contains(p)) {
                		mPartitions.remove(p);
                	}
                }
            } catch (Exception e) {
                sendLog(RomBackupUtils.getStackTrace(e));
            }
        });
        result2.get();
    }
    private void showDialog(String message,boolean exit) {
        showDialog(getResources().getString(R.string.app_name), message,exit);
    }

    private void showDialog(String title, String message,boolean exit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setOnDismissListener((dia) -> {
            if(exit){
                System.exit(0);
            }
        });
        builder.setPositiveButton(exit ? "Exit" : "Close", (d, b) -> {
            if(exit){
                System.exit(0);
            }else{
                d.dismiss();
            }
        });
        builder.show();
    }

    private boolean hideMounted() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREFS_HIDE_UNSUPPORTED, true);
    }

    private boolean isMounted(Partition p) {
        return mMountedItems.contains(p);
    }

    private boolean showSize() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREFS_SHOW_SIZE, true);
    }

    private boolean formatSize() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREFS_READABLE_SIZE, true);
    }
    
    private boolean autoCompress() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREFS_AUTO_COMPRESS, false);
    }
    
    private boolean tarFormat() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREFS_TAR_GZ, false);
    }
    
    private boolean deleteFolder() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREFS_DELETE_FOLDER, true);
    }
    
    private void sendLog(String txt) {
        try {
            FileWriter writer = new FileWriter(new File(this.getExternalFilesDir(null), "rb_logs.txt"), true);
            writer.write(RomBackupUtils.getFormattedTime() + "   --->   " + txt + "\n");
            writer.flush();
            writer.close();
        } catch (Exception ignored) {}
    }
    
    private void findPartitions() {
        try {
            ExecutorService mExecutor = Executors.newSingleThreadExecutor();
            Future result = mExecutor.submit(()->{
                try{
                    List<String> list = RootWorker.command(FIND_PARTITIONS).getOutput();
                    for (String line : list) {
                        if (line.contains("->")) {
                            String[] items = line.replace(" ->", "").split("\\s");
                            String name = items[items.length - 2];
                            String path = items[items.length - 1];
                            if (!name.isEmpty() && !path.isEmpty()) {
                                if (name.startsWith(".")) {
                                    name = name.substring(1);
                                }
                                Partition item = new Partition(name, path, 0);
                                
                                mPartitions.add(item);
                            }
                        }
                    }
                    findPartitionSize();
                    checkMountedItems();
                    runOnUiThread(()->{
                        mAdapter.sort();
                        mAdapter.notifyDataSetChanged();
                    });
                } catch (Exception err) {
                    saveLog(err);
                }
            });
            result.get();
            mExecutor.shutdown();
        } catch (Exception err) {
            saveLog(err);
        }
    }

    private void findPartitionSize() throws Exception {
        ExecutorService mExecutor = Executors.newSingleThreadExecutor();
        Future result = mExecutor.submit(() -> {
            final ArrayList<Partition> allParts = new ArrayList<>();
            allParts.addAll(mPartitions);
            allParts.addAll(mMountedItems);
            try {
                for(Partition part : allParts){
                    sendLog("finding the size of partition: " + part.getName());
                    long size = Long.parseLong(RootWorker.command(FIND_PARTITION_SIZE + " " + part.getPath()).getOutput().get(0));
                    part.setSize(size);
                    part.setStatus("Ready");
                    sendLog(part.getName() + " partition size found: "+  RomBackupUtils.readableFileSize(size, mBytesPerKb));
                }
                allParts.clear();
            } catch (Exception e) {
               saveLog(e);
            } 
        });
        result.get();
        mExecutor.shutdown();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // close our shell
        RootWorker.closeShell();
    }
    
    private void setupFiles() {
    	final File bin = new File(getFilesDir(),"bin");
        if(bin.exists()&&bin.list()!=null){
            return;
        }
        final File archbin = new File(getFilesDir(),RomBackupUtils.getArchName()+"/bin");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future result = executor.submit(()->{
            try {
            	RomBackupUtils.deCompressZip(getAssets().open("rbdata"),getFilesDir());
                if(archbin.exists()){
                    bin.mkdirs();
                    for(File file : archbin.listFiles()) {
                    	RomBackupUtils.copyFile(new File(bin,file.getName()),file);
                    }
                    for(File fbin:bin.listFiles()){
                        fbin.setExecutable(true);
                    }
                    File scripts = new File(getFilesDir(),"scripts");
                    for(File f : scripts.listFiles()) {
                    	f.setExecutable(true);
                    }
                }
            } catch(Exception err) {
            	saveLog(err);
            }
        });
        try{
            result.get();
            executor.shutdown();
        }catch(Exception e){
            saveLog(e);
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
        public void sort(){
            Comparator<Partition> c = new Comparator<Partition>(){
                @Override
                public int compare(Partition first, Partition second) {
                    return first.getName().compareTo(second.getName());
                }
            };
            sort(c);
        }
        @Override
        public View getView(int pos, View view, ViewGroup parent) {
            Partition item = getItem(pos);
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.partition_item, parent, false);
            }
            ImageView partitionIcon = view.findViewById(R.id.partition_icon);
            TextView partitionName = view.findViewById(R.id.partition_name);
            CheckBox partitionSelector = view.findViewById(R.id.partition_selector);
            TextView partitionSize = view.findViewById(R.id.partition_size);
            TextView partitionStatus = view.findViewById(R.id.partition_status);
            item.setOnCheckedListener((b) -> {
                notifyDataSetChanged();
                if (!mSelectedItems.contains(item)) {
                    if (b) {
                        mSelectedItems.add(item);
                    }
                } else if (!b) {
                    mSelectedItems.remove(item);
                }
                mStartBackup.setVisibility(mSelectedItems.size() > 0 ? View.VISIBLE : View.GONE);
            });
            partitionName.setText(item.getName());
            if (showSize()) {
                partitionSize.setText(formatSize() ? RomBackupUtils.readableFileSize(item.getSize(), mBytesPerKb) : NumberFormat.getInstance().format(item.getSize()));
            } else {
                partitionSize.setText("unknown");
            }
            partitionStatus.setText(item.getStatus());
            partitionSelector.setChecked(item.isChecked());
            view.setOnClickListener((v) -> item.toggle());
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
            if (mListener != null) {
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
}
