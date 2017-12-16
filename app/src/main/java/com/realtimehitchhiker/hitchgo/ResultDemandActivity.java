package com.realtimehitchhiker.hitchgo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ResultDemandActivity extends AppCompatActivity {
    public static final String TAG = "RESULT_DEMAND_DEBUG";
    private static final int MY_PERMISSIONS_REQUEST_CALL_PHONE =198765;
    public static final String BROADCAST_ACTION_RESULT_DEMAND_RESUME = "com.realtimehitchhiker.hitchgo.RESULT_DEMAND_RESUME";
    public static final String BROADCAST_ACTION_RESULT_DEMAND_PAUSE = "com.realtimehitchhiker.hitchgo.RESULT_DEMAND_PAUSE";

    //FireBase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference refUsers;
    private DatabaseReference refSupply;
    private DatabaseReference refDemand;
    private DatabaseReference refHistory;
    private GeoFire geoFireSupply;
    private GeoFire geoFireDemand;
    private MyGlobalHistory globalHistory;
    private String historyKey;

    private BroadcastReceiver broadcastReceiverLocationUpdate, broadcastReceiverLocationProviderOff,
            broadcastReceiverSupplyFoundUpdate;

    private Button btnCall;
    private Button btnNext;
    private Button btnPrev;
    private Button btnBook;
    private TextView txtShowSupplyProfile;
    private TextView txtShowSupplyDetails;
    private ImageView imProfile;

    private String facebookUserId;
    private String phoneFound = "tel:0000000000";
    private int  demand_seats;
    private Double myLatitude = 90.0, myLongitude = 0.0; //initialize at pole North

    private SharedPreferences sharedPref;
    //flag
    private boolean flag_supply;
    private boolean flag_demand;
    private boolean flag_demand_booked;

    //RESULT
    private ArrayList<String> resultKey = new ArrayList<>();
    private Integer index = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_demand);

        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        flag_supply = sharedPref.getBoolean(getString(R.string.pref_supply_status), false);
        flag_demand = sharedPref.getBoolean(getString(R.string.pref_demand_status), false);
        demand_seats = sharedPref.getInt(getString(R.string.pref_demand_seats_in_car), 1);
        flag_demand_booked = sharedPref.getBoolean(getString(R.string.pref_demand_booked_status), false);

        Set<String> setResultKey = sharedPref.getStringSet(getString(R.string.pref_resultKey_forDemand), null);
        if(setResultKey != null) {
            resultKey.addAll(setResultKey);
        }


        // Get the Intent that started this activity and extract the bundle
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();

        if (bundle != null) {
            if (bundle.getStringArrayList("facebookUserIdFound") != null) {
                resultKey.addAll(bundle.getStringArrayList("facebookUserIdFound"));

                Set<String> set = new HashSet<>();
                set.addAll(resultKey);
                SharedPreferences.Editor edit=sharedPref.edit();
                edit.putStringSet(getString(R.string.pref_resultKey_forDemand), set);
                edit.apply();
            }
            myLatitude = bundle.getDouble("latitude");
            myLongitude = bundle.getDouble("longitude");
        }

        btnCall = findViewById(R.id.button_call);
        btnNext = findViewById(R.id.button_result_next);
        btnPrev = findViewById(R.id.button_result_prev);
        btnBook = findViewById(R.id.button_book);
        txtShowSupplyProfile = findViewById(R.id.textView_resultSupplyProfile);
        txtShowSupplyDetails = findViewById(R.id.textView_resultSupplyDetails);
        imProfile = findViewById(R.id.imageView_result);

        //For FireBase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myDataBaseRef = database.getReference();
        refUsers = myDataBaseRef.child("users/");
        refSupply = myDataBaseRef.child("supply/");
        refDemand = myDataBaseRef.child("demand/");
        refHistory = myDataBaseRef.child("history/");
        geoFireSupply = new GeoFire(myDataBaseRef.child("geofire/geofire-supply"));
        geoFireDemand = new GeoFire(myDataBaseRef.child("geofire/geofire-demand"));
        globalHistory = new MyGlobalHistory(refHistory);

        for(UserInfo profile : currentUser.getProviderData()) {
            // check if the provider id matches "facebook.com"
            if(FacebookAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                facebookUserId = profile.getUid();
            }
        }

        //update the first Result UI
        updateResultUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "RESULT_DEMAND_onResume" );

        //Broadcast state of main activity : OnResume
        broadcastOnResume();

        //Initialize (if need is) the broadcastReceivers for location updates and change in providers status
        if(broadcastReceiverLocationUpdate == null){
            broadcastReceiverLocationUpdate = new BroadcastReceiver() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOCATION_UPDATE);
                    Location location = (Location)intent.getExtras().get("location");
                    if(location!=null){
                        myLatitude = location.getLatitude();
                        myLongitude = location.getLongitude();
                    }
                }
            };
        }
        if(broadcastReceiverLocationProviderOff == null){
            broadcastReceiverLocationProviderOff = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOCATION_OFF);
                    if(isAllActiveLocationProvidersDisabled()){
                        //do nothing
                    }
                }
            };
        }
        if(broadcastReceiverSupplyFoundUpdate == null) {
            Log.d(TAG,"broadcastReceiverSupplyFound : INITIALIZE");
            broadcastReceiverSupplyFoundUpdate = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Set<String> setResultKey = sharedPref.getStringSet(getString(R.string.pref_resultKey_forDemand), null);
                    if(setResultKey != null) {
                        if (resultKey.size() > setResultKey.size()) { //then we removed on key
                            resultKey.clear(); //todo check if correct
                            resultKey.addAll(setResultKey);
                            if(index!=0)
                                index -= 1;
                            if(index >= resultKey.size())//safety check
                                index = resultKey.size();
                        }
                        else {//then we apparently add key(s) and the index is ok
                            resultKey.clear(); //todo check if correct
                            resultKey.addAll(setResultKey);
                            if(index >= resultKey.size())//safety check
                                index = resultKey.size();
                        }
                        updateResultUI();
                    }
                }
            };
        }

        //Register the broadcastReceivers locally (internal to the app)
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(broadcastReceiverLocationUpdate,new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_UPDATE));
        localBroadcastManager.registerReceiver(broadcastReceiverLocationProviderOff,new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_OFF));
        localBroadcastManager.registerReceiver(broadcastReceiverSupplyFoundUpdate,new IntentFilter(FirebaseService.BROADCAST_ACTION_SUPPLY_UPDATE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "RESULT_DEMAND_onPause" );

        //Broadcast state of main activity : OnPause
        broadcastOnPause();

        //Unregister the broadcastReceivers
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        if(broadcastReceiverLocationUpdate != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverLocationUpdate);
        }
        if(broadcastReceiverLocationProviderOff != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverLocationProviderOff);
        }
        if(broadcastReceiverSupplyFoundUpdate != null){
            localBroadcastManager.unregisterReceiver(broadcastReceiverSupplyFoundUpdate);
        }
    }

    private void updateResultUI(){
        //If we booked already than, we can just see our supply and we cannot rebook
        //Or if there is no key found
        if(flag_demand_booked || resultKey.size()==0){
            btnBook.setEnabled(false);
            btnNext.setEnabled(false);
            btnPrev.setEnabled(false);
            btnCall.setEnabled(false);
            if(resultKey.size()==0) {
                imProfile.setBackgroundResource(R.drawable.com_facebook_profile_picture_blank_square);
                txtShowSupplyProfile.setText("");
                txtShowSupplyDetails.setText("");
                return;
            }
        }
        else {
            btnBook.setEnabled(true);
            btnCall.setEnabled(true);

            if (index == (resultKey.size() - 1)) //at the end of the list, there is no next after
                btnNext.setEnabled(false);
            else
                btnNext.setEnabled(true);
            if (index == 0) //at the beginning of the list, there is no prev before
                btnPrev.setEnabled(false);
            else
                btnPrev.setEnabled(true);
        }

        //Set the picture
        String photoUrl = "https://graph.facebook.com/" + resultKey.get(index) + "/picture?type=large";

        new DownloadImageTask(new DownloadImageTask.AsyncResponse() {
            @Override
            public void processFinish(Bitmap output) {
                //imProfile.setImageBitmap(output);
                Drawable drawable = new BitmapDrawable(getResources(), output);
                imProfile.setBackground(drawable);
            }
        }).execute(photoUrl);

        getSupplyInfoFireBase(resultKey.get(index));
    }

    /**
     * Loads the next Supply found
     *
     * @param view the view it is linked to
     */
    public void nextSupply(View view){
        index++;
        updateResultUI();
    }

    /**
     * Loads the prev Supply found
     *
     * @param view the view it is linked to
     */
    public void prevSupply(View view){
        index--;
        updateResultUI();
    }

    public void getSupplyInfoFireBase(String fbUserId){

        Query checkKeySupplyQuery = refSupply.orderByKey().equalTo(fbUserId);
        Query checkKeyUserQuery = refUsers.orderByKey().equalTo(fbUserId);

        checkKeyUserQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    setInfoSupplyProfileHelper("user not found in the database", " ");
                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot supplySnapshot : dataSnapshot.getChildren()) {
                        MyUser user = supplySnapshot.getValue(MyUser.class); //normally should be only one since unique KeyId
                        setInfoSupplyProfileHelper(user.name, user.phone);
                        phoneFound = user.phone;
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
                setInfoSupplyProfileHelper("Error", " ");
            }
        });

        checkKeySupplyQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    txtShowSupplyDetails.setText(" ");
                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        MySupply supply = userSnapshot.getValue(MySupply.class); //normally should be only one since unique KeyId
                        setInfoSupplyDetailsHelper(supply);
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
                txtShowSupplyDetails.setText(" ");
            }
        });
    }

    public void setInfoSupplyProfileHelper(String name, String phone){
        String stringMessage = getString(R.string.txtProfile_name) +
                name + "\n" +
                getString(R.string.txtProfile_phone) +
                phone;
        txtShowSupplyProfile.setText(stringMessage);
    }

    public void setInfoSupplyDetailsHelper(MySupply supply){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(getString(R.string.txtDetails_dest))
                .append(supply.destination).append("\n")
                .append(getString(R.string.txtDetails_price))
                .append(supply.fuelPrice).append(" ").append(supply.currency).append("\n")
                .append(getString(R.string.txtDetails_petAllowed));
        if(supply.petAllowed)
            stringBuilder.append(getString(R.string.txtDetails_petAllowed_true));
        else
            stringBuilder.append(getString(R.string.txtDetails_petAllowed_false));

        stringBuilder.append("\n")
                .append(getString(R.string.txtDetails_remainingSeats))
                .append(supply.remainingSeats);

        txtShowSupplyDetails.setText(stringBuilder.toString());

        historyKey = supply.historyKey;
    }

    /**
     * This function get called when you press the phone number button
     * it creates intent to call the number appears on the button and asks permission if needed
     * @param view the view it is linked to (here the button to call)
     */
    public void callPhoneNumberSupply(View view) {
        //String phoneNumber = view.getTag().toString();
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        callIntent.setData(Uri.parse("tel:"+phoneFound));
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE},
                    MY_PERMISSIONS_REQUEST_CALL_PHONE);

        } else {
            startActivity(callIntent);
        }
    }

    //When the user responds to permission request, the system invokes your app's onRequestPermissionsResult() method.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CALL_PHONE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    callPhoneNumberSupply(btnCall);
                    // permission was granted, yay!
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    //then just simply dial the number
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse("tel:"+phoneFound));
                    dialIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    startActivity(dialIntent);
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    /**
     * This function get called when you press the book button
     * it start a transaction that try to book the supply
     * @param view the view it is linked to (here the button to book)
     */
    public void bookSupply(View view){
        transactionBookSupply();
    }

    public void transactionBookSupply() {
        refSupply.child(resultKey.get(index)).runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData) {
                MySupply supply = mutableData.getValue(MySupply.class);
                if (supply == null) {
                    Log.d(TAG, "do Transaction : supply == null");
                    //Note: Because doTransaction() is called multiple times, it must be able to handle null data. Even if there is existing data in your remote database, it may not be locally cached when the transaction function is run, resulting in null for the initial value.
                    // Or too late, ALL the seats are gone already and the supply was already removed.
                    return Transaction.success(mutableData);
                }

                Log.d(TAG, "do Transaction : supply != null");
                if (supply.remainingSeats >= demand_seats) {
                    // There is still enough seats, then book
                    supply.remainingSeats = supply.remainingSeats - demand_seats;
                } else {
                    // To late, the seats are gone already.
                    return Transaction.abort();
                }

                // Set value and report transaction success
                mutableData.setValue(supply);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean committed,
                                   DataSnapshot currentData) {
                // Transaction completed
                if (databaseError != null) {
                    Log.d(TAG, "post Transaction:onComplete databaseError:" + databaseError);
                    Toast.makeText(getApplicationContext(), databaseError.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
                if(committed){
                    Log.d(TAG, "post Transaction:onComplete:" + currentData);
                    if (!currentData.exists()) {
                        Log.d(TAG, "post Transaction:onComplete: !currentData.exists()");
                        // To late, all the seats are gone already. And the supply got already removed
                        Toast.makeText(getApplicationContext(), getString(R.string.toast_all_seats_gone_or_supply_canceled),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        finishDemanding();
                        MySupply supply = currentData.getValue(MySupply.class);
                        if (supply.remainingSeats == 0) {
                            Log.d(TAG, "post Transaction:onComplete: remainingSeats == 0");
                            // We just booked the last seat(s), so remove supply
                            removeSupply();
                        }
                    }
                }
                else {
                    Log.d(TAG, "post Transaction:onComplete: not committed");
                    // To late, the seats are gone already. The Transaction was abort
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_all_seats_gone),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void removeSupply(){
        refSupply.child(resultKey.get(index)).removeValue();
        geoFireSupply.removeLocation(resultKey.get(index));

        flag_supply = false;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.pref_supply_status), flag_supply);
        editor.apply();
    }

    public void removeDemand(){
        refDemand.child(facebookUserId).removeValue();
        //geoFireDemand.removeLocation(facebookUserId);
        //we do not delete geoFireDemand, it will be the supply that have to delete it
    }

    /**
     * stopMyServices() terminated the services FirebaseService and LocationService
     */
    public void stopMyServices() {
        //Stop FirebaseService (and FirebaseService will stop LocationService in is onDestroy method)
        //To not get new update
        Intent i_stop = new Intent(getApplicationContext(), FirebaseService.class);
        stopService(i_stop);
    }

    /**
     * finishDemanding stop the services, remove the demand, write into the history
     *  And set the flag_demand_booked=true and flag_demand=false and save them in the shared preferences
     */
    public void finishDemanding() {
        stopMyServices();
        removeDemand();
        addHistoryToFireBase();

        flag_demand_booked = true;
        flag_demand = false;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.pref_demand_booked_status), flag_demand_booked);
        editor.putBoolean(getString(R.string.pref_demand_status), flag_demand);
        editor.apply();

        btnBook.setEnabled(false);
        btnNext.setEnabled(false);
        btnPrev.setEnabled(false);

        Set<String> set = new HashSet<>();
        set.add(resultKey.get(index));
        SharedPreferences.Editor edit=sharedPref.edit();
        edit.putStringSet(getString(R.string.pref_resultKey_forDemand), set);
        edit.apply();
    }

    /**
     * write into the history that a demand book a supply
     */
    public void addHistoryToFireBase(){
        Log.d(TAG, "addHistoryToFireBase");
        String destination = sharedPref.getString(getString(R.string.pref_destination), "Forgot to tell");
        Map<String, Object> demandUser = globalHistory.setDemandUser(facebookUserId,currentUser.getDisplayName(),demand_seats, destination);
        globalHistory.updateGlobalHistory(historyKey, demandUser, demand_seats);
    }

    /**
     * broadcast OnResume locally (internal to the app)
     */
    private void broadcastOnResume(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_RESULT_DEMAND_RESUME);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * broadcast OnPause locally (internal to the app)
     */
    private void broadcastOnPause(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_RESULT_DEMAND_PAUSE);
        localBroadcastManager.sendBroadcast(intent);
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
