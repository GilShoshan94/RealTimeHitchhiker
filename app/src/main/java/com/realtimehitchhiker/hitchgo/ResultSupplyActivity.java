package com.realtimehitchhiker.hitchgo;

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
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.firebase.geofire.GeoFire;
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

public class ResultSupplyActivity extends AppCompatActivity {
    public static final String TAG = "RESULT_SUPPLY_DEBUG";
    private static final int MY_PERMISSIONS_REQUEST_CALL_PHONE = 198766;
    public static final String BROADCAST_ACTION_RESULT_SUPPLY_RESUME = "com.realtimehitchhiker.hitchgo.RESULT_SUPPLY_RESUME";
    public static final String BROADCAST_ACTION_RESULT_SUPPLY_PAUSE = "com.realtimehitchhiker.hitchgo.RESULT_SUPPLY_PAUSE";


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
            broadcastReceiverDemandFoundUpdate;

    private Button btnCall;
    private Button btnNext;
    private Button btnPrev;
    private Button btnMap;
    private TextView txtShowSupplyProfile;
    private TextView txtShowSupplyDetails;
    private ImageView imProfile;

    private String facebookUserId;
    private String phoneFound = "tel:0000000000";
    private Double myLatitude = 90.0, myLongitude = 0.0; //initialize at pole North
    private Double DemandLatitude = 90.0, DemandLongitude = 0.0;

    private SharedPreferences sharedPref;
    //flag
    private boolean flag_supply;
    private boolean flag_demand;
    private boolean gotRealLocation;

