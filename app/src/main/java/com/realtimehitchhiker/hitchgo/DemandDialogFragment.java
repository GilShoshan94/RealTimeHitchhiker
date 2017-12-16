package com.realtimehitchhiker.hitchgo;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Geocoder;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
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
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.location.places.ui.SupportPlaceAutocompleteFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;

/**
 * Created by gilshoshan on 04-Dec-17.
 */

public class DemandDialogFragment extends DialogFragment {
    public static final String TAG = "DEMAND_DIALOG_FRAGMENT";
    public static final String BROADCAST_ACTION_DEMAND_DIALOG_FRAGMENT_REQUEST = "com.realtimehitchhiker.hitchgo.MAIN_DEMAND_DIALOG_FRAGMENT_REQUEST";
    private static final int PLACE_PICKER_REQUEST = 7497;

    private String facebookUserId = null;
    private Double latitude = null;
    private Double longitude = null;

    private DatabaseReference refDemand;
    private GeoFire geoFireDemand;
    private Geocoder geocoder;

    // SharedPreferences parameters
    private SharedPreferences sharedPref;
    private boolean demand_pet;
    private int demand_seats;

    // UI objects in the layer
    private SupportPlaceAutocompleteFragment autocompleteFragment;
    private Place placeDestination;
    private EditText autocompleteEditText;
    private TextView txtSeatsDemand;
    private CheckBox checkBoxPetDemand;
    private Button positiveButton;
    private Button btnPickOnMap;

    // The activity that creates an instance of this dialog fragment must implement this interface
    // It's to pass parameters
    public static DemandDialogFragment newInstance(String facebookUserId, Double latitude, Double longitude) {

        Bundle args = new Bundle();
        args.putString("facebookUserId", facebookUserId);
        args.putDouble("latitude", latitude);
        args.putDouble("longitude", longitude);

        DemandDialogFragment fragment = new DemandDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface DemandDialogListener {
        void onDemandDialogPositiveClick(DialogFragment dialog);
        void onDemandDialogNegativeClick(DialogFragment dialog);
    }

    // Use this instance of the interface to deliver action events
    DemandDialogListener mListener;

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (DemandDialogListener) context;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(context.toString()
                    + " must implement NoticeDialogListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        facebookUserId = getArguments().getString("facebookUserId","-1");
        latitude = getArguments().getDouble("latitude",90.0);
        longitude = getArguments().getDouble("longitude",0.0);

        DatabaseReference myDataBaseRef = FirebaseDatabase.getInstance().getReference();
        refDemand = myDataBaseRef.child(getString(R.string.firebase_folder_demand));
        geoFireDemand = new GeoFire(myDataBaseRef.child(getString(R.string.firebase_folder_geofire_demand)));

        //Load the sharedPreferences
        sharedPref = getActivity().getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        demand_seats = sharedPref.getInt(getString(R.string.pref_demand_seats_in_car), 1);
        demand_pet = sharedPref.getBoolean(getString(R.string.pref_demand_pet), false);

        setCancelable(false);

        geocoder = new Geocoder(getActivity());
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
                        String destination = placeDestination.getAddress().toString();
                        if(Objects.equals(destination, "") || Objects.equals(destination, " "))
                            destination = "Forgot to tell...";

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.pref_demand_seats_in_car), demand_seats);
                        editor.putBoolean(getString(R.string.pref_demand_pet), demand_pet);
                        editor.putString(getString(R.string.pref_destination),destination);
                        editor.apply();

                        MyDemand myDemand = new MyDemand(destination, demand_seats, demand_pet);
                        refDemand.child(facebookUserId).setValue(myDemand);
                        geoFireDemand.setLocation(facebookUserId, new GeoLocation(latitude, longitude));

                        // Send the demand details to FirebaseService
                        broadcastDemandDetails();

