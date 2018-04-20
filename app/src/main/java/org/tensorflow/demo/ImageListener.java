/* Copyright 2015 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

/*  This file has been modified by Nataniel Ruiz affiliated with Wall Lab
 *  at the Georgia Institute of Technology School of Interactive Computing
 */

package org.tensorflow.demo;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Environment;
import android.os.Trace;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

class ImageListener implements OnImageAvailableListener {

    //I need this command so that the app can use the yuv2rgb function
    static {
        System.loadLibrary("tensorflow_demo");
    }

    private static final Logger LOGGER = new Logger();

    private int previewWidth = 640;
    private int previewHeight = 480;
    private byte[][] yuvBytes;
    private int[] argbBytes = null;
    public Bitmap argbFrameBitmap = null;
    public boolean bTakePic = false;
    private MyTimerTask myTimerTask;
    private SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS");

    public void initialize(MyTimerTask theTask) {
        argbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        argbBytes = new int[previewWidth * previewHeight];
        myTimerTask = theTask;      //I need to yaw and pitch degrees
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {

        final Image image = reader.acquireLatestImage();
        if (image == null)
            return; //such a case happens.

        final Plane[] planes = image.getPlanes();

        yuvBytes = new byte[planes.length][];
        for (int i = 0; i < planes.length; ++i) {
            yuvBytes[i] = new byte[planes[i].getBuffer().capacity()];
            planes[i].getBuffer().get(yuvBytes[i]);
        }
        //y: 307200, u: 153599, v: 153599

        try {
            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0],
                    yuvBytes[1],
                    yuvBytes[2],
                    argbBytes,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }
        argbFrameBitmap.setPixels(argbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
//        SaveImage.save(argbFrameBitmap, "_onImageAvailable");

        if(bTakePic)
        {
            Date currentTime = Calendar.getInstance().getTime();
            String path = Environment.getExternalStorageDirectory().toString();
            OutputStream fOutputStream = null;
            File file = new File(path + "/Captures/", format.format(currentTime) + "yaw" + Integer.toString(myTimerTask.yawDegree) + " pitch" + Integer.toString(myTimerTask.pitchDegree) + ".jpg");
            try {
                fOutputStream = new FileOutputStream(file);

                argbFrameBitmap.compress(Bitmap.CompressFormat.JPEG, 50, fOutputStream);

                fOutputStream.flush();
                fOutputStream.close();

//                MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
//                Toast.makeText(this, "Save Failed", Toast.LENGTH_SHORT).show();
                return;
            } catch (IOException e) {
                e.printStackTrace();
//                Toast.makeText(this, "Save Failed", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Trace.endSection();
    }
}
