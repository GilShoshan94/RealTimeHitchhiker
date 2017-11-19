package com.realtimehitchhiker.hitchgo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * Created by gilshoshan on 19/11/17.
 */

class MyHistory {

    public String supplyUserId;
    public Map<String, Boolean> demandUserId;
    public MyLocation form;
    public MyLocation to;
    public Long timeStamp;
    public String date;

    public static class MyLocation{
        public Double latitude;
        public Double longitude;
        public MyLocation() {
            // Default constructor required for calls to DataSnapshot.getValue(User.class)
        }
        MyLocation(Double latitude, Double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public MyHistory() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MyHistory(String supplyUserId, Map<String, Boolean> demandUserId,Double fromLatitude, Double fromLongitude, Double toLatitude, Double toLongitude) {
        this.supplyUserId = supplyUserId;
        this.demandUserId  = demandUserId;
        form = new MyLocation(fromLatitude, fromLongitude);
        to = new MyLocation(toLatitude, toLongitude);
        timeStamp = System.currentTimeMillis();
        SimpleDateFormat sfd = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss.SSS z", Locale.US);
        date = sfd.format(new Date(timeStamp));
    }
}
