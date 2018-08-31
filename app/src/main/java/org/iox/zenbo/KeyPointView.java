/*  This file has been created by Nataniel Ruiz affiliated with Wall Lab
 *  at the Georgia Institute of Technology School of Interactive Computing
 */

package org.iox.zenbo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.List;


public class KeyPointView extends View {
    private float[][] o_results;
    private float[] y_results;
    private final Paint fgPaint, bgPaint, textPaint, trPaint, skPaint, yoPaint;
    private Bitmap OutputBitmap = null;

    public KeyPointView(final Context context, final AttributeSet set) {
        super(context, set);

        fgPaint = new Paint();
        fgPaint.setColor(0xff00ff00);
        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeWidth(4);

        bgPaint = new Paint();
        bgPaint.setARGB(0, 0, 0, 0);
        bgPaint.setAlpha(0);
        bgPaint.setStyle(Paint.Style.STROKE);

        trPaint = new Paint();
        trPaint.setColor(0xff00ff00);
        trPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setTextSize(50);  //set text size

        skPaint = new Paint();
        skPaint.setColor(0xff77ff00);
        skPaint.setStyle(Paint.Style.STROKE);
        skPaint.setStrokeWidth(2);

        yoPaint = new Paint();
        yoPaint.setColor(0x77770000);
        yoPaint.setStyle(Paint.Style.STROKE);
        yoPaint.setStrokeWidth(4);
    }

    public void setResults(final Bitmap NewBitmap, final float[][] results) {
        OutputBitmap = NewBitmap;
        this.o_results = results;
        postInvalidate();       //This function will lead to onDraw
    }

    public void setResults(final float[][] o_results, final float[] y_results) {
        this.o_results = o_results;
        this.y_results = y_results;
        postInvalidate();       //This function will lead to onDraw
    }


    @Override
    public void onDraw(final Canvas canvas) {
        if (o_results != null) {
//            canvas.drawBitmap(OutputBitmap, 0, 0, null);
            for (int i=0; i<18; ++i) {
                if( o_results[i][2] > 0 )
                {
                    float x = o_results[i][0];
                    float y = o_results[i][1];
                    float bounding_x = x -2;
                    float bounding_x2 = x+2;
                    float bounding_y = y -2;
                    float bounding_y2 = y+2;
                    RectF boundingBox = new RectF(bounding_x, bounding_y, bounding_x2, bounding_y2);

                    canvas.drawRect(boundingBox, fgPaint);
                }
            }

            // Draw skeletal displacement :)
            int[] src = {0,  0,  0, 1, 1, 1,  1, 2, 3, 5, 6, 8,  9, 11, 12, 14, 15};
            int[] dst = {1, 14, 15, 2, 5, 8, 11, 3, 4, 6, 7, 9, 10, 12, 13, 16, 17};

            for(int j = 0; j < 17; j++)
                if(o_results[src[j]][2] > 0 && o_results[dst[j]][2] > 0)
                    canvas.drawLine(o_results[src[j]][0], o_results[src[j]][1],
                                    o_results[dst[j]][0], o_results[dst[j]][1], skPaint);

        }

        if(y_results != null) {
            //Draw bounding boxes for yolo
            float c_w = canvas.getWidth(), c_h = canvas.getHeight();

            float top_x = (y_results[0] - y_results[2]/2) * c_w;
            float bot_x = (y_results[0] + y_results[2]/2) * c_w;
            float top_y = (y_results[1] + y_results[3]/2) * c_h;
            float bot_y = (y_results[1] - y_results[3]/2) * c_h;

            canvas.drawRect(top_x, bot_y, bot_x, top_y, yoPaint);
            canvas.drawCircle(top_x, top_y, 5, yoPaint);
            canvas.drawCircle(bot_x, bot_y, 5, yoPaint);
            canvas.drawCircle(top_x, bot_y, 5, yoPaint);
            canvas.drawCircle(bot_x, top_y, 5, yoPaint);

        }
    }
}
