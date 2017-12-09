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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;


/**
 * This is the Firebase Service class
 * It connect the app to the Firebase database and listen to change occurring into it
 * It also perfoms various query to extract the relevant information needed
 * It provides to the app the facebookUserIdFound and the location of the Supply found
 * It communicate through only one broadcast : BROADCAST_ACTION_SUPPLY_FOUND
 */

public class FirebaseService extends Service {
    public static final String BROADCAST_ACTION_SUPPLY_FOUND = "com.realtimehitchhiker.hitchgo.SUPPLY_FOUND";
    public static final String TAG = "FIREBASE_SERVICE_DEBUG";

    private NotificationManager mNotificationManager;
    private String channel_id; //For API 26+
    private NotificationCompat.Builder notificationBuilder;

    private FirebaseUser currentUser;
    private DatabaseReference refUsers;
    private DatabaseReference refSupply;
    private DatabaseReference refDemand;
    private GeoFire geoFireSupply, geoFireDemand;
    private GeoQuery geoQuery = null;
    private MyGlobalHistory globalHistory;

    private LocalBroadcastManager localBroadcastManager;
    private BroadcastReceiver broadcastReceiverLocUpdate, broadcastReceiverLocOff, broadcastReceiverRadiusUpdate;
    private BroadcastReceiver broadcastReceiverMainResume, broadcastReceiverMainPause, broadcastReceiverMainRequest;
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
    private boolean demand_true_or_cancel_false;
    private boolean on_process = false;
    private boolean initialization_flag = true;
    private boolean result_sent_already = false;

    //RESULT
    private ArrayList<String> resultKey = new ArrayList<>();
    private ArrayList<ResultLocation> resultLocation = new ArrayList<>();

    //Temporary Buffer
    private ArrayList<String> resultKeyTempBuffer = new ArrayList<>();
    private ArrayList<ResultLocation> resultLocationTempBuffer = new ArrayList<>();

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
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myDataBaseRef = database.getReference();
        refUsers = myDataBaseRef.child("users/");
        refSupply = myDataBaseRef.child("supply/");
        refDemand = myDataBaseRef.child("demand/");
        DatabaseReference refHistory = myDataBaseRef.child("history/");
        geoFireSupply = new GeoFire(myDataBaseRef.child("geofire/geofire-supply"));
        geoFireDemand = new GeoFire(myDataBaseRef.child("geofire/geofire-demand"));
        globalHistory = new MyGlobalHistory(refHistory);

