package com.realtimehitchhiker.hitchgo;

import android.Manifest;
import android.content.Intent;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.test.mock.MockPackageManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;

public class MainActivity extends AppCompatActivity {
    //public static final String EXTRA_MESSAGE = "com.realtimehitchhiker.hitchgo.MESSAGE";

    Button btnShowLocation;
    TextView txtShowLocation;
    String message;
    private static final int REQUEST_CODE_PERMISSION = 2;
    String mPermission = Manifest.permission.ACCESS_FINE_LOCATION;

    // GPSTracker class
    GPSTracker gps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //For Facebook SDK
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);

        setContentView(R.layout.activity_main);

        try {
            if (ActivityCompat.checkSelfPermission(this, mPermission)
                    != MockPackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{mPermission},
                        REQUEST_CODE_PERMISSION);

                // If any permission above not allowed by user, this condition will execute every time, else your else part will work
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        btnShowLocation = (Button) findViewById(R.id.button_testCoordinates);
        txtShowLocation = (TextView) findViewById(R.id.textView_testCoordinates);

        // show location button click event
        btnShowLocation.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // create class object
                gps = new GPSTracker(MainActivity.this);

                // check if GPS enabled
                if(gps.canGetLocation()){

                    double latitude = gps.getLatitude();
                    double longitude = gps.getLongitude();

                    message = "Your Location is - \nLat: "
                            + latitude + "\nLong: " + longitude;
                    txtShowLocation.setText(message);
                }else{
                    // can't get location
                    // GPS or Network is not enabled
                    // Ask user to enable GPS/network in settings
                    gps.showSettingsAlert();
                }

            }
        });
    }

    /** Called when the user taps the button_login */
    public void callLoginActivity(View view) {
        // Explicit Intent by specifying its class name
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
    }

    /** Called when the user taps the imageButton_settings */
    public void callSettingsActivity(View view) {
        // Explicit Intent by specifying its class name
        Intent intent = new Intent(this, SettingsActivity.class);
        //EditText editText = (EditText) findViewById(R.id.editText);
        //String message = editText.getText().toString();
        //intent.putExtra(EXTRA_MESSAGE, message);
        // Starts TargetActivity
        startActivity(intent);
    }
}

/**
 * private void setUpActionBar() {
 *  // Make sure we're running on Honeycomb or higher to use ActionBar APIs
 *  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
 *      ActionBar actionBar = getActionBar();
 *      actionBar.setDisplayHomeAsUpEnabled(true);
 *  }
 * }
 *
 *
 Note:
 When parsing XML resources, Android ignores XML attributes that aren’t supported
 by the current device. So you can safely use XML attributes that are only supported
 by newer versions without worrying about older versions breaking when they encounter that code.
 For example, if you set the targetSdkVersion="11", your app includes the ActionBar by default
 on Android 3.0 and higher. To then add menu items to the action bar, you need to set
 android:showAsAction="ifRoom" in your menu resource XML. It's safe to do this in a
 cross-version XML file, because the older versions of Android simply ignore
 the showAsAction attribute (that is, you do not need a separate version in res/menu-v11/).
*/