package com.ubicomplab.lightsensor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Location;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;

import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

// requires 'https://jitpack.io' in settings.gradle.
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

/*
import dji.common.error.DJIError;
import dji.common.gimbal.Attitude;
import dji.common.gimbal.GimbalState;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
*/

public class MainActivity extends AppCompatActivity {
    // Variables for serial connection.
    public static final String ACTION_USB_PERMISSION = "com.ubicomplab.lightsensor.USB_PERMISSION";
    static UsbSerialDevice serialPort;
    UsbDeviceConnection connection;
    UsbDevice usbDevice;
    //static Button serialLoggingButton;
    boolean serialLogging;
    Context packageContext;

    // Camera variables
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private TextureView textureViewFront;
    private TextureView textureViewRear;
    private CameraDevice cameraDeviceFront;
    private CameraDevice cameraDeviceRear;
    private MediaRecorder mediaRecorderFront;
    private MediaRecorder mediaRecorderRear;
    private String cameraIdFront;
    private String cameraIdRear;
    private CameraCaptureSession cameraCaptureSessionFront;
    private CameraCaptureSession cameraCaptureSessionRear;
    private FileWriter metadataWriter;

    double longitude;
    double latitude;
    TextView textview;
    EditText noteInput;
    TextView generalIndicator;
    TextView rotationIndicator;
    TextView locationIndicator;
    private Button startButton;
    private Button stopButton;
    private Button verticalCalibrateButton;
    private Button horizontalCalibrateButton;
    private Button logNoteButton;
    boolean logging;
    private volatile boolean keepRunning = true; // Used in location and notes file logging.
    String startTimestamp;
    private Activity mainActivity;

    private SensorManager mySensorManager;
    private Sensor lightSensor;
    private SensorLogger lightSensorLogger;
    private Sensor rotationSensor;
    private SensorLogger rotationSensorLogger;
    private Sensor accelerometer;
    private SensorLogger accelerometerLogger;
    private Sensor gyroscope;
    private SensorLogger gyroscopeLogger;
    private Sensor magnetometer;
    private SensorLogger magnetometerLogger;

    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private FusedLocationProviderClient mFusedLocationClient;

    // For logging the location and notes:
    private ConcurrentLinkedQueue<String> locationQueue = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<String> notesQueue = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<String> externalLightSensorDataQueueSerial = new ConcurrentLinkedQueue<>();

    private Thread locationFileWritingThread = null;
    private Thread notesFileWritingThread = null;
    private Thread externalLightSensorFileWritingThread = null;
    private File locationOutputFile;
    private File externalLightSensorOutputFile;
    private File notesOutputFile;
    private int restartCounter;

