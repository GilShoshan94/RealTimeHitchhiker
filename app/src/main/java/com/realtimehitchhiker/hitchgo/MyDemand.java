package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MyDemand {
    public String destination;
    public int requestingSeats;
    public boolean hasPet;
    public Long timeStamp;

    public MyDemand() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MyDemand(String destination, int requestingSeats, boolean hasPet) {
        this.destination = destination;
        this.requestingSeats = requestingSeats;
        this.hasPet = hasPet;
        timeStamp = System.currentTimeMillis();
    }
}
