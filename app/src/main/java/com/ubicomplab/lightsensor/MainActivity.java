package com.ubicomplab.lightsensor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

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
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Location;
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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

// requires 'https://jitpack.io' in settings.gradle.
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

public class MainActivity extends AppCompatActivity {
    // Variables for serial connection.
    public static final String ACTION_USB_PERMISSION = "com.ubicomplab.lightsensor.USB_PERMISSION";
    static UsbSerialDevice serialPort;
    UsbDeviceConnection connection;
    UsbDevice usbDevice;
    //static Button serialLoggingButton;
    boolean serialLogging;

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
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbPermissionReceiver);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                // Clear buffer immediately to throw away any content that may have been stored prior to start.
                externalLightSensorDataQueueSerial.clear();

                Date currentTime = new Date();
                startTimestamp = new SimpleDateFormat(
                        "yyyy-MM-dd_hh-mm-ss").format(currentTime);
                String noteValue = noteInput.getText().toString();
                startTimestamp = noteValue + "_" + startTimestamp;

                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                horizontalCalibrateButton.setEnabled(true);
                logNoteButton.setEnabled(true);

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
            public void onClick(View view){
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                horizontalCalibrateButton.setEnabled(false);
                verticalCalibrateButton.setEnabled(false);
                logNoteButton.setEnabled(false);
                serialLogging = false;

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