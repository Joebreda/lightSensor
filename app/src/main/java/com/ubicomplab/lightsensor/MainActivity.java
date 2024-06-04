package com.ubicomplab.lightsensor;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends AppCompatActivity {
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
    private Thread locationFileWritingThread = null;
    private Thread notesFileWritingThread = null;
    private File locationOutputFile;
    private File notesOutputFile;
    private int restartCounter;

    // Requesting permissions
    private static final int REQUEST_PERMISSIONS = 1;
    private boolean permissionAccepted = false;
    private String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_NETWORK_STATE};

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
                bufferedWriter.write(dataString.toString());
                bufferedWriter.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

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
                this.sensorManager.registerListener(this.sensorEventListener,
                        this.sensor, this.sensorManager.SENSOR_DELAY_NORMAL, sensorHandler);
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
                    this.bufferedWriter.close();
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        mainActivity = this;
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        //mFusedLocationClient.setMockMode(true);

        File file = new File(getExternalFilesDir(null), "file.csv");
        Log.i("filepath!", "" + file.getParent());

        restartCounter = 0;
        logging = false;
        Log.i("date", "" + new Date().getTime());

        // Initialize the IMU sensors.
        mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
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
            public void onClick(View view){
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
            public void onClick(View view){
                Date currentTime = new Date();
                startTimestamp = new SimpleDateFormat(
                        "yyyy-MM-dd_hh-mm-ss").format(currentTime);

                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                horizontalCalibrateButton.setEnabled(true);
                logNoteButton.setEnabled(true);

                String locationFilename = getExternalFilesDir(null) + "/" + startTimestamp + "_location.csv";
                String notesFilename = getExternalFilesDir(null) + "/" + startTimestamp + "_notes.csv";
                locationOutputFile = new File(locationFilename);
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
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                horizontalCalibrateButton.setEnabled(false);
                verticalCalibrateButton.setEnabled(false);
                logNoteButton.setEnabled(false);

                locationFileWritingThread = stopFileWritingThread(locationFileWritingThread);
                locationQueue.clear(); // Clear the data queue
                notesFileWritingThread = stopFileWritingThread(notesFileWritingThread);
                notesQueue.clear(); // Clear the data queue

                lightSensorLogger.close();
                rotationSensorLogger.close();
                accelerometerLogger.close();
                gyroscopeLogger.close();
                magnetometerLogger.close();

            }
        });
        logNoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
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

        thread = new Thread(() -> {
            while (keepRunning) {
                while (!queue.isEmpty()) {
                    String polledValue = queue.poll();
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
}