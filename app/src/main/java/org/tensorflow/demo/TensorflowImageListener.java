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

import java.util.List;

import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotCallback;


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
  private Bitmap croppedBitmap = null;

  private boolean computing = false;
  private boolean readyForNextImage = true;
  private Handler handler;

  private RecognitionScoreView scoreView;
  private BoundingBoxView boundingView;

  private com.asus.robotframework.API.RobotAPI YOLO_robotAPI;
  private YOLO_RobotCallback robotCallback;

  public void initialize(
      final AssetManager assetManager,
      final RecognitionScoreView scoreView,
      final BoundingBoxView boundingView,
      final Handler handler,
      final Integer sensorOrientation) {
    Assert.assertNotNull(sensorOrientation);
    tensorflow.initializeTensorFlow(
        assetManager, MODEL_FILE, LABEL_FILE, NUM_CLASSES, INPUT_SIZE, IMAGE_MEAN, IMAGE_STD,
        INPUT_NAME, OUTPUT_NAME);
    this.scoreView = scoreView;
    this.boundingView = boundingView;
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

//      if (computing || !readyForNextImage || !robotCallback.motion_complete) {
      if (computing || !readyForNextImage ) {
        image.close();
        return;
      }

//      robotCallback.motion_complete = false;
      readyForNextImage = true;
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
        croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

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
    drawResizedBitmap(rgbFrameBitmap, croppedBitmap);

//    handler.post(
//        new Runnable() {
//          @Override
//          public void run() {
            final List<Classifier.Recognition> results = tensorflow.recognizeImage(croppedBitmap);

            LOGGER.v("%d results", results.size());
            for (final Classifier.Recognition result : results) {
              LOGGER.v("Result: " + result.getTitle());
            }
            scoreView.setResults(results);
            boundingView.setResults(results);

            if( results.get(0).getTitle().equals("person") ){
              RectF preBoundingBox = results.get(0).getLocation();
              float center_x = preBoundingBox.left;   //in fact, the meaning of the .left property is the box center

              float x = (float) 0;
              float y = (float) 0;
              //Zenbo's horizotal view angle: 62.5053
              float rotate_rate = 0.9f;
              float theta = (float) -(center_x - INPUT_SIZE/2 )/INPUT_SIZE * (62.5053f/180) * 3.14f * rotate_rate;
              LOGGER.d("center_x %f theta %f", center_x, theta);
              if (SAVE_PREVIEW_BITMAP) {
                ImageUtils.saveBitmap(croppedBitmap);
              }

//              if( results.get(0).getConfidence() > 0.3f)
//                robotCallback.motion_complete = false;
                YOLO_robotAPI.motion.moveBody(x,y, theta);
            }
//            else
//            {
//              robotCallback.motion_complete = true;
//            }

            computing = false;
//          }
//        });

    Trace.endSection();
  }

  public void takePic() {
    readyForNextImage = true;
    LOGGER.v("Taking picture");
    return;
  }

  public static int getInputSize() {
    return INPUT_SIZE;
  }
}
