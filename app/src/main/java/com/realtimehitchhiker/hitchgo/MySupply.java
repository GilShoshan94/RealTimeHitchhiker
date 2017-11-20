package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MySupply {
    public Long remainingSeats;
    public Long timeStamp;

    public MySupply() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MySupply(Long remainingSeats) {
        this.remainingSeats = remainingSeats;
        timeStamp = System.currentTimeMillis();
    }
}
