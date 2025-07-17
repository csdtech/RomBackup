package com.sdtech.rombackup;

import android.os.Bundle;
import android.app.Activity;
import android.widget.TextView;
import android.text.method.LinkMovementMethod;
import android.text.Html;

public class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        TextView info = findViewById(R.id.app_description);
        info.setMovementMethod(new LinkMovementMethod());
        String details = "ROM Backup app allow you to backup some partitions or ";
        details += "the entire ROM of your phone.<br>The saved partition can be ";
        details += "restored using the 'fastboot flash' command. <br><cite><font color=\"red\">";
        details += "<b>Note: </b></font>Make sure you have an expertise about fastboot command";
        details += " and your can recover your phone when bricked, otherwise do not try to";
        details += " restore the backup yourself, the authors of this app are not responsible ";
        details += "for any damage you caused to your device. Proceed with caution!</cite>";
        details += "<br><br>For more info see the app on <a href=\"https://github.com/csdtech/ROMBackup";
        details += "\">Github</a>.";
        info.setText(Html.fromHtml(details));
    }
}
