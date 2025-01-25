package com.sdtech.rombackup;

import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceActivity;

public class SettingActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.rb_preferences);
    }
}
