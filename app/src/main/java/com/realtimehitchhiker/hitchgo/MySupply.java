package com.realtimehitchhiker.hitchgo;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by gilshoshan on 18/11/17.
 */

public class MySupply {
    public Double latitude;
    public Double longitude;
    public Long remainingSeats;
    public Long timeStamp;
    public String date;

    public MySupply() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MySupply(Double latitude, Double longitude, Long remainingSeats) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.remainingSeats = remainingSeats;
        timeStamp = System.currentTimeMillis();
        SimpleDateFormat sfd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        date = sfd.format(new Date(timeStamp));
    }
}
