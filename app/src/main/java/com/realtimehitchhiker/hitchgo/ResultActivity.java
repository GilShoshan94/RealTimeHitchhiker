package com.realtimehitchhiker.hitchgo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ResultActivity extends AppCompatActivity {
    public static final String TAG = "RESULT_DEBUG";

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

    private Button btnResultOk;
    private TextView txtShowDriver;
    private ImageView imProfile;

    private String facebookUserIdFound, facebookUserId;
    private Double latitude, longitude;
    private String  requestingSeats="0", remainingSeats="0";

    private SharedPreferences sharedPref;
    //flag
    private boolean flag_supply;
    private boolean flag_demand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        flag_supply = sharedPref.getBoolean(getString(R.string.pref_supply_status), false);
        flag_demand = sharedPref.getBoolean(getString(R.string.pref_demand_status), false);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        facebookUserIdFound = (String) intent.getExtras().get("facebookUserIdFound");
        latitude = (Double) intent.getExtras().get("geoLocationLatitude");
        longitude = (Double) intent.getExtras().get("geoLocationLongitude");

        btnResultOk = findViewById(R.id.button_result);
        txtShowDriver = findViewById(R.id.textView_result);
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

        //Set the picture
        String photoUrl = "https://graph.facebook.com/" + facebookUserIdFound + "/picture?type=large";

        new DownloadImageTask(new DownloadImageTask.AsyncResponse() {
            @Override
            public void processFinish(Bitmap output) {
                imProfile.setImageBitmap(output);
            }
        }).execute(photoUrl);

        getNameFireBase(facebookUserIdFound);

        updateFireBasDataBase();

        //Stop FirebaseService (and FirebaseService will stop LocationService in is onDestroy method)
        Intent i_stop = new Intent(getApplicationContext(), FirebaseService.class);
        stopService(i_stop);
    }

    public void updateFireBasDataBase(){
        facebookUserId = "";
        for(UserInfo profile : currentUser.getProviderData()) {
            // check if the provider id matches "facebook.com"
            if(FacebookAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                facebookUserId = profile.getUid();
            }
        }
        Query checkKeyQuery = refDemand.orderByKey().equalTo(facebookUserId);
        checkKeyQuery.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    requestingSeats = "-1";
                    Log.w(TAG, "DATABASE Failed to read value. in removeDemand");
                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        MyDemand user = userSnapshot.getValue(MyDemand.class); //normally should be only one since unique KeyId
                        requestingSeats = (user.requestingSeats);
                    }
                }
                refDemand.child(facebookUserId).removeValue();
                updateFireBasDataBaseHelper();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in removeDemand", databaseError.toException());
                refDemand.child(facebookUserId).removeValue();
                updateFireBasDataBaseHelper();
            }
        });
        geoFireDemand.removeLocation(facebookUserId);
        flag_demand = false;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.pref_demand_status), flag_demand);
        editor.apply();
    }

    public void updateFireBasDataBaseHelper(){
        Query query = refSupply.orderByKey().equalTo(facebookUserIdFound);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    remainingSeats = "-1";
                    Log.w(TAG, "DATABASE Failed to read value. in removeSupply");
                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        Log.d(TAG, "updateFireBasDataBaseHelper Supply : " + userSnapshot.toString());
                        //TODO WTF it doesn't work normaly ???
                        //MySupply user = userSnapshot.getValue(MySupply.class); //normally should be only one since unique KeyId
                        //remainingSeats = (user.remainingSeats);
                        remainingSeats = userSnapshot.child("remainingSeats").getValue().toString();
                        Log.d(TAG, "updateFireBasDataBaseHelper Supply TRY : "+remainingSeats);
                    }
                }
                refSupply.child(facebookUserIdFound).removeValue();
                addHistoryToFireBase();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in removeSupply", databaseError.toException());
                refSupply.child(facebookUserIdFound).removeValue();
                addHistoryToFireBase();
            }
        });
        geoFireSupply.removeLocation(facebookUserIdFound);
        flag_supply = false;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.pref_supply_status), flag_supply);
        editor.apply();
    }

    public void addHistoryToFireBase(){
        Log.d(TAG, "addHistoryToFireBase");
        Log.d(TAG, "Seats : " + remainingSeats + " and " + requestingSeats);
        String supplyUserId = facebookUserIdFound;
        Map<String, String> demandUserId = new HashMap<>();
        String demandName = currentUser.getDisplayName();
        demandUserId.put(demandName, requestingSeats);

        globalHistory.setGlobalHistory(refHistory.push().getKey(),
                new GeoLocation(latitude, longitude), new GeoLocation(latitude, longitude),
                supplyUserId, demandUserId, remainingSeats, requestingSeats);
    }

    public void getNameFireBase(String fbUserId){
        Query checkKeyQuery = refUsers.orderByKey().equalTo(fbUserId);
        checkKeyQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    getNameFireBaseHelper("user not found in the database", " ", " ");
                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        MyUser user = userSnapshot.getValue(MyUser.class); //normally should be only one since unique KeyId
                        getNameFireBaseHelper(user.name, user.email, user.phone);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
                getNameFireBaseHelper("Error", " ", " ");
            }
        });
    }

    public void getNameFireBaseHelper(String name, String email, String phone){
        txtShowDriver.append(" "+name+"\nEmail : "+email+"\nPhone : "+phone);
    }

    public void removeSupply(){
        Query query = refSupply.orderByKey().equalTo(facebookUserIdFound);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "removeSupply dataSnapshot " + dataSnapshot.toString());
                if (!dataSnapshot.exists()) {
                    remainingSeats = "-1";
                    Log.w(TAG, "DATABASE Failed to read value. in removeSupply");
                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        MySupply user = userSnapshot.getValue(MySupply.class); //normally should be only one since unique KeyId
                        //remainingSeats = (user.remainingSeats);
                        remainingSeats = "999"; //todo
                    }
                }
                refSupply.child(facebookUserIdFound).removeValue();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in removeSupply", databaseError.toException());
                refSupply.child(facebookUserIdFound).removeValue();
            }
        });
        geoFireSupply.removeLocation(facebookUserIdFound);
        flag_supply = false;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.pref_supply_status), flag_supply);
        editor.apply();
    }

    public void removeDemand(){
        facebookUserId = "";
        for(UserInfo profile : currentUser.getProviderData()) {
            // check if the provider id matches "facebook.com"
            if(FacebookAuthProvider.PROVIDER_ID.equals(profile.getProviderId())) {
                facebookUserId = profile.getUid();
            }
        }
        Log.d(TAG, "removeDemand : facebookUserId : " + facebookUserId);
        Query checkKeyQuery = refDemand.orderByKey().equalTo(facebookUserId);
        checkKeyQuery.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    requestingSeats = "-1";
                    Log.w(TAG, "DATABASE Failed to read value. in removeDemand");
                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        MyDemand user = userSnapshot.getValue(MyDemand.class); //normally should be only one since unique KeyId
                        requestingSeats = (user.requestingSeats);
                    }
                }
                refDemand.child(facebookUserId).removeValue();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in removeDemand", databaseError.toException());
                refDemand.child(facebookUserId).removeValue();
            }
        });
        geoFireDemand.removeLocation(facebookUserId);
        flag_demand = false;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.pref_demand_status), flag_demand);
        editor.apply();
    }

    public void quit(View view){
        finish();
    }

}
