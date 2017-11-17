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
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.ResultCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    //public static final String EXTRA_MESSAGE = "com.realtimehitchhiker.hitchgo.MESSAGE"; for intent //intent.putExtra(EXTRA_MESSAGE, message); to be unique
    private static final int PERMISSION_LOCATION_REQUEST_CODE = 2;
    private static final int RC_SIGN_IN = 123; //FireBase
    public static final String TAG = "MAIN";

    //FireBase
    private FirebaseAuth mAuth;
    private FirebaseDatabase database;

    // Choose authentication providers
    List<AuthUI.IdpConfig> authProviders = Arrays.asList(
            new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
            new AuthUI.IdpConfig.Builder(AuthUI.PHONE_VERIFICATION_PROVIDER).build(),
            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build(),
            new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER).build()//, //to add more permission than the default, add ".setPermissions(Arrays.asList("user_friends"))" before ".build()"
            //new AuthUI.IdpConfig.Builder(AuthUI.TWITTER_PROVIDER).build()
    );

    private Button btnShowLocation;
    private Button btnLog;
    private TextView txtShowLocation;
    private BroadcastReceiver broadcastReceiverLocUpdate;
    private BroadcastReceiver broadcastReceiverLocOff;
    private Location location = null;

    //flag
    int flag_service = 0;
    int flag_login = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MAIN_onCreate" );
        //For Facebook SDK
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);

        setContentView(R.layout.activity_main);
        //For FireBase
        mAuth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();

        btnShowLocation = (Button) findViewById(R.id.button_testCoordinates);
        txtShowLocation = (TextView) findViewById(R.id.textView_testCoordinates);
        btnLog = (Button) findViewById(R.id.button_login);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "MAIN_onStart" );

        // Check permissions and start service location
        if(!runtimePermissions())
            enableLocationService();

        enableLogButtton();

        //For FireBase
        // Check if user is signed in (non-null) and update UI accordingly.
        if (mAuth.getCurrentUser() != null) {
            // already signed in
            //updateUI(currentUser);
        } else {
            // not signed in
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MAIN_onResume" );
        if(broadcastReceiverLocUpdate == null){
            broadcastReceiverLocUpdate = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOC_UPDATE );
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
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOC_OFF );
                    if(isAllActiveProviderDisabled())
                        showSettingsAlert();
                }
            };
        }
        registerReceiver(broadcastReceiverLocUpdate,new IntentFilter(LocationService.BROADCAST_ACTION_LOC_UPDATE));
        registerReceiver(broadcastReceiverLocOff,new IntentFilter(LocationService.BROADCAST_ACTION_LOC_OFF));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "MAIN_onPause" );
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "MAIN_onStop" );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MAIN_onDestroy" );
        if(broadcastReceiverLocUpdate != null){
            unregisterReceiver(broadcastReceiverLocUpdate);
        }
        if(broadcastReceiverLocOff != null){
            unregisterReceiver(broadcastReceiverLocOff);
        }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "MAIN_onActivityResult" );

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);
            if (response != null) {
                Log.d(TAG, "RC_SIGN_IN_response = " + response.toString());
            }
            if (resultCode == RESULT_OK) {
                // Successfully signed in
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                Log.d(TAG, "Successfully signed in = " + user.toString());
                //startActivity(SignedInActivity.createIntent(this, response));
                //finish();
                //return;
            } else {
                // Sign in failed, check response for error code
                Log.d(TAG, "Sign in failed, check response for error code");
                if (response == null) {
                    // User pressed back button
                    //showSnackbar(R.string.sign_in_cancelled);
                    return;
                }

                if (response.getErrorCode() == ErrorCodes.NO_NETWORK) {
                    Log.d(TAG, "no_internet_connection");
                    //showSnackbar(R.string.no_internet_connection);
                    return;
                }

                if (response.getErrorCode() == ErrorCodes.UNKNOWN_ERROR) {
                    //showSnackbar(R.string.unknown_error);
                    return;
                }
            }

            //showSnackbar(R.string.unknown_sign_in_response);
        }
    }

    public void logInAuth(View view){
        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(authProviders)
                        .setLogo(R.drawable.ic_menu_settings_gear)      // Set logo drawable
                        .setTheme(R.style.Theme_AppCompat)      // Set theme
                        .setIsSmartLockEnabled(false) //FOR DEBUG
                        .build(),
                RC_SIGN_IN);
    }

    public void logOutAuth(View v) {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        // user is now signed out
                    }
                });
    }

    private void enableLogButtton() {
        btnLog.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (flag_login == 0) {
                    Button b = (Button) view;
                    b.setText(R.string.button_logout);
                    flag_login++;
                    logInAuth(view);

                } else {
                    flag_login = 0;
                    Button b = (Button) view;
                    b.setText(R.string.button_login);
                    logOutAuth(view);

                }
            }
        });
    }

    private void enableLocationService() {

        btnShowLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (flag_service == 0) {
                    Button b = (Button) view;
                    b.setText("STOP LOC SERVICE");
                    flag_service++;
                    Toast.makeText(getApplicationContext(), "Location Service ON",
                            Toast.LENGTH_SHORT).show();
                    Intent i_start = new Intent(getApplicationContext(), LocationService.class);
                    startService(i_start);
                } else {
                    flag_service = 0;
                    Button b = (Button) view;
                    b.setText("START LOC SERVICE");
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
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                getApplicationContext().startActivity(intent);
            }
        });
        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                //showSettingsAlert();
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

    @IgnoreExtraProperties
    public class User {

        public String username;
        //public String email;
        public String lat;
        public String lon;

        public User() {
            // Default constructor required for calls to DataSnapshot.getValue(User.class)
            username = "Gil";
            //lat=location.getLatitude();
            //lon=location.getLongitude();
            lat="0.0";
            lon="1.0";
        }

        //public User(String username) {
          //  this.username = username;
        //}

    }
    public void testFirebase(View view) {
        //User user = new User();
        Double lat=location.getLatitude();
        Double lon=location.getLongitude();
        //DatabaseReference myRef = database.getReference();
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
        mDatabase.child("users").child("1245678").child("name").setValue("Gil");
        mDatabase.child("users").child("1245678").child("latitude").setValue(lat);
        mDatabase.child("users").child("1245678").child("longtitude").setValue(lon);
        //mDatabase.child("users").child("1245678").child("phoneNumber").setValue();



    }

    public void test2firebase(View view){

    }

}