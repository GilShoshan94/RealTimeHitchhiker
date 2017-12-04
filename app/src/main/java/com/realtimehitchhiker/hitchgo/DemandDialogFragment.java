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

public class DemandDialogFragment extends DialogFragment {
    public static final String TAG = "DEMAND_DIALOG_FRAGMENT";

    private String facebookUserId = null;
    private Double latitude = null;
    private Double longitude = null;

    private DatabaseReference refDemand;
    private GeoFire geoFireDemand;

    // SharedPreferences parameters
    private SharedPreferences sharedPref;
    private boolean demand_pet;
    private int demand_seats;

    // UI objects in the layer
    private EditText txtDestination;
    private TextView txtSeatsDemand;
    private CheckBox checkBoxPetDemand;

    public static DemandDialogFragment newInstance(String facebookUserId, Double latitude, Double longitude) {

        Bundle args = new Bundle();
        args.putString("facebookUserId", facebookUserId);
        args.putDouble("latitude", latitude);
        args.putDouble("longitude", longitude);

        DemandDialogFragment fragment = new DemandDialogFragment();
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
        refDemand = myDataBaseRef.child(getString(R.string.firebase_folder_demand));
        geoFireDemand = new GeoFire(myDataBaseRef.child(getString(R.string.firebase_folder_geofire_demand)));
        //Load the sharedPreferences
        sharedPref = getActivity().getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        demand_seats = sharedPref.getInt(getString(R.string.pref_demand_seats_in_car), 1);
        demand_pet = sharedPref.getBoolean(getString(R.string.pref_demand_pet), false);

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
        View dialogView = inflater.inflate(R.layout.dialog_demand, null);

        // Set the layout for the dialog
        builder.setView(dialogView)
                .setTitle(R.string.alert_dialog_demand_title)
                .setMessage(R.string.alert_dialog_demand_message)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        demand_pet = checkBoxPetDemand.isChecked();
                        String destination = txtDestination.getText().toString();

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.pref_demand_seats_in_car), demand_seats);
                        editor.putBoolean(getString(R.string.pref_demand_pet), demand_pet);
                        editor.apply();

                        MyDemand myDemand = new MyDemand(destination, demand_seats, demand_pet);
                        refDemand.child(facebookUserId).setValue(myDemand);
                        geoFireDemand.setLocation(facebookUserId, new GeoLocation(latitude, longitude));

                    }
                })
                .setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //allow_pet_supply = checkBoxPetSupply.isChecked();

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.pref_demand_seats_in_car), demand_seats);
                        editor.putBoolean(getString(R.string.pref_demand_pet), demand_pet);
                        editor.apply();
                    }
                })
                .setCancelable(false);

        //UI initialization of links
        txtDestination = dialogView.findViewById(R.id.editText_destination);

        txtSeatsDemand = dialogView.findViewById(R.id.textView_seats_demand);
        Button btnPlusSeats = dialogView.findViewById(R.id.button_seats_demand_plus);
        Button btnMinusSeats = dialogView.findViewById(R.id.button_seats_demand_minus);

        checkBoxPetDemand = dialogView.findViewById(R.id.checkBox_pet_demand);

        //UI set from sharedPreferences
        final int max_seats = getResources().getInteger(R.integer.pref_supply_max_seats_in_car);
        final int min_seats = 1;

        txtSeatsDemand.setText(R.string.txt_seat_demand); txtSeatsDemand.append(" : " + demand_seats);
        checkBoxPetDemand.setChecked(demand_pet);

        btnPlusSeats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(demand_seats < max_seats) {
                    demand_seats++;
                    txtSeatsDemand.setText(R.string.txt_seat_demand);
                    txtSeatsDemand.append(" : " + demand_seats);
                }
            }
        });
        btnMinusSeats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(demand_seats > min_seats) {
                    demand_seats--;
                    txtSeatsDemand.setText(R.string.txt_seat_demand);
                    txtSeatsDemand.append(" : " + demand_seats);
                }
            }
        });

        return builder.create();
    }
}
