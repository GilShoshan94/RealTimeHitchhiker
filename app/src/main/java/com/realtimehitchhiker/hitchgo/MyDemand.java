package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MyDemand {
    public String destination;
    public int requestingSeats;
    public boolean hasAllowed;
    public Long timeStamp;

    public MyDemand() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MyDemand(String destination, int requestingSeats, boolean hasAllowed) {
        this.destination = destination;
        this.requestingSeats = requestingSeats;
        this.hasAllowed = hasAllowed;
        timeStamp = System.currentTimeMillis();
    }
}
