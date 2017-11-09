package com.realtimehitchhiker.hitchgo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.facebook.login.widget.ProfilePictureView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    protected LoginButton loginButton;
    protected TextView txtStatus, txtName, txtEmail, txtID;
    protected ProfilePictureView imProfile;
    protected CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //For Facebook SDK
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);

        setContentView(R.layout.activity_login);

        txtStatus = (TextView) findViewById(R.id.textView_status_fb);
        txtStatus.setText(R.string.fb_status);
        txtName = (TextView) findViewById(R.id.textView_name);
        txtName.setText(R.string.name);
        txtEmail = (TextView) findViewById(R.id.textView_email);
        txtEmail.setText(R.string.email);
        txtID = (TextView) findViewById(R.id.textView_id);
        txtID.setText(R.string.id);
        imProfile = (ProfilePictureView) findViewById(R.id.fb_image_profile);
        //imProfile.setDefaultProfilePicture(bitmap);
        imProfile.setPresetSize(ProfilePictureView.LARGE);
        //imProfile.setCropped(false);

        loginButton = (LoginButton) findViewById(R.id.fb_login_button);
        loginButton.setReadPermissions("email", "public_profile");

        // Callback registration
        callbackManager = CallbackManager.Factory.create();
        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {

                // String userId = loginResult.getAccessToken().getUserId(); We will use this as URI for people

                GraphRequest graphRequest = GraphRequest.newMeRequest(loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        if (response != null) {
                            displayUserInfo(object);
                            txtStatus.append("Login succeed");
                        }
                        else
                            txtStatus.append("Login succeed but no respond from GraphRequest\nRespond : " + response.toString());
                    }
                });

                Bundle parameters = new Bundle();
                parameters.putString("fields" , "first_name, last_name, email, id, picture");
                graphRequest.setParameters(parameters);
                graphRequest.executeAsync();
            }

            @Override
            public void onCancel() {
                txtStatus.append("Login cancelled");
            }

            @Override
            public void onError(FacebookException error) {
                txtStatus.append("Login error");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    public void displayUserInfo(JSONObject object) {
        String first_name = "error", last_name = "error", email = "error", id = "error";
        try {
            first_name = object.getString("first_name");
            last_name = object.getString("last_name");
            email = object.getString("email");
            id = object.getString("id");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        txtName.append(first_name+" "+last_name);
        txtEmail.append(email);
        txtID.append(id);
        imProfile.setProfileId(id);
    }
}
