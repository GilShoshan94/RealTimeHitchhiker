package com.realtimehitchhiker.hitchgo;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import static android.location.LocationProvider.AVAILABLE;
import static android.location.LocationProvider.OUT_OF_SERVICE;
import static android.location.LocationProvider.TEMPORARILY_UNAVAILABLE;

/**
 * This is the Location Service class
 * It provides to the app the location (via the standard android object : Location)
 * It provides to the app the info of a location provider when it does disabled
 * It communicate through only two broadcasts : BROADCAST_ACTION_LOCATION_UPDATE
 *                                              BROADCAST_ACTION_LOCATION_OFF
 */

public class LocationService extends Service {

    @SuppressLint("MissingPermission")
    public static final String BROADCAST_ACTION_LOCATION_UPDATE = "com.realtimehitchhiker.hitchgo.LOCATION_UPDATE";
    public static final String BROADCAST_ACTION_LOCATION_OFF = "com.realtimehitchhiker.hitchgo.LOCATION_OFF";
    public static final String TAG = "LOCATION";
    private LocationManager mLocationManager = null;
    private Location myLastLocation = null;
    private final static String passiveProvider = LocationManager.PASSIVE_PROVIDER;
    private final static String networkProvider = LocationManager.NETWORK_PROVIDER;
    private final static String gpsProvider = LocationManager.GPS_PROVIDER;
    public final static String[] providerList = {passiveProvider, networkProvider, gpsProvider}; //todo .getAllProviders() ?
    public final static String[] activeProviderList = {networkProvider, gpsProvider};
    private static String lastActiveProvider = networkProvider;
    private MyLocationListener listener = new MyLocationListener();

    // The minimum distance to change updates, in meters
    private float MIN_DISTANCE;
    // The minimum time between updates from a provider, in milliseconds
    private long MIN_TIME;
    // The maximum time between updates for the location, in milliseconds
    private int MAXIMUM_TIME;

    class MyLocationListener implements LocationListener{

        MyLocationListener()
        {
            Log.d(TAG, "LocationListener" );
        }

        @Override
        public void onLocationChanged(Location location) {
            if (isBetterLocation(location, myLastLocation)){
                myLastLocation = location;
                broadcastMyLocation();
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            switch (status){
                case OUT_OF_SERVICE:
                    Log.d(TAG, provider + "is OUT_OF_SERVICE" );
                    break;
                case TEMPORARILY_UNAVAILABLE :
                    Log.d(TAG, provider + "is TEMPORARILY_UNAVAILABLE" );
                    break;
                case AVAILABLE :
                    Log.d(TAG, provider + "is AVAILABLE" );
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            lastActiveProvider = provider;
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG, "onProviderDisabled " + provider + " ---- " + lastActiveProvider );
            if(isAllActiveProviderDisabled() && isLastActiveProvider(provider)){
                Log.d(TAG, "isAllActiveProviderDisabled" );
                broadcastLocOff();
            }
            else if (isLastActiveProvider(provider)){
                Log.d(TAG, "onProviderDisabled else if " + provider + " ---- " + lastActiveProvider );
                for (String anActiveProviderList : activeProviderList) {
                    if (mLocationManager.isProviderEnabled(anActiveProviderList))
                        lastActiveProvider = anActiveProviderList;
                }
                Log.d(TAG, "lastActiveProvider = " + lastActiveProvider );
            }
        }
    }

    private boolean isLastActiveProvider(String provider){
        return pToI(provider) == pToI(lastActiveProvider);
    }

    /** Helper function
     *
     * @return index for the bool array
     */
    private int pToI(String provider){
        switch (provider){
            case passiveProvider:
                return 0;
            case networkProvider:
                return 1;
            case gpsProvider:
                return 2;
            default:
                Log.wtf(TAG, "Not normal! Error pToI(String provider)");
                return -1; //Should not arrive here
        }
    }

    private boolean isAllActiveProviderDisabled(){
        int tot=0;
        for (String anActiveProviderList : activeProviderList) {
            if (!mLocationManager.isProviderEnabled(anActiveProviderList))
                tot++;
        }
        return tot == activeProviderList.length;
    }

    private void broadcastMyLocation(){
        Log.d(TAG, "BROADCAST MyLocation" );
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(BROADCAST_ACTION_LOCATION_UPDATE);
        intent.putExtra("location", myLastLocation);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void broadcastLocOff(){
        Log.d(TAG, "BROADCAST Location OFF" );
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intentOff = new Intent(BROADCAST_ACTION_LOCATION_OFF);
        localBroadcastManager.sendBroadcast(intentOff);
    }


    /** Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }
        if (location == null)
            return false;

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > MAXIMUM_TIME;
        boolean isSignificantlyOlder = timeDelta < -MAXIMUM_TIME;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();
        //initialize the parameters
        Log.d(TAG, "initialize the parameters " + getApplicationContext().toString());
        MIN_DISTANCE = (float) getApplicationContext().getResources().getInteger(R.integer.location_min_distance);
        MIN_TIME = (long) getApplicationContext().getResources().getInteger(R.integer.location_min_time);
        MAXIMUM_TIME = getApplicationContext().getResources().getInteger(R.integer.location_max_time);
        Log.d(TAG, "MIN_DISTANCE = " + String.valueOf(MIN_DISTANCE));
        Log.d(TAG, "MIN_TIME = " + String.valueOf(MIN_TIME));
        Log.d(TAG, "MAXIMUM_TIME = " + String.valueOf(MAXIMUM_TIME));

        if (mLocationManager == null)
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        for (int i = 0; i < providerList.length; i++) {
            try {
                Log.d(TAG, "requestLocationUpdates : " + i);
                mLocationManager.requestLocationUpdates(providerList[i], MIN_TIME, MIN_DISTANCE, listener);
            } catch (java.lang.SecurityException ex) {
                Log.d(TAG, "fail to request location update, ignore", ex);
            } catch (IllegalArgumentException ex) {
                Log.d(TAG,providerList[i] + " provider does not exist, " + ex.getMessage());
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent i, int flags, int startId)
    {
        super.onStartCommand(i, flags, startId);
        Log.d(TAG, "onStartCommand" );
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.NO_REQUIREMENT);
        String provider = mLocationManager.getBestProvider(criteria, true);//todo
        if(provider != null) {
            if (isBetterLocation(mLocationManager.getLastKnownLocation(provider), myLastLocation)) {
                myLastLocation = mLocationManager.getLastKnownLocation(provider);
            }
        }
        if(myLastLocation != null){
            Log.d(TAG, "onStart LastKnownLocation" );
            broadcastMyLocation();
        }

        Log.d(TAG, "provider = "+provider );
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLocationManager != null) {
            try {
                mLocationManager.removeUpdates(listener);
                Log.d(TAG, "Remove location listener ");
            } catch (Exception ex) {
                Log.d(TAG, "fail to remove location listeners, ignore", ex);
            }
        }
    }
}
