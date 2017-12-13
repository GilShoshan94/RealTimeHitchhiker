package com.realtimehitchhiker.hitchgo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MySupply {
    public String destination;
    public int remainingSeats;
    public int fuelPrice;
    public String currency;
    public boolean petAllowed;
    public Long timeStamp;
    public String date;
    public String historyKey;

    public MySupply() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MySupply(String destination, int remainingSeats, int fuelPrice, String currency, boolean petAllowed, String historyKey) {
        this.destination = destination;
        this.remainingSeats = remainingSeats;
        this.fuelPrice = fuelPrice;
        this.currency = currency;
        this.petAllowed = petAllowed;
        timeStamp = System.currentTimeMillis();
        SimpleDateFormat sfd = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss.SSS z", Locale.US);
        this.date = sfd.format(new Date(timeStamp));
        this.historyKey = historyKey;
    }
}
