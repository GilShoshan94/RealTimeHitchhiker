package com.realtimehitchhiker.hitchgo;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.location.places.ui.SupportPlaceAutocompleteFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by gilshoshan on 04-Dec-17.
 */

public class SupplyDialogFragment extends DialogFragment implements CounterHandler.CounterListener{
    public static final String TAG = "SUPPLY_DIALOG_FRAGMENT";
    private static final int PLACE_PICKER_REQUEST = 7496;

    private String facebookUserId = null;
    private Double latitude = null;
    private Double longitude = null;

    private DatabaseReference refSupply;
    private GeoFire geoFireSupply;
    private DatabaseReference refHistory;
    private Geocoder geocoder;

    // SharedPreferences parameters
    private SharedPreferences sharedPref;
    private int fuel_price;
    private boolean allow_pet_supply;
    private int seats_in_car;

    // UI objects in the layer
    private SupportPlaceAutocompleteFragment autocompleteFragment;
    private Place placeDestination;
    private EditText autocompleteEditText;
    private TextView txtFuelPrice;
    private TextView txtSeatsSupply;
    private CheckBox checkBoxPetSupply;
    private Button positiveButton;
    private Button btnPickOnMap;
    //todo String currency...
    String currency = "NIS";

    // The activity that creates an instance of this dialog fragment must implement this interface
    // It's to pass parameters
    public static SupplyDialogFragment newInstance(String facebookUserId, Double latitude, Double longitude) {
        
        Bundle args = new Bundle();
        args.putString("facebookUserId", facebookUserId);
        args.putDouble("latitude", latitude);
        args.putDouble("longitude", longitude);
        
        SupplyDialogFragment fragment = new SupplyDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface SupplyDialogListener {
        void onSupplyDialogPositiveClick(DialogFragment dialog);
        void onSupplyDialogNegativeClick(DialogFragment dialog);
    }

    // Use this instance of the interface to deliver action events
    SupplyDialogListener mListener;

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (SupplyDialogListener) context;
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
        refSupply = myDataBaseRef.child(getString(R.string.firebase_folder_supply));
        geoFireSupply = new GeoFire(myDataBaseRef.child(getString(R.string.firebase_folder_geofire_supply)));
        refHistory = myDataBaseRef.child("history/");
        //Load the sharedPreferences
        sharedPref = getActivity().getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        seats_in_car = sharedPref.getInt(getString(R.string.pref_supply_seats_in_car), 1);
        fuel_price = sharedPref.getInt(getString(R.string.pref_supply_fuel_price), 0);
        allow_pet_supply = sharedPref.getBoolean(getString(R.string.pref_supply_pet), false);

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
        View dialogView = inflater.inflate(R.layout.dialog_supply, null);

        // Set the layout for the dialog
        builder.setView(dialogView)
                .setTitle(R.string.alert_dialog_supply_title)
                .setMessage(R.string.alert_dialog_supply_message)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        allow_pet_supply = checkBoxPetSupply.isChecked();
                        String destination = placeDestination.getAddress().toString();
                        if(Objects.equals(destination, "") || Objects.equals(destination, " "))
                            destination = "Forgot to tell...";

                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt(getString(R.string.pref_supply_seats_in_car), seats_in_car);
                        editor.putInt(getString(R.string.pref_supply_fuel_price), fuel_price);
                        editor.putBoolean(getString(R.string.pref_supply_pet), allow_pet_supply);
                        editor.apply();

                        String historyKey = refSupply.push().getKey();

                        MySupply mySupply = new MySupply(destination, seats_in_car, fuel_price, currency, allow_pet_supply, historyKey);
                        refSupply.child(facebookUserId).setValue(mySupply);
                        geoFireSupply.setLocation(facebookUserId, new GeoLocation(latitude, longitude));

                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        assert currentUser != null;
                        String name = currentUser.getDisplayName();
                        MyGlobalHistory globalHistory = new MyGlobalHistory(refHistory);
                        GeoLocation fromLocation = new GeoLocation(latitude,longitude);
                        GeoLocation toLocation = new GeoLocation(placeDestination.getLatLng().latitude,placeDestination.getLatLng().longitude);
                        Map<String, Object> supplyUser = globalHistory.setSupplyUser(facebookUserId, name);
                        Context context = getActivity();
                        globalHistory.setGlobalHistory(context, historyKey, fromLocation, toLocation, supplyUser, seats_in_car);

                        // Send the positive button event back to the host activity
                        mListener.onSupplyDialogPositiveClick(SupplyDialogFragment.this);
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

                        // Send the negative button event back to the host activity
                        mListener.onSupplyDialogNegativeClick(SupplyDialogFragment.this);
                    }
                })
                .setCancelable(false);

        //UI initialization of links
        txtFuelPrice = dialogView.findViewById(R.id.textView_fuel_price);
        Button btnPlusPrice = dialogView.findViewById(R.id.button_fuel_plus);
        Button btnMinusPrice = dialogView.findViewById(R.id.button_fuel_minus);

        txtSeatsSupply = dialogView.findViewById(R.id.textView_seats_supply);
        Button btnPlusSeats = dialogView.findViewById(R.id.button_seats_supply_plus);
        Button btnMinusSeats = dialogView.findViewById(R.id.button_seats_supply_minus);

        checkBoxPetSupply = dialogView.findViewById(R.id.checkBox_pet_supply);

        btnPickOnMap = dialogView.findViewById(R.id.button_placePicker_supply);

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
                .counterDelay(20) // speed of counter
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
        //transaction.add(R.id.place_autocomplete_fragment_supply_content, autocompleteFragment ).commit();

        autocompleteFragment = (SupportPlaceAutocompleteFragment)
                getActivity().getSupportFragmentManager().findFragmentById(R.id.place_autocomplete_fragment_supply);
        Log.i(TAG, "Place: " + autocompleteFragment.toString());

        autocompleteEditText = (EditText)autocompleteFragment.getView().findViewById(R.id.place_autocomplete_search_input);
        autocompleteEditText.setTextColor(autocompleteEditText.getHintTextColors());//todo check
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

    private String getReverseGeoCoding() {
        try {
            List<Address> addressList = geocoder.getFromLocation(
                    latitude, longitude, 1);
            if (addressList.size() > 0) {
                Address address = addressList.get(0);
                int lines = address.getMaxAddressLineIndex();
                if (lines > 0) {
                    StringBuilder addressLine = new StringBuilder(address.getAddressLine(0));
                    for (int i = 1 ; i < lines; i++){
                        addressLine.append(", ").append(address.getAddressLine(i));
                    }
                    return addressLine.toString();
                }

                String locality = address.getLocality();
                String adminArea = address.getAdminArea();
                String countryCode = address.getCountryCode();

                if(locality == null)
                    locality = "";
                else
                    locality = locality + ", ";
                if(adminArea == null)
                    adminArea = "";
                else
                    adminArea = adminArea + ", ";
                if(countryCode == null)
                    countryCode = "";
                else
                    countryCode = countryCode + ", ";

                if (Objects.equals(locality, "") && Objects.equals(adminArea, "") && Objects.equals(countryCode, "")) {
                    return "unknown address";
                }
                else
                    return locality + adminArea + countryCode;
            }
        } catch (IOException e) {
            // Can safely ignore
        }
        return "unknown address";
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