        //SharedPreferences
        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        int defaultValue = getResources().getInteger(R.integer.pref_radius_min);
        radius = sharedPref.getInt(getString(R.string.pref_radius), defaultValue);
        demand_true_or_cancel_false = sharedPref.getBoolean(getString(R.string.pref_main_request_boolean), false);
        demand_pet = sharedPref.getBoolean(getString(R.string.pref_demand_pet), false);
        demand_seats = sharedPref.getInt(getString(R.string.pref_supply_seats_in_car), 1);
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
                            }
                        }
                    }
                }
            };
        }
        if(broadcastReceiverLocOff == null){
            broadcastReceiverLocOff = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    //todo for future improvements could fire a notification asking to enable location again if not in app
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

        if(localBroadcastManager == null) {
            localBroadcastManager = LocalBroadcastManager.getInstance(this);
            localBroadcastManager.registerReceiver(broadcastReceiverLocUpdate, new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_UPDATE));
            localBroadcastManager.registerReceiver(broadcastReceiverLocOff, new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_OFF));
            localBroadcastManager.registerReceiver(broadcastReceiverRadiusUpdate, new IntentFilter(SettingsActivity.BROADCAST_ACTION_RADIUS_UPDATE));
            localBroadcastManager.registerReceiver(broadcastReceiverMainResume, new IntentFilter(MainActivity.BROADCAST_ACTION_MAIN_RESUME));
            localBroadcastManager.registerReceiver(broadcastReceiverMainPause, new IntentFilter(MainActivity.BROADCAST_ACTION_MAIN_PAUSE));
            localBroadcastManager.registerReceiver(broadcastReceiverMainRequest, new IntentFilter(MainActivity.BROADCAST_ACTION_MAIN_REQUEST));
            localBroadcastManager.registerReceiver(broadcastReceiverDemandDetails, new IntentFilter(DemandDialogFragment.BROADCAST_ACTION_DEMAND_DIALOG_FRAGMENT_REQUEST));
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
        if(broadcastReceiverMainRequest != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverMainRequest);
        }
        if(broadcastReceiverRadiusUpdate != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverRadiusUpdate);
        }
        if(broadcastReceiverDemandDetails != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverDemandDetails);
        }

        Intent i_stop = new Intent(getApplicationContext(), LocationService.class);
        stopService(i_stop);
    }

    private void buildAndFireNotification() {
        Log.d(TAG, "buildAndFire_NOTIFICATION");
        notificationBuilder = new NotificationCompat.Builder(this, channel_id);
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setTicker(getString(R.string.notification_title))
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
        Intent resultIntent = new Intent(this, ResultActivity.class);

        Bundle bundle = new Bundle();
        bundle.putStringArrayList("facebookUserIdFound", new ArrayList<>(resultKey));
        bundle.putParcelableArrayList("resultLocationFound", new ArrayList<>(resultLocation));

        resultIntent.putExtras(bundle);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your app to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(ResultActivity.class);
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
        mNotificationManager.notify(getResources().getInteger(R.integer.notification_id), notificationBuilder.build());
    }

    private void mainRequest() {
        if (demand_true_or_cancel_false){
            Log.d(TAG, "mainRequest true");
            initialization_flag = true;
            findRideFromFireBase();
        }
        else {
            Log.d(TAG, "mainRequest false");
            if(geoQuery!=null) {
                geoQuery.removeAllListeners();
            }
            geoQuery = null;
            on_process = false;
            resultKey.clear();
            resultLocation.clear();
            resultKeyTempBuffer.clear();
            resultLocationTempBuffer.clear();
            result_sent_already = false;
        }
    }

    public void findRideFromFireBase(){
        Log.d(TAG, "FIND :  with radius = " + radius + "\nFIND : latitude, longitude" + latitude.toString() + ", " + longitude.toString());
        // Read from the database
        // creates a new query around [latitude, longitude] with a radius of "radius" kilometers
        geoQuery = geoFireSupply.queryAtLocation(new GeoLocation(latitude,longitude), radius);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                Log.d(TAG, "FIND : "+String.format("Key %s entered the search area at [%f,%f]", key, location.latitude, location.longitude));
                if(initialization_flag && resultKey.size() < getResources().getInteger(R.integer.pref_max_supply_found) ) {
                    resultKey.add(key);
                    resultLocation.add(new ResultLocation(location));
                }

                else if(initialization_flag && resultKey.size() >= getResources().getInteger(R.integer.pref_max_supply_found) && !on_process){
                    on_process = true;
                    initialization_flag = false;
                    processResult();
                }
                else if (!initialization_flag && !on_process){
                    resultKey.add(key);
                    resultLocation.add(new ResultLocation(location));
                    on_process = true;
                    processResult();
                }
                else if (!initialization_flag && on_process && resultKeyTempBuffer.size() < getResources().getInteger(R.integer.pref_max_supply_found_buffer)){
                    //add temporary buffer if on process with size limit for performance
                    resultKeyTempBuffer.add(key);
                    resultLocationTempBuffer.add(new ResultLocation(location));
                }
            }

            @Override
            public void onKeyExited(String key) {
                Log.d(TAG, "FIND : "+String.format("Key %s is no longer in the search area", key));
                int index = resultKey.indexOf(key);
                int indexBuffer = resultKeyTempBuffer.indexOf(key);

                if(index !=-1) { //todo race with process() problem ??
                    resultKey.remove(index);
                    resultLocation.remove(index);
                    if(!resultKeyTempBuffer.isEmpty()){
                        resultKey.add(resultKeyTempBuffer.remove(0));
                        resultLocation.add(resultLocationTempBuffer.remove(0));
                        //todo update notification / result activity ?
                    }
                    if(resultKey.isEmpty()){//todo [A] fix the logic here since we delete the sent keys at [B]...
                        mNotificationManager.cancel(getResources().getInteger(R.integer.notification_id));
                        on_process = false;
                    }
                }

                if(indexBuffer !=-1) {
                    resultKeyTempBuffer.remove(index);
                    resultLocationTempBuffer.remove(index);
                }

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d(TAG, "FIND : "+String.format("Key %s moved within the search area to [%f,%f]", key, location.latitude, location.longitude));
                int index = resultKey.indexOf(key);
                int indexBuffer = resultKeyTempBuffer.indexOf(key);

                if(index !=-1) {
                    resultLocation.set(index, new ResultLocation(location));
                    //todo update notification / result activity ?
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

    private void processResult(){
        // ... check data... remove if not enough seats...
        ArrayList<String> oldKey = new ArrayList<>(resultKey); // to detect a change
        for (int i=0; i<=getResources().getInteger(R.integer.pref_max_supply_found); i++) {
            if(i == resultKey.size()){
                while (!resultKeyTempBuffer.isEmpty() && resultKey.size()<getResources().getInteger(R.integer.pref_max_supply_found)){
                    resultKey.add(resultKeyTempBuffer.remove(0));
                    resultLocation.add(resultLocationTempBuffer.remove(0));
                }
                if(i == resultKey.size())
                    break;
            }
            if(resultKey.equals(oldKey)) {//todo make sure the equals() works as we wanted to
                //if a change was made then we need to recheck the same i to not skip an element in the list
                i--;
                if(i<0)
                    i=0;
                oldKey.clear();
                oldKey.addAll(resultKey);
            }

            Query checkKeySupplyQuery = refSupply.orderByKey().equalTo(resultKey.get(i));
            checkKeySupplyQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (!dataSnapshot.exists()) {
                        //not possible to arrive here...
                    } else {
                        //using for loop because FireBase returns JSON object that are always list.
                        for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                            MySupply supply = userSnapshot.getValue(MySupply.class); //normally should be only one since unique KeyId

                            //check conditions:
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
                    }
                }
                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
                }
            });
        }


        if(resultKey.isEmpty()){
            on_process = false;
            return;
        }


        if(!result_sent_already) {
            if (main_activity_is_on) {
                Log.d(TAG, "processResult main activity ON");

                broadcastResult();
            } else {
                Log.d(TAG, "processResult main activity OFF");

                buildAndFireNotification();
            }

            result_sent_already = true;

            //clearing the lists we just sent to result to not sent it again
            resultKey.clear(); //todo [b] fix the logic here to work with [A]...
            resultLocation.clear();
            on_process = false;
        }
        else {
            // stay with the on_process flag set to true to freeze the resultKey/Location lists. (but the buffer are still loading)
        }
    }

    private void broadcastResult(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_SUPPLY_FOUND);

        Bundle bundle = new Bundle();
        bundle.putStringArrayList("facebookUserIdFound", new ArrayList<>(resultKey));
        bundle.putParcelableArrayList("resultLocationFound", new ArrayList<>(resultLocation));

        intent.putExtras(bundle);
        localBroadcastManager.sendBroadcast(intent);
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
