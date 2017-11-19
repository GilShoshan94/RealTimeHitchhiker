package com.realtimehitchhiker.hitchgo;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MySupply {
    public String id;
    public Long remainingSeats;
    public Double latitude;
    public Double longitude;
    public Long timeStamp;

    public MySupply() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MySupply(String id, Long remainingSeats, Double latitude, Double longitude) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.remainingSeats = remainingSeats;
        timeStamp = System.currentTimeMillis();
    }
}
