package com.microsoft.devicepolicies;


import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by huadi on 2015/3/22.
 */
public class WifiDetectService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener
{
    String TAG = "WifiDetectService";

    HashMap<LatLng, Float> companyLocations = new HashMap<LatLng, Float>();
    List<String> companyWifiNames = new ArrayList<String>();

    boolean isEntryCompanyLocation = true;
    boolean isConnectCompanyWifi = true;
    boolean isScanCompanyWifi = true;
    boolean cameraCurrentStatus = true;
    long cameraEnableTime = 0; //seconds

    DevicePolicyManager mDPM;
    ComponentName mDeviceAdminSample;
    BroadcastReceiver awaitIPAddress = null;

    private GoogleApiClient mGoogleApiClient;
    private boolean mResolvingError = false;

    private FusedLocationProviderApi fusedLocationProviderApi = LocationServices.FusedLocationApi;
    private LocationRequest mLocationRequest;
    private Location mCurrentLocation;


    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);
        isScanCompanyWifi = sharedPreferences.getBoolean("isScanCompanyWifi", true);
        Log.d(TAG, "isScanCompanyWifi : " + isScanCompanyWifi);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(TAG, "onStartCommand");

        InitSettings();

        // Connect the client.
        if (!mResolvingError)
        {
            Log.d(TAG, "GoogleApiClient Connect");
            mGoogleApiClient.connect(); // Connect the client.
        }
        else
        {
            Log.d(TAG, "GoogleApiClient mResolvingError");
        }

        mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDeviceAdminSample = new ComponentName(WifiDetectService.this, MyDeviceAdminReceiver.class);
        //mDeviceAdminSample = (ComponentName) intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN);
        if (!mDPM.isAdminActive(mDeviceAdminSample))
        {
            // try to become active – must happen here in this activity, to get result
            Log.d(TAG, "NO Admin Active");
        }
        else
        {
            // Already is a device administrator, can do security operations now.
            Log.d(TAG, "Admin Active");
            //is connect company wifi
//            IntentFilter filter = new IntentFilter();
//            filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
//            registerReceiver(receiver, filter);
            if(isScanCompanyWifi)
                SetCameraDisable(true);
            else
                SetCameraDisable(false);
        }

        final SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);

        new CountDownTimer(cameraEnableTime * 1000, 1000)
        {
            public void onTick(long millisUntilFinished)
            {
                LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    SetCameraDisable(true);
                sharedPreferences.edit().putLong("timerNow", millisUntilFinished).commit();
//                Log.d(TAG, "" + millisUntilFinished);
            }

            public void onFinish()
            {
                Log.d(TAG, "Timer finish");
                sharedPreferences.edit().putLong("timerNow", 0).commit();
                WifiDetectService.this.stopSelf();
            }
        }.start();


        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        SetCameraDisable(true);
        mGoogleApiClient.disconnect();
        //unregisterReceiver(receiver);

        //if camera is running, the admin policy cannot stop camera.
        //So, min apps (just like click Home button) that policy will stop camera run
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.d(TAG, "onBind");
        return null;
    }

    private void SetCameraDisable(boolean isToDisable)
    {
        mDPM.setCameraDisabled(mDeviceAdminSample, isToDisable);

//        if (!isToDisable && (cameraCurrentStatus != isToDisable))
//            Toast.makeText(WifiDetectService.this, "已可以開啟相機", Toast.LENGTH_LONG).show();
//        else if (isToDisable && (cameraCurrentStatus != isToDisable))
//            Toast.makeText(WifiDetectService.this, "相機功能已關閉", Toast.LENGTH_LONG).show();

        cameraCurrentStatus = isToDisable;
        SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);
        sharedPreferences.edit().putBoolean("isCameraDisable", isToDisable).commit();

        Log.d(TAG, "Camera Disabled = " + isToDisable);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION))
            {
                if (intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE) == SupplicantState.COMPLETED)
                {
                    //WiFi is associated
                    WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo != null) // Wifi info available (should be, we are associated)
                    {
                        if (wifiInfo.getIpAddress() != 0) // Lucky, already have an ip address. happens when a connection is complete
                        {
                            Log.d(TAG, "Already Connected " + wifiInfo.getSSID());

                            if (!isEntryCompanyLocation)
                            {
                                InitSettings();
                                isConnectCompanyWifi = false;
                                for (String wifiName : companyWifiNames)
                                    if (wifiInfo.getSSID().toLowerCase().equals("\"" + wifiName + "\""))
                                    {
                                        isConnectCompanyWifi = true;
                                    }
                                SetCameraDisable(isConnectCompanyWifi);
                                Log.d(TAG, "isConnectCompanyWifi : " + isConnectCompanyWifi);
                            }
                        }
                        else // No ip address yet, need to wait...
                        {
                            if (awaitIPAddress == null)
                            {
                                awaitIPAddress = new BroadcastReceiver()
                                {
                                    @Override
                                    public void onReceive(Context ctx, Intent in)
                                    {
                                        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                                        if (wifiInfo != null)
                                        {
                                            if (wifiInfo.getIpAddress() != 0)
                                            {
                                                Log.d(TAG, "Now Connected " + wifiInfo.getSSID());
                                                InitSettings();
                                                isConnectCompanyWifi = false;
                                                if (!isEntryCompanyLocation)
                                                {
                                                    for (String wifiName : companyWifiNames)
                                                        if (wifiInfo.getSSID().toLowerCase().equals("\"" + wifiName + "\""))
                                                        {
                                                            isConnectCompanyWifi = true;
                                                        }
                                                    SetCameraDisable(isConnectCompanyWifi);
                                                }
                                                Log.d(TAG, "isConnectCompanyWifi : " + isConnectCompanyWifi);
                                            }
                                        }
                                        else
                                        {
                                            Log.d(TAG, "NO WiFi");
                                            SetCameraDisable(true);

                                            ctx.unregisterReceiver(this);
                                            awaitIPAddress = null;
                                        }
                                    }
                                };
                                // register a new receiver for connectivity events(getting a new IP address)
                                context.registerReceiver(awaitIPAddress, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                            }
                        }
                    }
                }
                else // wifi connection not complete, release ip address receiver if registered
                {
                    if (awaitIPAddress != null)
                    {
                        Log.d(TAG, "wifi connection not complete");
                        SetCameraDisable(true);

                        context.unregisterReceiver(awaitIPAddress);
                        awaitIPAddress = null;
                    }
                }
            }
        }
    };


    @Override
    public void onConnected(Bundle bundle)
    {
        Log.d(TAG, "onConnected");
        Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (currentLocation != null)
        {
            mCurrentLocation = currentLocation;
            Log.d(TAG, String.valueOf(mCurrentLocation.getLatitude()) + ", " + String.valueOf(mCurrentLocation.getLongitude()));

            InitSettings();
            isEntryCompanyLocation = false;
            if (!isConnectCompanyWifi)
            {
                for (LatLng latlng : companyLocations.keySet())
                {
                    float[] results = new float[1];
                    Location.distanceBetween(latlng.latitude, latlng.longitude, mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), results);

                    if (results[0] <= companyLocations.get(latlng))
                        isEntryCompanyLocation = true;
                }
                SetCameraDisable(isEntryCompanyLocation);
            }
//            Log.d(TAG, companyWifiNames.get(0));
        }
        startLocationUpdates();
    }

    protected void startLocationUpdates()
    {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
//        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        Log.d(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult)
    {
        Log.d(TAG, "onConnectionFailed");
        if (mResolvingError)
        {
            Log.d(TAG, "Already attempting to resolve an error");
        }
        else if (connectionResult.hasResolution())
        {
            Log.d(TAG, "connectionResult.hasResolution()");
        }
        else
        {
            mResolvingError = true;
        }
    }

    @Override
    public void onLocationChanged(Location location)
    {
        mCurrentLocation = location;
        //Toast.makeText(getApplicationContext(), String.valueOf(mCurrentLocation.getLatitude()) + ", " + String.valueOf(mCurrentLocation.getLongitude()), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Location Changed " + String.valueOf(mCurrentLocation.getLatitude()) + ", " + String.valueOf(mCurrentLocation.getLongitude()));

        if (!isScanCompanyWifi)
        {
            InitSettings();
            isEntryCompanyLocation = false;
            for (LatLng latlng : companyLocations.keySet())
            {
                float[] results = new float[1];
                Location.distanceBetween(latlng.latitude, latlng.longitude, mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), results);

                if (results[0] <= companyLocations.get(latlng))
                    isEntryCompanyLocation = true;
            }
            SetCameraDisable(isEntryCompanyLocation);

            mGoogleApiClient.disconnect();
        }

    }

    void InitSettings()
    {
        AssetManager assetManager = getAssets();
        InputStream inputStream = null;

        String myStream;
        try
        {
            // 指定/assets/MyAssets.txt
            inputStream = assetManager.open("Settings.txt");

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bytes = new byte[4096];

            int len;
            while ((len = inputStream.read(bytes)) > 0)
            {
                byteArrayOutputStream.write(bytes, 0, len);
            }
            myStream = new String(byteArrayOutputStream.toByteArray(), "UTF8");
        }
        catch (IOException e)
        {
            e.printStackTrace();
            myStream = e.toString();
        }

        //Log.d(TAG, myStream);

        try
        {
            SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);

            JSONObject settings = new JSONObject(myStream).getJSONObject("Settings");
            JSONArray location = settings.getJSONArray("Location");
            JSONArray wifi = settings.getJSONArray("Wifi");
            String password = settings.getString("Password");
            String cameraEnableTimeString = settings.getString("CameraEnableTime");

            if(sharedPreferences.getLong("timerNow", 0) <= 0)
                cameraEnableTime = Long.parseLong(cameraEnableTimeString);
            else
                cameraEnableTime = (long)((float)sharedPreferences.getLong("timerNow", 0) / 1000f);

//            Log.d(TAG, "timerNow: " + sharedPreferences.getLong("timerNow", 0));
//            Log.d(TAG, "cameraEnableTime: " + cameraEnableTime);

            for (int i = 0; i < location.length(); i++)
            {
                String lat = location.getJSONObject(i).getString("Latitude");
                String lng = location.getJSONObject(i).getString("Longitude");
                String distance = location.getJSONObject(i).getString("Distance");

                companyLocations.put(new LatLng(Double.parseDouble(lat), Double.parseDouble(lng)), Float.parseFloat(distance));
            }

            for (int i = 0; i < wifi.length(); i++)
                companyWifiNames.add(wifi.getJSONObject(i).getString("SSID").toLowerCase());

            sharedPreferences.edit().putString("pwd", password).commit();
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }

    }


}
