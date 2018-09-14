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

package org.iox.zenbo;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.util.Log;

import org.iox.zenbo.env.ImageUtils;
import org.iox.zenbo.env.Logger;

import java.io.ByteArrayOutputStream;
import java.net.Socket;

class ImageListener implements OnImageAvailableListener {

    static {
        System.loadLibrary("native-lib");
    }

    private static final Logger LOGGER = new Logger();

    private int previewWidth = 640;
    private int previewHeight = 480;
    private byte[][] yuvBytes;
    private int[] argbBytes = null;
    private Bitmap argbFrameBitmap = null;
    private Handler inferenceHandler;
    private InputView inputView;
    private long timestamp_prevous_processed_image = 0;
    private ActionRunnable mActionRunnable;
    private int mPort_Number_Send_Frame = 8895;

    public void initialize(Handler inferenceHandler, InputView inputView,
                           ActionRunnable ActionRunnable) {
        argbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        argbBytes = new int[previewWidth * previewHeight];
        this.inferenceHandler = inferenceHandler;
        this.inputView = inputView;
        mActionRunnable = ActionRunnable;
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {

        final Image image = reader.acquireLatestImage();

        if (image == null)
            return; //such a case happens.

        boolean b_test_speed = false;

        final long timestamp_image = System.currentTimeMillis();

        //TODO: disable the postpone on the robot side, and do it in the server side for action recognition
        long frame_send_postpone = 200; //in millisecond
        if (b_test_speed == false) {
            if (timestamp_image - timestamp_prevous_processed_image > frame_send_postpone)
                timestamp_prevous_processed_image = timestamp_image;
            else {
                image.close();
                return;
            }
        }

        final Plane[] planes = image.getPlanes();

        yuvBytes = new byte[planes.length][];
        for (int i = 0; i < planes.length; ++i) {
            yuvBytes[i] = new byte[planes[i].getBuffer().capacity()];
            planes[i].getBuffer().get(yuvBytes[i]);
        }

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
        inputView.setBitmap(argbFrameBitmap);
        inputView.postInvalidate();

        final boolean post = inferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String ServerName = "Pepper";
                            String machine_URL = ServerName + ".csie.ntu.edu.tw";

                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            argbFrameBitmap.compress(Bitmap.CompressFormat.JPEG, 50, os);

                            byte[] array_JPEG = os.toByteArray();

                            //use socket
                            Socket socket_client;
                            socket_client = new Socket(machine_URL, mPort_Number_Send_Frame);
                            String key = Long.toString(timestamp_image) + "_" + Integer.toString(mActionRunnable.pitchDegree) + "\n";
                            socket_client.getOutputStream().write(key.getBytes());
                            socket_client.getOutputStream().write(array_JPEG);
                            socket_client.close();

                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            //connection.disconnect();
                        }
                    }//end of run
                }
        );
        Trace.endSection();
    }
}
