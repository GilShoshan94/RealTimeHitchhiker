package com.realtimehitchhiker.hitchgo;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Created by gilshoshan on 04-Dec-17.
 */

public class SupplyDialogFragment extends DialogFragment implements CounterHandler.CounterListener{
    public static final String TAG = "SUPPLY_DIALOG_FRAGMENT";

    private String facebookUserId = null;
    private Double latitude = null;
    private Double longitude = null;

    private DatabaseReference refSupply;
    private GeoFire geoFireSupply;

    // SharedPreferences parameters
    private SharedPreferences sharedPref;
    private int fuel_price;
    private boolean allow_pet_supply;
    private int seats_in_car;

    // UI objects in the layer
    private EditText txtDestination;
    private TextView txtFuelPrice;
    private TextView txtSeatsSupply;
    private CheckBox checkBoxPetSupply;
    //todo String currency...
    String currency = "NIS";

    public static SupplyDialogFragment newInstance(String facebookUserId, Double latitude, Double longitude) {
        
        Bundle args = new Bundle();
        args.putString("facebookUserId", facebookUserId);
        args.putDouble("latitude", latitude);
        args.putDouble("longitude", longitude);
        
        SupplyDialogFragment fragment = new SupplyDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        facebookUserId = getArguments().getString("facebookUserId","-1");
        latitude = getArguments().getDouble("latitude",90.0);
        longitude = getArguments().getDouble("latitude",0.0);

        DatabaseReference myDataBaseRef = FirebaseDatabase.getInstance().getReference();
        refSupply = myDataBaseRef.child(getString(R.string.firebase_folder_supply));
        geoFireSupply = new GeoFire(myDataBaseRef.child(getString(R.string.firebase_folder_geofire_supply)));
        //Load the sharedPreferences
        sharedPref = getActivity().getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        seats_in_car = sharedPref.getInt(getString(R.string.pref_supply_seats_in_car), 1);
        fuel_price = sharedPref.getInt(getString(R.string.pref_supply_fuel_price), 0);
        allow_pet_supply = sharedPref.getBoolean(getString(R.string.pref_supply_pet), false);

        setCancelable(false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.d(TAG, "onCreateDialog");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and the layout
        // Pass null as the parent view because its going in the dialog layout
        View dialogView = inflater.inflate(R.layout.dialog_supply, null);

        // Set the layout for the dialog
        builder.setView(dialogView)
                .setTitle(R.string.alert_dialog_supply_title)
                .setMessage(R.string.alert_dialog_supply_message)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        allow_pet_supply = checkBoxPetSupply.isChecked();
                        String destination = txtDestination.getText().toString();

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.pref_supply_seats_in_car), seats_in_car);
                        editor.putInt(getString(R.string.pref_supply_fuel_price), fuel_price);
                        editor.putBoolean(getString(R.string.pref_supply_pet), allow_pet_supply);
                        editor.apply();

                        MySupply mySupply = new MySupply(destination, seats_in_car, fuel_price, currency, allow_pet_supply);
                        refSupply.child(facebookUserId).setValue(mySupply);
                        geoFireSupply.setLocation(facebookUserId, new GeoLocation(latitude, longitude));

                    }
                })
                .setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //allow_pet_supply = checkBoxPetSupply.isChecked();

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.pref_supply_seats_in_car), seats_in_car);
                        editor.putInt(getString(R.string.pref_supply_fuel_price), fuel_price);
                        editor.putBoolean(getString(R.string.pref_supply_pet), allow_pet_supply);
                        editor.apply();
                    }
                })
                .setCancelable(false);

        //UI initialization of links
        txtDestination = dialogView.findViewById(R.id.editText_destination);

        txtFuelPrice = dialogView.findViewById(R.id.textView_fuel_price);
        Button btnPlusPrice = dialogView.findViewById(R.id.button_fuel_plus);
        Button btnMinusPrice = dialogView.findViewById(R.id.button_fuel_minus);

        txtSeatsSupply = dialogView.findViewById(R.id.textView_seats_supply);
        Button btnPlusSeats = dialogView.findViewById(R.id.button_seats_supply_plus);
        Button btnMinusSeats = dialogView.findViewById(R.id.button_seats_supply_minus);

        checkBoxPetSupply = dialogView.findViewById(R.id.checkBox_pet_supply);

        //UI set from sharedPreferences
        final int max_seats = getResources().getInteger(R.integer.pref_supply_max_seats_in_car);
        final int min_seats = 1;
        final int max_fuel_price = getResources().getInteger(R.integer.pref_max_fuel_price);
        final int min_fuel_price = 0;

        txtFuelPrice.setText(R.string.txt_fuel_price); txtFuelPrice.append(" : "+fuel_price+" "+ currency);
        txtSeatsSupply.setText(R.string.txt_seat_supply); txtSeatsSupply.append(" : "+seats_in_car);
        checkBoxPetSupply.setChecked(allow_pet_supply);

        //Set the listeners
        new CounterHandler.Builder()
                .incrementalView(btnPlusPrice)
                .decrementalView(btnMinusPrice)
                .minRange((long)min_fuel_price) // cant go any less than min_fuel_price
                .maxRange((long)max_fuel_price) // cant go any further than max_fuel_price
                .startNumber((long)fuel_price)
                .isCycle(false) // 49,50,-50,-49 and so on
                .counterDelay(400) // speed of counter
                .counterStep(1)  // steps e.g. 0,2,4,6...
                .listener(this) // to listen counter results and show them in app
                .build();

        btnPlusSeats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(seats_in_car < max_seats) {
                    seats_in_car++;
                    txtSeatsSupply.setText(R.string.txt_seat_supply);
                    txtSeatsSupply.append(" : " + seats_in_car);
                }
            }
        });
        btnMinusSeats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(seats_in_car > min_seats) {
                    seats_in_car--;
                    txtSeatsSupply.setText(R.string.txt_seat_supply);
                    txtSeatsSupply.append(" : " + seats_in_car);
                }
            }
        });

        return builder.create();
    }

    @Override
    public void onIncrement(View view, long number) {
        fuel_price = (int)number;
        txtFuelPrice.setText(R.string.txt_fuel_price);
        txtFuelPrice.append(" : " + fuel_price + " " + currency);
    }

    @Override
    public void onDecrement(View view, long number) {
        fuel_price = (int)number;
        txtFuelPrice.setText(R.string.txt_fuel_price);
        txtFuelPrice.append(" : " + fuel_price + " " + currency);
    }
}
