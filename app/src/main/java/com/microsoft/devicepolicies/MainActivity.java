package com.microsoft.devicepolicies;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity
{
    String TAG = "MainActivity";
    public static final int REQUEST_ENABLE = 1001;
    public static final int REQUEST_GPS = 1002;

    Button btnOpenGPS;
    Handler btnGpsStatusHandler;

    TextView textCameraStatus;

    private CheckBoxPreference mDisableCameraCheckbox;
    DevicePolicyManager mDPM;
    ComponentName mDeviceAdminSample;

    List<String> companyWifiNames = new ArrayList<String>();
    WifiManager wifi;
    boolean isCurrentWifiOn = false;
    List<ScanResult> results;
    String showText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GetSettings();

        mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDeviceAdminSample = new ComponentName(this, MyDeviceAdminReceiver.class);

        if (!mDPM.isAdminActive(mDeviceAdminSample))
        {
            // try to become active – must happen here in this activity, to get result
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminSample);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "");
            startActivityForResult(intent, REQUEST_ENABLE);
        }
        else
        {
            // Already is a device administrator, can do security operations now.
            mDPM.setCameraDisabled(mDeviceAdminSample, true);
//            startService(new Intent(MainActivity.this, WifiDetectService.class));
        }

        textCameraStatus = (TextView)findViewById(R.id.textCameraStatus);
        if (isMyServiceRunning(WifiDetectService.class))
        {
            SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);
            boolean isCameraDisable = sharedPreferences.getBoolean("isCameraDisable", false);
            if(isCameraDisable)
                textCameraStatus.setText("您位於限制區域內，無法使用相機功能");
            else
                textCameraStatus.setText("您已經可以開啟相機");
        }

        btnOpenGPS = (Button) findViewById(R.id.btnOpenGPS);
        btnOpenGPS.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) //current GPS status disable
                {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("開啟裝置定位選項")
                            .setMessage("開啟相機需要開啟裝置GPS")
                            .setPositiveButton("確定", new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int id)
                                {
                                    startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_GPS);
                                }
                            })
                            .setNegativeButton("取消", null).show();
                }
                else if (!isMyServiceRunning(WifiDetectService.class))
                {
                    wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    isCurrentWifiOn = wifi.isWifiEnabled();

                    if (!isCurrentWifiOn)
                    {
                        wifi.setWifiEnabled(true);
                    }

                    registerReceiver(WifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                    wifi.startScan();

                }
                else
                    Log.d(TAG, "service is running");

            }
        });
    }

    private BroadcastReceiver WifiScanReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            results = wifi.getScanResults();
            if (!isCurrentWifiOn)
            {
                wifi.setWifiEnabled(false);
            }

            GetSettings();

            SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);
            sharedPreferences.edit().putBoolean("isScanCompanyWifi", false).commit();

            try
            {
                for (String ssid : companyWifiNames)
                    for (ScanResult result : results)
                    {
                        //Log.d(TAG, result.SSID.toLowerCase() + ", " + ssid.toLowerCase());
                        if (result.SSID.toLowerCase().contains(ssid.toLowerCase()))
                        {
                            Log.d(TAG, "Mach SSID, " + result.SSID.toLowerCase() + ", " + ssid.toLowerCase());
                            mDPM.setCameraDisabled(mDeviceAdminSample, true);
                            sharedPreferences.edit().putBoolean("isScanCompanyWifi", true).commit();
                        }
                    }
            }
            catch (Exception e)
            {
            }

            Intent wifiServiceIntent = new Intent(MainActivity.this, WifiDetectService.class);
            wifiServiceIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminSample);
            startService(wifiServiceIntent);

            btnGpsStatusHandler = new Handler();
            Runnable r = new Runnable()
            {
                @Override
                public void run()
                {
                    if (isMyServiceRunning(WifiDetectService.class))
                    {
                        SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);
                        boolean isCameraDisable = sharedPreferences.getBoolean("isCameraDisable", false);
                        if (isCameraDisable)
                        {
                            showText = "您位於限制區域內，無法使用相機功能";
                            textCameraStatus.setText(showText);
                        }
                        else
                        {
                            showText = "您已經可以開啟相機";
                            textCameraStatus.setText(showText);
                        }

//                                btnOpenGPS.setEnabled(false);
                        btnGpsStatusHandler.postDelayed(this, 200);
                    }
                    else
                    {
//                                btnOpenGPS.setEnabled(true);
                        textCameraStatus.setText("");
                        showText = "";
                    }
                }
            };
            btnGpsStatusHandler.postDelayed(r, 200);

            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);

            if(showText.length() > 0)
                Toast.makeText(MainActivity.this, showText, Toast.LENGTH_SHORT).show();

            MainActivity.this.unregisterReceiver(this);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode)
        {
            case REQUEST_ENABLE:
                if (resultCode == Activity.RESULT_OK)
                {
                    Log.d(TAG, "Activity.RESULT_OK");
                    // Has become the device administrator.
                    mDPM.setCameraDisabled(mDeviceAdminSample, true);
//                    Intent intent = new Intent(MainActivity.this, WifiDetectService.class);
//                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminSample);
//                    startService(intent);
                }
                else
                {
                    //Canceled or failed.
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminSample);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "");
                    startActivityForResult(intent, REQUEST_ENABLE);
                }
                break;

            case REQUEST_GPS:
                //press < will back to Activity
                break;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            new AlertDialog.Builder(this)
                    .setTitle("請輸入密碼以解除安裝")
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setView(input)
                    .setPositiveButton("確定", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int id)
                        {
                            SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);
                            if (input.getText().toString().equals(sharedPreferences.getString("pwd", "")))
                            {
                                Log.d(TAG, "uninstall");
                                mDPM.removeActiveAdmin(mDeviceAdminSample);
//                                stopService(new Intent(MainActivity.this, WifiDetectService.class));

                                new CountDownTimer(500, 100)
                                {
                                    public void onTick(long millisUntilFinished)
                                    {
                                        //wait for service stop
                                    }

                                    public void onFinish()
                                    {
                                        //uninstall
                                        Intent intent = new Intent(Intent.ACTION_DELETE);
                                        intent.setData(Uri.parse("package:" + MainActivity.this.getPackageName()));
                                        startActivity(intent);
                                    }
                                }.start();

                            }
                            else
                                Toast.makeText(MainActivity.this, "Password Error", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("取消", null).show();
            //return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private boolean isMyServiceRunning(Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (serviceClass.getName().equals(service.service.getClassName()))
            {
                return true;
            }
        }
        return false;
    }

    private void GetSettings()
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

        try
        {
            JSONObject settings = new JSONObject(myStream).getJSONObject("Settings");
            JSONArray wifi = settings.getJSONArray("Wifi");
            String password = settings.getString("Password");

            for (int i = 0; i < wifi.length(); i++)
                companyWifiNames.add(wifi.getJSONObject(i).getString("SSID").toLowerCase());

            SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);
            sharedPreferences.edit().putString("pwd", password).commit();
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }

    }
}
