package com.realtimehitchhiker.hitchgo;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.SeekBar;

/**
 * Created by gilshoshan on 20-Nov-17.
 */

public class MySeekBar extends android.support.v7.widget.AppCompatSeekBar {
    public MySeekBar (Context context) {
        super(context);
    }

    public MySeekBar (Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MySeekBar (Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int thumb_x;
        if(this.getProgress() != this.getMax())
            thumb_x = (int) (( (double)this.getProgress()/this.getMax() ) * (double)this.getWidth());
        else
            thumb_x = (int) ( ((double)this.getMax() * (double)this.getMeasuredWidth()) - 10 );

        float middle = (float) (this.getHeight());

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(60);
        paint.setFakeBoldText(true);
        c.drawText(""+(this.getProgress()+1), thumb_x, middle, paint);
    }
}