package com.realtimehitchhiker.hitchgo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by gilshoshan on 22-Nov-17.
 */

/**
 * This class is an helper tool to Download an Image, and does this Task asynchronously
 *
 */

class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {

    private static final String TAG = "DOWNLOAD_IMAGE_TASK_DEBUG";
    //private ImageView bmImage;

    public interface AsyncResponse {
        void processFinish(Bitmap output);
    }

    private AsyncResponse delegate = null;

    //DownloadImageTask(ImageView bmImage) {
    //    this.bmImage = bmImage;
    //}
    DownloadImageTask(AsyncResponse delegate) {
        this.delegate = delegate;
    }


    @Override
    protected Bitmap doInBackground(String... urls) {
        String urlDisplay = urls[0];
        Bitmap mIcon = null;
        InputStream in = null;
        try {
            in = new java.net.URL(urlDisplay).openStream();
            mIcon = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch(IOException ioEx) {
                    //Very bad things just happened... handle it
                    Log.wtf(TAG, "Stream.close throw exception :" + ioEx );
                }
            }
        }
        return mIcon;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        //bmImage.setImageBitmap(result);
        delegate.processFinish(result);
    }
}
