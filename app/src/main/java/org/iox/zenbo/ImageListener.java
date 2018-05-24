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
import android.os.Trace;
import android.util.Log;

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

    //I need this command so that the app can use the yuv2rgb function
    static {
        System.loadLibrary("tensorflow_demo");
//        System.loadLibrary("jniLib");
    }

    public native String StringFromJNI();

    private static final Logger LOGGER = new Logger();

    private int previewWidth = 640;
    private int previewHeight = 480;
    private byte[][] yuvBytes;
    private int[] argbBytes = null;
    private Bitmap argbFrameBitmap = null;
    public boolean bTakePic = false;
    private MyTimerTask myTimerTask;
    private SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS");
    private Handler inferenceHandler;
    private InputView inputView;
    private Data[] DataArray;
    private KeyPointView keypointView;
    private final String TAG = "Listener";

    public void initialize(MyTimerTask theTask, Handler inferenceHandler, InputView inputView, Data[] dataArray, KeyPointView keypointView) {
        argbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        argbBytes = new int[previewWidth * previewHeight];
        myTimerTask = theTask;      //I need to yaw and pitch degrees
        this.inferenceHandler = inferenceHandler;
        this.inputView = inputView;
        this.DataArray = dataArray;
        this.keypointView = keypointView;
    }

    //5/15/2018 Chih-Yuan: The java function is too slow.
    private void getRGBIntFromPlanes(Image.Plane[] planes) {
        ByteBuffer yPlane = planes[0].getBuffer();
        ByteBuffer uPlane = planes[1].getBuffer();
        ByteBuffer vPlane = planes[2].getBuffer();

        int bufferIndex = 0;
        final int total = yPlane.capacity();
        final int uvCapacity = uPlane.capacity();
        final int width = planes[0].getRowStride();

        int yPos = 0;
        for (int i = 0; i < previewHeight; i++) {
            int uvPos = (i >> 1) * width;

            for (int j = 0; j < width; j++) {
                if (uvPos >= uvCapacity-1)
                    break;
                if (yPos >= total)
                    break;

                final int y1 = yPlane.get(yPos++) & 0xff;

            /*
              The ordering of the u (Cb) and v (Cr) bytes inside the planes is a
              bit strange. The _first_ byte of the u-plane and the _second_ byte
              of the v-plane build the u/v pair and belong to the first two pixels
              (y-bytes), thus usual YUV 420 behavior. What the Android devs did
              here (IMHO): just copy the interleaved NV21 U/V data to two planes
              but keep the offset of the interleaving.
             */
                final int u = (uPlane.get(uvPos) & 0xff) - 128;
//                final int v = (vPlane.get(uvPos+1) & 0xff) - 128;
                final int v = (vPlane.get(uvPos) & 0xff) - 128;
                if ((j & 1) == 1) {
                    uvPos += 2;
                }

                // This is the integer variant to convert YCbCr to RGB, NTSC values.
                // formulae found at
                // https://software.intel.com/en-us/android/articles/trusted-tools-in-the-new-android-world-optimization-techniques-from-intel-sse-intrinsics-to
                // and on StackOverflow etc.
                final int y1192 = 1192 * y1;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = (r < 0) ? 0 : ((r > 262143) ? 262143 : r);
                g = (g < 0) ? 0 : ((g > 262143) ? 262143 : g);
                b = (b < 0) ? 0 : ((b > 262143) ? 262143 : b);

                argbBytes[bufferIndex++] = (0xff000000 | (r << 6) & 0xff0000) |
                        ((g >> 2) & 0xff00) |
                        ((b >> 10) & 0xff);
            }
        }
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {

        final Image image = reader.acquireLatestImage();
        final long timestamp_image = System.currentTimeMillis();
        final int yawDegree = myTimerTask.yawDegree;
        final int pitchDegree = myTimerTask.pitchDegree;
        //save the time sample and keypoints cooridinates.
        for (int i = DataArray.length-1; i > 0; i--) {
            DataArray[i].timestamp_OnImageAvailable = DataArray[i - 1].timestamp_OnImageAvailable;
            DataArray[i].timestamp_ReceivedFromServer = DataArray[i - 1].timestamp_ReceivedFromServer;
            DataArray[i].yawDegree = DataArray[i - 1].yawDegree;
            DataArray[i].pitchDegree = DataArray[i - 1].pitchDegree;
            DataArray[i].ServerReturns = DataArray[i - 1].ServerReturns;
            DataArray[i].bNew = DataArray[i - 1].bNew;
        }
        DataArray[0].timestamp_OnImageAvailable = timestamp_image;
        DataArray[0].yawDegree = yawDegree;
        DataArray[0].pitchDegree = pitchDegree;
        DataArray[0].timestamp_ReceivedFromServer = -1;
        DataArray[0].ServerReturns = "";
        DataArray[0].bNew = true;
        final long timestamp = System.currentTimeMillis();
        Log.i(TAG, "onImageAvailable() is called " + Long.toString(timestamp));


        if (image == null)
            return; //such a case happens.

        final Plane[] planes = image.getPlanes();

/*        yuvBytes = new byte[planes.length][];
        for (int i = 0; i < planes.length; ++i) {
            yuvBytes[i] = new byte[planes[i].getBuffer().capacity()];
            planes[i].getBuffer().get(yuvBytes[i]);
        }
        //y: 307200, u: 153599, v: 153599
*/


        try {
            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            org.tensorflow.demo.env.ImageUtils.convertYUV420ToARGB8888(
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


              //5/15/2018 Chih-Yuan: The java function is too slow.
//            getRGBIntFromPlanes( planes );

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
        //send every frame to the server
        final boolean post = inferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        String Openpose_results = "";
                        long timestamp = timestamp_image;
                        try {
                            String UPLOAD_URL = "http://140.112.30.188:8894";

                            String username = "test_user_123";
                            String datetime = "2016-12-09 10:00:00";
                            final OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
                            OkHttpClient client = httpBuilder
                                    .connectTimeout(1, TimeUnit.SECONDS)
                                    .writeTimeout(1, TimeUnit.SECONDS)
                                    .build();

                            inputView.setBitmap(argbFrameBitmap);
                            inputView.postInvalidate();
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            argbFrameBitmap.compress(Bitmap.CompressFormat.JPEG, 50, os);

                            byte[] array_JPEG = os.toByteArray();

// Create a multipart request body. Add metadata and files as 'data parts'.
                            long array_length = array_JPEG.length;
                            RequestBody requestBody = new MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)      //should we use form? According to the okhttp3 API, there is a MIXED type.
                                    .addFormDataPart("application/octet-stream", Long.toString(array_length), RequestBody.create(MediaType.parse("application/octet-stream"), array_JPEG))
                                    .build();

// Create a POST request to send the data to UPLOAD_URL
                            Request request = new Request.Builder()
                                    .url(UPLOAD_URL)
                                    .post(requestBody)
                                    .build();

// Execute the request and get the response from the server
                            Response response = null;

                            try {
                                response = client.newCall(request).execute();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

// Check the response to see if the upload succeeded
                            if (response == null || !response.isSuccessful()) {
                                Log.i("response == null", "response == null.");
                            } else {
                                Openpose_results = response.body().string();  //Why do I get an exception here?
                                String[] lines = Openpose_results.split(System.getProperty("line.separator"));
                                if (lines.length > 1) {
                                    String[] number_lines = Arrays.copyOfRange(lines, 1, 19);
                                    float[][] fMatrix = new float[18][3];
                                    int i = 0, j;
                                    for (String line : number_lines) {
                                        j = 0;
                                        String[] value_strings = line.split(" ");
                                        for (String value_string : value_strings) {
                                            fMatrix[i][j] = Float.parseFloat(value_string);
                                            j++;
                                        }
                                        i++;
                                    }
                                    keypointView.setResults(fMatrix);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            //connection.disconnect();
                        }
                        //save the time sample and keypoint coordinates.
                        for( int data_index = 0 ; data_index < DataArray.length ; data_index++ ) {
                            if( DataArray[data_index].timestamp_OnImageAvailable == timestamp) {
                                DataArray[data_index].ServerReturns = Openpose_results;
                                DataArray[data_index].timestamp_ReceivedFromServer = System.currentTimeMillis();
                                break;
                            }
                        }
                    }//end of run
                });

//        SaveImage.save(argbFrameBitmap, "_onImageAvailable");

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
        Trace.endSection();
    }
}
