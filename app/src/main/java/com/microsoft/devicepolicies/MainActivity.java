package com.microsoft.devicepolicies;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.Toast;


public class MainActivity extends ActionBarActivity
{
    String TAG = "MainActivity";
    public int REQUEST_ENABLE = 1001;

    Button btnOpenGPS;
    Button btnUninstall;
    EditText txtPwd;

    private CheckBoxPreference mDisableCameraCheckbox;
    DevicePolicyManager mDPM;
    ComponentName mDeviceAdminSample;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDeviceAdminSample = new ComponentName(this, MyDeviceAdminReceiver.class);

        if (!mDPM.isAdminActive(mDeviceAdminSample))
        {
            // try to become active – must happen here in this activity, to get result
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminSample);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "1234856");
            startActivityForResult(intent, REQUEST_ENABLE);
        }
        else
        {
            // Already is a device administrator, can do security operations now.
            //mDPM.setCameraDisabled(mDeviceAdminSample, true);
            startService(new Intent(MainActivity.this, WifiDetectService.class));
        }

//        txtPwd = (EditText) findViewById(R.id.txtPassword);
//
//        btnUninstall = (Button) findViewById(R.id.btnUninstall);
//        btnUninstall.setOnClickListener(new View.OnClickListener()
//        {
//            @Override
//            public void onClick(View v)
//            {
//                SharedPreferences sharedPreferences = getSharedPreferences("Preference", 0);
//                if (txtPwd.getText().toString().equals(sharedPreferences.getString("pwd", "")))
//                {
//                    Log.d(TAG, "uninstall");
//                    mDPM.removeActiveAdmin(mDeviceAdminSample);
//                    stopService(new Intent(MainActivity.this, WifiDetectService.class));
//
//                    try
//                    {
//                        Thread.sleep(500);
//                    }
//                    catch (InterruptedException e)
//                    {
//                        e.printStackTrace();
//                    }
//
//                    //uninstall
//                    Intent intent = new Intent(Intent.ACTION_DELETE);
//                    intent.setData(Uri.parse("package:" + MainActivity.this.getPackageName()));
//                    startActivity(intent);
//                }
//                else
//                    Toast.makeText(MainActivity.this, "Password Error", Toast.LENGTH_LONG).show();
//            }
//        });

        btnOpenGPS = (Button) findViewById(R.id.btnOpenGPS);
        btnOpenGPS.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (REQUEST_ENABLE == requestCode)
        {
            if (resultCode == Activity.RESULT_OK)
            {
                Log.d(TAG, "Activity.RESULT_OK");
                // Has become the device administrator.
                //mDPM.setCameraDisabled(mDeviceAdminSample, true);
                Intent intent = new Intent(MainActivity.this, WifiDetectService.class);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminSample);
                startService(intent);
            }
            else
            {
                //Canceled or failed.
            }
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
                                stopService(new Intent(MainActivity.this, WifiDetectService.class));

                                try
                                {
                                    Thread.sleep(500);
                                }
                                catch (InterruptedException e)
                                {
                                    e.printStackTrace();
                                }

                                //uninstall
                                Intent intent = new Intent(Intent.ACTION_DELETE);
                                intent.setData(Uri.parse("package:" + MainActivity.this.getPackageName()));
                                startActivity(intent);
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


}
