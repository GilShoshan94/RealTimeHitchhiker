package com.realtimehitchhiker.hitchgo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by gilshoshan on 18/11/17.
 */

class MyUser {
    public String name;
    public String email;
    public String phone;
    public String dateCreated;

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
        Long timeStamp = System.currentTimeMillis();
        SimpleDateFormat sfd = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateCreated = sfd.format(new Date(timeStamp));
    }
}
