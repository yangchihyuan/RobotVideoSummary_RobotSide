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
    private final Paint PaintKeyPoint, PaintKeyPoint_FirstPerson, PaintSkeleton, Paint_YoloBoundingBox, PaintSkeleton_FirstPerson;
    private Paint PaintKeyPoint_used, skPaint_used;
    private Bitmap OutputBitmap = null;
    private AnalyzedFrame analyzedFrame;

    public KeyPointView(final Context context, final AttributeSet set) {
        super(context, set);

        PaintKeyPoint = new Paint();
        PaintKeyPoint.setColor(0xff0000ff);    //Blue
        PaintKeyPoint.setStyle(Paint.Style.STROKE);
        PaintKeyPoint.setStrokeWidth(4);

        PaintKeyPoint_FirstPerson = new Paint();
        PaintKeyPoint_FirstPerson.setColor(0xff00ff00);  //Green
        PaintKeyPoint_FirstPerson.setStyle(Paint.Style.STROKE);
        PaintKeyPoint_FirstPerson.setStrokeWidth(4);

        PaintSkeleton = new Paint();
        PaintSkeleton.setColor(0xff7700ff);
        PaintSkeleton.setStyle(Paint.Style.STROKE);
        PaintSkeleton.setStrokeWidth(2);

        PaintSkeleton_FirstPerson = new Paint();
        PaintSkeleton_FirstPerson.setColor(0xff77ff00);
        PaintSkeleton_FirstPerson.setStyle(Paint.Style.STROKE);
        PaintSkeleton_FirstPerson.setStrokeWidth(2);

        Paint_YoloBoundingBox = new Paint();
        Paint_YoloBoundingBox.setColor(0x77770000);
        Paint_YoloBoundingBox.setStyle(Paint.Style.STROKE);
        Paint_YoloBoundingBox.setStrokeWidth(4);
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

    public void setResults( AnalyzedFrame frame)
    {
        analyzedFrame = frame;
        postInvalidate();       //This function will lead to onDraw
    }

    @Override
    public void onDraw(final Canvas canvas) {
        if( analyzedFrame == null)
            return;

        for( int idx_openpose = 0 ; idx_openpose < analyzedFrame.openpose_cnt ;idx_openpose++)
        {
            if( idx_openpose == 0 ) {
                PaintKeyPoint_used = PaintKeyPoint_FirstPerson;
                skPaint_used = PaintSkeleton_FirstPerson;
            }
            else {
                PaintKeyPoint_used = PaintKeyPoint;
                skPaint_used = PaintSkeleton;
            }

            float [][] o_results = analyzedFrame.openpose_coordinate.get(idx_openpose);
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

                    canvas.drawRect(boundingBox, PaintKeyPoint_used);
                }
            }

            // Draw skeletal displacement :)
            int[] src = {0,  0,  0, 1, 1, 1,  1, 2, 3, 5, 6, 8,  9, 11, 12, 14, 15};
            int[] dst = {1, 14, 15, 2, 5, 8, 11, 3, 4, 6, 7, 9, 10, 12, 13, 16, 17};

            for(int j = 0; j < 17; j++)
                if(o_results[src[j]][2] > 0 && o_results[dst[j]][2] > 0)
                    canvas.drawLine(o_results[src[j]][0], o_results[src[j]][1],
                                    o_results[dst[j]][0], o_results[dst[j]][1], skPaint_used);

        }

        for( int idx_yolo = 0 ; idx_yolo < analyzedFrame.yolo_cnt ;idx_yolo++)
        {
            float[] y_results = analyzedFrame.yolo_coordinate.get(idx_yolo);
            //Draw bounding boxes for yolo
            float c_w = canvas.getWidth(), c_h = canvas.getHeight();

            float top_x = (y_results[0] - y_results[2]/2) * c_w;
            float bot_x = (y_results[0] + y_results[2]/2) * c_w;
            float top_y = (y_results[1] + y_results[3]/2) * c_h;
            float bot_y = (y_results[1] - y_results[3]/2) * c_h;

            canvas.drawRect(top_x, bot_y, bot_x, top_y, Paint_YoloBoundingBox);
            canvas.drawCircle(top_x, top_y, 5, Paint_YoloBoundingBox);
            canvas.drawCircle(bot_x, bot_y, 5, Paint_YoloBoundingBox);
            canvas.drawCircle(top_x, bot_y, 5, Paint_YoloBoundingBox);
            canvas.drawCircle(bot_x, top_y, 5, Paint_YoloBoundingBox);

        }
    }
}
