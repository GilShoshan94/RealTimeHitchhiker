package com.realtimehitchhiker.hitchgo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.firebase.geofire.core.GeoHash;
import com.firebase.geofire.core.GeoHashQuery;
import com.firebase.geofire.util.GeoUtils;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class MainActivity extends AppCompatActivity {
    //public static final String EXTRA_MESSAGE = "com.realtimehitchhiker.hitchgo.MESSAGE"; for intent //intent.putExtra(EXTRA_MESSAGE, message); to be unique
    private static final int PERMISSION_LOCATION_REQUEST_CODE = 2;
    private static final int RC_SIGN_IN = 123; //FireBase
    public static final double LAT_MIN = -90.0, LAT_MAX = 90.0, LNG_MIN = -180.0, LNG_MAX = 180.0;
    public static final double EARTH_RADIUS = 6371008.8; //in meter the mean radius of Earth is 6371008.8 m
    public static final String TAG = "MAIN_DEBUG";
    private static Integer count=0; // FOR TEST

    private SharedPreferences sharedPref;
    private int radius; // in meters
    private int seats_in_car;
    private int demand_seats;

    //FireBase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseDatabase database;
    private DatabaseReference myDataBaseRef;
    private DatabaseReference refUsers;
    private DatabaseReference refSupply;
    private DatabaseReference refDemand;
    private DatabaseReference refHistory;
    private GeoFire geoFireSupply;
    private GeoFire geoFireDemand;
    private MyGlobalHistory globalHistory;

    private String facebookUserId = null;
    private String photoUrl = null;

    // Choose authentication providers
    List<AuthUI.IdpConfig> authProviders = Arrays.asList(
            //new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
            //new AuthUI.IdpConfig.Builder(AuthUI.PHONE_VERIFICATION_PROVIDER).build(),
            //new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build(),
            new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER).build()//, //to add more permission than the default, add ".setPermissions(Arrays.asList("user_friends"))" before ".build()"
            //new AuthUI.IdpConfig.Builder(AuthUI.TWITTER_PROVIDER).build()
    );

    private Button btnLog;
    private Button btnSupply;
    private Button btnDemand;
    private TextView txtShowLocation, txtWelcome;
    private ImageView imProfile;
    private BroadcastReceiver broadcastReceiverLocUpdate, broadcastReceiverLocOff;
    private Location location = null;
    private Double latitude = null;
    private Double longitude = null;

    //flag
    private int flag_login = 0;
    private boolean flag_supply;
    private boolean flag_demand;

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
        currentUser = mAuth.getCurrentUser();
        database = FirebaseDatabase.getInstance();
        myDataBaseRef = database.getReference();
        refUsers = myDataBaseRef.child("users/");
        refSupply = myDataBaseRef.child("supply/");
        refDemand = myDataBaseRef.child("demand/");
        refHistory = myDataBaseRef.child("history/");
        geoFireSupply = new GeoFire(myDataBaseRef.child("geofire/geofire-supply"));
        geoFireDemand = new GeoFire(myDataBaseRef.child("geofire/geofire-demand"));
        globalHistory = new MyGlobalHistory(refHistory);

        txtShowLocation = (TextView) findViewById(R.id.textView_testCoordinates);
        txtWelcome = (TextView) findViewById(R.id.textView_welcome_profile);
        btnLog = (Button) findViewById(R.id.button_login);
        btnSupply = (Button) findViewById(R.id.button_giveRide);
        btnDemand = (Button) findViewById(R.id.button_findRide);
        imProfile = (ImageView) findViewById(R.id.profile_image);
        //imProfile.setMaxHeight(100);
        //imProfile.setMaxWidth(100);
        imProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imProfile.setCropToPadding(true);

        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        int defaultValue = getResources().getInteger(R.integer.pref_radius_min);
        radius = sharedPref.getInt(getString(R.string.pref_radius), defaultValue);
        seats_in_car = sharedPref.getInt(getString(R.string.pref_supply_seats_in_car), 1);
        demand_seats = sharedPref.getInt(getString(R.string.pref_supply_seats_in_car), 1);
        flag_supply = sharedPref.getBoolean(getString(R.string.pref_supply_status), false);
        flag_demand = sharedPref.getBoolean(getString(R.string.pref_demand_status), false);
        Log.d(TAG, "getSharedPreferences : radius = " + radius );

        // Check if user is signed in (non-null) and update UI accordingly.
        updateUI(currentUser);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "MAIN_onStart" );

        enableSupplyButton();
        enableDemandButton();

        // Check permissions and start service location
        if(!runtimePermissions())
            enableLocationService();

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MAIN_onResume" );
        if(broadcastReceiverLocUpdate == null){
            broadcastReceiverLocUpdate = new BroadcastReceiver() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOC_UPDATE );
                    location = (Location)intent.getExtras().get("location");
                    if(location!=null){
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        txtShowLocation.setText("Lat :\t"+latitude+"\nLong :\t"+longitude+"\nProvider :\t"+location.getProvider());
                    }
                }
            };
        }
        if(broadcastReceiverLocOff == null){
            broadcastReceiverLocOff = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOC_OFF );
                    if(isAllActiveProviderDisabled())
                        showLocationSettingsAlert();
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
        Intent i_start = new Intent(getApplicationContext(), FirebaseService.class);
        startService(i_start);
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

        Intent i_stop = new Intent(getApplicationContext(), LocationService.class);
        stopService(i_stop);
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
                //FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                currentUser = mAuth.getCurrentUser();
                updateUI(currentUser);
                addUserIfNewInFireBase();
                ///////////////////////////////////////////////////////
                Log.d(TAG, "Successfully signed in = " + currentUser.toString());
                Log.d(TAG, "Successfully signed in = " + currentUser.getDisplayName());
                Log.d(TAG, "Successfully signed in = " + currentUser.getEmail());
                Log.d(TAG, "Successfully signed in = " + currentUser.getPhoneNumber());
                Log.d(TAG, "Successfully signed in = " + currentUser.getProviderId());
                Log.d(TAG, "Successfully signed in = " + currentUser.getProviders());
                Log.d(TAG, "Successfully signed in = " + currentUser.getProviderData());
                // currentUser.getUid() : The user's ID, unique to the Firebase project.
                // Do NOT use this value to authenticate with your backend server, if you have one.
                Log.d(TAG, "Successfully signed in = " + currentUser.getUid());
                Log.d(TAG, "Successfully signed in = " + currentUser.getIdToken(true));
                Log.d(TAG, "Successfully signed in = " + currentUser.getIdToken(false));
                Log.d(TAG, "Successfully signed in = " + currentUser.getPhotoUrl());
                ///////////////////////////////////////////////////////
            } else {
                // Sign in failed, check response for error code
                Log.d(TAG, "Sign in failed, check response for error code");
                if (response == null) {
                    // User pressed back button
                    Toast.makeText(getApplicationContext(), "Sign in cancelled by the user",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                if (response.getErrorCode() == ErrorCodes.NO_NETWORK) {
                    Log.d(TAG, "no_internet_connection");
                    Toast.makeText(getApplicationContext(), "No Internet Connection",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                if (response.getErrorCode() == ErrorCodes.UNKNOWN_ERROR) {
                    Log.e(TAG, "UNKNOWN_ERROR");
                }
            }
        }
    }

    private void logInAuth(){
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

    private void logOutAuth() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        // user is now signed out
                        currentUser = mAuth.getCurrentUser(); //normally =null
                        facebookUserId = null;
                        photoUrl = null;
                        updateUI(currentUser);
                    }
                });
    }

    private void enableLogButton(boolean bool) {
        if (bool){ //already signed in
            flag_login = 1;
            btnLog.setText(R.string.button_logout);
        }
        else {
            flag_login = 0;
            btnLog.setText(R.string.button_login);
        }
        btnLog.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (flag_login == 0) {
                    Button b = (Button) view;
                    b.setText(R.string.button_logout);
                    flag_login = 1;
                    logInAuth();

                } else {
                    flag_login = 0;
                    Button b = (Button) view;
                    b.setText(R.string.button_login);
                    logOutAuth();

                }
            }
        });
    }

    private void enableSupplyButton() {
        if (flag_supply){ //already supplying
            btnSupply.setText(R.string.button_giveRide_cancel);
        }
        else {
            btnSupply.setText(R.string.button_giveRide);
        }
        btnSupply.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Button b = (Button) view;
                if (!flag_supply) {
                    if(addSupplyToFireBase()){
                        flag_supply = true;
                        b.setText(R.string.button_giveRide_cancel);
                    }
                } else {
                    if(removeSupplyFromFireBase()) {
                        flag_supply = false;
                        b.setText(R.string.button_giveRide);
                    }
                }
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean(getString(R.string.pref_supply_status), flag_supply);
                editor.apply();
            }
        });
    }

    private void enableDemandButton() {
        if (flag_demand){ //already demanding
            btnDemand.setText(R.string.button_findRide_cancel);
        }
        else {
            btnDemand.setText(R.string.button_findRide);
        }
        btnDemand.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Button b = (Button) view;
                if (!flag_demand) {
                    if(addDemandToFireBase()){
                        flag_demand = true;
                        b.setText(R.string.button_findRide_cancel);
                    }
                } else {
                    if(removeDemandFromFireBase()){
                        flag_demand = false;
                        b.setText(R.string.button_findRide);
                    }
                }
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean(getString(R.string.pref_demand_status), flag_demand);
                editor.apply();
            }
        });
    }

    private void updateUI(FirebaseUser user){
        if(user!=null){
            //already signed in
            txtWelcome.setText("Welcome back " + user.getDisplayName());
            enableLogButton(true);
            // find the Facebook profile and get the user's id
            for(UserInfo profile : currentUser.getProviderData()) {
                // check if the provider id matches "facebook.com"
                if(FacebookAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                    facebookUserId = profile.getUid();
                }
            }
            // construct the URL to the profile picture, with a custom height
            // alternatively, use '?type=small|medium|large' instead of ?height=500
            photoUrl = "https://graph.facebook.com/" + facebookUserId + "/picture?type=large";
            new DownloadImageTask(imProfile).execute(photoUrl);
        }
        else{
            enableLogButton(false);
            txtWelcome.setText("Welcome, please log in");
            imProfile.setImageResource(R.drawable.com_facebook_profile_picture_blank_square);
        }
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urlDisplay = urls[0];
            Bitmap mIcon = null;
            InputStream in = null;
            try {
                in = new java.net.URL(urlDisplay).openStream();
                mIcon = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    }
                    catch(IOException ioEx) {
                        //Very bad things just happened... handle it
                        Log.wtf(TAG, "Stream.close throw exception :" + ioEx );
                    }
                }
            }
            return mIcon;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }

    private void enableLocationService() {
        Toast.makeText(getApplicationContext(), "Location Service ON",
                Toast.LENGTH_SHORT).show();
        Intent i_start = new Intent(getApplicationContext(), LocationService.class);
        startService(i_start);
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
        LocationManager locMan = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if(locMan==null)
            return true;
        for (int i = 0; i < LocationService.activeProviderList.length; i++) {
            if (!locMan.isProviderEnabled(LocationService.activeProviderList[i]))
                tot++;
        }
        return (tot == LocationService.activeProviderList.length);
    }

    /**
     * Function to show settings alert dialog
     * On pressing Settings button will launch Settings Options
     * */
    public void showLocationSettingsAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
        // Setting Dialog Title
        alertDialog.setTitle(R.string.alert_dialog_location_title);
        // Setting Dialog Message
        alertDialog.setMessage(R.string.alert_dialog_location_message);
        // On pressing Settings button
        alertDialog.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                getApplicationContext().startActivity(intent);
            }
        });
        // on pressing cancel button
        alertDialog.setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                showLocationSettingsAlert();
            }
        });
        // Showing Alert Message
        alertDialog.show();
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

    public void addUserIfNewInFireBase() {
        Log.d(TAG, "addUserIfNewInFireBase" );
        if(facebookUserId==null){
            Toast.makeText(getApplicationContext(), "ERROR : addNewUserToFirebase\nYou are not logged in\nLog in please",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Query checkKeyQuery = refUsers.orderByKey().equalTo(facebookUserId);
        checkKeyQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "addUserIfNewInFireBase onDataChange" );
                Log.d(TAG, "DATABASE Value is: " + dataSnapshot.toString());
                if(!dataSnapshot.exists()){
                    addNewUserToFireBase();
                }
                else {
                    checkSupplyDemandStatusAndUpdateUI();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Failed to read value
                Log.w(TAG, "DATABASE Failed to read value.", databaseError.toException());
            }
        });
    }

    private void addNewUserToFireBase(){
        Log.d(TAG, "addNewUserToFirebase" );
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), "ERROR : addNewUserToFirebase\nYou are not logged in\nLog in please",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference myRef = refUsers.child(facebookUserId);
        MyUser myUser = new MyUser(currentUser.getDisplayName(), currentUser.getEmail(),
                                    currentUser.getPhoneNumber());
        myRef.setValue(myUser);
    }

    private void checkSupplyDemandStatusAndUpdateUI(){
        Query checkKeyDemandQuery = refDemand.orderByKey().equalTo(facebookUserId);
        checkKeyDemandQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "DATABASE checkSupplyDemandStatusAndUpdateUI: " + dataSnapshot.toString());
                if(dataSnapshot.exists()){
                    flag_demand = true;
                    enableDemandButton();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Failed to read value
                Log.w(TAG, "DATABASE Failed to read value.", databaseError.toException());
            }
        });

        Query checkKeySupplyQuery = refSupply.orderByKey().equalTo(facebookUserId);
        checkKeySupplyQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "DATABASE checkSupplyDemandStatusAndUpdateUI: " + dataSnapshot.toString());
                if(dataSnapshot.exists()){
                    flag_supply = true;
                    enableSupplyButton();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Failed to read value
                Log.w(TAG, "DATABASE Failed to read value.", databaseError.toException());
            }
        });
    }

    public boolean addSupplyToFireBase() {
        Log.d(TAG, "addRideToFireBase" );
        if(location==null){
            Toast.makeText(getApplicationContext(), "Enable location or wait for location fix",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), "You are not logged in\nLog in please",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        Long remainingSeats = (long) chooseNumberSupplyAlert();
        MySupply mySupply = new MySupply(remainingSeats);
        refSupply.child(facebookUserId).setValue(mySupply);
        //FOR TEST
        Double lat = randomLatGen(), lng = randomLngGen();
        count++;
        String index = facebookUserId+(count).toString();

        geoFireSupply.setLocation(index, new GeoLocation(lat, lng)); //For Test use index, normal use facebookUserId;
        return true;
    }

    public int chooseNumberSupplyAlert(){
        final MySeekBar barNumber1 = new MySeekBar(this);
        barNumber1.setMax(getResources().getInteger(R.integer.pref_supply_max_seats_in_car)-1);
        barNumber1.setProgress(seats_in_car-1);


        new AlertDialog.Builder(this)
                .setTitle(R.string.alert_dialog_num_seats_supply_title)
                .setMessage(R.string.alert_dialog_num_seats_supply_message)
                .setView(barNumber1)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        seats_in_car = (barNumber1.getProgress()+1);

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.pref_supply_seats_in_car), seats_in_car);
                        editor.apply();
                    }
                })
                .setCancelable(false)
                .show();

        return seats_in_car;
    }

    public boolean removeSupplyFromFireBase(){
        if(location==null){
            Toast.makeText(getApplicationContext(), "Enable location or wait for location fix",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), "You are not logged in\nLog in please",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        refSupply.child(facebookUserId).removeValue();
        //geoFireSupply.removeLocation(facebookUserId); ////For Test comment
        return true;
    }

    public boolean addDemandToFireBase(){
        Log.d(TAG, "addDemandToFireBase" );
        if(location==null){
            Toast.makeText(getApplicationContext(), "Enable location or wait for location fix",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), "You are not logged in\nLog in please",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        Long requestingSeats = (long) chooseNumberDemandAlert();
        MyDemand myDemand = new MyDemand(requestingSeats);
        refDemand.child(facebookUserId).setValue(myDemand);
        geoFireDemand.setLocation(facebookUserId, new GeoLocation(latitude, longitude));
        return true;
    }

    public int chooseNumberDemandAlert(){
        final MySeekBar barNumber2 = new MySeekBar(this);
        barNumber2.setMax(getResources().getInteger(R.integer.pref_supply_max_seats_in_car)-1);
        barNumber2.setProgress(demand_seats-1);


        new AlertDialog.Builder(this)
                .setTitle(R.string.alert_dialog_num_seats_demand_title)
                .setMessage(R.string.alert_dialog_num_seats_demand_message)
                .setView(barNumber2)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        demand_seats = (barNumber2.getProgress()+1);

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.pref_demand_seats_in_car), demand_seats);
                        editor.apply();
                    }
                })
                .setCancelable(false)
                .show();
        return demand_seats;
    }

    public boolean removeDemandFromFireBase(){
        if(location==null){
            Toast.makeText(getApplicationContext(), "Enable location or wait for location fix",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), "You are not logged in\nLog in please",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        refDemand.child(facebookUserId).removeValue();
        geoFireDemand.removeLocation(facebookUserId);
        return true;
    }

    public void addHistoryToFireBase(){
        //TEST HISTORY
        String supplyUserId = facebookUserId;
        Long offeredSeats = new Long(seats_in_car);
        Long usedSeats = new Long(seats_in_car-demand_seats);
        Map<String, Long> demandUserId = new HashMap<>();
        demandUserId.put("Gil", Long.valueOf(1));
        demandUserId.put("Rivka", Long.valueOf(1));
        demandUserId.put("Pavel", Long.valueOf(2));
        demandUserId.put("Moscalej", Long.valueOf(2));

        globalHistory.setGlobalHistory(refHistory.push().getKey(),
                new GeoLocation(latitude, longitude), new GeoLocation(latitude, longitude),
                supplyUserId, demandUserId, offeredSeats,usedSeats);
    }

    public void findRideFromFireBase(){
        Log.d(TAG, "FIND :");
        // Read from the database
        // creates a new query around [37.7832, -122.4056] with a radius of 0.6 kilometers
        GeoQuery geoQuery = geoFireSupply.queryAtLocation(new GeoLocation(latitude,longitude), radius);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                Log.d(TAG, "FIND : "+String.format("Key %s entered the search area at [%f,%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onKeyExited(String key) {
                Log.d(TAG, "FIND : "+String.format("Key %s is no longer in the search area", key));
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d(TAG, "FIND : "+String.format("Key %s moved within the search area to [%f,%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {
                Log.d(TAG, "FIND : "+"All initial data has been loaded and events have been fired!");
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.d(TAG, "FIND : "+"There was an error with this query: " + error);
            }
        });
        //geoQuery.removeAllListeners();
        //Updating the query criteria: The GeoQuery search area can be changed with setCenter and setRadius
        //You can call either removeGeoQueryEventListener to remove a single event listener or removeAllListeners to remove all event listeners for a GeoQuery

    }

    //FOR TESTING
    public double randomLatGen(){
        double min = latitude-approxLatDelta();
        double max = latitude+approxLatDelta();
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
    public double randomLngGen(){
        double min = longitude-approxLngDelta();
        double max = longitude+approxLngDelta();
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
    private double approxLatDelta(){
        double dist = radius*1000; //radius is in km and we need it in m
        return (180/Math.PI)*(dist/EARTH_RADIUS);
    }
    private double approxLngDelta(){
        double dist = radius*1000; //radius is in km and we need it in m
        return (180/Math.PI)*(dist/EARTH_RADIUS)*(1/Math.cos(latitude));
    }
}