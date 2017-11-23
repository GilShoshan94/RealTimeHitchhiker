package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MySupply {
    public String remainingSeats;
    public Long timeStamp;

    public MySupply() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MySupply(String remainingSeats) {
        this.remainingSeats = remainingSeats;
        timeStamp = System.currentTimeMillis();
    }
}
