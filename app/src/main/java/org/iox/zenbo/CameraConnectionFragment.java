/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*  This file has been modified by Nataniel Ruiz affiliated with Wall Lab
 *  at the Georgia Institute of Technology School of Interactive Computing
 */

package org.iox.zenbo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.asus.robotframework.API.MotionControl;
import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.SpeakConfig;

import org.iox.zenbo.env.Logger;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


//public class CameraConnectionFragment extends Fragment implements View.OnClickListener {
public class CameraConnectionFragment extends Fragment {
    private static final Logger LOGGER = new Logger();

    private KeyPointView keypointView;

    private InputView inputView;

    private ActionRunnable mActionRunnable = new ActionRunnable();
    private Button button_test;
    private Button button30;
    private Button button_takepic;
    private Button button_freezedata;
    private Button button_dontmove;
    private MessageView mMessageView_Detection;
    private MessageView mMessageView_Timestamp;
    private com.asus.robotframework.API.RobotAPI ZenboAPI;
    private com.asus.robotframework.API.SpeakConfig speakConfig;
    private DataBuffer m_DataBuffer;
    private MediaRecorder mMediaRecorder;
    private String mVideoAbsolutePath;
    private static final String TAG = "Camera2VideoFragment";
    private Size mPreviewSize = new Size(640, 480);
    private CameraDevice mCameraDevice;
    private HandlerThread backgroundThread;
    private Handler mBackgroundHandler;
    private HandlerThread inferenceThread;
    private Handler inferenceHandler;
    private HandlerThread mActionThread;
    private Handler mActionHandler;
    private ImageReader mPreviewReader;     //used to get onImageAvailable, not used in Camera2Video project
    private CaptureRequest.Builder mPreviewBuilder;
    //    private CaptureRequest mPreviewRequest;
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private boolean mIsRecordingVideo = false;

