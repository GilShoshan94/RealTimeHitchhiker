package com.realtimehitchhiker.hitchgo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sharedPref;
    private TextView txtShowRadius;
    private SeekBar barRadius;
    private int radius;
    private int minimum;
    private int maximum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Get the Intent that started this activity and extract the string
        //Intent intent = getIntent();
        //String message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);

        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        minimum = getResources().getInteger(R.integer.pref_radius_min);
        maximum = getResources().getInteger(R.integer.pref_radius_max);
        radius = sharedPref.getInt(getString(R.string.pref_radius), minimum); //minimum = defaultValue

        txtShowRadius = (TextView) findViewById(R.id.textView_prefs_radius_unit);
        barRadius = (SeekBar) findViewById(R.id.seekBar_prefs_radius);

        txtShowRadius.setText(String.valueOf(radius));
        txtShowRadius.append(" " + getString(R.string.pref_radius_unit));

        barRadius.setMax(maximum - minimum);
        barRadius.setProgress((radius - minimum));

        barRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                radius = (barRadius.getProgress() + minimum);
                txtShowRadius.setText(String.valueOf(radius));
                txtShowRadius.append(" " + getString(R.string.pref_radius_unit));

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
