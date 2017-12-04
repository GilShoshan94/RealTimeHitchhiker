package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MySupply {
    public String destination;
    public int remainingSeats;
    public int fuelPrice;
    public boolean petAllowed;
    public Long timeStamp;

    public MySupply() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MySupply(String destination, int remainingSeats, int fuelPrice, boolean petAllowed) {
        this.destination = destination;
        this.remainingSeats = remainingSeats;
        this.fuelPrice = fuelPrice;
        this.petAllowed = petAllowed;
        timeStamp = System.currentTimeMillis();
    }
}
