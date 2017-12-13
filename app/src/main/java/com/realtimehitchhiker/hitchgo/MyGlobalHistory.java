package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 20-Nov-17.
 */


import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.LocationCallback;
import com.firebase.geofire.core.GeoHash;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.GenericTypeIndicator;

import java.lang.Throwable;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A MyGlobalHistory instance is used to store history with geo location data in Firebase.
 */
public class MyGlobalHistory {

    private Map<String, Object> supplyUser;
    private Map<String, Object> demandUser;
    private Long timeStamp;
    private String date;
    private Integer offeredSeats;
    private Integer usedSeats;
    private String fromLocation;
    private String toLocation;
    private final DatabaseReference databaseReference;

    /**
     * A listener that can be used to be notified about a successful write or an error on writing.
     */
    public static interface CompletionListener {
        /**
         * Called once a location was successfully saved on the server or an error occurred. On success, the parameter
         * error will be null; in case of an error, the error will be passed to this method.
         *
         * @param key   The key whose location was saved
         * @param error The error or null if no error occurred
         */
        public void onComplete(String key, DatabaseError error);
    }

    /**
     * Creates a new MyGlobalHistory instance at the given Firebase reference.
     *
     * @param databaseReference The Firebase reference this MyGlobalHistory instance uses
     */
    public MyGlobalHistory(DatabaseReference databaseReference) {
        this.databaseReference = databaseReference;
    }

    public MyGlobalHistory(String fromLocation,
                           String toLocation, Map<String, Object> supplyUserId,
                           Map<String, Object> demandUserId,
                           Integer offeredSeats, Integer usedSeats, DatabaseReference databaseReference) {
        this.supplyUser = supplyUserId;
        this.demandUser = demandUserId;
        this.timeStamp = System.currentTimeMillis();
        SimpleDateFormat sfd = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss.SSS z", Locale.US);
        this.date = sfd.format(new Date(timeStamp));
        this.offeredSeats = offeredSeats;
        this.usedSeats = usedSeats;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.databaseReference = databaseReference;
    }

    /**
     * @return The Firebase reference this MyGlobalHistory instance uses
     */
    public DatabaseReference getDatabaseReference() {
        return this.databaseReference;
    }

    DatabaseReference getDatabaseRefForKey(String key) {
        return this.databaseReference.child(key);
    }


    public Map<String, Object> setDemandUser(String id, String name, int requestingSeats){
        Map<String, Object> demandUserDetails = new HashMap<>();
        demandUserDetails.put("name", name);
        demandUserDetails.put("requestingSeats", requestingSeats);

        Map<String, Object> demandUser = new HashMap<String, Object>();
        demandUser.put(id, demandUserDetails);

        return demandUser;
    }

    public Map<String, Object> setSupplyUser(String id, String name){
        Map<String, Object> supplyUserDetails = new HashMap<>();
        supplyUserDetails.put("name", name);

        Map<String, Object> supplyUser = new HashMap<String, Object>();
        supplyUser.put(id, supplyUserDetails);

        return supplyUser;
    }

    /**
     * Sets the history for a given key.
     *
     * @param key      The key to save the history for
     * @param fromLocation The location of origin
     * @param toLocation The location of destination
     * @param supplyUser The map of supply user id with name
     * @param offeredSeats The num of seats offered
     */
    //setLocation
    public void setGlobalHistory(String key, String fromLocation,
                                 String toLocation, Map<String, Object> supplyUser,
                                 Integer offeredSeats) {
        this.setGlobalHistory(key, fromLocation, toLocation, supplyUser,
                offeredSeats,null);
    }

    /**
     * Sets the history for a given key.
     *
     * @param key      The key to save the history for
     * @param fromLocation The location of origin
     * @param toLocation The location of destination
     * @param supplyUser The map of supply user id with name
     * @param offeredSeats The num of seats offered
     * @param completionListener A listener that is called once the location was successfully saved on the server or an
     *                           error occurred
     */
    public void setGlobalHistory(final String key, final String fromLocation,
                                 final String toLocation, final Map<String, Object> supplyUser,
                                 final Integer offeredSeats,
                                 final CompletionListener completionListener) {
        if (key == null) {
            throw new NullPointerException();
        }
        DatabaseReference keyRef = this.getDatabaseRefForKey(key);

        this.timeStamp = System.currentTimeMillis();
        SimpleDateFormat sfd = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss.SSS z", Locale.US);
        this.date = sfd.format(new Date(timeStamp));

        //GeoHash geoHashTo = new GeoHash(new GeoLocation(toLocation));//todo Geocoder
        //GeoHash geoHashFrom = new GeoHash(fromLocation);

        Map<String, Object> updates = new HashMap<String, Object>();
        //updates.put("g-destination", geoHashTo.getGeoHashString());
        //updates.put("g-departure", geoHashFrom.getGeoHashString());
        //updates.put("l-destination", Arrays.asList(toLocation.latitude, toLocation.longitude));
        //updates.put("l-departure", Arrays.asList(fromLocation.latitude, fromLocation.longitude));
        updates.put("Departure", fromLocation);
        updates.put("Destination", fromLocation);
        updates.put("supplyUser", supplyUser);
        updates.put("offeredSeats", offeredSeats);
        updates.put("usedSeats", 0);
        updates.put("demandUsers", null);
        updates.put("timeStamp", timeStamp);
        updates.put("date", date);
        if (completionListener != null) {
            keyRef.setValue(updates, new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                    completionListener.onComplete(key, databaseError);
                }
            });
        } else {
            keyRef.updateChildren(updates);
        }
    }

    /**
     * Updates the history for a given key.
     *
     * @param key      The key to save the history for
     * @param demandUser The map of demand users id with the num of seats they used
     * @param usedSeats The num of seats used
     */
    public void updateGlobalHistory(String key, Map<String, Object> demandUser,
                                    String usedSeats) {
        this.updateGlobalHistory(key, demandUser, usedSeats,null);
    }

    /**
     * Updates the history for a given key.
     *
     * @param key      The key to save the history for
     * @param demandUser The map of demand users id with the num of seats they used
     * @param usedSeats The num of seats used
     * @param completionListener A listener that is called once the location was successfully saved on the server or an
     *                           error occurred
     */
    public void updateGlobalHistory(final String key, final Map<String, Object> demandUser,
                                 final String usedSeats,
                                 final CompletionListener completionListener) {
        if (key == null) {
            throw new NullPointerException();
        }
        DatabaseReference keyRef = this.getDatabaseRefForKey(key);

        Map<String, Object> updates = new HashMap<String, Object>();
        //updates.put("usedSeats", usedSeats); //todo transaction on usedSeats
        updates.put("demandUsers", demandUser);
        if (completionListener != null) {
            keyRef.setValue(updates, new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                    completionListener.onComplete(key, databaseError);
                }
            });
        } else {
            keyRef.updateChildren(updates);
        }
    }

}
