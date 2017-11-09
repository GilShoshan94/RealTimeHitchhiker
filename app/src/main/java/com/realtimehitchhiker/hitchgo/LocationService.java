package com.realtimehitchhiker.hitchgo;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import static android.location.LocationProvider.OUT_OF_SERVICE;
import static android.location.LocationProvider.TEMPORARILY_UNAVAILABLE;
import static android.location.LocationProvider.AVAILABLE;

public class LocationService extends Service {

    @SuppressLint("MissingPermission")
    public static final String BROADCAST_ACTION_LOC_UPDATE = "location_update";
    public static final String BROADCAST_ACTION_LOC_OFF = "location_off";
    private static final String TAG = "TESTGPS";
    private LocationManager mLocationManager = null;
    private Location myLastLocation = null;
    private boolean[] isProviderAvailable = {false, false, false}; // Passive, Network, GPS
    private final static String passiveProvider = LocationManager.PASSIVE_PROVIDER;
    private final static String networkProvider = LocationManager.NETWORK_PROVIDER;
    private final static String gpsProvider = LocationManager.GPS_PROVIDER;
    public final static String[] providerList = {passiveProvider, networkProvider, gpsProvider};
    public final static String[] activeProviderList = {networkProvider, gpsProvider};
    private MyLocationListener listener = new MyLocationListener();

    // The minimum distance to change updates, in meters
    private static final float MIN_DISTANCE = 10; // 10 meters
    // The minimum time between updates from a provider, in milliseconds
    private static final long MIN_TIME = 1000 * 20 * 1; // 20 sec (ms * s * h)
    // The maximum time between updates for the location, in milliseconds
    private static final int TWO_MINUTES = 1000 * 6 * 1;

    private class MyLocationListener implements LocationListener{
        Location listLoc;

        public MyLocationListener()
        {
            Log.i(TAG, "LocationListener" );
            listLoc = null;
        }

        @Override
        public void onLocationChanged(Location location) {
            if (isBetterLocation(location, myLastLocation)){
                listLoc = location;
                myLastLocation = location;
                Intent intent = new Intent(BROADCAST_ACTION_LOC_UPDATE);
                intent.putExtra("location", myLastLocation);
                sendBroadcast(intent);
            }
            else if (isBetterLocation(location, listLoc)){
                listLoc = location;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            switch (status){
                case OUT_OF_SERVICE:
                    isProviderAvailable[pToI(provider)] = false;
                    break;
                case TEMPORARILY_UNAVAILABLE :
                    break;
                case AVAILABLE :
                    isProviderAvailable[pToI(provider)] = true;
            }
            if(isAllActiveProviderDisabled()){
                Log.i(TAG, "isAllActiveProviderDisabled 2" );
                Intent intentlocoff = new Intent(BROADCAST_ACTION_LOC_OFF);
                sendBroadcast(intentlocoff);
            }

        }

        @Override
        public void onProviderEnabled(String provider) {
            isProviderAvailable[pToI(provider)] = true;
        }

        @Override
        public void onProviderDisabled(String provider) {
            isProviderAvailable[pToI(provider)] = false;
            if(isAllActiveProviderDisabled()){
                Log.i(TAG, "isAllActiveProviderDisabled 3" );
                Intent intentlocoff = new Intent(BROADCAST_ACTION_LOC_OFF);
                sendBroadcast(intentlocoff);
            }
        }
    }

    /** Helper function
     *
     * @return index for the bool array
     */
    protected int pToI(String provider){
        switch (provider){
            case passiveProvider:
                return 0;
            case networkProvider:
                return 1;
            case gpsProvider:
                return 2;
            default:
                return -1; //Should not arrive here
        }
    }

    private boolean isAllActiveProviderDisabled(){
        int tot=0;
        for (int i = 0; i < activeProviderList.length; i++) {
            if (!mLocationManager.isProviderEnabled(activeProviderList[i]))
                tot++;
        }
        if (tot == activeProviderList.length)
            return true;
        else
            return false;
    }

    //public LocationService() {
    //}

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
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
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
        if (mLocationManager == null)
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        for (int i = 0; i < providerList.length; i++) {
            try {
                mLocationManager.requestLocationUpdates(providerList[i], MIN_TIME, MIN_DISTANCE, listener);
            } catch (java.lang.SecurityException ex) {
                Log.i(TAG, "fail to request location update, ignore", ex);
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
        Log.i(TAG, "onStartCommand" );
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        String provider = mLocationManager.getBestProvider(criteria, true);
        if(provider != null)
            myLastLocation = mLocationManager.getLastKnownLocation(provider);
        if(myLastLocation != null){
            Log.i(TAG, "onStart LastKnownLocation" );
            Intent intent = new Intent(BROADCAST_ACTION_LOC_UPDATE);
            intent.putExtra("location", myLastLocation);
            sendBroadcast(intent);
        }

        if(isAllActiveProviderDisabled()){
            Log.i(TAG, "isAllActiveProviderDisabled 1" );
            Intent intentlocoff = new Intent(BROADCAST_ACTION_LOC_OFF);
            sendBroadcast(intentlocoff);
        }
        Log.i(TAG, "provider = "+provider );
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLocationManager != null) {
            try {
                mLocationManager.removeUpdates(listener);
                Log.i(TAG, "Remove location listener ");
            } catch (Exception ex) {
                Log.i(TAG, "fail to remove location listeners, ignore", ex);
            }
        }
    }
}
