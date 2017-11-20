package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MyDemand {
    public Long requestingSeats;
    public Long timeStamp;

    public MyDemand() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MyDemand( Long requestingSeats) {
        this.requestingSeats = requestingSeats;
        timeStamp = System.currentTimeMillis();
    }
}
