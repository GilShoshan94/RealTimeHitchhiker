package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MyDemand {
    public String requestingSeats;
    public Long timeStamp;

    public MyDemand() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MyDemand( String requestingSeats) {
        this.requestingSeats = requestingSeats;
        timeStamp = System.currentTimeMillis();
    }
}
