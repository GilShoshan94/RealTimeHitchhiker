package com.realtimehitchhiker.hitchgo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sharedPref;
    private TextView txtShowRadius;
    private SeekBar barRadius;
    private int radius;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Get the Intent that started this activity and extract the string
        //Intent intent = getIntent();
        //String message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);

        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        int defaultValue = getResources().getInteger(R.integer.pref_radius_min);
        radius = sharedPref.getInt(getString(R.string.pref_radius), defaultValue);

        txtShowRadius = (TextView) findViewById(R.id.textView_prefs_radius_unit);
        barRadius = (SeekBar) findViewById(R.id.seekBar_prefs_radius);

        txtShowRadius.setText(radius + R.string.pref_radius_unit);
        barRadius.setProgress(radius);

        barRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                radius = barRadius.getProgress();
                txtShowRadius.setText(radius + R.string.pref_radius_unit);

                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putInt(getString(R.string.pref_radius), radius);
                editor.commit();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }
}
