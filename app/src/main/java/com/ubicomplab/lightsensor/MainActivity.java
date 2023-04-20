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
//import android.location.LocationRequest;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Looper;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    float x, y, z, w, accuracy;
    double roll_x, pitch_y, yaw_z;
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
    private ArrayList<String> data;
    boolean logging;
    String startTimestamp;
    private Activity mainActivity;
    String rotationData = "";
    float lightSensorValue;
    CountDownTimer countdownTimer;
    private String noteColumnValue;
    private String accelReading;

    float horizontal_x_avg = 0;
    float horizontal_y_avg = 0;
    float horizontal_z_avg = 0;
    float horizontal_w_avg = 0;
    double horizontal_roll_x_avg = 0;
    double horizontal_pitch_y_avg = 0;
    double horizontal_yaw_z_avg = 0;
    float horizontal_accuracy_avg = 0;

    float vertical_x_avg = 0;
    float vertical_y_avg = 0;
    float vertical_z_avg = 0;
    float vertical_w_avg = 0;
    double vertical_roll_x_avg = 0;
    double vertical_pitch_y_avg = 0;
    double vertical_yaw_z_avg = 0;
    float vertical_accuracy_avg = 0;

    //private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor accelerometer;

    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private FusedLocationProviderClient mFusedLocationClient;
    private Location mcurrentLocation;

    // Requesting permissions
    private static final int REQUEST_PERMISSIONS = 1;
    private boolean permissionAccepted = false;
    private String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_NETWORK_STATE};

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
        // Set up the reocuring request.
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
                    /*
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(MainActivity.this,
                                REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                    }
                    */
                }
            }
        });

        //locationRequest = LocationRequest.create();
        //locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        //locationRequest.setInterval(10 * 1000);
        //locationRequest.setFastestInterval(2 * 1000);
        //locationRequest.setMaxWaitTime(10 * 1000);
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
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        locationIndicator.setText(String.format(Locale.US, "%s -- %s", latitude, longitude));
                        Log.i("LOCATION", "Got a location at " + latitude + " " + longitude);
                        Toast.makeText(mainActivity, "LocationCallback SUCCESS!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };

        // Just a check to make sure locationClient is defined.
        if (mFusedLocationClient == null) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
                Looper.getMainLooper());//Looper.myLooper());
    }

    public void logDataRow() {
        long timestamp = new Date().getTime();
        String row = timestamp + "," + lightSensorValue + "," + latitude + "," + longitude + "," + rotationData + "," + noteColumnValue + "\n";
        data.add(row);
        // Reset the note column to null after you log it once.
        noteColumnValue = "-";
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

        logging = false;
        Log.i("date", "" + new Date().getTime());

        textview = findViewById(R.id.indicator);
        locationIndicator = findViewById(R.id.location);
        rotationIndicator = findViewById(R.id.rotation);
        generalIndicator = findViewById(R.id.general_indicator);
        noteInput = findViewById(R.id.plain_text_input);
        //locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        startButton = findViewById(R.id.start);
        stopButton = findViewById(R.id.stop);
        verticalCalibrateButton = findViewById(R.id.vertical_calibrate);
        horizontalCalibrateButton = findViewById(R.id.horizontal_calibrate);
        logNoteButton = findViewById(R.id.logNote);
        //saveButton = findViewById(R.id.save);
        startButton.setEnabled(false);
        stopButton.setEnabled(false);
        verticalCalibrateButton.setEnabled(false);
        logNoteButton.setEnabled(false);

        // Get last known location as a baseline.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        /*
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            locationIndicator.setText("Last known location is NULL...");
                        } else {
                            locationIndicator.setText("Last known location is " + location.getLatitude() + ", " + location.getLongitude());
                        }
                        mcurrentLocation = location;
                    }
                });
        */
        startLocationUpdates();

        /*
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled = false;
        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
        }

        boolean network_enabled = false;
        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
        }
        Log.i("LOCATION", "GPS enabled? " + gps_enabled + " Network enabled? " + network_enabled);
         */

        /*
        Location mockLocation = new Location(LocationManager.GPS_PROVIDER);
        mockLocation.setLatitude(1.2797677);
        mockLocation.setLongitude(103.8459285);
        mockLocation.setAltitude(0);
        mockLocation.setTime(System.currentTimeMillis());
        mockLocation.setAccuracy(1);
        mFusedLocationClient.setMockLocation(mockLocation);
        Log.i("mocked location", "" + mockLocation.toString());
        */

        horizontalCalibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new CountDownTimer(3000, 1000) {
                    float x_avg = 0;
                    float y_avg = 0;
                    float z_avg = 0;
                    float w_avg = 0;
                    double roll_x_avg = 0;
                    double pitch_y_avg = 0;
                    double yaw_z_avg = 0;
                    float accuracy_avg = 0;
                    float count = 0;

                    public void onTick(long millisUntilFinished) {
                        generalIndicator.setText("seconds remaining: " + millisUntilFinished / 1000);

                        x_avg += x;
                        y_avg += y;
                        z_avg += z;
                        w_avg += w;
                        roll_x_avg += roll_x;
                        pitch_y_avg += pitch_y;
                        yaw_z_avg += yaw_z;
                        accuracy_avg += accuracy;
                        count += 1;
                    }

                    public void onFinish() {
                        generalIndicator.setText("Done with Horizontal Calibration");
                        horizontalCalibrateButton.setEnabled(false);
                        verticalCalibrateButton.setEnabled(true);
                        horizontal_x_avg = x_avg / count;
                        horizontal_y_avg = y_avg / count;
                        horizontal_z_avg = z_avg / count;
                        horizontal_w_avg = w_avg / count;
                        horizontal_roll_x_avg = roll_x_avg / count;
                        horizontal_pitch_y_avg = pitch_y_avg / count;
                        horizontal_yaw_z_avg = yaw_z_avg / count;
                        horizontal_accuracy_avg = accuracy_avg / count;
                    }
                }.start();
            }
        });
        verticalCalibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                new CountDownTimer(3000, 1000) {
                    float x_avg = 0;
                    float y_avg = 0;
                    float z_avg = 0;
                    float w_avg = 0;
                    double roll_x_avg = 0;
                    double pitch_y_avg = 0;
                    double yaw_z_avg = 0;
                    float accuracy_avg = 0;
                    float count = 0;
                    public void onTick(long millisUntilFinished) {
                        generalIndicator.setText("seconds remaining: " + millisUntilFinished / 1000);
                        x_avg += x;
                        y_avg += y;
                        z_avg += z;
                        w_avg += w;
                        roll_x_avg += roll_x;
                        pitch_y_avg += pitch_y;
                        yaw_z_avg += yaw_z;
                        accuracy_avg += accuracy;
                        count += 1;
                    }

                    public void onFinish() {
                        generalIndicator.setText("Done with Horizontal Calibration");
                        startButton.setEnabled(true);
                        verticalCalibrateButton.setEnabled(false);
                        vertical_x_avg = x_avg / count;
                        vertical_y_avg = y_avg / count;
                        vertical_z_avg = z_avg / count;
                        vertical_w_avg = w_avg / count;
                        vertical_roll_x_avg = roll_x_avg / count;
                        vertical_pitch_y_avg = pitch_y_avg / count;
                        vertical_yaw_z_avg = yaw_z_avg / count;
                        vertical_accuracy_avg = accuracy_avg / count;
                    }
                }.start();
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                noteColumnValue = "-";
                data = new ArrayList<>();
                Date currentTime = new Date();
                startTimestamp = new SimpleDateFormat(
                        "yyyy-MM-dd_hh-mm-ss").format(currentTime);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                logNoteButton.setEnabled(true);
                //saveButton.setEnabled(false);

                // Log once per second.
                countdownTimer = new CountDownTimer(1000 * 60 * 60, 1000) {
                    public void onTick(long millisUntilFinished) {
                        logDataRow();
                    }

                    public void onFinish() {
                    }
                };
                countdownTimer.start();

                // Start logging every time a new light sensor reading is read.
                logging = true;
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                logging = false;
                countdownTimer.cancel();
                writeToCsv(data, startTimestamp);
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                logNoteButton.setEnabled(false);
                //saveButton.setEnabled(true);
            }
        });
        logNoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                noteColumnValue = noteInput.getText().toString();
            }
        });


        //getLocation();

        SensorManager mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        rotationSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationSensor != null) {
            textview.setText("Sensor.TYPE_ROTATION_VECTOR Available");
            mySensorManager.registerListener(
                    rotationSensorListener,
                    rotationSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);

        } else {
            textview.setText("Sensor.TYPE_ROTATION_VECTOR NOT Available");
        }

        Sensor lightSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor != null) {
            textview.setText("Sensor.TYPE_LIGHT Available");
            mySensorManager.registerListener(
                    lightSensorListener,
                    lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);

        } else {
            textview.setText("Sensor.TYPE_LIGHT NOT Available");
        }
        /*
        accelerometer = mySensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            textview.setText("Sensor.TYPE_ACCELEROMETER Available");
            mySensorManager.registerListener(
                    accelerometerListener,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);

        } else {
            textview.setText("Sensor.TYPE_ACCELEROMETER NOT Available");
        }
        */

    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
    }

    private final SensorEventListener lightSensorListener
            = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                textview.setText("LIGHT: " + event.values[0]);
                long timeInMillis = (new Date()).getTime()
                        + (event.timestamp - System.nanoTime()) / 1000000L;
                lightSensorValue = event.values[0];
                if (logging) {
                    //logDataRow();
                }
            }
        }

    };

    private final SensorEventListener accelerometerListener
            = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accelReading = event.values[0] + "," +  event.values[1] + "," +  event.values[2];
                //generalIndicator.setText(accelReading);
            }
        }

    };

    private double[] quaternionToEuler(float x, float y, float z, float w) {
        double t0 = 2.0 * ((w * x) + (y * z));
        double t1 = 1.0 - (2.0 * ((x * x) + (y * y)));
        double roll_x = Math.atan2(t0, t1);

        double t2 = 2.0 * ((w * y) - (z * x));
        if (t2 > 1) {
            t2 = 1;
        }
        if (t2 < -1) {
            t2 = -1;
        }
        double pitch_y = Math.asin(t2);

        double t3 = 2.0 * ((w * z) + (x * y));
        double t4 = 1.0 - (2.0 * ((y * y) + (z * z)));
        double yaw_z = Math.atan2(t3, t4);

        double[] q = new double[3];
        q[0] = roll_x * (180 / Math.PI);
        q[1] = pitch_y * (180 / Math.PI);
        q[2] = yaw_z * (180 / Math.PI);
        return q;
    }

    private final SensorEventListener rotationSensorListener
            = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                //rotationIndicator.setText("Rotation vector: " + event.values[0]);
                x = event.values[0];
                y = event.values[1];
                z = event.values[2];
                w = event.values[3];
                accuracy = event.values[3]; // accuracy estimate in radians
                double[] angles = quaternionToEuler(x, y, z, w);
                roll_x = angles[0];
                pitch_y = angles[1];
                yaw_z = angles[2];

                double roll_x_norm = roll_x - horizontal_roll_x_avg;
                double pitch_y_norm = pitch_y - horizontal_pitch_y_avg;
                double yaw_z_norm = yaw_z - horizontal_yaw_z_avg;

                rotationIndicator.setText("Rotation vector: " + roll_x_norm);
                rotationData = roll_x_norm + "," + pitch_y_norm + "," + yaw_z_norm + "," + accuracy;
                //Log.i("rotation", "X: " + roll_x_norm + " Y: " + pitch_y_norm + " Z: " + yaw_z_norm + " accuracy: " + accuracy);
                /*
                float x_normalized = x - horizontal_x_avg;
                float y_normalized = y - horizontal_y_avg;
                float z_normalized = z - horizontal_z_avg;
                float w_normalized = w - horizontal_w_avg;
                rotationData = x_normalized + "," + y_normalized + "," + z_normalized + "," + w_normalized + "," + accuracy;
                Log.i("rotation", "X: " + x_normalized + " Y: " + y_normalized + " Z: " + z_normalized + " w: " + w_normalized + " accuracy: " + accuracy);
                */


            }
        }

    };


    private void writeToCsv(ArrayList<String> data, String starttime) {
        File file = new File(getExternalFilesDir(null), "file.csv");
        Log.i("path", "" + file.getParent());
        // Make application data directory if it is not there yet.
        File folder = new File(file.getParent());
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }
        if (!success) {
            Log.i(
                    "Uh oh...",
                    "Could not create folder in local storage. Please check app permissions.");
        // Otherwise write to it.
        } else {
            try {
                File out = new File(getExternalFilesDir(null) + "/" + starttime + ".csv");
                String dir = out.getParent();
                Log.i("writing csv", dir);
                FileWriter writer = new FileWriter(out, false);
                for (int i = 0; i < data.size(); i++) {
                    writer.append("" + data.get(i) + ",");
                }
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*
    private String getLocation() {
        try {
            // Check the permissions before getting location.
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
                return "";
            }

            //gps_loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            //network_loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            //locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER, );


        } catch (Exception e) {
            e.printStackTrace();
        }

        if (gps_loc != null) {
            final_loc = gps_loc;
            latitude = final_loc.getLatitude();
            longitude = final_loc.getLongitude();
        }
        else if (network_loc != null) {
            final_loc = network_loc;
            latitude = final_loc.getLatitude();
            longitude = final_loc.getLongitude();
        }
        else {
            latitude = 0.0;
            longitude = 0.0;
        }

        locationIndicator.setText(latitude + ", " + longitude);
        return latitude + "," + longitude;
    }
    */
}