package com.ubicomplab.lightsensor;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class cameraService extends Service {

    private CameraDevice cameraDeviceFront, cameraDeviceRear;
    private CameraCaptureSession cameraCaptureSessionFront, cameraCaptureSessionRear;
    private MediaRecorder mediaRecorderFront, mediaRecorderRear;
    private TextureView textureViewFront, textureViewRear;
    private String cameraIdFront, cameraIdRear;
    private FileWriter metadataWriter;
    private String startTimestamp;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startTimestamp = intent.getStringExtra("startTime");
        startCameraRecording();
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startTimestamp = String.valueOf(System.currentTimeMillis());
        textureViewFront = new TextureView(this);
        textureViewRear = new TextureView(this);

        textureViewFront.setSurfaceTextureListener(textureListenerFront);
        textureViewRear.setSurfaceTextureListener(textureListenerRear);

        startBackgroundThread();
    }

    private final TextureView.SurfaceTextureListener textureListenerFront = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            startCameraRecording();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    private final TextureView.SurfaceTextureListener textureListenerRear = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            startCameraRecording();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startCameraRecording() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            cameraIdFront = manager.getCameraIdList()[0];
            cameraIdRear = manager.getCameraIdList()[1];

            CameraCharacteristics characteristicsFront = manager.getCameraCharacteristics(cameraIdFront);
            CameraCharacteristics characteristicsRear = manager.getCameraCharacteristics(cameraIdRear);

            StreamConfigurationMap mapFront = characteristicsFront.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            StreamConfigurationMap mapRear = characteristicsRear.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size videoSizeFront = mapFront.getOutputSizes(MediaRecorder.class)[0];
            Size videoSizeRear = mapRear.getOutputSizes(MediaRecorder.class)[0];

            setUpMediaRecorderFront(videoSizeFront);
            setUpMediaRecorderRear(videoSizeRear);

            File metadataFile = new File(getExternalFilesDir(null), startTimestamp + "_camera_metadata.csv");
            metadataWriter = new FileWriter(metadataFile);
            metadataWriter.write("Timestamp,Camera,Exposure Time,ISO,Sensitivity\n");

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show();
                stopSelf();
                return;
            }
            manager.openCamera(cameraIdFront, stateCallbackFront, backgroundHandler);
            manager.openCamera(cameraIdRear, stateCallbackRear, backgroundHandler);

        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    private void setUpMediaRecorderFront(Size videoSize) throws IOException {
        mediaRecorderFront = new MediaRecorder();
        mediaRecorderFront.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorderFront.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorderFront.setOutputFile(new File(getExternalFilesDir(null), startTimestamp + "_front_camera.mp4").getAbsolutePath());
        mediaRecorderFront.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorderFront.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorderFront.setVideoFrameRate(30);
        mediaRecorderFront.prepare();
    }

    private void setUpMediaRecorderRear(Size videoSize) throws IOException {
        mediaRecorderRear = new MediaRecorder();
        mediaRecorderRear.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorderRear.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorderRear.setOutputFile(new File(getExternalFilesDir(null), startTimestamp + "_rear_camera.mp4").getAbsolutePath());
        mediaRecorderRear.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorderRear.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorderRear.setVideoFrameRate(30);
        mediaRecorderRear.prepare();
    }

    private final CameraDevice.StateCallback stateCallbackFront = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDeviceFront = camera;
            createCameraPreviewSessionFront();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDeviceFront = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDeviceFront = null;
        }
    };

    private final CameraDevice.StateCallback stateCallbackRear = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDeviceRear = camera;
            createCameraPreviewSessionRear();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDeviceRear = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDeviceRear = null;
        }
    };

    private void createCameraPreviewSessionFront() {
        try {
            SurfaceTexture texture = textureViewFront.getSurfaceTexture();
            Surface previewSurface = new Surface(texture);
            Surface recorderSurface = mediaRecorderFront.getSurface();

            final CaptureRequest.Builder captureBuilder = cameraDeviceFront.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureBuilder.addTarget(previewSurface);
            captureBuilder.addTarget(recorderSurface);

            cameraDeviceFront.createCaptureSession(Arrays.asList(previewSurface, recorderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSessionFront = session;
                    try {
                        cameraCaptureSessionFront.setRepeatingRequest(captureBuilder.build(), captureCallback, backgroundHandler);
                        mediaRecorderFront.start();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSessionRear() {
        try {
            SurfaceTexture texture = textureViewRear.getSurfaceTexture();
            texture.setDefaultBufferSize(1920, 1080);
            Surface previewSurface = new Surface(texture);
            Surface recorderSurface = mediaRecorderRear.getSurface();

            final CaptureRequest.Builder captureBuilder = cameraDeviceRear.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureBuilder.addTarget(previewSurface);
            captureBuilder.addTarget(recorderSurface);

            cameraDeviceRear.createCaptureSession(Arrays.asList(previewSurface, recorderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSessionRear = session;
                    try {
                        cameraCaptureSessionRear.setRepeatingRequest(captureBuilder.build(), captureCallback, backgroundHandler);
                        mediaRecorderRear.start();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            try {
                long timestamp = System.currentTimeMillis();
                String cameraId = session.getDevice().getId();
                Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                Integer sensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY);

                synchronized (metadataWriter) {
                    metadataWriter.write(timestamp + "," + cameraId + "," + exposureTime + "," + sensitivity + "\n");
                    metadataWriter.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBackgroundThread();
        if (metadataWriter != null) {
            try {
                metadataWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mediaRecorderFront != null) {
            mediaRecorderFront.release();
        }
        if (mediaRecorderRear != null) {
            mediaRecorderRear.release();
        }
        if (cameraDeviceFront != null) {
            cameraDeviceFront.close();
        }
        if (cameraDeviceRear != null) {
            cameraDeviceRear.close();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}