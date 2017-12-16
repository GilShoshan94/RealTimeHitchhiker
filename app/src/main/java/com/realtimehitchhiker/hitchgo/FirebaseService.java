package com.realtimehitchhiker.hitchgo;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


/**
 * This is the Firebase Service class
 * It connect the app to the Firebase database and listen to change occurring into it
 * It also perfoms various query to extract the relevant information needed
 * It provides to the app the facebookUserIdFound and the location of the Supply found
 * It communicate through only one broadcast : BROADCAST_ACTION_SUPPLY_FOUND
 */

public class FirebaseService extends Service {
    public static final String BROADCAST_ACTION_SUPPLY_FOUND = "com.realtimehitchhiker.hitchgo.SUPPLY_FOUND";
    public static final String BROADCAST_ACTION_SUPPLY_UPDATE = "com.realtimehitchhiker.hitchgo.SUPPLY_UPDATE";
    public static final String BROADCAST_ACTION_DEMAND_FOUND = "com.realtimehitchhiker.hitchgo.DEMAND_FOUND";
    public static final String BROADCAST_ACTION_DEMAND_UPDATE = "com.realtimehitchhiker.hitchgo.DEMAND_UPDATE";
    public static final String TAG = "FIREBASE_SERVICE_DEBUG";

    private NotificationManager mNotificationManager;
    private String channel_id; //For API 26+
    private NotificationCompat.Builder notificationBuilder;

    private FirebaseUser currentUser;
    private String facebookUserId = "";
    private DatabaseReference refUsers;
    private DatabaseReference refSupply;
    private DatabaseReference refDemand;
    private DatabaseReference refHistory;
    private GeoFire geoFireSupply, geoFireDemand;
    private GeoQuery geoQuery = null;
    private MyGlobalHistory globalHistory;
    private Query listenNewDemandQuery = null;

    private LocalBroadcastManager localBroadcastManager;
    private BroadcastReceiver broadcastReceiverLocUpdate, broadcastReceiverLocOff, broadcastReceiverRadiusUpdate;
    private BroadcastReceiver broadcastReceiverMainResume, broadcastReceiverMainPause, broadcastReceiverMainRequest;
    private BroadcastReceiver broadcastReceiverResultDemandResume, broadcastReceiverResultDemandPause,
            broadcastReceiverResultSupplyResume, broadcastReceiverResultSupplyPause, broadcastReceiverMainSupplyRequest;
    private BroadcastReceiver broadcastReceiverDemandDetails;
    private Double latitude = 90.0, longitude = 0.0; //initialize at pole North
    private SharedPreferences sharedPref;
    private int radius; // in meters

    //Demand details //
    private String demand_destination = "";
    private boolean demand_pet;
    private int demand_seats;

    //Flag
    private boolean main_activity_is_on = true;
    private boolean result_demand_activity_is_on = false;
    private boolean result_supply_activity_is_on = false;
    private boolean demand_true_or_cancel_false;
    private boolean supply_true_or_cancel_false;
    private boolean on_process = false;
    private boolean initialization_flag = true;
    private boolean result_sent_already = false;
    private boolean forLoopFinished = false;
    private int queryFinished = 0;
    private int querySent = 0;

    //RESULT
    private ArrayList<String> resultKey = new ArrayList<>();
    private ArrayList<ResultLocation> resultLocation = new ArrayList<>();

    //Temporary Buffer
    private ArrayList<String> resultKeyTempBuffer = new ArrayList<>();
    private ArrayList<ResultLocation> resultLocationTempBuffer = new ArrayList<>();

    //RESULT SENT
    private ArrayList<String> resultKeySent = new ArrayList<>();

