package com.realtimehitchhiker.hitchgo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;

public class ResultActivity extends AppCompatActivity {
    public static final String TAG = "RESULT_DEBUG";
    private static final int MY_PERMISSIONS_REQUEST_CALL_PHONE =1;

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

    private Button btnCall;
    private Button btnNext;
    private Button btnPrev;
    private TextView txtShowDriver;
    private TextView txtShowDriver2;
    private ImageView imProfile;

    private String facebookUserId;
    private String phoneFound = "tel:0000000000";
    private String  requestingSeats="0", remainingSeats="0";

    private SharedPreferences sharedPref;
    //flag
    private boolean flag_supply;
    private boolean flag_demand;

    //RESULT
    private ArrayList<String> resultKey = new ArrayList<>();
    private ArrayList<ResultLocation> resultLocation = new ArrayList<>();
    private Integer index = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        //Stop FirebaseService (and FirebaseService will stop LocationService in is onDestroy method)
        //To not get new update
        Intent i_stop = new Intent(getApplicationContext(), FirebaseService.class);
        stopService(i_stop);

        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        flag_supply = sharedPref.getBoolean(getString(R.string.pref_supply_status), false);
        flag_demand = sharedPref.getBoolean(getString(R.string.pref_demand_status), false);

        // Get the Intent that started this activity and extract the bundle
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        //bundle.setClassLoader(ResultLocation.class.getClassLoader());
        assert bundle != null;
        resultKey.addAll(bundle.getStringArrayList("facebookUserIdFound"));
        //resultLocation.addAll(bundle.<ResultLocation>getParcelableArrayList("resultLocationFound"));
        //todo
        //resultKey.add(bundle.getString("facebookUserIdFound"));
        resultLocation.add((ResultLocation) bundle.getParcelable("resultLocationFound"));
        resultLocation.add((ResultLocation) bundle.getParcelable("resultLocationFound"));
        //resultKey.add("10155826110714804");
        //resultLocation.add(new ResultLocation(35.5, 32.2));

        btnCall = findViewById(R.id.button_call);
        btnNext = findViewById(R.id.button_result_next);
        btnPrev = findViewById(R.id.button_result_prev);
        txtShowDriver = findViewById(R.id.textView_result);
        txtShowDriver2 = findViewById(R.id.textView_result2);
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

        //update the first Result
        updateResult();
    }

    private void updateResult(){
        if(index==(resultKey.size()-1)) //at the end of the list, there is no next after
            btnNext.setEnabled(false);
        else
            btnNext.setEnabled(true);
        if(index==0) //at the beginning of the list, there is no prev before
            btnPrev.setEnabled(false);
        else
            btnPrev.setEnabled(true);

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

        getSupplyDetailsFireBase(resultKey.get(index));
    }

    /**
     * Loads the next Supply found
     *
     * @param view the view it is linked to
     */
    public void nextSupply(View view){
        index++;
        updateResult();
    }

    /**
     * Loads the prev Supply found
     *
     * @param view the view it is linked to
     */
    public void prevSupply(View view){
        index--;
        updateResult();
    }

    /**
     * This function get called when you press the phone number button
     * it creates intent to call the number appears on the button and asks permission if needed
     * @param view the view it is linked to (here the button to call)
     */
    public void callPhoneNumberSupply(View view) {
        //String phoneNumber = view.getTag().toString();
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);//todo explain
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
                    dialIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);//todo explain
                    startActivity(dialIntent);
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
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
                        //todo requestingSeats = (user.requestingSeats);
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
        Query query = refSupply.orderByKey().equalTo(resultKey.get(index));
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
                refSupply.child(resultKey.get(index)).removeValue();
                addHistoryToFireBase();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in removeSupply", databaseError.toException());
                refSupply.child(resultKey.get(index)).removeValue();
                addHistoryToFireBase();
            }
        });
        geoFireSupply.removeLocation(resultKey.get(index));
        flag_supply = false;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.pref_supply_status), flag_supply);
        editor.apply();
    }

    public void addHistoryToFireBase(){
        Log.d(TAG, "addHistoryToFireBase");
        Log.d(TAG, "Seats : " + remainingSeats + " and " + requestingSeats);
        String supplyUserId = resultKey.get(index);
        Map<String, String> demandUserId = new HashMap<>();
        String demandName = currentUser.getDisplayName();
        demandUserId.put(demandName, requestingSeats);

        globalHistory.setGlobalHistory(refHistory.push().getKey(),//todo...
                new GeoLocation(resultLocation.get(index).latitude, resultLocation.get(index).longitude),
                new GeoLocation(resultLocation.get(index).latitude, resultLocation.get(index).longitude),
                supplyUserId, demandUserId, remainingSeats, requestingSeats);
    }

    public void getSupplyDetailsFireBase(String fbUserId){

        Query checkKeySupplyQuery = refSupply.orderByKey().equalTo(fbUserId);
        Query checkKeyUserQuery = refUsers.orderByKey().equalTo(fbUserId);

        checkKeyUserQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    getNameFireBaseHelper("user not found in the database", " ", " ");
                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        MyUser user = userSnapshot.getValue(MyUser.class); //normally should be only one since unique KeyId
                        getNameFireBaseHelper(user.name, user.email, user.phone);//todo change name....
                        phoneFound = user.phone;
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
                getNameFireBaseHelper("Error", " ", " ");
            }
        });

        checkKeySupplyQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {

                } else {
                    //using for loop because FireBase returns JSON object that are always list.
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        MySupply supply = userSnapshot.getValue(MySupply.class); //normally should be only one since unique KeyId
                        getNameFireBaseHelper2(supply);//todo change name....
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in getNameFireBase", databaseError.toException());
            }
        });
    }

    public void getNameFireBaseHelper(String name, String email, String phone){
        txtShowDriver.append(" "+name+"\nEmail : "+email+"\nPhone : "+phone);
    }

    public void getNameFireBaseHelper2(MySupply supply){
        txtShowDriver2.setText("Destination : " + supply.destination +
                "\nFuel price : " + supply.fuelPrice + " " +supply.currency +
                "\nPet allowed : " + supply.petAllowed + "\nRemaining seats : " + supply.remainingSeats);
    }

    public void removeSupply(){
        Query query = refSupply.orderByKey().equalTo(resultKey.get(index));
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
                refSupply.child(resultKey.get(index)).removeValue();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "DATABASE Failed to read value. in removeSupply", databaseError.toException());
                refSupply.child(resultKey.get(index)).removeValue();
            }
        });
        geoFireSupply.removeLocation(resultKey.get(index));
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
                        //todo requestingSeats = (user.requestingSeats);
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


}
