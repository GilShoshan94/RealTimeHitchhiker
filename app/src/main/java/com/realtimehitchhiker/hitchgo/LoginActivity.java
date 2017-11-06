package com.realtimehitchhiker.hitchgo;

import android.content.Intent;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    LoginButton loginButton;
    TextView txtStatus, txtName, txtEmail, txtID;
    ImageView imProfile;
    CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        txtStatus = (TextView) findViewById(R.id.textView_status_fb);
        txtName = (TextView) findViewById(R.id.textView_name);
        txtEmail = (TextView) findViewById(R.id.textView_email);
        txtID = (TextView) findViewById(R.id.textView_id);
        imProfile = (ImageView) findViewById(R.id.imageView_profile);

        loginButton = (LoginButton) findViewById(R.id.fb_login_button);
        loginButton.setReadPermissions("email", "public_profile");

        // Callback registration
        callbackManager = CallbackManager.Factory.create();
        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                String userId = loginResult.getAccessToken().getUserId();

                GraphRequest graphRequest = GraphRequest.newMeRequest(loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        displayUserInfo(object);
                    }
                });

                Bundle parameters = new Bundle();
                parameters.putString("fields" , "first_name, last_name, email, id");
                graphRequest.setParameters(parameters);
                graphRequest.executeAsync();

                txtStatus.append("Login succeed");
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

    }
}
