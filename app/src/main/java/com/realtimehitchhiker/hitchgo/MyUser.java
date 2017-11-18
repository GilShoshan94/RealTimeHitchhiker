package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

public class MyUser {
    public String username;
    public String email;

    public MyUser() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    public MyUser(String username, String email) {
        this.username = username;
        this.email = email;
    }
}
