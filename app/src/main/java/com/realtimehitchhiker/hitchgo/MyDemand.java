package com.realtimehitchhiker.hitchgo;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by gilshoshan on 18/11/17.
 */

public class MyDemand {
    public Double latitude;
    public Double longitude;
    public Long timeStamp;
    public String date;

    public MyDemand() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MyDemand(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        timeStamp = System.currentTimeMillis();
        SimpleDateFormat sfd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        date = sfd.format(new Date(timeStamp));
    }
}