    //public FirebaseService() {
    //}

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // The id of the channel.
        channel_id = getString(R.string.channel_id);
        // The user-visible name of the channel.
        CharSequence channel_name = getString(R.string.channel_name);
        // The user-visible description of the channel.
        String channel_description = getString(R.string.channel_description);
        int importance = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            importance = NotificationManager.IMPORTANCE_HIGH;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(channel_id, channel_name, importance);
            // Configure the notification channel.
            mChannel.setDescription(channel_description);
            mChannel.enableLights(true);
            // Sets the notification light color for notifications posted to this
            // channel, if the device supports this feature.
            mChannel.setLightColor(Color.RED);
            mChannel.enableVibration(true);
            mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            assert mNotificationManager != null;
            mNotificationManager.createNotificationChannel(mChannel);
        }

        //For FireBase
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        // find the Facebook profile and get the user's id
        for(UserInfo profile : currentUser.getProviderData()) {
            // check if the provider id matches "facebook.com"
            if(FacebookAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                facebookUserId = profile.getUid();
            }
        }
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myDataBaseRef = database.getReference();
        refUsers = myDataBaseRef.child("users/");
        refSupply = myDataBaseRef.child("supply/");
        refDemand = myDataBaseRef.child("demand/");
        refHistory = myDataBaseRef.child("history/");
        geoFireSupply = new GeoFire(myDataBaseRef.child("geofire/geofire-supply"));
        geoFireDemand = new GeoFire(myDataBaseRef.child("geofire/geofire-demand"));
        globalHistory = new MyGlobalHistory(refHistory);

        //SharedPreferences
        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        int defaultValue = getResources().getInteger(R.integer.pref_radius_min);
        radius = sharedPref.getInt(getString(R.string.pref_radius), defaultValue);
        demand_true_or_cancel_false = sharedPref.getBoolean(getString(R.string.pref_main_request_boolean), false);
        supply_true_or_cancel_false = sharedPref.getBoolean(getString(R.string.pref_main_supply_request_boolean), false);
        demand_pet = sharedPref.getBoolean(getString(R.string.pref_demand_pet), false);
        demand_seats = sharedPref.getInt(getString(R.string.pref_supply_seats_in_car), 1);
        Set<String> setResultKey = sharedPref.getStringSet(getString(R.string.pref_resultKey_forDemand), null);
        if(setResultKey != null) {
            resultKeySent.clear();
            resultKeySent.addAll(setResultKey);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand");

        if(broadcastReceiverLocUpdate == null){
            broadcastReceiverLocUpdate = new BroadcastReceiver() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onReceive(Context context, Intent intent) {
                    Location location = (Location)intent.getExtras().get("location");
                    if(location!=null) {
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        Log.d(TAG, "broadcastReceiverLocUpdate");
                        if (demand_true_or_cancel_false){
                            if (geoQuery!=null){
                                initialization_flag = true;
                                geoQuery.setCenter(new GeoLocation(latitude,longitude));
                                geoFireDemand.setLocation(facebookUserId, new GeoLocation(latitude, longitude));
                            }
                        }
                        else if (supply_true_or_cancel_false){
                            geoFireSupply.setLocation(facebookUserId, new GeoLocation(latitude, longitude));
                        }
                    }
                }
            };
        }
        if(broadcastReceiverLocOff == null){
            broadcastReceiverLocOff = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if(isAllActiveLocationProvidersDisabled() && !main_activity_is_on && !result_demand_activity_is_on && !result_supply_activity_is_on)
                        buildAndFireNotificationAlertLocationOff();
                }
            };
        }
        if(broadcastReceiverRadiusUpdate == null){
            broadcastReceiverRadiusUpdate = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "broadcastReceiver_RADIUS_Update");
                    radius = (int)intent.getExtras().get(SettingsActivity.EXTRA_RADIUS_MESSAGE);
                    if (demand_true_or_cancel_false){
                        if (geoQuery!=null){
                            initialization_flag = true;
                            geoQuery.setRadius(radius);
                        }
                    }
                }
            };
        }
        if(broadcastReceiverMainResume == null){
            broadcastReceiverMainResume = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "broadcastReceiverMain_RESUME");
                    main_activity_is_on = true;
                    mNotificationManager.cancel(getResources().getInteger(R.integer.notification_location_off_id));
                }
            };
        }
        if(broadcastReceiverMainPause == null){
            broadcastReceiverMainPause = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "broadcastReceiverMain_PAUSE");
                    main_activity_is_on = false;
                }
            };
        }
        if(broadcastReceiverResultDemandResume == null){
            broadcastReceiverResultDemandResume = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "broadcastReceiverMain_RESUME");
                    result_demand_activity_is_on = true;
                    mNotificationManager.cancel(getResources().getInteger(R.integer.notification_supply_found_id));
                }
            };
        }
        if(broadcastReceiverResultDemandPause == null){
            broadcastReceiverResultDemandPause = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "broadcastReceiverMain_PAUSE");
                    result_demand_activity_is_on = false;
                }
            };
        }
        if(broadcastReceiverResultSupplyResume == null){
            broadcastReceiverResultSupplyResume = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "broadcastReceiverMain_RESUME");
                    result_supply_activity_is_on = true;
                    mNotificationManager.cancel(getResources().getInteger(R.integer.notification_demand_found_id));
                }
            };
        }
        if(broadcastReceiverResultSupplyPause == null){
            broadcastReceiverResultSupplyPause = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "broadcastReceiverMain_PAUSE");
                    result_supply_activity_is_on = false;
                }
            };
        }
        if(broadcastReceiverMainRequest == null){
            broadcastReceiverMainRequest = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    demand_true_or_cancel_false = (boolean)intent.getExtras().get(MainActivity.EXTRA_REQUEST_MESSAGE);
                    Log.d(TAG, "broadcastReceiverMain_REQUEST bool : " + demand_true_or_cancel_false);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putBoolean(getString(R.string.pref_main_request_boolean), demand_true_or_cancel_false);
                    editor.apply();
                    mainRequest();
                }
            };
        }
        if(broadcastReceiverDemandDetails == null){
            broadcastReceiverDemandDetails = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Bundle bundle = intent.getExtras();

                    demand_destination = bundle.getString("demand_destination");
                    demand_pet = bundle.getBoolean("demand_pet");
                    demand_seats = bundle.getInt("demand_seats");
                }
            };
        }
        if(broadcastReceiverMainSupplyRequest == null){
            broadcastReceiverMainSupplyRequest = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    supply_true_or_cancel_false = (boolean)intent.getExtras().get("supply_true_or_cancel_false");
                    Log.d(TAG, "broadcastReceiverMain_SUPPLY_REQUEST bool : " + supply_true_or_cancel_false);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putBoolean(getString(R.string.pref_main_supply_request_boolean), supply_true_or_cancel_false);
                    editor.apply();
                    String historyKey = sharedPref.getString(getString(R.string.pref_historyKey), "-n-u-l-l-");
                    listenNewDemandQuery = refHistory.orderByKey().equalTo(historyKey);
                    mainSupplyRequest();
                }
            };
        }

        if(localBroadcastManager == null) {
            localBroadcastManager = LocalBroadcastManager.getInstance(this);
            localBroadcastManager.registerReceiver(broadcastReceiverLocUpdate, new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_UPDATE));
            localBroadcastManager.registerReceiver(broadcastReceiverLocOff, new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_OFF));
            localBroadcastManager.registerReceiver(broadcastReceiverRadiusUpdate, new IntentFilter(SettingsActivity.BROADCAST_ACTION_RADIUS_UPDATE));
            localBroadcastManager.registerReceiver(broadcastReceiverMainResume, new IntentFilter(MainActivity.BROADCAST_ACTION_MAIN_RESUME));
            localBroadcastManager.registerReceiver(broadcastReceiverMainPause, new IntentFilter(MainActivity.BROADCAST_ACTION_MAIN_PAUSE));
            localBroadcastManager.registerReceiver(broadcastReceiverResultDemandResume, new IntentFilter(ResultDemandActivity.BROADCAST_ACTION_RESULT_DEMAND_RESUME));
            localBroadcastManager.registerReceiver(broadcastReceiverResultDemandPause, new IntentFilter(ResultDemandActivity.BROADCAST_ACTION_RESULT_DEMAND_PAUSE));
            localBroadcastManager.registerReceiver(broadcastReceiverResultSupplyResume, new IntentFilter(ResultSupplyActivity.BROADCAST_ACTION_RESULT_SUPPLY_RESUME));
            localBroadcastManager.registerReceiver(broadcastReceiverResultSupplyPause, new IntentFilter(ResultSupplyActivity.BROADCAST_ACTION_RESULT_SUPPLY_PAUSE));
            localBroadcastManager.registerReceiver(broadcastReceiverMainRequest, new IntentFilter(MainActivity.BROADCAST_ACTION_MAIN_REQUEST));
            localBroadcastManager.registerReceiver(broadcastReceiverDemandDetails, new IntentFilter(DemandDialogFragment.BROADCAST_ACTION_DEMAND_DIALOG_FRAGMENT_REQUEST));
            localBroadcastManager.registerReceiver(broadcastReceiverMainSupplyRequest, new IntentFilter(MainActivity.BROADCAST_ACTION_MAIN_SUPPLY_REQUEST));
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if(geoQuery!=null) {
            geoQuery.removeAllListeners();
        }

        // The id of the channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationManager.deleteNotificationChannel(channel_id);
        }
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        if(broadcastReceiverLocUpdate != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverLocUpdate);
        }
        if(broadcastReceiverLocOff != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverLocOff);
        }
        if(broadcastReceiverMainResume != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverMainResume);
        }
        if(broadcastReceiverMainPause != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverMainPause);
        }
        if(broadcastReceiverResultDemandResume != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverResultDemandResume);
        }
        if(broadcastReceiverResultDemandPause != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverResultDemandPause);
        }
        if(broadcastReceiverResultSupplyResume != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverResultSupplyResume);
        }
        if(broadcastReceiverResultSupplyPause != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverResultSupplyPause);
        }
        if(broadcastReceiverMainRequest != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverMainRequest);
        }
        if(broadcastReceiverRadiusUpdate != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverRadiusUpdate);
        }
        if(broadcastReceiverDemandDetails != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverDemandDetails);
        }
        if(broadcastReceiverMainSupplyRequest != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverMainSupplyRequest);
        }

        Intent i_stop = new Intent(getApplicationContext(), LocationService.class);
        stopService(i_stop);
    }

    private void buildAndFireNotification() {
        Log.d(TAG, "buildAndFire_NOTIFICATION");
        notificationBuilder = new NotificationCompat.Builder(this, channel_id);
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notification_supply_found_title))
                .setContentText(getString(R.string.notification_supply_found_text))
                .setTicker(getString(R.string.notification_supply_found_title))
                .setPriority(Notification.PRIORITY_HIGH) //or Notification.PRIORITY_MAX maybe ?
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setLights(Color.MAGENTA, 500, 1500)
                .setOngoing(true) //Ongoing notifications are sorted above the regular notifications in the notification panel and  do not have an 'X' close button, and are not affected by the "Clear all" button
                .setVisibility(Notification.VISIBILITY_PRIVATE);
            /*
            NotificationCompat.Builder setDeleteIntent (PendingIntent intent)
            Supply a PendingIntent to send when the notification is cleared by the user directly from the notification panel. For example, this intent is sent when the user clicks the "Clear all" button, or the individual "X" buttons on notifications. This intent is not sent when the application calls NotificationManager.cancel(int).
            */
        // Creates an explicit intent for an Activity in your app
        Intent parentIntent = new Intent(this, MainActivity.class);
        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your app to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(parentIntent);

        Intent resultIntent = new Intent(this, ResultDemandActivity.class);

        Bundle bundle = new Bundle();
        bundle.putDouble("latitude",latitude);
        bundle.putDouble("longitude",longitude);

        resultIntent.putExtras(bundle);

        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        notificationBuilder.setContentIntent(resultPendingIntent);

        // mNotificationId is a unique integer your app uses to identify the
        // notification. For example, to cancel the notification, you can pass its ID
        // number to NotificationManager.cancel().
        mNotificationManager.notify(getResources().getInteger(R.integer.notification_supply_found_id), notificationBuilder.build());
    }

    private void mainRequest() {
        if (demand_true_or_cancel_false){
            Log.d(TAG, "mainRequest true");
            initialization_flag = true;
            queryFinished = 0;
            findRideFromFireBase();
        }
        else {
            Log.d(TAG, "mainRequest false");
            if(geoQuery!=null) {
                geoQuery.removeAllListeners();
            }
            geoQuery = null;
            on_process = false;
            querySent = 0;
            queryFinished = 0;
            forLoopFinished = false;
            resultKey.clear();
            resultLocation.clear();
            resultKeyTempBuffer.clear();
            resultLocationTempBuffer.clear();
            result_sent_already = false;
            resultKeySent.clear();
            mNotificationManager.cancel(getResources().getInteger(R.integer.notification_supply_found_id));
            mNotificationManager.cancel(getResources().getInteger(R.integer.notification_location_off_id));
        }
    }

    private void findRideFromFireBase(){
        Log.d(TAG, "FIND :  with radius = " + radius + "\nFIND : latitude, longitude" + latitude.toString() + ", " + longitude.toString());
        // Read from the database
        // creates a new query around [latitude, longitude] with a radius of "radius" kilometers
        geoQuery = geoFireSupply.queryAtLocation(new GeoLocation(latitude,longitude), radius);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                Log.d(TAG, "FIND : "+String.format("Key %s entered the search area at [%f,%f]", key, location.latitude, location.longitude));
                if(initialization_flag && resultKey.size() < getResources().getInteger(R.integer.pref_max_supply_found) ) {
                    Log.d(TAG, "FIND : case 1");
                    resultKey.add(key);
                    resultLocation.add(new ResultLocation(location));
                }

                else if(initialization_flag && resultKey.size() >= getResources().getInteger(R.integer.pref_max_supply_found) && !on_process){
                    Log.d(TAG, "FIND : case 2");
                    resultKeyTempBuffer.add(key);
                    resultLocationTempBuffer.add(new ResultLocation(location));
                    on_process = true;
                    initialization_flag = false;
                    processResult();
                }
                else if (!initialization_flag && !on_process){
                    Log.d(TAG, "FIND : case 3");
                    resultKey.add(key);
                    resultLocation.add(new ResultLocation(location));
                    on_process = true;
                    processResult();
                }
                else if (!initialization_flag && on_process && resultKeyTempBuffer.size() < getResources().getInteger(R.integer.pref_max_supply_found_buffer)){
                    Log.d(TAG, "FIND : case 4");
                    //add temporary buffer if on process with size limit for performance
                    resultKeyTempBuffer.add(key);
                    resultLocationTempBuffer.add(new ResultLocation(location));
                }
            }

            @Override
            public void onKeyExited(String key) {
                Log.d(TAG, "FIND : "+String.format("Key %s is no longer in the search area", key));
                int index = resultKey.indexOf(key);

                if(index !=-1) { //todo check if race with process() problem ??
                    resultKey.remove(index);
                    resultLocation.remove(index);
                    if(!resultKeyTempBuffer.isEmpty()){
                        resultKey.add(resultKeyTempBuffer.remove(0));
                        resultLocation.add(resultLocationTempBuffer.remove(0));
                    }
                }

                int indexBuffer = resultKeyTempBuffer.indexOf(key);
                if(indexBuffer !=-1) {
                    resultKeyTempBuffer.remove(indexBuffer);
                    resultLocationTempBuffer.remove(indexBuffer);
                }

                int indexSent = resultKeySent.indexOf(key);
                if (indexSent !=-1) {
                    resultKeySent.remove(indexSent);
                    if(resultKeySent.isEmpty()){
                        if(!result_sent_already) {
                            mNotificationManager.cancel(getResources().getInteger(R.integer.notification_supply_found_id));
                        }
                    }
                    Set<String> set = new HashSet<>();
                    set.addAll(resultKeySent);
                    SharedPreferences.Editor edit=sharedPref.edit();
                    edit.putStringSet(getString(R.string.pref_resultKey_forDemand), set);
                    edit.apply();
                    broadcastResultUpdate(); //update notification / result activity
                }

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d(TAG, "FIND : "+String.format("Key %s moved within the search area to [%f,%f]", key, location.latitude, location.longitude));
                int index = resultKey.indexOf(key);
                int indexBuffer = resultKeyTempBuffer.indexOf(key);

                if(index !=-1) {
                    resultLocation.set(index, new ResultLocation(location));
                }
                if(indexBuffer !=-1) {
                    resultLocationTempBuffer.set(indexBuffer, new ResultLocation(location));
                }
            }

            @Override
            public void onGeoQueryReady() {
                Log.d(TAG, "FIND : "+"All initial data has been loaded and events have been fired!");
                initialization_flag = false;
                if(resultKey.size()>0){
                    if(!on_process){
                        on_process = true;
                        processResult();
                    }
                }
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

    private void processResult() {
        // ... check data... remove if not enough seats...
        ArrayList<String> oldKey = new ArrayList<>(resultKey); // to detect a change
        querySent = 0;
        queryFinished = 0;
        forLoopFinished = false;

        for (int i = 0; i <= getResources().getInteger(R.integer.pref_max_supply_found); i++) {
            if (i == resultKey.size()) {
                while (!resultKeyTempBuffer.isEmpty() && resultKey.size() < getResources().getInteger(R.integer.pref_max_supply_found)) {
                    resultKey.add(resultKeyTempBuffer.remove(0));
                    resultLocation.add(resultLocationTempBuffer.remove(0));
                }
                if (i == resultKey.size())
                    break;
            }
            if (!resultKey.equals(oldKey)) {//todo make sure the equals() works as we wanted to
                //if a change was made then we need to recheck the same i to not skip an element in the list
                i--;
                if (i < 0)
                    i = 0;
                oldKey.clear();
                oldKey.addAll(resultKey);
            }

            querySent++;
            Query checkKeySupplyQuery = refSupply.orderByKey().equalTo(resultKey.get(i));
            checkKeySupplyQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (!dataSnapshot.exists()) {
                        //not possible to arrive here...
                    } else {
                        //using for loop because FireBase returns JSON object that are always list.
                        for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                            //normally should be only once for this loop since unique KeyId

                            //check conditions:
                            checkConditions(userSnapshot);

                            //add 1 to flag to indicate this query finished
                            queryFinished++;

                            Log.d(TAG, "ProcessResult in Query onDataChange : forLoopFinished = " + forLoopFinished);
                            Log.d(TAG, "ProcessResult in Query onDataChange : querySent = " + querySent);
                            Log.d(TAG, "ProcessResult in Query onDataChange : queryFinished = " + queryFinished);
                            if (forLoopFinished) {
                                if ((queryFinished == querySent)) {
                                    postProcessResult();
                                }
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
                }
            });
        }

        forLoopFinished = true;
    }

    private void postProcessResult(){
        Log.d(TAG, "postProcessResult : (queryFinished == querySent) ? " + (queryFinished == querySent));
        querySent = 0;
        queryFinished = 0;
        forLoopFinished = false;

        if(resultKey.isEmpty()){
            on_process = false;
            return;
        }

        if(!result_sent_already) {

            //Add resultKey to resultKeySent to be able to remember what we sent and save it
            resultKeySent.addAll(resultKey);
            Set<String> set = new HashSet<>();
            set.addAll(resultKey);
            SharedPreferences.Editor edit=sharedPref.edit();
            edit.putStringSet(getString(R.string.pref_resultKey_forDemand), set);
            edit.apply();

            //clearing the lists we just sent to result to not sent it again
            resultKey.clear();
            resultLocation.clear();

            if (main_activity_is_on && !result_demand_activity_is_on && !result_supply_activity_is_on) {
                Log.d(TAG, "processResult main activity ON");

                broadcastResult();
            }
            else if(result_demand_activity_is_on){
                broadcastResultUpdate();
            }
            else {
                Log.d(TAG, "processResult main activity OFF");

                buildAndFireNotification();
            }

            result_sent_already = true;

            on_process = false;
        }
        else {
            // update notification / result activity that there is more supply found
            //Add resultKey to resultKeySent to be able to remember what we sent and save it
            resultKeySent.addAll(resultKey);
            Set<String> set = new HashSet<>();
            set.addAll(resultKey);
            SharedPreferences.Editor edit=sharedPref.edit();
            edit.putStringSet(getString(R.string.pref_resultKey_forDemand), set);
            edit.apply();

            if (main_activity_is_on && !result_demand_activity_is_on && !result_supply_activity_is_on) {
                Log.d(TAG, "processResult main activity ON");

                broadcastResult();
            }
            else if(result_demand_activity_is_on){
                broadcastResultUpdate();
            }

            on_process = false;
        }
    }

    private void broadcastResult(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_SUPPLY_FOUND);

        Bundle bundle = new Bundle();
        bundle.putDouble("latitude",latitude);
        bundle.putDouble("longitude",longitude);


        intent.putExtras(bundle);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void broadcastResultUpdate(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_SUPPLY_UPDATE);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void checkConditions(DataSnapshot userSnapshot){
        MySupply supply = userSnapshot.getValue(MySupply.class);

        assert supply != null;
        if (demand_pet && !supply.petAllowed) { //if supply don't allow pet but demand has a pet
            int index = resultKey.indexOf(userSnapshot.getKey());
            resultKey.remove(index);
            resultLocation.remove(index);
            //then add from the buffer to keep feeding a minimum amount if possible
            if(!resultKeyTempBuffer.isEmpty()){
                resultKey.add(resultKeyTempBuffer.remove(0));
                resultLocation.add(resultLocationTempBuffer.remove(0));
            }
        }
        else if (demand_seats >  supply.remainingSeats) { ///if supply don't have enough remaining seats
            int index = resultKey.indexOf(userSnapshot.getKey());
            resultKey.remove(index);
            resultLocation.remove(index);
            //then add from the buffer to keep feeding a minimum amount if possible
            if(!resultKeyTempBuffer.isEmpty()){
                resultKey.add(resultKeyTempBuffer.remove(0));
                resultLocation.add(resultLocationTempBuffer.remove(0));
            }
        }
    }


    //For the supply here down
    private boolean result_for_supply_sent_already = false;
    private Set<String> oldSet = new HashSet<>(); // to detect a change
    private ValueEventListener valueEventListenerForSupplyRequest = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            if (!dataSnapshot.exists()) {
                Log.e(TAG, "user not found in the database NOT NORMAL");
            } else {
                Set<String> set = new HashSet<>();
                set.clear();
                String historyKey = sharedPref.getString(getString(R.string.pref_historyKey), "-n-u-l-l-");
                Log.d(TAG, "AAdemandIdSnapshot" + dataSnapshot.child(historyKey).child("demandUsers").toString());

                for (DataSnapshot demandIdSnapshot : dataSnapshot.child(historyKey).child("demandUsers").getChildren()) {
                    String demandId = demandIdSnapshot.getKey();
                    Log.d(TAG, "valueEventListenerForSupplyRequest : onDataChange : demandId = "+demandId);
                    set.add(demandId);
                }
                if(set.size() == 0)
                    return;

                if(!set.equals(oldSet)){
                    oldSet.clear();
                    oldSet.addAll(set);

                    SharedPreferences.Editor edit=sharedPref.edit();
                    edit.putStringSet(getString(R.string.pref_resultKey_forSupply), set);
                    edit.apply();
                    postProcessResultForSupplyRequest();
                }
            }
        }
        @Override
        public void onCancelled(DatabaseError databaseError) {
            Log.w(TAG, "DATABASE Failed to read value. in valueEventListenerForSupplyRequest", databaseError.toException());
        }
    };
    private void mainSupplyRequest() {
        if (supply_true_or_cancel_false){
            Log.d(TAG, "mainSupplyRequest true");
            result_for_supply_sent_already = false;
            oldSet.clear();
            listenNewDemandQuery.addValueEventListener(valueEventListenerForSupplyRequest);
        }
        else {
            Log.d(TAG, "mainSupplyRequest false");
            if(listenNewDemandQuery!=null) {
                listenNewDemandQuery.removeEventListener(valueEventListenerForSupplyRequest);
            }
            result_for_supply_sent_already = false;
            mNotificationManager.cancel(getResources().getInteger(R.integer.notification_demand_found_id));
            mNotificationManager.cancel(getResources().getInteger(R.integer.notification_location_off_id));
        }
    }

    private void postProcessResultForSupplyRequest() {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.pref_supply_booked_status), true);
        editor.apply();
        if (main_activity_is_on && !result_demand_activity_is_on && !result_supply_activity_is_on) {
            Log.d(TAG, "postProcessResultForSupplyRequest main activity ON");

            broadcastSupplyResult();
        }
        else if(result_supply_activity_is_on){
            broadcastSupplyResultUpdate();
        }
        else { //else if (result_for_supply_sent_already) //todo pick one
            Log.d(TAG, "postProcessResultForSupplyRequest main activity OFF");

            buildAndFireNotificationForSupply();
        }
        result_for_supply_sent_already = true;
    }

    private void buildAndFireNotificationForSupply() {
        Log.d(TAG, "buildAndFire_NOTIFICATION");
        notificationBuilder = new NotificationCompat.Builder(this, channel_id);
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notification_demand_found_title))
                .setContentText(getString(R.string.notification_demand_found_text))
                .setTicker(getString(R.string.notification_demand_found_title))
                .setPriority(Notification.PRIORITY_HIGH) //or Notification.PRIORITY_MAX maybe ?
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setLights(Color.MAGENTA, 500, 1500)
                .setOngoing(true) //Ongoing notifications are sorted above the regular notifications in the notification panel and  do not have an 'X' close button, and are not affected by the "Clear all" button
                .setVisibility(Notification.VISIBILITY_PRIVATE);
            /*
            NotificationCompat.Builder setDeleteIntent (PendingIntent intent)
            Supply a PendingIntent to send when the notification is cleared by the user directly from the notification panel. For example, this intent is sent when the user clicks the "Clear all" button, or the individual "X" buttons on notifications. This intent is not sent when the application calls NotificationManager.cancel(int).
            */
        // Creates an explicit intent for an Activity in your app
        Intent parentIntent = new Intent(this, MainActivity.class);
        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your app to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(parentIntent);

        Intent resultIntent = new Intent(this, ResultSupplyActivity.class);

        Bundle bundle = new Bundle();
        bundle.putDouble("latitude",latitude);
        bundle.putDouble("longitude",longitude);

        resultIntent.putExtras(bundle);

        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        notificationBuilder.setContentIntent(resultPendingIntent);

        // mNotificationId is a unique integer your app uses to identify the
        // notification. For example, to cancel the notification, you can pass its ID
        // number to NotificationManager.cancel().
        mNotificationManager.notify(getResources().getInteger(R.integer.notification_demand_found_id), notificationBuilder.build());
    }

    private void broadcastSupplyResult(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_DEMAND_FOUND);

        Bundle bundle = new Bundle();
        bundle.putDouble("latitude",latitude);
        bundle.putDouble("longitude",longitude);


        intent.putExtras(bundle);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void broadcastSupplyResultUpdate(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_DEMAND_UPDATE);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void buildAndFireNotificationAlertLocationOff() {
        Log.d(TAG, "buildAndFireNotificationAlertLocationOff");
        notificationBuilder = new NotificationCompat.Builder(this, channel_id);
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notification_location_off_title))
                .setContentText(getString(R.string.notification_location_off_text))
                .setTicker(getString(R.string.notification_location_off_title))
                .setPriority(Notification.PRIORITY_HIGH) //or Notification.PRIORITY_MAX maybe ?
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setLights(Color.MAGENTA, 500, 1500)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
            /*
            NotificationCompat.Builder setDeleteIntent (PendingIntent intent)
            Supply a PendingIntent to send when the notification is cleared by the user directly from the notification panel. For example, this intent is sent when the user clicks the "Clear all" button, or the individual "X" buttons on notifications. This intent is not sent when the application calls NotificationManager.cancel(int).
            */
        // mNotificationId is a unique integer your app uses to identify the
        // notification. For example, to cancel the notification, you can pass its ID
        // number to NotificationManager.cancel().
        mNotificationManager.notify(getResources().getInteger(R.integer.notification_supply_found_id), notificationBuilder.build());
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
}


/*
//the name of the method explains it well...
    public boolean isTwoArrayListsWithSameValues(ArrayList<Object> list1, ArrayList<Object> list2)
    {
        //null checking
        if(list1==null && list2==null)
            return true;
        if((list1 == null && list2 != null) || (list1 != null && list2 == null))
            return false;

        if(list1.size()!=list2.size())
            return false;
        for(Object itemList1: list1)
        {
            if(!list2.contains(itemList1))
                return false;
        }

        return true;
    }
 */