                        // Send the positive button event back to the host activity
                        mListener.onDemandDialogPositiveClick(DemandDialogFragment.this);
                    }
                })
                .setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        demand_pet = checkBoxPetDemand.isChecked();

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.pref_demand_seats_in_car), demand_seats);
                        editor.putBoolean(getString(R.string.pref_demand_pet), demand_pet);
                        editor.apply();

                        // Send the positive button event back to the host activity
                        mListener.onDemandDialogNegativeClick(DemandDialogFragment.this);
                    }
                })
                .setCancelable(false);

        //UI initialization of links
        txtSeatsDemand = dialogView.findViewById(R.id.textView_seats_demand);
        Button btnPlusSeats = dialogView.findViewById(R.id.button_seats_demand_plus);
        Button btnMinusSeats = dialogView.findViewById(R.id.button_seats_demand_minus);

        checkBoxPetDemand = dialogView.findViewById(R.id.checkBox_pet_demand);

        btnPickOnMap = dialogView.findViewById(R.id.button_placePicker_demand);

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

    @Override
    public void onStart() {
        super.onStart();

        //get the dialog instance to be able to manipulate (disable/enable) the positive button
        AlertDialog myDialog = (AlertDialog) getDialog();
        Log.d(TAG, "AlertDialog myDialog = (AlertDialog) getDialog();");
        if (myDialog != null) {
            Log.d(TAG, "myDialog != null" + myDialog.toString());
            positiveButton = myDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setEnabled(false);
        }

        //set the Google widget

        //autocompleteFragment = new SupportPlaceAutocompleteFragment();
        //FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        //transaction.add(R.id.place_autocomplete_fragment_demand_content, autocompleteFragment ).commit();

        autocompleteFragment = (SupportPlaceAutocompleteFragment)
                getActivity().getSupportFragmentManager().findFragmentById(R.id.place_autocomplete_fragment_demand);
        Log.i(TAG, "Place: " + autocompleteFragment.toString());

        autocompleteEditText = (EditText)autocompleteFragment.getView().findViewById(R.id.place_autocomplete_search_input);
        autocompleteEditText.setTextColor(getResources().getColor(R.color.colorWhite));//todo check
        AutocompleteFilter autocompleteFilter = new AutocompleteFilter.Builder()
                .setTypeFilter(AutocompleteFilter.TYPE_FILTER_NONE)
                //.setCountry("IL")
                .build();
        autocompleteFragment.setHint(getString(R.string.txt_hint_destination));
        autocompleteFragment.setFilter(autocompleteFilter);
        autocompleteFragment.setBoundsBias(new LatLngBounds(
                new LatLng(latitude-0.9, longitude-0.9),
                new LatLng(latitude+0.9, longitude+0.9)));
        autocompleteFragment.setOnPlaceSelectedListener((new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                Log.i(TAG, "Place1: " + place.getName());
                Log.i(TAG, "Place4 GOOD: " + place.getAddress());
                Log.i(TAG, "Place5: " + place.getViewport().toString());
                Log.i(TAG, "Place6: " + place.getLatLng());
                placeDestination = place;
                positiveButton.setEnabled(true);
            }

            @Override
            public void onError(Status status) {
                Log.i(TAG, "An error occurred: " + status);
            }
        }));

        autocompleteFragment.getView().findViewById(R.id.place_autocomplete_clear_button)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        autocompleteFragment.setText("");
                        view.setVisibility(View.GONE);
                        positiveButton.setEnabled(false);
                    }
                });

        //set the Place Picker by Google
        btnPickOnMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
                try {
                    Intent intent = builder.build(getActivity());
                    startActivityForResult(intent, PLACE_PICKER_REQUEST);
                } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() != null) {
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.remove(autocompleteFragment).commit();
        }
    }

    /**
     * broadcast Demand details for FirebaseService locally (internal to the app)
     */
    private void broadcastDemandDetails(){
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
        Intent intent = new Intent(BROADCAST_ACTION_DEMAND_DIALOG_FRAGMENT_REQUEST);

        Bundle bundle = new Bundle();
        bundle.putString("demand_destination", placeDestination.getAddress().toString());
        bundle.putBoolean("demand_pet", demand_pet);
        bundle.putInt("demand_seats", demand_seats);

        intent.putExtras(bundle);
        localBroadcastManager.sendBroadcast(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PLACE_PICKER_REQUEST){
            if (resultCode == getActivity().RESULT_OK){
                placeDestination = PlacePicker.getPlace(getActivity(),data);
                Log.i(TAG, "Place1: " + placeDestination.getName());
                Log.i(TAG, "Place4 GOOD: " + placeDestination.getAddress());
                Log.i(TAG, "Place5: " + placeDestination.getViewport().toString());
                Log.i(TAG, "Place6: " + placeDestination.getLatLng());
                autocompleteFragment.setText(placeDestination.getAddress());
                positiveButton.setEnabled(true);
            }
        }
    }
}