    // Requesting permissions
    private static final int REQUEST_PERMISSIONS = 1;
    private boolean permissionAccepted = false;
    private String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO};

    //private Gimbal gimbal;

    public class SensorEventWriter implements SensorEventListener {
        private BufferedWriter bufferedWriter;

        public SensorEventWriter(BufferedWriter bufferedWriter) {
            this.bufferedWriter = bufferedWriter;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // Process sensor data (e.g., write to a file)
            writeSensorDataToFile(event);
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                //textview.setText("LIGHT: " + event.values[0]);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Update your TextView here
                        textview.setText("LIGHT: " + event.values[0]);
                    }
                });
                Log.i("Sensor OnChange", "Light even found!" + event.values[0]);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Handle sensor accuracy changes if needed
        }

        private void writeSensorDataToFile(SensorEvent event) {
            try {
                // Prepare the data string in CSV format
                StringBuilder dataString = new StringBuilder();
                long eventTimeInMillis = (
                        event.timestamp / 1000000L) + System.currentTimeMillis() - SystemClock.elapsedRealtime();

                dataString.append(eventTimeInMillis).append(","); // Timestamp

                // Append sensor values
                for (float value : event.values) {
                    dataString.append(value).append(",");
                }
                // Remove the last comma
                dataString.deleteCharAt(dataString.length() - 1);
                // Write to file and add a new line
                synchronized (bufferedWriter) {
                    bufferedWriter.write(dataString.toString());
                    bufferedWriter.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Single generalized class to handle reading from a sensor and writing to a file.
    public class SensorLogger {
        private SensorManager sensorManager;
        private Sensor sensor;
        private String sensorType;

        private SensorEventListener sensorEventListener;
        private HandlerThread handlerThread;
        private BufferedWriter bufferedWriter;

        public SensorLogger(SensorManager sensorManager, Sensor sensor, String sensorType) {

            this.sensorManager = sensorManager;
            this.sensor = sensor;
            this.sensorType = sensorType;
        }

        public void register(String commonFileName) {
            if (this.sensor != null) {
                this.handlerThread = new HandlerThread(this.sensorType + "SensorThread");
                this.handlerThread.start();
                Handler sensorHandler = new Handler(this.handlerThread.getLooper());
                // Initialize BufferedWriter
                File file = new File(getExternalFilesDir(null), commonFileName + "_" + this.sensorType + ".csv");
                try {
                    this.bufferedWriter = new BufferedWriter(new FileWriter(file, true)); // 'true' to append
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Initialize your sensor event listener
                this.sensorEventListener = new SensorEventWriter(this.bufferedWriter);
                if (sensorType.equals("light")) {
                    this.sensorManager.registerListener(this.sensorEventListener,
                            this.sensor, SensorManager.SENSOR_DELAY_FASTEST, sensorHandler);
                } else {
                    this.sensorManager.registerListener(this.sensorEventListener,
                            this.sensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler);
                }

            } else {
                Log.i("SENSOR!", sensorType + " NOT Available");
            }
        }

        public void close() {
            // Unregister the sensor listener
            if (this.sensorEventListener != null) {
                this.sensorManager.unregisterListener(this.sensorEventListener);
                this.sensorEventListener = null;
            }

            // Close the BufferedWriter
            try {
                if (this.bufferedWriter != null) {
                    synchronized (bufferedWriter) {
                        this.bufferedWriter.close();
                    }
                    this.bufferedWriter = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Stop the HandlerThread
            if (this.handlerThread != null) {
                this.handlerThread.quitSafely();
                try {
                    this.handlerThread.join();
                    this.handlerThread = null;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSIONS:
                permissionAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionAccepted) finish();
    }

    // Trigger new location updates at interval
    protected void startLocationUpdates() {
        // Set up the reoccurring request.
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(1000)
                .setMaxUpdateDelayMillis(5000)
                .build();

        // Added location settings request?
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                // All location settings are satisfied. The client can initialize
                // location requests here.
                // ...
                Log.i("LOCATION", "Successfully got location setting response");
            }
        });
        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                }
            }
        });

        Toast.makeText(mainActivity, "Starting Location Updates!", Toast.LENGTH_SHORT).show();

        Log.i("LOCATION", "Starting location requests.");

        // TODO: Maybe move this to on create?
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.i("LOCATION", "Null result...");
                    Toast.makeText(mainActivity, "LocationCallback NULL", Toast.LENGTH_SHORT).show();
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        // TODO add to concurrently linked queue and have a separate thread to append to file.
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        String locationString = String.format(Locale.US, "%s -- %s", latitude, longitude);
                        long timestamp = System.currentTimeMillis();
                        String locationRow = timestamp + "," + locationString;
                        // Only offer to queue if the start button has been pressed.
                        if (locationFileWritingThread != null) {
                            locationQueue.offer(locationRow);
                        }
                        locationIndicator.setText(locationString);
                        Log.i("LOCATION", "Got a location at " + latitude + " " + longitude);
                        Toast.makeText(mainActivity, "LocationCallback SUCCESS!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };

        // Just a check to make sure locationClient is defined.
        if (mFusedLocationClient == null) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.i("LOCATION", "Could not get permission.");
                return;
            }
        }
        // Connection locationClient to locationRequest and callback.
        mFusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbPermissionReceiver);

        try {
            if (metadataWriter != null) {
                metadataWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startCameraService() {
        Intent intent = new Intent(this, cameraService.class);
        intent.putExtra("startTime", startTimestamp);
        startService(intent);
    }

    private void stopCameraService() {
        Intent intent = new Intent(this, cameraService.class);
        stopService(intent);
    }
    /*
    private void initializeGimbal() {
        gimbal = DJISDKManager.getInstance().getProduct().getGimbal();
        if (gimbal != null) {
            gimbal.setStateCallback(new GimbalState.Callback() {
                @Override
                public void onUpdate(@NonNull GimbalState gimbalState) {
                    // Get the gimbal attitude
                    Attitude attitude = gimbalState.getAttitudeInDegrees();

                    float pitch = attitude.getPitch();
                    float roll = attitude.getRoll();
                    float yaw = attitude.getYaw();

                    // Log the angles
                    Log.d("GimbalAngles", "Pitch: " + pitch + " Roll: " + roll + " Yaw: " + yaw);
                }
            });
        } else {
            Log.e("DJI SDK", "Gimbal is null, unable to initialize");
        }
    }

     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        /*
        // Initialize the DJI SDK
        DJISDKManager.getInstance().registerApp(this, new DJISDKManager.SDKManagerCallback() {
            @Override
            public void onRegister(DJIError djiError) {
                Log.i("DJI REGISTER", djiError.toString() + "\n" + djiError.getDescription());
                DJISDKManager.getInstance().startConnectionToProduct();

            }

            @Override
            public void onProductDisconnect() {
                Log.d("DJI SDK", "Product Disconnected");
            }

            @Override
            public void onProductConnect(BaseProduct baseProduct) {
                Log.d("DJI SDK", "Product Connected");
                initializeGimbal();
            }

            @Override
            public void onProductChanged(BaseProduct baseProduct) {

            }

            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent baseComponent, BaseComponent baseComponent1) {

            }

            @Override
            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

            }

            @Override
            public void onDatabaseDownloadProgress(long l, long l1) {

            }
        });

         */

        textureViewFront = findViewById(R.id.textureViewFront);
        textureViewRear = findViewById(R.id.textureViewRear);
        packageContext = this;

        /*
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
        }

        */



        // Write logs to log file
        // TODO Uncomment if you need to debug serial connection code.
        /*
        try {
            // Define the log file
            String filename = "zzz_logcat_" + System.currentTimeMillis() + ".txt";
            File outputFile = new File(getExternalFilesDir(null), filename);

            // Start the logcat process
            Process process = Runtime.getRuntime().exec("logcat -f " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        */

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        mainActivity = this;
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        //mFusedLocationClient.setMockMode(true);

        File file = new File(getExternalFilesDir(null), "file.csv");
        Log.i("filepath!", "" + file.getParent());

        restartCounter = 0;
        logging = false;
        Log.i("date", "" + new Date().getTime());

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, filter);

        // Initialize all sensors for logging.
        mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor != null) {
            StringBuilder sensorInfo = new StringBuilder();
            sensorInfo.append("Sensor Name: ").append(lightSensor.getName()).append("\n");
            sensorInfo.append("Vendor: ").append(lightSensor.getVendor()).append("\n");
            sensorInfo.append("Version: ").append(lightSensor.getVersion()).append("\n");
            sensorInfo.append("Maximum Range: ").append(lightSensor.getMaximumRange()).append("\n");
            sensorInfo.append("Resolution: ").append(lightSensor.getResolution()).append("\n");
            sensorInfo.append("Power: ").append(lightSensor.getPower()).append("\n");
            sensorInfo.append("Min Delay: ").append(lightSensor.getMinDelay()).append(" µs\n");

            // Log the sensor information
            Log.i("SENSORINFO", sensorInfo.toString());
        }
        lightSensorLogger = new SensorLogger(
                mySensorManager, lightSensor, "light");
        rotationSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        rotationSensorLogger = new SensorLogger(
                mySensorManager, rotationSensor, "rotation");
        accelerometer = mySensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        accelerometerLogger = new SensorLogger(
                mySensorManager, accelerometer, "accelerometer");
        gyroscope = mySensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        gyroscopeLogger = new SensorLogger(
                mySensorManager, gyroscope, "gyroscope");
        magnetometer = mySensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        magnetometerLogger = new SensorLogger(
                mySensorManager, magnetometer, "magnetometer");

        textview = findViewById(R.id.indicator);
        locationIndicator = findViewById(R.id.location);
        rotationIndicator = findViewById(R.id.rotation);
        generalIndicator = findViewById(R.id.general_indicator);
        noteInput = findViewById(R.id.plain_text_input);

        startButton = findViewById(R.id.start);
        stopButton = findViewById(R.id.stop);
        verticalCalibrateButton = findViewById(R.id.vertical_calibrate);
        horizontalCalibrateButton = findViewById(R.id.horizontal_calibrate);
        logNoteButton = findViewById(R.id.logNote);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        horizontalCalibrateButton.setEnabled(false);
        verticalCalibrateButton.setEnabled(false);
        logNoteButton.setEnabled(false);

        // Get last known location as a baseline.
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        startLocationUpdates();


        // TODO just save the start and stop time of calibration and use this to grab values from respective files.
        horizontalCalibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                long timestamp = System.currentTimeMillis();
                String row = timestamp + ",STARTED HORIZONTAL CALIBRATION";
                notesQueue.offer(row);
                horizontalCalibrateButton.setEnabled(false);
                new CountDownTimer(3000, 1000) {

                    public void onTick(long millisUntilFinished) {
                        Log.i("TICK", "TOCK");
                    }

                    public void onFinish() {
                        long timestamp = System.currentTimeMillis();
                        String row = timestamp + ",ENDED HORIZONTAL CALIBRATION";
                        notesQueue.offer(row);
                        verticalCalibrateButton.setEnabled(true);
                    }
                }.start();
            }
        });
        verticalCalibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                long timestamp = System.currentTimeMillis();
                String row = timestamp + ",STARTED VERTICAL CALIBRATION";
                notesQueue.offer(row);
                verticalCalibrateButton.setEnabled(false);
                new CountDownTimer(3000, 1000) {
                    public void onTick(long millisUntilFinished) {
                    }

                    public void onFinish() {
                        long timestamp = System.currentTimeMillis();
                        String row = timestamp + ",ENDED VERTICAL CALIBRATION";
                        notesQueue.offer(row);
                    }
                }.start();
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Clear buffer immediately to throw away any content that may have been stored prior to start.
                externalLightSensorDataQueueSerial.clear();

                Date currentTime = new Date();
                startTimestamp = new SimpleDateFormat(
                        "yyyy-MM-dd_hh-mm-ss").format(currentTime);
                String noteValue = noteInput.getText().toString();
                if (noteValue.length() >= 1) {
                    noteValue = noteValue + "_";
                }
                startTimestamp = noteValue + startTimestamp;

                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                horizontalCalibrateButton.setEnabled(true);
                logNoteButton.setEnabled(true);

                startCameraRecording(startTimestamp);
                //startCameraService();


                serialLogging = true;
                Log.i("Start", "start button was pressed!");

                String locationFilename = getExternalFilesDir(null) + "/" + startTimestamp + "_location.csv";
                String notesFilename = getExternalFilesDir(null) + "/" + startTimestamp + "_notes.csv";
                String externalLightSensorFilename = getExternalFilesDir(null) + "/" + startTimestamp + "_external_light_sensor.csv";
                locationOutputFile = new File(locationFilename);
                externalLightSensorOutputFile = new File(externalLightSensorFilename);
                notesOutputFile = new File(notesFilename);
                lightSensorLogger.register(startTimestamp);
                rotationSensorLogger.register(startTimestamp);
                accelerometerLogger.register(startTimestamp);
                gyroscopeLogger.register(startTimestamp);
                magnetometerLogger.register(startTimestamp);
                restartCounter++;

                // For logging to location file
                if (!isFileWritingThreadRunning(notesFileWritingThread)) {
                    notesFileWritingThread = startFileWritingThread(
                            notesFileWritingThread, notesQueue,
                            notesOutputFile, "notesThread" + restartCounter);
                }

                // For logging notes file.
                if (!isFileWritingThreadRunning(locationFileWritingThread)) {
                    locationFileWritingThread = startFileWritingThread(
                            locationFileWritingThread, locationQueue,
                            locationOutputFile, "locationThread" + restartCounter);
                }

                // For logging any data from an embedded system connected over serial.
                if (!isFileWritingThreadRunning(externalLightSensorFileWritingThread)) {
                    externalLightSensorFileWritingThread = startFileWritingThread(
                            externalLightSensorFileWritingThread, externalLightSensorDataQueueSerial,
                            externalLightSensorOutputFile, "externalLightSensorThread" + restartCounter);
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                horizontalCalibrateButton.setEnabled(false);
                verticalCalibrateButton.setEnabled(false);
                logNoteButton.setEnabled(false);
                serialLogging = false;

                stopCameraRecording();
                //stopCameraService();

                locationFileWritingThread = stopFileWritingThread(locationFileWritingThread);
                locationQueue.clear(); // Clear the data queue
                notesFileWritingThread = stopFileWritingThread(notesFileWritingThread);
                notesQueue.clear(); // Clear the data queue
                externalLightSensorFileWritingThread = stopFileWritingThread(externalLightSensorFileWritingThread);
                externalLightSensorDataQueueSerial.clear();

                lightSensorLogger.close();
                rotationSensorLogger.close();
                accelerometerLogger.close();
                gyroscopeLogger.close();
                magnetometerLogger.close();

            }
        });
        logNoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //noteColumnValue = noteInput.getText().toString();
                String noteValue = noteInput.getText().toString();
                long timestamp = System.currentTimeMillis();
                String row = timestamp + "," + noteValue;
                // only offer to queue if start button has been pressed.
                if (notesFileWritingThread != null) {
                    notesQueue.offer(row);
                }
            }
        });
    }

    private void startCameraRecording(String startTimestamp) {
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

            setUpMediaRecorderFront(videoSizeFront, startTimestamp);
            setUpMediaRecorderRear(videoSizeRear, startTimestamp);

            metadataWriter = new FileWriter(new File(getExternalFilesDir(null), startTimestamp + "_camera_metadata.csv"));
            metadataWriter.write("Timestamp,Camera,Exposure Time,ISO,Sensitivity\n");


            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            manager.openCamera(cameraIdFront, stateCallbackFront, null);
            manager.openCamera(cameraIdRear, stateCallbackRear, null);

        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    private void stopCameraRecording() {
        try {
            // Stop and release the front media recorder
            if (mediaRecorderFront != null) {
                mediaRecorderFront.stop();
                mediaRecorderFront.reset();
                mediaRecorderFront.release();
                mediaRecorderFront = null;
            }

            // Stop and release the rear media recorder
            if (mediaRecorderRear != null) {
                mediaRecorderRear.stop();
                mediaRecorderRear.reset();
                mediaRecorderRear.release();
                mediaRecorderRear = null;
            }

            // Close the front camera device
            if (cameraDeviceFront != null) {
                cameraDeviceFront.close();
                cameraDeviceFront = null;
            }

            // Close the rear camera device
            if (cameraDeviceRear != null) {
                cameraDeviceRear.close();
                cameraDeviceRear = null;
            }

            // Close the metadata writer
            if (metadataWriter != null) {
                metadataWriter.close();
                metadataWriter = null;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // Handle case where mediaRecorder.stop() is called in an invalid state
            e.printStackTrace();
        }
    }


    private void setUpMediaRecorderFront(Size videoSize, String startTimestamp) throws IOException {
        mediaRecorderFront = new MediaRecorder();
        mediaRecorderFront.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorderFront.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        String outputPath = new File(getExternalFilesDir(null), startTimestamp + "_front_camera.mp4").getAbsolutePath();
        Log.i("video output path", outputPath);
        mediaRecorderFront.setOutputFile(outputPath);
        mediaRecorderFront.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorderFront.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorderFront.setVideoFrameRate(30);
        mediaRecorderFront.prepare();

    }

    private void setUpMediaRecorderRear(Size videoSize, String startTimestamp) throws IOException {
        mediaRecorderRear = new MediaRecorder();
        mediaRecorderRear.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorderRear.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        String outputPath = new File(getExternalFilesDir(null), startTimestamp + "_rear_camera.mp4").getAbsolutePath();
        Log.i("video output path", outputPath);
        mediaRecorderRear.setOutputFile(outputPath);
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
            Surface textureSurface = new Surface(textureViewFront.getSurfaceTexture());
            Surface recorderSurface = mediaRecorderFront.getSurface();

            final CaptureRequest.Builder captureBuilder = cameraDeviceFront.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureBuilder.addTarget(textureSurface);
            captureBuilder.addTarget(recorderSurface);

            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 200);
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 33320700L);
            // Disable auto-exposure
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            // Optionally, you can also disable auto white balance
            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);



            cameraDeviceFront.createCaptureSession(Arrays.asList(textureSurface, recorderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSessionFront = session;
                    try {
                        cameraCaptureSessionFront.setRepeatingRequest(captureBuilder.build(), captureCallback, null);
                        mediaRecorderFront.start();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSessionRear() {
        try {
            Surface textureSurface = new Surface(textureViewRear.getSurfaceTexture());
            Surface recorderSurface = mediaRecorderRear.getSurface();

            final CaptureRequest.Builder captureBuilder = cameraDeviceRear.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureBuilder.addTarget(textureSurface);
            captureBuilder.addTarget(recorderSurface);

            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 200);
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 33320700L);
            // Disable auto-exposure
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            // Optionally, you can also disable auto white balance
            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);


            cameraDeviceRear.createCaptureSession(Arrays.asList(textureSurface, recorderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSessionRear = session;
                    try {
                        cameraCaptureSessionRear.setRepeatingRequest(captureBuilder.build(), captureCallback, null);
                        mediaRecorderRear.start();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, null);
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
                Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);

                if (metadataWriter != null) {
                    metadataWriter.write(timestamp + "," + cameraId + "," + exposureTime + "," + iso + "," + "\n");
                    metadataWriter.flush();
                } else {
                    metadataWriter = new FileWriter(new File(getExternalFilesDir(null), startTimestamp + "_camera_metadata.csv"));
                }
                // Log the exposure values
                Log.i("ExposureLog", cameraId + " Exposure Time: " + exposureTime + " ns, ISO: " + iso);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };



    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
    }

    private void writeLineToFile(String line, File outputFile) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile, true))) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            // Handle IOException
        }
    }

    private synchronized boolean isFileWritingThreadRunning(Thread fileWritingThread) {
        return fileWritingThread != null && fileWritingThread.isAlive();
    }

    // Thread for writing bluetooth data only.
    private synchronized Thread startFileWritingThread(Thread thread,
                                                       ConcurrentLinkedQueue<String> queue,
                                                       File outputFile,
                                                       String threadName) {
        // if thread is already running just return it.
        if (isFileWritingThreadRunning(thread)) {
            return thread;
            //return; // The thread is already running
        }
        keepRunning = true;
        Log.i("STARTING THREAD" , threadName);

        thread = new Thread(() -> {
            while (keepRunning) {
                while (!queue.isEmpty()) {
                    String polledValue = queue.poll();
                    //Log.i("INSIDE THREAD!", Thread.currentThread().getName() + " Writing: " + polledValue);
                    writeLineToFile(polledValue, outputFile);
                }
                // Optional: Sleep a bit if queue is empty to reduce CPU usage
                try {
                    Thread.sleep(10); // Sleep for 10 milliseconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, threadName);
        thread.start();
        return thread;
    }

    private synchronized Thread stopFileWritingThread(Thread thread) {
        if (!isFileWritingThreadRunning(thread)) {
            return null; // The thread is not running
        }
        keepRunning = false;
        thread.interrupt();
        try {
            thread.join(); // Wait for the thread to finish
            Log.i("STOP THREAD", "thread joined.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.i("STOP THREAD", "Could not join thread.");
        }
        // only necessary if returns void using global thread variable.
        // thread = null; // Clear the thread reference
        return null;
    }


    // Receiver for a connected USB serial device.
    public static class UsbSerialReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(action)) {
                // Routine executed when attaching a USB device.
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // call method to set up device communication
                    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                    for (UsbDevice d : deviceList.values()) {
                        int vendorId = d.getVendorId();
                        int productId = d.getProductId();
                        // TODO if you do not add these to the device_filter.xml then you cannot receive anything!
                        Log.i("USB_DEVICE_INFO", "VID: " + vendorId + " PID: " + productId);
                    }
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
                    usbManager.requestPermission(device, permissionIntent);
                }
            } else if ("android.hardware.usb.action.USB_DEVICE_DETACHED".equals(action)) {
                // Routine executed when detaching the USB device.
                UsbDevice detachedDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (serialPort != null) {
                    serialPort.close();
                }
                // If logging was still left on before USB unplugged, stop it by programmatically pressing the button.
                //serialLoggingButton.setEnabled(false);
            }
        }
    }

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] arg0) {
            // Convert bytes to string
            if (serialLogging) {

                String data = new String(arg0, StandardCharsets.UTF_8);
                Log.i("SerialData", data);

                String[] parts = data.trim().split("\\s+"); // Split the data by whitespace

                int sensorId;

                try {
                    sensorId = Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    // Handle invalid number format
                    return;
                }

                // Only
                Log.i("parts length is: ", "" + parts.length);
                if (parts.length >= 5 && sensorId == 1) {
                    Log.i("Writing parts with lux of: ", "" + parts[4]);
                    long timestamp = System.currentTimeMillis();
                    // Replace the sensor ID with a timestamp and create a CSV row.
                    parts[0] = timestamp + "";
                    String row = String.join(", ", parts);
                    Log.i("SerialRow", row);
                    externalLightSensorDataQueueSerial.offer(row);
                    // Update UI with external light sensor reading.
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Update UI with data
                            rotationIndicator.setText(parts[4] + "");
                        }
                    });

                } else {
                    // not a sensor reading.
                    Log.i("Serial error", "Serial received something unexpected! Check serial processing code or how data is sent from embedded system.");
                }
            }
        }
    };



    BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            // Permission granted - setup the device
                            Log.i("SERIAL", "Permission granted for device " + device);
                            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                            connection = usbManager.openDevice(device);
                            if (connection != null) {
                                serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                                if (serialPort != null) {
                                    if (serialPort.open()) {
                                        // Set Serial Connection Parameters.
                                        serialPort.setBaudRate(115200);
                                        serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                                        serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                                        serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                                        serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                                        serialPort.read(mCallback);
                                        //serialLoggingButton.setEnabled(true);
                                    }
                                }
                            } else {
                                Log.i("SERIAL", "SERIAL CONNECTION IS NULL...");
                            }
                        }
                    } else {
                        // Permission denied
                        Log.i("SERIAL!", "permission defied for device: " + device);
                    }
                }
            }
        }
    };

    private void stopSerialConnection() {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.close();
            serialPort = null;
            Log.i("SERIAL", "Serial connection stopped");
        }
    }

    private void startSerialConnection(UsbDevice device) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection != null) {
            serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
            if (serialPort != null && serialPort.open()) {
                serialPort.setBaudRate(115200);
                serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serialPort.read(mCallback);
                Log.i("SERIAL", "Serial connection started");
            } else {
                Log.i("SERIAL", "Failed to open serial port");
            }
        } else {
            Log.i("SERIAL", "Failed to open USB device connection");
        }
    }
}