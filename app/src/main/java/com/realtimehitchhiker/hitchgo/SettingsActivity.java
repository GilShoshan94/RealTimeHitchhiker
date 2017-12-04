package com.realtimehitchhiker.hitchgo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class SettingsActivity extends AppCompatActivity {
    public static final String BROADCAST_ACTION_RADIUS_UPDATE = "com.realtimehitchhiker.hitchgo.RADIUS_UPDATE";
    public static final String EXTRA_RADIUS_MESSAGE = "com.realtimehitchhiker.hitchgo.EXTRA_RADIUS_MESSAGE";

    private SharedPreferences sharedPref;
    private TextView txtShowRadius;
    private SeekBar barRadius;
    private Button btnLogout;
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

        txtShowRadius = findViewById(R.id.textView_prefs_radius_unit);
        barRadius = findViewById(R.id.seekBar_prefs_radius);
        btnLogout = findViewById(R.id.button_logout);

        txtShowRadius.setText(String.valueOf(radius));
        txtShowRadius.append(" " + getString(R.string.settings_radius_unit));

        barRadius.setMax(maximum - minimum);
        barRadius.setProgress((radius - minimum));

        barRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                radius = (barRadius.getProgress() + minimum);
                txtShowRadius.setText(String.valueOf(radius));
                txtShowRadius.append(" " + getString(R.string.settings_radius_unit));

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

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AuthUI.getInstance()
                        .signOut(getApplication())
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            public void onComplete(@NonNull Task<Void> task) {
                                // user is now signed out
                                boolean flag_supply = false;
                                boolean flag_demand = false;
                                SharedPreferences.Editor editor = sharedPref.edit();
                                editor.putBoolean(getString(R.string.pref_supply_status), flag_supply);
                                editor.putBoolean(getString(R.string.pref_demand_status), flag_demand);
                                editor.apply();

                                updateUI(currentUser);
                            }
                        });
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        broadcastRadiusUpdate();
    }

    private void broadcastRadiusUpdate(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_RADIUS_UPDATE);
        intent.putExtra(EXTRA_RADIUS_MESSAGE, radius);
        localBroadcastManager.sendBroadcast(intent);
    }
}

//todo
//refUsers.child(facebookUserId).child("phone").setValue(phone_number);