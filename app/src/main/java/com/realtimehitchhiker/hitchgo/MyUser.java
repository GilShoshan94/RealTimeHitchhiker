package com.realtimehitchhiker.hitchgo;

/**
 * Created by gilshoshan on 18/11/17.
 */

public class MyUser {
    public String name;
    public String email;
    public String phone;

    public MyUser() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    MyUser(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        if(phone != null)
            this.phone = phone;
        else
            this.phone = "false";
    }
}
