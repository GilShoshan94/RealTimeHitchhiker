package com.realtimehitchhiker.hitchgo;

import android.os.Parcel;
import android.os.Parcelable;

import com.firebase.geofire.GeoLocation;

/**
 * Created by gilshoshan on 06-Dec-17.
 */

public class ResultLocation implements Parcelable {

    Double latitude, longitude;

    public ResultLocation() {
        // Default constructor required for Parcelable
    }
    ResultLocation(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
    ResultLocation(GeoLocation location) {
        this.latitude = location.latitude;
        this.longitude = location.longitude;
    }
    ResultLocation(ResultLocation location) {
        this.latitude = location.latitude;
        this.longitude = location.longitude;
    }

    private ResultLocation(Parcel in) {
        if (in.readByte() == 0) {
            latitude = null;
        } else {
            latitude = in.readDouble();
        }
        if (in.readByte() == 0) {
            longitude = null;
        } else {
            longitude = in.readDouble();
        }
    }

    public static final Creator<ResultLocation> CREATOR = new Creator<ResultLocation>() {
        @Override
        public ResultLocation createFromParcel(Parcel in) {
            return new ResultLocation(in);
        }

        @Override
        public ResultLocation[] newArray(int size) {
            return new ResultLocation[size];
        }
    };

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
    }
}