    private AutoFitTextureView mTextureView;
    private CameraCaptureSession mPreviewSession;
    private final ImageListener mPreviewListener = new ImageListener();
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS");
    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };
    DialogCallback dsCallback;


    /**
     * {@link android.view.TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
//                    openCamera(width, height);
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
//                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };


    /**
     * {@link android.hardware.camera2.CameraDevice.StateCallback}
     * is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice cameraDevice) {
                    // This method is called when the camera is opened.  We start camera preview here.
                    mCameraDevice = cameraDevice;
//                startPreview();
                    startRecordingVideo();      //The startRecordingVideo is called in a callback function. Is it fine?
                    cameraOpenCloseLock.release();
//                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(final CameraDevice cd) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    mCameraDevice = null;
                }

                @Override
                public void onError(final CameraDevice cd, final int error) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    mCameraDevice = null;
                    final Activity activity = getActivity();
                    if (null != activity) {
                        activity.finish();
                    }
                }
            };

    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    public static CameraConnectionFragment newInstance() {
        return new CameraConnectionFragment();
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.camera_connection_fragment, container, false);
        return v;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        keypointView = (KeyPointView) view.findViewById(R.id.keypoint);
        inputView = (InputView) view.findViewById(R.id.inputview);
        button_test = (Button) view.findViewById(R.id.button_test);
        button30 = (Button) view.findViewById(R.id.button_30);
        button_takepic = (Button) view.findViewById(R.id.button_takepic);
        button_freezedata = (Button) view.findViewById(R.id.button_freezedata);
        button_dontmove = (Button) view.findViewById(R.id.button_dontmove);
        mMessageView_Detection = (MessageView) view.findViewById(R.id.MessageView_Detection);
        mMessageView_Timestamp = (MessageView) view.findViewById(R.id.MessageView_Timestamp);
        ZenboAPI = new RobotAPI(view.getContext());
        speakConfig = new SpeakConfig();
        speakConfig.domain("5823CA7CDACF43EEAF4417D783FB1321");
        speakConfig.languageId(SpeakConfig.LANGUAGE_ID_EN_US);
        ZenboAPI.robot.jumpToPlan("5823CA7CDACF43EEAF4417D783FB1321", "zenboTest.plan.showface");

        if( dsCallback == null)
            dsCallback = new DialogCallback();

        dsCallback.setRobotAPI(ZenboAPI);
        dsCallback.setActionRunnable(mActionRunnable);

        if (dsCallback != null)
            ZenboAPI.robot.registerListenCallback(dsCallback);

        button_test.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {

                ZenboAPI.robot.speakAndListen("Would you like me to show my face?", speakConfig);
            }
        });

        button30.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                ZenboAPI.motion.moveHead(0, 30, MotionControl.SpeedLevel.Head.L3);
            }
        });

        button_takepic.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String Caption = button_takepic.getText().toString();
                if (Caption.equals("Take Pic")) {
                    button_takepic.setText("Stop");
                    mPreviewListener.bTakePic = true;
                } else {
                    button_takepic.setText("Take Pic");
                    mPreviewListener.bTakePic = false;
                }
            }
        });

        button_freezedata.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String Caption = button_freezedata.getText().toString();
                if (Caption.equals("Freeze Data")) {
                    button_freezedata.setText("UnFreeze");
                    m_DataBuffer.FreezeData();
                } else {
                    button_freezedata.setText("Freeze Data");
                    m_DataBuffer.UnfreezeData();
                }
            }
        });

        button_dontmove.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String Caption = button_dontmove.getText().toString();
                if (Caption.equals("Don't Move")) {
                    button_dontmove.setText("Enable Move");
                    mActionRunnable.bDontMove = true;
                } else {
                    button_dontmove.setText("Don't Move");
                    mActionRunnable.bDontMove = false;
                }
            }
        });

        mActionRunnable.setMessageView(mMessageView_Detection, mMessageView_Timestamp);
        m_DataBuffer = new DataBuffer(100);
        mActionRunnable.setDataBuffer(m_DataBuffer);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
//            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        try{
            if( mIsRecordingVideo) {
                mMediaRecorder.stop();      //Sometimes I get an error message here, why? Maybe I cannot call the stop() if it is not recording.
                mIsRecordingVideo = false;
            }
        }
        catch( Exception e)
        {

        }
        closeCamera();
        stopBackgroundThread();
        super.onPause();
        Log.d("CameraConnectionFragment","onPause() is called.");
    }

    /**
     * Opens the camera specified by {@link CameraConnectionFragment#cameraId}.
     */
    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.M)
    private void openCamera() {
        mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        final Activity activity = getActivity();
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(5000, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];
            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }

            mMediaRecorder = new MediaRecorder();
            // 4/25/2018 Chih-Yuan: The permission check is done in the TrackActivity.java
            manager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
        } catch (final CameraAccessException e) {
            LOGGER.e(e, "Exception!");
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != mPreviewSession) {
                mPreviewSession.close();
                mPreviewSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mPreviewReader) {
                mPreviewReader.close();
                mPreviewReader = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        long max_filesize_bytes = 4*1024*1024*1024;  //4Gib
        mMediaRecorder.setMaxFileSize(max_filesize_bytes);      //Does it work?
        mVideoAbsolutePath = getVideoFilePath(getActivity());
        mMediaRecorder.setOutputFile(mVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)
                {
                    closeCamera();
//                    openCamera(640,480);
                    openCamera();
                }
            }
        });
        mMediaRecorder.prepare();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        mBackgroundHandler = new Handler(backgroundThread.getLooper());

        inferenceThread = new HandlerThread("InferenceThread");
        inferenceThread.start();
        inferenceHandler = new Handler(inferenceThread.getLooper());

        mActionThread = new HandlerThread( "ActionThread");
        mActionThread.start();
        mActionHandler = new Handler(mActionThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        inferenceThread.quitSafely();
        mActionThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            mBackgroundHandler = null;

            inferenceThread.join();
            inferenceThread = null;
            inferenceHandler = null;

            mActionThread.join();
            mActionThread = null;
            mActionHandler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }
    }

    private String getVideoFilePath(Context context) {
        String path = Environment.getExternalStorageDirectory().toString();
        Date currentTime = Calendar.getInstance().getTime();
        Log.d("getVideoFilePath", mDateFormat.format(currentTime));
        //I have to mkdir the Captures folder.
        File file=new File(path + "/Captures");
        if(!file.exists()){
            file.mkdirs();
        }
        return path + "/Captures/" + mDateFormat.format(currentTime) + ".mp4";
    }

    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // Set up Surface for the ImageReader
            mPreviewReader = ImageReader.newInstance( mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            mPreviewReader.setOnImageAvailableListener(mPreviewListener, mBackgroundHandler);
            mPreviewBuilder.addTarget(mPreviewReader.getSurface());
            surfaces.add(mPreviewReader.getSurface());

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
//                            mButtonVideo.setText(R.string.stop);
                            mIsRecordingVideo = true;

                            // Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
        mPreviewListener.initialize(inferenceHandler, mMessageView_Detection, inputView,
                m_DataBuffer, keypointView, mActionHandler, mActionRunnable);
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            //CaptureRequest.CONTROL_MODE: Overall mode of 3A
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            final Surface surface = new Surface(texture);
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);

            // Create the reader for the preview frames.
            mPreviewReader = ImageReader.newInstance( mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            mPreviewReader.setOnImageAvailableListener(mPreviewListener, mBackgroundHandler);
            mPreviewBuilder.addTarget(mPreviewReader.getSurface());

            mCameraDevice.createCaptureSession(
                    Arrays.asList(surface, mPreviewReader.getSurface()),//Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Activity activity = getActivity();
                            if (null != activity) {
                                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mPreviewListener.initialize(inferenceHandler, mMessageView_Detection, inputView,
                m_DataBuffer, keypointView, mActionHandler,mActionRunnable);
    }
}
