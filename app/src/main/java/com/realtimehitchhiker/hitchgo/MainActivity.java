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
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.firebase.geofire.GeoFire;
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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

/**
 * THis is the main activity of the app
 */
public class MainActivity extends AppCompatActivity {
    // The static final Strings
    public static final String BROADCAST_ACTION_MAIN_RESUME = "com.realtimehitchhiker.hitchgo.MAIN_RESUME";
    public static final String BROADCAST_ACTION_MAIN_PAUSE = "com.realtimehitchhiker.hitchgo.MAIN_PAUSE";
    public static final String BROADCAST_ACTION_MAIN_REQUEST = "com.realtimehitchhiker.hitchgo.MAIN_REQUEST";
    public static final String EXTRA_REQUEST_MESSAGE = "com.realtimehitchhiker.hitchgo.DEMAND_TRUE_CANCEL_FALSE";
    private static final int PERMISSION_LOCATION_REQUEST_CODE = 2;
    private static final int RC_SIGN_IN = 123; //for FirebaseAuthentication request
    public static final double EARTH_RADIUS = 6371008.8; //in meter the mean radius of Earth is 6371008.8 m
    public static final String TAG = "MAIN_DEBUG";

    // SharedPreferences parameters
    private SharedPreferences sharedPref;
    private int radius; // in meters
    private int fuel_price;
    private boolean allow_pet_supply;
    private boolean demand_pet;
    private int seats_in_car;
    private int demand_seats;
    private String phone_number;

    // Firebase variables
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference refUsers;
    private DatabaseReference refSupply;
    private DatabaseReference refDemand;
    private DatabaseReference refHistory;
    private GeoFire geoFireSupply;
    private GeoFire geoFireDemand;
    private MyGlobalHistory globalHistory;

