package com.realtimehitchhiker.hitchgo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_MESSAGE = "com.realtimehitchhiker.hitchgo.MESSAGE";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /** Called when the user taps the imageButton_settings */
    public void callSettingsActivity(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        //EditText editText = (EditText) findViewById(R.id.editText);
        //String message = editText.getText().toString();
        //intent.putExtra(EXTRA_MESSAGE, message);
        startActivity(intent);
    }
}

/**
 * private void setUpActionBar() {
 *  // Make sure we're running on Honeycomb or higher to use ActionBar APIs
 *  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
 *      ActionBar actionBar = getActionBar();
 *      actionBar.setDisplayHomeAsUpEnabled(true);
 *  }
 * }
 *
 *
 Note:
 When parsing XML resources, Android ignores XML attributes that aren’t supported
 by the current device. So you can safely use XML attributes that are only supported
 by newer versions without worrying about older versions breaking when they encounter that code.
 For example, if you set the targetSdkVersion="11", your app includes the ActionBar by default
 on Android 3.0 and higher. To then add menu items to the action bar, you need to set
 android:showAsAction="ifRoom" in your menu resource XML. It's safe to do this in a
 cross-version XML file, because the older versions of Android simply ignore
 the showAsAction attribute (that is, you do not need a separate version in res/menu-v11/).
*/