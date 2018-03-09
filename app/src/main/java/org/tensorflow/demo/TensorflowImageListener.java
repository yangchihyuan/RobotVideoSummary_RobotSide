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

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.Trace;

import junit.framework.Assert;

import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.List;
import java.lang.Math;

import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotCallback;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with Tensorflow.
 */
class TensorFlowImageListener implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  //1/15/2018 Chih-Yuan: disable file save, to reduce the delay
  private static final boolean SAVE_PREVIEW_BITMAP = false;

  // These are the settings for the original v1 Inception model. If you want to
  // use a model that's been produced from the TensorFlow for Poets codelab,
  // you'll need to set IMAGE_SIZE = 299, IMAGE_MEAN = 128, IMAGE_STD = 128,
  // INPUT_NAME = "Mul:0", and OUTPUT_NAME = "final_result:0".
  // You'll also need to update the MODEL_FILE and LABEL_FILE paths to point to
  // the ones you produced.
  private static final int NUM_CLASSES = 1470;
  private static final int INPUT_SIZE = 448;
  private static final int IMAGE_MEAN = 128;
  private static final float IMAGE_STD = 128;
  private static final String INPUT_NAME = "Placeholder";
  private static final String OUTPUT_NAME = "19_fc";

  private static final String MODEL_FILE = "file:///android_asset/android_graph.pb";
  private static final String LABEL_FILE =
      "file:///android_asset/label_strings.txt";

  private Integer sensorOrientation;

  private final TensorFlowClassifier tensorflow = new TensorFlowClassifier();

  private int previewWidth = 0;
  private int previewHeight = 0;
  private byte[][] yuvBytes;
  private int[] rgbBytes = null;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap shrink_for_fullwidth_Bitmap = null;

  private boolean computing = false;
  //private boolean readyForNextImage = true;
  private Handler handler;

  private RecognitionScoreView scoreView;
  private BoundingBoxView boundingView;
  private InputView inputView;

  private com.asus.robotframework.API.RobotAPI YOLO_robotAPI;
  private YOLO_RobotCallback robotCallback;

  public void initialize(
      final AssetManager assetManager,
      final RecognitionScoreView scoreView,
      final BoundingBoxView boundingView,
      final InputView inputView,
      final Handler handler,
      final Integer sensorOrientation) {
    Assert.assertNotNull(sensorOrientation);
    tensorflow.initializeTensorFlow(
        assetManager, MODEL_FILE, LABEL_FILE, NUM_CLASSES, INPUT_SIZE, IMAGE_MEAN, IMAGE_STD,
        INPUT_NAME, OUTPUT_NAME);
    this.scoreView = scoreView;
    this.boundingView = boundingView;
    this.inputView = inputView;
    this.handler = handler;
    this.sensorOrientation = sensorOrientation;
    this.robotCallback = new YOLO_RobotCallback();
    this.YOLO_robotAPI = new RobotAPI(scoreView.getContext(), robotCallback);
  }

  private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {
    Assert.assertEquals(dst.getWidth(), dst.getHeight());
//    final float minDim = Math.min(src.getWidth(), src.getHeight());
    final float maxDim = Math.max(src.getWidth(), src.getHeight());
    final Matrix matrix = new Matrix();

    // We only want the center square out of the original rectangle.
//    final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
//    final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
    final float translateX = 0;
    final float translateY = (maxDim - src.getHeight()) / 2;
    matrix.preTranslate(translateX, translateY);

    //final float scaleFactor = dst.getHeight() / minDim;
    //1/15/2018 Chih-Yuan: I want to use the full width
    final float scaleFactor = dst.getWidth() / maxDim;
    matrix.postScale(scaleFactor, scaleFactor);

    // Rotate around the center if necessary.
    // Nataniel: added rotation because image is rotated on my device (Pixel C tablet)
    // TODO: Find out if this is happenning in every device.
    //1/12/2018 Chih-Yuan: This is the script causing problems.
//    sensorOrientation = 90;
//    if (sensorOrientation != 0) {
///     matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
//      matrix.postRotate(sensorOrientation);
//      matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
//    }
    //LOGGER.i("sensorOrientationImageListener" + sensorOrientation.toString());

    final Canvas canvas = new Canvas(dst);
    Paint fgPaint = new Paint();
    fgPaint.setColor(0xff00ff00);
    canvas.drawRect(0,0,448,448,fgPaint);
    canvas.drawBitmap(src, matrix, null);
  }

  @Override
  public void onImageAvailable(final ImageReader reader) {
    Image image = null;
    try {
      // No mutex needed as this method is not reentrant.
      image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

//      if (computing || !robotCallback.motion_complete) {
      if (computing ) {
        image.close();
        return;
      }

      computing = true;

      Trace.beginSection("imageAvailable");

      final Plane[] planes = image.getPlanes();

      // Initialize the storage bitmaps once when the resolution is known.
      if (previewWidth != image.getWidth() || previewHeight != image.getHeight()) {
        previewWidth = image.getWidth();
        previewHeight = image.getHeight();

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbBytes = new int[previewWidth * previewHeight];
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);

        yuvBytes = new byte[planes.length][];
        for (int i = 0; i < planes.length; ++i) {
          yuvBytes[i] = new byte[planes[i].getBuffer().capacity()];
        }
      }

      for (int i = 0; i < planes.length; ++i) {
        planes[i].getBuffer().get(yuvBytes[i]);
      }

      final int yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();
      ImageUtils.convertYUV420ToARGB8888(
          yuvBytes[0],
          yuvBytes[1],
          yuvBytes[2],
          rgbBytes,
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

    rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
    shrink_for_fullwidth_Bitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);
    drawResizedBitmap(rgbFrameBitmap, shrink_for_fullwidth_Bitmap);
    inputView.setBitmap(shrink_for_fullwidth_Bitmap);

/*      URL url = null;
      try {
          url = new URL("http://140.112.30.188:8889");
      } catch (MalformedURLException e) {
          e.printStackTrace();
      }
      HttpURLConnection connection = null;
      try {
          connection = (HttpURLConnection) url.openConnection();
      } catch (IOException e) {
          e.printStackTrace();
      }
      */
      try {

          String UPLOAD_URL = "http://140.112.30.188:8890";

          String username = "test_user_123";
          String datetime = "2016-12-09 10:00:00";
//          File file_image = new File("/sdcard/DCIM/Camera/IMG_20180305_144519.jpg");
//          int file_size = Integer.parseInt(String.valueOf(file_image.length()));
          final okhttp3.OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
          OkHttpClient client  = httpBuilder
                  .connectTimeout(1, TimeUnit.SECONDS)
                  .writeTimeout(1, TimeUnit.SECONDS)
                  .build();

          int bytes = shrink_for_fullwidth_Bitmap.getByteCount();
          ByteBuffer buffer_ARGB = ByteBuffer.allocate(bytes); //Create a new buffer
          shrink_for_fullwidth_Bitmap.copyPixelsToBuffer(buffer_ARGB); //Move the byte data to the buffer
          byte[] array_ARGB = buffer_ARGB.array();
          //The format is RGBA
          //3/8/2018 Chih-Yuan: Convert the RGBA format to RGB. I don't know how to do it.

// Create a multipart request body. Add metadata and files as 'data parts'.
          RequestBody requestBody = new MultipartBody.Builder()
                  .setType(MultipartBody.FORM)      //should we use form? According to the okhttp3 API, there is a MIXED type.
//                  .addFormDataPart("username", username)
//                  .addFormDataPart("datetime", datetime)
                  //.addFormDataPart("file","FileTestName")    //the String FileTestName will show as the data field.
                  //.addFormDataPart("image", "TestImageName",
                  //        RequestBody.create(MediaType.parse("image/jpeg"), array))
                  .addFormDataPart("application/octet-stream", "Framedata", RequestBody.create(MediaType.parse("application/octet-stream"), array_ARGB))
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
              //3/8/2018 Check here, what can I get?
          } catch (IOException e) {
              e.printStackTrace();
          }

// Check the response to see if the upload succeeded
          if (response == null || !response.isSuccessful()) {
              //Log.w("Example", "Unable to upload to server.");
          } else {
              //System.out.println(response.body().string());
              LOGGER.d("%s", response.body().string());
          }
      } catch (Exception e) {
          e.printStackTrace();
      } finally {
          //connection.disconnect();
      }


