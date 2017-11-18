package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

public class MyUser {
    public String name;
    public String email;
    public String phone;
    public Double latitude;
    public Double longitude;

    public MyUser() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    public MyUser(String name, String email, String phone, Double latitude, Double longitude) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
