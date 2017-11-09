package com.realtimehitchhiker.hitchgo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.test.mock.MockPackageManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;

public class MainActivity extends AppCompatActivity {
    //public static final String EXTRA_MESSAGE = "com.realtimehitchhiker.hitchgo.MESSAGE"; for intent //intent.putExtra(EXTRA_MESSAGE, message); to be unique

    private Button btnShowLocation;
    private TextView txtShowLocation;
    private static final int PERMISSION_LOCATION_REQUEST_CODE = 2;
    private BroadcastReceiver broadcastReceiverLocUpdate;
    private BroadcastReceiver broadcastReceiverLocOff;
    private Location location = null;

    //flag
    int flag_service = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //For Facebook SDK
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);

        setContentView(R.layout.activity_main);
        btnShowLocation = (Button) findViewById(R.id.button_testCoordinates);
        txtShowLocation = (TextView) findViewById(R.id.textView_testCoordinates);

        if(!runtimePermissions())
            enableLocationService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(broadcastReceiverLocUpdate == null){
            broadcastReceiverLocUpdate = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    location = (Location)intent.getExtras().get("location");
                    txtShowLocation.setText("Lat :\t"+location.getLatitude()
                            +"\nLong :\t"+location.getLongitude()+"\nProvider :\t"
                            +location.getProvider());
                }
            };
        }
        if(broadcastReceiverLocOff == null){
            broadcastReceiverLocOff = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.i("TESTGPS", "BROADCAST_ACTION_LOC_OFF" );
                    if(isAllActiveProviderDisabled())
                        showSettingsAlert();
                }
            };
        }
        registerReceiver(broadcastReceiverLocUpdate,new IntentFilter(LocationService.BROADCAST_ACTION_LOC_UPDATE));
        registerReceiver(broadcastReceiverLocOff,new IntentFilter(LocationService.BROADCAST_ACTION_LOC_OFF));
    }

    /**
     * Function to show settings alert dialog
     * On pressing Settings button will launch Settings Options
     * */
    public void showSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
        // Setting Dialog Title
        alertDialog.setTitle("GPS is settings");
        // Setting Dialog Message
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");
        // On pressing Settings button
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int which) {
                dialog.dismiss();
                dialog.cancel();
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                getApplicationContext().startActivity(intent);
            }
        });
        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                showSettingsAlert();
            }
        });
        // Showing Alert Message
        alertDialog.show();
    }

    /**
     * Called when the user taps the button_login
     */
    public void callLoginActivity(View view) {
        // Explicit Intent by specifying its class name
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
    }

    /**
     * Called when the user taps the imageButton_settings
     */
    public void callSettingsActivity(View view) {
        // Explicit Intent by specifying its class name
        Intent intent = new Intent(this, SettingsActivity.class);
        // Starts TargetActivity
        startActivity(intent);
    }

    private void enableLocationService() {

        btnShowLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (flag_service == 0) {
                    flag_service++;
                    Toast.makeText(getApplicationContext(), "Location Service ON",
                            Toast.LENGTH_SHORT).show();
                    Intent i_start = new Intent(getApplicationContext(), LocationService.class);
                    startService(i_start);
                } else {
                    flag_service = 0;
                    Toast.makeText(getApplicationContext(), "Location Service OFF",
                            Toast.LENGTH_SHORT).show();
                    Intent i_stop = new Intent(getApplicationContext(), LocationService.class);
                    stopService(i_stop);
                }
            }
        });
    }

    /**
     * Checks if we needs permissions and requests them
     */
    private boolean runtimePermissions() {
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {

                // Check if explanation needed
                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) || ActivityCompat.shouldShowRequestPermissionRationale(
                        MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)) {

                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                    alertDialog.setTitle("App needs Location permission");  // Setting Dialog Title
                    alertDialog.setMessage("Grant to location permission or leave the app");     // Setting Dialog Message
                    // on pressing ok button
                    alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                                    PERMISSION_LOCATION_REQUEST_CODE);
                        }
                    });
                    // on pressing no button
                    alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(0);  // terminates the Linux process and all threads for the app ( so no background)
                        }
                    });
                    alertDialog.show();     // Showing Alert Message
                } else {   // No explanation needed, we can request the permission.
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                            PERMISSION_LOCATION_REQUEST_CODE);
                }

                return true;
            }
            else
                return false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isAllActiveProviderDisabled(){
        int tot=0;
        LocationManager locman = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        for (int i = 0; i < LocationService.activeProviderList.length; i++) {
            if (!locman.isProviderEnabled(LocationService.activeProviderList[i]))
                tot++;
        }
        if (tot == LocationService.activeProviderList.length)
            return true;
        else
            return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_LOCATION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                enableLocationService();
            } else {
                runtimePermissions();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(broadcastReceiverLocUpdate != null){
            unregisterReceiver(broadcastReceiverLocUpdate);
        }
        if(broadcastReceiverLocOff != null){
            unregisterReceiver(broadcastReceiverLocOff);
        }
    }
}