/*
          String twoHyphens = "--";
          String boundary =  "*****"+Long.toString(System.currentTimeMillis())+"*****";
          String lineEnd = "\r\n";

          connection.setDoOutput(true);
          connection.setDoInput(true);
          connection.setUseCaches(false);
          connection.setRequestMethod("POST");
          connection.setRequestProperty("Connection", "Keep-Alive");
          connection.setRequestProperty("User-Agent", "Android Multipart HTTP Client 1.0");
          connection.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);

          //connection.setFixedLengthStreamingMode(802816);
          connection.setChunkedStreamingMode(0);  //use this when the output size is unknown

        DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
          outputStream.writeBytes(twoHyphens + boundary + lineEnd);
          //the name will be the key in the server side, the filename keeps the same, the content_type is image/jpeg
          outputStream.writeBytes("Content-Disposition: form-data; name=\"" + "test123" + "\"; filename=\"" + "test456" +"\"" + lineEnd);
          outputStream.writeBytes("Content-Type: image/jpeg" + lineEnd);
          outputStream.writeBytes("Content-Transfer-Encoding: binary" + lineEnd);
          outputStream.writeBytes(lineEnd);
        int bytes = shrink_for_fullwidth_Bitmap.getByteCount();
        ByteBuffer buffer = ByteBuffer.allocate(bytes); //Create a new buffer
        shrink_for_fullwidth_Bitmap.copyPixelsToBuffer(buffer); //Move the byte data to the buffer

        byte[] array = buffer.array();
          outputStream.write(array, 0, bytes);
          outputStream.writeBytes(lineEnd);
        //  out.writeBytes("Test 123");
          outputStream.flush();
          outputStream.close();

//        InputStream in = new BufferedInputStream(urlConnection.getInputStream());
//        readStream(in);
    } catch (IOException e) {
          e.printStackTrace();
      } finally {
        connection.disconnect();
    }
*/


    //2/23/2018 Chih-Yuan: This handle in fact is the inferenceHandler declared in the CameraConnectionFragment.java
