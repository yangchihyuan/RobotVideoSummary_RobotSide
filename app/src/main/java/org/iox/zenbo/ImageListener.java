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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
    public boolean bTakePic = false;
    private Handler inferenceHandler;
    private MessageView mMessageView;
    private Handler mActionHandler;
    private InputView inputView;
    private DataBuffer dataBuffer;
    private KeyPointView keypointView;
    private long timestamp_prevous_processed_image = 0;
    OkHttpClient.Builder httpBuilder;
    OkHttpClient client;
    int NumberOfSentFrames = 0;
    private ActionRunnable mActionRunnable;
    private int mPort_Number = 8894;

    public void initialize(Handler inferenceHandler, MessageView MessageView,
                           InputView inputView, DataBuffer dataBuffer, KeyPointView keypointView,
                           Handler ActionHandler, ActionRunnable ActionRunnable) {
        argbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        argbBytes = new int[previewWidth * previewHeight];
        this.inferenceHandler = inferenceHandler;
        this.inputView = inputView;
        this.dataBuffer = dataBuffer;
        this.keypointView = keypointView;
        mMessageView = MessageView;
        mActionHandler = ActionHandler;
        mActionRunnable = ActionRunnable;

        httpBuilder = new OkHttpClient.Builder();
        client = httpBuilder
                .connectTimeout(1, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {

        final Image image = reader.acquireLatestImage();

        if (image == null)
            return; //such a case happens.

        boolean b_test_speed = true;

        final long timestamp_image = System.currentTimeMillis();

        if( b_test_speed == false) {
            if (timestamp_image - timestamp_prevous_processed_image > 181)
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
                            String UPLOAD_URL = "http://140.112.30.188:" + Integer.toString(mPort_Number);
                            if( ServerName.equals("Pepper"))
                                UPLOAD_URL = "http://140.112.30.185:" + Integer.toString(mPort_Number);
                            else if(ServerName.equals("Sakura"))
                                UPLOAD_URL = "http://140.112.30.188:" + Integer.toString(mPort_Number);

                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            argbFrameBitmap.compress(Bitmap.CompressFormat.JPEG, 50, os);

                            byte[] array_JPEG = os.toByteArray();

// Create a multipart request body. Add metadata and files as 'data parts'.
                            long array_length = array_JPEG.length;
                            RequestBody requestBody = new MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)      //should we use form? According to the okhttp3 API, there is a MIXED type.
                                    .addFormDataPart(Long.toString(timestamp_image) + "_" + Integer.toString(mActionRunnable.pitchDegree), Long.toString(array_length), RequestBody.create(MediaType.parse("application/octet-stream"), array_JPEG))
                                    .build();

// Create a POST request to send the data to UPLOAD_URL
                            Request request = new Request.Builder()
                                    .url(UPLOAD_URL)
                                    .post(requestBody)
                                    .build();

// Execute the request and get the response from the server
                            Response response = null;

                            try {
                                NumberOfSentFrames++;
//                                Log.d("ImageListener NumberOfSentFrames", Integer.toString(NumberOfSentFrames));
                                response = client.newCall(request).execute();
                            } catch (IOException e) {
                                e.printStackTrace();
                                mMessageView.setString("Server timeout");
                                //setMessageViewText(4);
                            }

// Check the response to see if the upload succeeded
                            if (response == null || !response.isSuccessful()) {
                                Log.i("response == null", "response == null.");
                            } else {
                                if (dataBuffer.IsDataFrozen() == false)     //for debugging
                                    dataBuffer.AddNewFrame(response.body().string());
                                mActionHandler.post(mActionRunnable);
                                response.close();       //to prevent an error message java.net.ConnectException: failed to connect to /140.112.30.188 (port 8894) after 1000ms: connect failed: EMFILE (Too many open files)
                                keypointView.setResults(dataBuffer.getLatestfMatrix());

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            //connection.disconnect();
                        }
                    }//end of run
                }
        );

//        SaveImage.save(argbFrameBitmap, "_onImageAvailable");
/*
        if (bTakePic) {
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
*/
        Trace.endSection();
    }
}
