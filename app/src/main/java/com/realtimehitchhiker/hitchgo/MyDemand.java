package com.realtimehitchhiker.hitchgo;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MyDemand {
    public String id;
    public Long requestingSeats;
    public Double latitude;
    public Double longitude;
    public Long timeStamp;

    public MyDemand() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MyDemand(String id, Long requestingSeats, Double latitude, Double longitude) {
        this.id = id;
        this.requestingSeats = requestingSeats;
        this.latitude = latitude;
        this.longitude = longitude;
        timeStamp = System.currentTimeMillis();
    }
}