/*        final boolean post = handler.post(
            new Runnable() {
                @Override
                public void run() {
                    final List<Classifier.Recognition> results = tensorflow.recognizeImage(shrink_for_fullwidth_Bitmap);
                    computing = false;
                    scoreView.setResults(results);
                    boundingView.setResults(shrink_for_fullwidth_Bitmap,results);

                    float Confidence = results.get(0).getConfidence();
                    float Confidence_threshold = 0.13f;
                    if (Confidence > Confidence_threshold) {
                        RectF preBoundingBox = results.get(0).getLocation();
                        float boundingbox_center_x = preBoundingBox.left;   //in fact, the meaning of the .left property is the box center
                        float boundingbox_half_width = preBoundingBox.right;
                        float boundingbox_center_y = preBoundingBox.top;      //2/12/2018 Chih-Yuan: I will this value later to adjust Zenbo's neck angle
                        float boundingbox_half_height = preBoundingBox.bottom;
                        float boundingbox_area = 2 * boundingbox_half_width * 2 * boundingbox_half_height;
                        float expected_boundingbox_area = (INPUT_SIZE / 2) * (INPUT_SIZE / 2);

                        float x = (float) expected_boundingbox_area / boundingbox_area - 1;      //the unit is meter
                        //Zenbo's horizotal view angle: 62.5053
                        float rotate_rate = 0.9f;
                        float full_rotation_angle = (float) -(boundingbox_center_x - INPUT_SIZE / 2) / INPUT_SIZE * (62.5053f / 180) * 3.14f;
                        float y = x * (float) Math.tan(full_rotation_angle);

                        float theta = full_rotation_angle * rotate_rate;
                        LOGGER.d("center_x %f theta %f", boundingbox_center_x, theta);
                        //              if (SAVE_PREVIEW_BITMAP) {
                        //                ImageUtils.saveBitmap(shrink_for_fullwidth_Bitmap);
                        //              }
                        //2/22/2018 Chih-Yuan: break the previous motion action
                        int result = YOLO_robotAPI.motion.stopMoving();
                        robotCallback.motion_complete = false;
                        YOLO_robotAPI.motion.moveBody(x, y, theta);
                    }

                }
            }
        );
*/
    computing = false;
    robotCallback.motion_complete = false;
      Trace.endSection();
  }

  public void takePic() {   //1/18/2017 Chih-Yuan: this function is never called.
    //readyForNextImage = true;
    LOGGER.v("Taking picture");
    return;
  }

  public static int getInputSize() {
    return INPUT_SIZE;
  }
}