    // Choose authentication providers for Firebase
    List<AuthUI.IdpConfig> authProviders = Arrays.asList(
            //new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
            //new AuthUI.IdpConfig.Builder(AuthUI.PHONE_VERIFICATION_PROVIDER).build(),
            //new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build(),
            new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER).build()//, //to add more permission than the default, add ".setPermissions(Arrays.asList("user_friends"))" before ".build()"
            //new AuthUI.IdpConfig.Builder(AuthUI.TWITTER_PROVIDER).build()
    );

    // Variable that store some user data for quick access
    private String facebookUserId = null;
    private Location location = null;
    private Double latitude = null;
    private Double longitude = null;

    // UI objects in the layer
    private Button btnLog;
    private Button btnSupply;
    private Button btnDemand;
    private TextView txtShowLocation, txtWelcome;
    private ImageView imProfile;
    private ImageButton imageButtonSetting;

    // BroadcastReceiver (for inter-modules/services communication)
    private BroadcastReceiver broadcastReceiverLocationUpdate, broadcastReceiverLocionProviderOff, broadcastReceiverSupplyFound;

    // Flags to store various states (yes or no) of the app
    private boolean flag_login = false;
    private boolean flag_supply;
    private boolean flag_demand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MAIN_onCreate" );
        //For Facebook SDK (From Facebook)
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);

        //setContentView
        setContentView(R.layout.activity_main);

        //Firebase initialization
        mAuth = FirebaseAuth.getInstance();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myDataBaseRef = database.getReference();
        //Get pointers to all the nodes/folders of the database
        refUsers = myDataBaseRef.child(getString(R.string.firebase_folder_users));
        refSupply = myDataBaseRef.child(getString(R.string.firebase_folder_supply));
        refDemand = myDataBaseRef.child(getString(R.string.firebase_folder_demand));
        refHistory = myDataBaseRef.child(getString(R.string.firebase_folder_history));
        //The geofire folders holds the location coordinates latitude and longitude
        // encode into a single hash-key with the propriety that 2 closes locations share the
        // same "code" at the beginning of their keys. The technique is close "Geohashing"
        // We use the geohashing from the GeoFire library from Google for Firebase
        geoFireSupply = new GeoFire(myDataBaseRef.child(getString(R.string.firebase_folder_geofire_supply)));
        geoFireDemand = new GeoFire(myDataBaseRef.child(getString(R.string.firebase_folder_geofire_demand)));
        globalHistory = new MyGlobalHistory(refHistory);

        //UI initialization of links
        txtShowLocation = findViewById(R.id.textView_testCoordinates);
        txtWelcome = findViewById(R.id.textView_welcome_profile);
        btnLog = findViewById(R.id.button_login);
        btnSupply = findViewById(R.id.button_supply);
        btnDemand = findViewById(R.id.button_demand);
        imageButtonSetting = findViewById(R.id.imageButton_settings);
        imProfile = findViewById(R.id.profile_image);
        //imProfile.setMaxHeight(100);
        //imProfile.setMaxWidth(100);
        imProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imProfile.setCropToPadding(true);

        //Load the sharedPreferences
        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        int defaultValue = getResources().getInteger(R.integer.pref_radius_min);
        radius = sharedPref.getInt(getString(R.string.pref_radius), defaultValue);
        seats_in_car = sharedPref.getInt(getString(R.string.pref_supply_seats_in_car), 1);
        fuel_price = sharedPref.getInt(getString(R.string.pref_supply_fuel_price), 0);
        allow_pet_supply = sharedPref.getBoolean(getString(R.string.pref_supply_pet), false);
        demand_pet = sharedPref.getBoolean(getString(R.string.pref_demand_pet), false);
        demand_seats = sharedPref.getInt(getString(R.string.pref_demand_seats_in_car), 1);
        flag_supply = sharedPref.getBoolean(getString(R.string.pref_supply_status), false);
        flag_demand = sharedPref.getBoolean(getString(R.string.pref_demand_status), false);
        phone_number = sharedPref.getString(getString(R.string.pref_phone_number), "false");
        Log.d(TAG, "getSharedPreferences : radius = " + radius );

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "MAIN_onStart" );

        //Check if user is signed in (non-null) and update UI accordingly.
        currentUser = mAuth.getCurrentUser();
        updateUI(currentUser);

        // Check permissions at runtime and get them if need to and start firebase service and location service
        if(!runtimePermissions()) {
            enableFirebaseAndLocationService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MAIN_onResume" );

        //Broadcast state of main activity : OnResume
        broadcastOnResume();

        //Initialize (if need is) the broadcastReceivers for location updates and change in providers status
        if(broadcastReceiverLocationUpdate == null){
            broadcastReceiverLocationUpdate = new BroadcastReceiver() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOCATION_UPDATE);
                    location = (Location)intent.getExtras().get("location");
                    if(location!=null){
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        //todo deleted the txtShowLocation, it's here only for debug
                        txtShowLocation.setText("Lat :\t"+latitude+"\nLong :\t"+longitude+"\nProvider :\t"+location.getProvider());
                    }
                }
            };
        }
        if(broadcastReceiverLocionProviderOff == null){
            broadcastReceiverLocionProviderOff = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOCATION_OFF);
                    if(isAllActiveLocationProvidersDisabled())
                        showLocationSettingsAlert();
                }
            };
        }
        if(broadcastReceiverSupplyFound == null) {
            broadcastReceiverSupplyFound = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String facebookUserIdFound = (String) intent.getExtras().get("facebookUserIdFound");
                    Double latitude = (Double) intent.getExtras().get("geoLocationLatitude");
                    Double longitude = (Double) intent.getExtras().get("geoLocationLongitude");

                    Log.d(FirebaseService.TAG, "broadcastReceiverSupplyFound : "+facebookUserIdFound+" "+latitude+" "+longitude );
                    callResultActivity(facebookUserIdFound, latitude, longitude);
                }
            };
        }

        //Register the broadcastReceivers locally (internal to the app)
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(broadcastReceiverLocationUpdate,new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_UPDATE));
        localBroadcastManager.registerReceiver(broadcastReceiverLocionProviderOff,new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_OFF));
        localBroadcastManager.registerReceiver(broadcastReceiverSupplyFound,new IntentFilter(FirebaseService.BROADCAST_ACTION_SUPPLY_FOUND));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "MAIN_onPause" );

        //Broadcast state of main activity : OnPause
        broadcastOnPause();

        //Unregister the broadcastReceivers
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        if(broadcastReceiverLocationUpdate != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverLocationUpdate);
        }
        if(broadcastReceiverLocionProviderOff != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverLocionProviderOff);
        }
        if(broadcastReceiverSupplyFound != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverSupplyFound);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "MAIN_onStop" );

        //If there is no ride demand or supply, then there is no need to keep tracking service on. So stop them
        if(!flag_demand && !flag_supply){
            //Stop FirebaseService (and FirebaseService will stop LocationService in is onDestroy method)
            Intent i_stop = new Intent(getApplicationContext(), FirebaseService.class);
            stopService(i_stop);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MAIN_onDestroy" );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //Result for PERMISSION_LOCATION_REQUEST
        if (requestCode == PERMISSION_LOCATION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                //If got the location permission, enable the location service
                enableFirebaseAndLocationService();
            } else {
                //else (did not get the location permission), keep asking for it
                runtimePermissions();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "MAIN_onActivityResult" );

        //Result for sign in with firebase authentication
        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);
            if (response != null) {
                Log.d(TAG, "RC_SIGN_IN_response = " + response.toString());
            }
            if (resultCode == RESULT_OK) {
                //Successfully signed in
                //Get the current user, update the UI and add the user to the database if required
                currentUser = mAuth.getCurrentUser();
                addUserIfNewInFirebaseOrGetSupplyDemandStatusAndPhoneNumberUpdateAndUpdateUI();
                updateUI(currentUser);

                //Here down we are logging all the data Firebase Authentication can provide us by curiosity fo development
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
                    //User pressed back button
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_sign_in_cancelled),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                if (response.getErrorCode() == ErrorCodes.NO_NETWORK) {
                    //NO_NETWORK
                    Log.d(TAG, "no_internet_connection");
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_no_internet_connection),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                if (response.getErrorCode() == ErrorCodes.UNKNOWN_ERROR) {
                    //UNKNOWN_ERROR
                    Log.e(TAG, "UNKNOWN_ERROR");
                }
            }
        }
    }








    /**
     * LogIn: create and launch sign-in intent to the FirebaseAuth "module" (startActivityForResult)
     */
    private void logInAuth(){
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

    /**
     * LogOut: log out from the FirebaseAuth "module"
     */
    private void logOutAuth() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        // user is now signed out
                        currentUser = mAuth.getCurrentUser(); //normally =null
                        facebookUserId = null;
                        flag_supply = false;
                        flag_demand = false;
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putBoolean(getString(R.string.pref_supply_status), flag_supply);
                        editor.putBoolean(getString(R.string.pref_demand_status), flag_demand);
                        editor.apply();

                        updateUI(currentUser);
                    }
                });
    }

    /**
     * initialize the Login button accordingly of the user state (signed in or not)
     *
     * @param signed_in The boolean that indicate if the user is already signed in or not for initialization
     */
    private void initializeLogButton(boolean signed_in) {
        //if already signed in
        if (signed_in){
            flag_login = true;
            btnLog.setText(R.string.button_logout);
        }
        //else not already signed in
        else {
            flag_login = false;
            btnLog.setText(R.string.button_login);
        }

        //setOnClickListener for the Log button
        btnLog.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (!flag_login) {
                    Button b = (Button) view;
                    b.setText(R.string.button_logout);
                    flag_login = true;
                    logInAuth();

                } else {
                    flag_login = false;
                    Button b = (Button) view;
                    b.setText(R.string.button_login);
                    logOutAuth();

                }
            }
        });
    }

    /**
     * initialize the Supply button accordingly of the user state (supplying already or not)
     */
    private void initializeSupplyButton() {
        //if already supplying
        if (flag_supply){
            btnSupply.setText(R.string.button_giveRide_cancel);
        }
        //else not already supplying
        else {
            btnSupply.setText(R.string.button_giveRide);
        }

        //setOnClickListener for the Supply button
        btnSupply.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Button b = (Button) view;
                if (!flag_supply) {
                    if(addSupplyToFirebase()){
                        flag_supply = true;
                        b.setText(R.string.button_giveRide_cancel);
                    }
                } else {
                    if(removeSupplyFromFirebase()) {
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

    /**
     * initialize the Demand button accordingly of the user state (demanding already or not)
     */
    private void initializeDemandButton() {
        //if already demanding
        if (flag_demand){
            btnDemand.setText(R.string.button_findRide_cancel);
        }
        //else not already demanding
        else {
            btnDemand.setText(R.string.button_findRide);
        }

        //setOnClickListener for the Demand button
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

    /**
     * update the UI accordingly of the user state and data (signed in or not, supplying/demanding or not).
     *  get the profile picture, set the buttons accordingly.
     *
     * @param user The FirebaseUser variable that hold the current user metadata (can be null if not logged in)
     */
    private void updateUI(FirebaseUser user){//todo initializeLogButton....
        //if already signed in
        if(user!=null){
            //set the UI and initialize the Log Button
            initializeLogButton(true);
            txtWelcome.setText(R.string.ui_welcome_logged_in);
            txtWelcome.append("\n" + user.getDisplayName());

            //Show the UI for logged in users
            btnSupply.setVisibility(View.VISIBLE);
            btnDemand.setVisibility(View.VISIBLE);
            txtShowLocation.setVisibility(View.VISIBLE);
            imProfile.setVisibility(View.VISIBLE);
            imageButtonSetting.setVisibility(View.VISIBLE);
            btnLog.setVisibility(View.GONE);

            // find the Facebook profile and get the user's id
            for(UserInfo profile : currentUser.getProviderData()) {
                // check if the provider id matches "facebook.com"
                if(FacebookAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                    facebookUserId = profile.getUid();
                }
            }
            // construct the URL to the profile picture, with a custom height
            // alternatively, use '?type=small|medium|large' instead of ?height=500
            String photoUrl = "https://graph.facebook.com/" + facebookUserId + "/picture?type=large";
            //Download the profile picture from Facebook (download it in background asynchronously)
            new DownloadImageTask(new DownloadImageTask.AsyncResponse() {
                @Override
                public void processFinish(Bitmap output) {
                    imProfile.setImageBitmap(output);
                }
            }).execute(photoUrl);
        }
        //else not already signed in
        else{
            //set the UI and initialize the Log Button
            initializeLogButton(false);
            txtWelcome.setText(R.string.ui_welcome_logged_out);
            imProfile.setImageResource(R.drawable.com_facebook_profile_picture_blank_square);

            //Hide the UI
            btnSupply.setVisibility(View.INVISIBLE);
            btnDemand.setVisibility(View.INVISIBLE);
            txtShowLocation.setVisibility(View.INVISIBLE);
            imProfile.setVisibility(View.INVISIBLE);
            imageButtonSetting.setVisibility(View.INVISIBLE);
            btnLog.setVisibility(View.VISIBLE);
        }

        //initialize supply and demand button accordingly to the user state
        initializeSupplyButton();
        initializeDemandButton();
    }

    /**
     * enable first the firebase service and then the location service
    */
    private void enableFirebaseAndLocationService() {

        Intent i_start_first = new Intent(getApplicationContext(), FirebaseService.class);
        startService(i_start_first);

        Intent i_start = new Intent(getApplicationContext(), LocationService.class);
        startService(i_start);
    }

    /**
     * Checks if we needs permissions on runtime and requests them
     */
    private boolean runtimePermissions() {
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {

                //if explanation is needed, show dialog accordingly
                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) || ActivityCompat.shouldShowRequestPermissionRationale(
                        MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)) {

                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                    alertDialog.setTitle(R.string.alert_dialog_permission_location_title);  // Setting Dialog Title
                    alertDialog.setMessage(R.string.alert_dialog_permission_location_message);     // Setting Dialog Message
                    // on pressing ok button, we can request the permission.
                    alertDialog.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                                    PERMISSION_LOCATION_REQUEST_CODE);
                        }
                    });
                    // on pressing no button
                    alertDialog.setNegativeButton(R.string.alert_dialog_permission_location_negative_button, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(0);  // terminates the Linux process and all threads for the app ( so no background)
                        }
                    });
                    alertDialog.show();     // Showing Alert Message
                }
                //else, no explanation needed, we can request the permission.
                else {
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

    /**
     * returns true if all active location providers are disabled
     * returns false otherwise
     */
    private boolean isAllActiveLocationProvidersDisabled(){
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
     * start settings activity when the user taps the imageButton_settings
     * (this function is used in the layout xml file)
     */
    public void callSettingsActivity(View view) {
        // Explicit Intent by specifying its class name
        Intent intent = new Intent(this, SettingsActivity.class);
        // Starts TargetActivity
        startActivity(intent);
    }

    /**
     * start Result activity and send to it important data for result
     */
    public void callResultActivity(String facebookUserIdFound, Double latitude, Double longitude) {
        Intent resultIntent = new Intent(this, ResultActivity.class);
        resultIntent.putExtra("facebookUserIdFound", facebookUserIdFound);
        resultIntent.putExtra("geoLocationLatitude", latitude);
        resultIntent.putExtra("geoLocationLongitude", longitude);
        startActivity(resultIntent);
    }

    /**
     * Get and check the phone number by asking the user (need that currentUser != null) and update the database
     */
    public void getOrCheckPhoneNumber() {
        Log.d(TAG, "getOrCheckPhoneNumber");

        final TelephonyManager manager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        //getNetworkCountryIso
        assert manager != null;
        String CountryID= manager.getSimCountryIso().toUpperCase();
        Log.d(TAG, "getNetworkCountryIso = "+CountryID);
        final PhoneNumberUtil phoneUtil = PhoneNumberUtil.createInstance(getApplicationContext());

        String prefetch_phone_number = "+";
        phone_number = sharedPref.getString(getString(R.string.pref_phone_number), "false");
        if(!Objects.equals(phone_number, "false"))
            prefetch_phone_number = phone_number;
        else if(currentUser.getPhoneNumber() != null) {
            prefetch_phone_number = currentUser.getPhoneNumber();
            final Phonenumber.PhoneNumber phoneNumber;
            try {
                phoneNumber = phoneUtil.parse(prefetch_phone_number, CountryID);
                prefetch_phone_number = (phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL));
            } catch (NumberParseException e) {
                e.printStackTrace();
            }
        }

        //final Spinner spinnerCountry = new Spinner(this); //todo improvement add a spinner to select country

        final EditText textPhoneNumber = new EditText(this);
        textPhoneNumber.setHint("Phone number");
        textPhoneNumber.setText(prefetch_phone_number);
        textPhoneNumber.addTextChangedListener(new PhoneNumberFormattingTextWatcher(CountryID){
            boolean flag_reset = false;
            @Override
            public synchronized void afterTextChanged(Editable s) {
                super.afterTextChanged(s);
                if(s.length()==1 && flag_reset) {
                    flag_reset = false;
                    s.clear();
                }
                else if(s.length()==0){
                    s.append("+");
                }
                else if(s.length()>1 && !flag_reset) {
                    flag_reset = true;
                }

            }
        });
        textPhoneNumber.setLines(1);
        textPhoneNumber.setFilters(new InputFilter[]{new InputFilter.LengthFilter(18)}); // max(here 18) is the max input char for phone number
        textPhoneNumber.setInputType(InputType.TYPE_CLASS_PHONE);
        textPhoneNumber.setSelection(textPhoneNumber.getText().length());


        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(textPhoneNumber)
                .setTitle(R.string.alert_dialog_phone_number_title)
                .setMessage(R.string.alert_dialog_phone_number_message)
                .setPositiveButton(R.string.alert_dialog_ok, null)
                .setCancelable(false);

        final AlertDialog dialog = builder.create();
        dialog.show();
        //Overriding the handler immediately after show
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                phone_number = textPhoneNumber.getText().toString();

                //getNetworkCountryIso
                assert manager != null;
                String CountryID= manager.getSimCountryIso().toUpperCase();
                Phonenumber.PhoneNumber forCheckPhoneNumber = phoneUtil.getInvalidExampleNumber(CountryID);
                try {
                    forCheckPhoneNumber = phoneUtil.parse(phone_number, CountryID);
                    Log.d(TAG, "forCheckPhoneNumber" + forCheckPhoneNumber.toString() + " phone_number " + phone_number);
                } catch (NumberParseException e) {
                    e.printStackTrace();
                }

                Log.d(TAG, "forCheckPhoneNumber" + forCheckPhoneNumber.toString());
                if(phoneUtil.isValidNumber(forCheckPhoneNumber)) {
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString(getString(R.string.pref_phone_number), phone_number);
                    editor.apply();
                    refUsers.child(facebookUserId).child("phone").setValue(phone_number);

                    dialog.dismiss();
                }
                else {
                    //else dialog stays open. Make sure you have an obvious way to close the dialog especially if you set cancellable to false.
                    Toast.makeText(getApplicationContext(), R.string.toast_invalid_phone_number,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * add user if new in firebase database OR get Supply Demand status and phone number update and update UI
     * will query the database to find the user, if not found then must be new and then add it
     * if present in the database, will check the status and the phone number
     */
    public void addUserIfNewInFirebaseOrGetSupplyDemandStatusAndPhoneNumberUpdateAndUpdateUI() {
        Log.d(TAG, "addUserIfNewInFirebaseOrGetSupplyDemandStatusAndPhoneNumberUpdateAndUpdateUI" );

        // find the Facebook profile and get the user's id
        for(UserInfo profile : currentUser.getProviderData()) {
            // check if the provider id matches "facebook.com"
            if(FacebookAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                facebookUserId = profile.getUid();
            }
        }

        if(facebookUserId==null){
            Toast.makeText(getApplicationContext(), R.string.toast_not_logged_in,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Query checkKeyQuery = refUsers.orderByKey().equalTo(facebookUserId);
        checkKeyQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "addUserIfNewInFirebaseOrGetSupplyDemandStatusAndPhoneNumberUpdateAndUpdateUI onDataChange" );
                Log.d(TAG, "DATABASE Value is: " + dataSnapshot.toString());
                //if the user doesn't exist in the database, then add it
                if(!dataSnapshot.exists()){
                    getOrCheckPhoneNumber();
                    addNewUserToFirebase();
                }
                //else the user already exist, check Supply/Demand status and update UI and update phone number
                else {
                    getOrCheckPhoneNumber();
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

    /**
     * add new user in firebase database
     */
    private void addNewUserToFirebase(){
        Log.d(TAG, "addNewUserToFirebase" );
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), R.string.toast_not_logged_in,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference myRef = refUsers.child(facebookUserId);
        MyUser myUser = new MyUser(currentUser.getDisplayName(), currentUser.getEmail(),
                                    phone_number);
        myRef.setValue(myUser);
    }

    /**
     * check Supply Demand status and update UI, current user must be logged in to call this function
     * will query the database to find the user's Supply and Demand status, and will set the flags and buttons accordingly
     */
    private void checkSupplyDemandStatusAndUpdateUI(){
        Query checkKeyDemandQuery = refDemand.orderByKey().equalTo(facebookUserId);
        checkKeyDemandQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "DATABASE checkSupplyDemandStatusAndUpdateUI: " + dataSnapshot.toString());
                if(dataSnapshot.exists()){
                    flag_demand = true;
                    initializeDemandButton();
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
                    initializeSupplyButton();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Failed to read value
                Log.w(TAG, "DATABASE Failed to read value.", databaseError.toException());
            }
        });
    }

    /**
     * add Supply to Firebase
     */
    public boolean addSupplyToFirebase() {
        Log.d(TAG, "addRideToFireBase" );
        if(location==null){
            Toast.makeText(getApplicationContext(), R.string.toast_no_location_fix,
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), R.string.toast_not_logged_in,
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        setSupplyDetails();

        return true;
    }


    //todo comments
    public void setSupplyDetails(){
        Log.d(TAG, "setSupplyDetails ");
        DialogFragment newSupplyFragment = SupplyDialogFragment.newInstance(facebookUserId,latitude,longitude);
        newSupplyFragment.show(getSupportFragmentManager(), "supplyDialogFragment");
    }

    /**
     * remove Supply to Firebase
     */
    public boolean removeSupplyFromFirebase(){
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), R.string.toast_not_logged_in,
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        refSupply.child(facebookUserId).removeValue();
        geoFireSupply.removeLocation(facebookUserId);
        return true;
    }

    /**
     * add Demand to Firebase
     */
    public boolean addDemandToFireBase(){
        Log.d(TAG, "addDemandToFireBase" );
        if(location==null){
            Toast.makeText(getApplicationContext(), R.string.toast_no_location_fix,
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), R.string.toast_not_logged_in,
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        setDemandDetails();
        return true;
    }

    //todo comments
    public void setDemandDetails(){
        Log.d(TAG, "setDemandDetails ");
        DialogFragment newDemandFragment = DemandDialogFragment.newInstance(facebookUserId,latitude,longitude);
        newDemandFragment.show(getSupportFragmentManager(), "supplyDialogFragment");
    }

    /**
     * remove Demand to Firebase
     */
    public boolean removeDemandFromFireBase(){
        if(currentUser==null){
            Toast.makeText(getApplicationContext(), R.string.toast_not_logged_in,
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        refDemand.child(facebookUserId).removeValue();
        geoFireDemand.removeLocation(facebookUserId);
        broadcastRequest(false);
        return true;
    }

    /**
     * broadcast OnResume locally (internal to the app)
     */
    private void broadcastOnResume(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_MAIN_RESUME);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * broadcast OnPause locally (internal to the app)
     */
    private void broadcastOnPause(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_MAIN_PAUSE);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * broadcast Request (a change in demand status) locally (internal to the app)
     *
     * @param demand_true_or_cancel_false TRUE for the demand has been made, FALSE for the demand has been cancelled
     */
    private void broadcastRequest(boolean demand_true_or_cancel_false){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_MAIN_REQUEST);
        intent.putExtra(EXTRA_REQUEST_MESSAGE, demand_true_or_cancel_false);
        localBroadcastManager.sendBroadcast(intent);
    }

    //todo to delete this part when not needed
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