    //RESULT
    private ArrayList<String> resultKey = new ArrayList<>();
    private Integer index = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_supply);

        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        flag_supply = sharedPref.getBoolean(getString(R.string.pref_supply_status), false);
        flag_demand = sharedPref.getBoolean(getString(R.string.pref_demand_status), false);
        historyKey = sharedPref.getString(getString(R.string.pref_historyKey), "-n-u-l-l-");

        Set<String> setResultKey = sharedPref.getStringSet(getString(R.string.pref_resultKey_forSupply), null);
        if (setResultKey != null) {
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
                SharedPreferences.Editor edit = sharedPref.edit();
                edit.putStringSet(getString(R.string.pref_resultKey_forSupply), set);
                edit.apply();
            }
            myLatitude = bundle.getDouble("latitude");
            myLongitude = bundle.getDouble("longitude");
            gotRealLocation = true;
        }
        else {
            gotRealLocation = false;
        }

        btnCall = findViewById(R.id.button_supply_call);
        btnNext = findViewById(R.id.button_result_supply_next);
        btnPrev = findViewById(R.id.button_result_supply_prev);
        btnMap = findViewById(R.id.button_findOnMap);
        txtShowSupplyProfile = findViewById(R.id.textView_resultDemandProfile);
        txtShowSupplyDetails = findViewById(R.id.textView_resultDemandDetails);
        imProfile = findViewById(R.id.imageView_supply_result);

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

        for (UserInfo profile : currentUser.getProviderData()) {
            // check if the provider id matches "facebook.com"
            if (FacebookAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                facebookUserId = profile.getUid();
            }
        }

        //update the first Result UI
        updateResultUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "RESULT_DEMAND_onResume");

        //Broadcast state of main activity : OnResume
        broadcastOnResume();

        //Initialize (if need is) the broadcastReceivers for location updates and change in providers status
        if (broadcastReceiverLocationUpdate == null) {
            broadcastReceiverLocationUpdate = new BroadcastReceiver() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOCATION_UPDATE);
                    Location location = (Location) intent.getExtras().get("location");
                    if (location != null) {
                        myLatitude = location.getLatitude();
                        myLongitude = location.getLongitude();
                    }
                    gotRealLocation = true;
                }
            };
        }
        if (broadcastReceiverLocationProviderOff == null) {
            broadcastReceiverLocationProviderOff = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(LocationService.TAG, LocationService.BROADCAST_ACTION_LOCATION_OFF);
                    if (isAllActiveLocationProvidersDisabled()) {
                        //do nothing
                    }
                }
            };
        }
        if (broadcastReceiverDemandFoundUpdate == null) {
            Log.d(TAG, "broadcastReceiverDemandFoundUpdate : INITIALIZE");
            broadcastReceiverDemandFoundUpdate = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Set<String> setResultKey = sharedPref.getStringSet(getString(R.string.pref_resultKey_forSupply), null);
                    if (setResultKey != null) {
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
        localBroadcastManager.registerReceiver(broadcastReceiverLocationUpdate, new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_UPDATE));
        localBroadcastManager.registerReceiver(broadcastReceiverLocationProviderOff, new IntentFilter(LocationService.BROADCAST_ACTION_LOCATION_OFF));
        localBroadcastManager.registerReceiver(broadcastReceiverDemandFoundUpdate, new IntentFilter(FirebaseService.BROADCAST_ACTION_DEMAND_UPDATE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "RESULT_DEMAND_onPause");

        //Broadcast state of main activity : OnPause
        broadcastOnPause();

        //Unregister the broadcastReceivers
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        if (broadcastReceiverLocationUpdate != null) {
            localBroadcastManager.unregisterReceiver(broadcastReceiverLocationUpdate);
        }
        if (broadcastReceiverLocationProviderOff != null) {
            localBroadcastManager.unregisterReceiver(broadcastReceiverLocationProviderOff);
        }
        if (broadcastReceiverDemandFoundUpdate != null) {
            localBroadcastManager.unregisterReceiver(broadcastReceiverDemandFoundUpdate);
        }
    }

    private void updateResultUI() {
        //If we booked already than, we can just see our supply and we cannot rebook
        //Or if there is no key found
        if (resultKey.size() == 0) {
            btnMap.setEnabled(false);
            btnNext.setEnabled(false);
            btnPrev.setEnabled(false);
            btnCall.setEnabled(false);
            imProfile.setBackgroundResource(R.drawable.com_facebook_profile_picture_blank_square);
            txtShowSupplyProfile.setText("");
            txtShowSupplyDetails.setText("");
            return;
        } else {
            btnMap.setEnabled(true);
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

        getDemandInfoFireBase(resultKey.get(index));
    }

    /**
     * Loads the next Supply found
     *
     * @param view the view it is linked to
     */
    public void nextSupply(View view) {
        index++;
        updateResultUI();
    }

    /**
     * Loads the prev Supply found
     *
     * @param view the view it is linked to
     */
    public void prevSupply(View view) {
        index--;
        updateResultUI();
    }

    public void getDemandLocationFireBase() {
        Query checkKeyLocation = geoFireDemand.getDatabaseReference().orderByKey().equalTo(resultKey.get(index));
        checkKeyLocation.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                DemandLatitude = dataSnapshot.child("l").child("0").getValue(Double.class);
                DemandLongitude = dataSnapshot.child("l").child("1").getValue(Double.class);
                //Here we do delete geoFireDemand for the Demand
                //todo principle question, do we really need to delete in the geoFireDemand?
                //it could be good to keep this place for real time update of the location of the demand
                //and it would not hurt the function of the app since the demand itself got already clear
                //answer : It would be good idea to keep it. but for now we will still delete it and
                //we won't implement real time location update for the demand to the supply.
                geoFireDemand.removeLocation(resultKey.get(index));

                Log.d(TAG, "Demand index = "+index+" coordinate = "+DemandLatitude+" "+DemandLongitude);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
            }
        });
    }

    public void getDemandInfoFireBase(String fbUserId) {

        Query checkKeyHistoryQuery = refHistory.orderByKey().equalTo(historyKey);
        Query checkKeyUserQuery = refUsers.orderByKey().equalTo(fbUserId);

        checkKeyUserQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    setInfoDemandProfileHelper("user not found in the database", " ");
                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot supplySnapshot : dataSnapshot.getChildren()) {
                        MyUser user = supplySnapshot.getValue(MyUser.class); //normally should be only one since unique KeyId
                        setInfoDemandProfileHelper(user.name, user.phone);
                        phoneFound = user.phone;
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
                setInfoDemandProfileHelper("Error", " ");
            }
        });

        checkKeyHistoryQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    txtShowSupplyDetails.setText(" ");
                } else {

                    String demandDestination = dataSnapshot.child("demandUsers").child(resultKey.get(index))
                            .child("destination").getValue(String.class);
                    Integer requestingSeats = dataSnapshot.child("demandUsers").child(resultKey.get(index))
                            .child("requestingSeats").getValue(Integer.class);
                    setInfoDemandDetailsHelper(demandDestination, requestingSeats);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
                txtShowSupplyDetails.setText(" ");
            }
        });

        getDemandLocationFireBase();
    }

    public void setInfoDemandProfileHelper(String name, String phone) {
        String stringMessage = getString(R.string.txtProfile_name) +
                name + "\n" +
                getString(R.string.txtProfile_phone) +
                phone;
        txtShowSupplyProfile.setText(stringMessage);
    }

    public void setInfoDemandDetailsHelper(String demandDestination, Integer requestingSeats) {
        String stringBuilder = getString(R.string.txtDetails_dest) +
                demandDestination + "\n" +
                getString(R.string.txtDetails_requestingSeats) +
                requestingSeats;

        txtShowSupplyDetails.setText(stringBuilder);
    }

    /**
     * This function get called when you press the phone number button
     * it creates intent to call the number appears on the button and asks permission if needed
     *
     * @param view the view it is linked to (here the button to call)
     */
    public void callPhoneNumberSupply(View view) {
        //String phoneNumber = view.getTag().toString();
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        callIntent.setData(Uri.parse("tel:" + phoneFound));
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CALL_PHONE},
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
                    dialIntent.setData(Uri.parse("tel:" + phoneFound));
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
     * stopMyServices() terminated the services FirebaseService and LocationService
     */
    public void stopMyServices() {
        //Stop FirebaseService (and FirebaseService will stop LocationService in is onDestroy method)
        //To not get new update
        Intent i_stop = new Intent(getApplicationContext(), FirebaseService.class);
        stopService(i_stop);
    }

    /**
     * broadcast OnResume locally (internal to the app)
     */
    private void broadcastOnResume() {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_RESULT_SUPPLY_RESUME);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * broadcast OnPause locally (internal to the app)
     */
    private void broadcastOnPause() {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_RESULT_SUPPLY_PAUSE);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * returns true if all active location providers are disabled
     * returns false otherwise
     */
    private boolean isAllActiveLocationProvidersDisabled() {
        int tot = 0;
        LocationManager locMan = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if (locMan == null)
            return true;
        for (int i = 0; i < LocationService.activeProviderList.length; i++) {
            if (!locMan.isProviderEnabled(LocationService.activeProviderList[i]))
                tot++;
        }
        return (tot == LocationService.activeProviderList.length);
    }
}
