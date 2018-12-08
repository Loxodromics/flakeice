package net.quatur.flakeice;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.central.BleManager;

public class MainActivity extends AppCompatActivity {

    // Constants
    private final static String TAG = MainActivity.class.getSimpleName();

    // Permission requests
    private final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    public final static int PERMISSION_REQUEST_FINE_LOCATION = 2;

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_EnableBluetooth = 1;
    public static final int kActivityRequestCode_PlayServicesAvailability = 2;

    private AlertDialog mRequestLocationDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
//        BluefruitApplication.activityPaused();

        // Remove location dialog if present
        if (mRequestLocationDialog != null) {
            mRequestLocationDialog.cancel();
            mRequestLocationDialog = null;
        }
    }

    private void checkPermissions() {

        final boolean areLocationServicesReadyForScanning = manageLocationServiceAvailabilityForScanning();
        if (!areLocationServicesReadyForScanning) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            mRequestLocationDialog = builder.setMessage(R.string.bluetooth_locationpermission_disabled_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            //DialogUtils.keepDialogOnOrientationChanges(mRequestLocationDialog);
        } else {
            if (mRequestLocationDialog != null) {
                mRequestLocationDialog.cancel();
                mRequestLocationDialog = null;
            }

            // Bluetooth state
            final boolean isBluetoothEnabled = manageBluetoothAvailability();

            if (isBluetoothEnabled) {
                // Request Bluetooth scanning permissions
                final boolean isLocationPermissionGranted = requestCoarseLocationPermissionIfNeeded();

                if (isLocationPermissionGranted) {
                    // All good. Start Scanning
                    BleManager.getInstance().start(MainActivity.this);
                    // Bluetooth was enabled, resume scanning
//                    mMainFragment.startScanning();
                    //TODO
                }
            }
        }
    }

    // region Permissions
    private boolean manageLocationServiceAvailabilityForScanning() {

        boolean areLocationServiceReady = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {        // Location services are only needed to be enabled from Android 6.0
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            areLocationServiceReady = locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }

        return areLocationServiceReady;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean requestCoarseLocationPermissionIfNeeded() {
        boolean permissionGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android Marshmallow Permission check 
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionGranted = false;
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                mRequestLocationDialog = builder.setTitle(R.string.bluetooth_locationpermission_title)
                        .setMessage(R.string.bluetooth_locationpermission_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .setOnDismissListener(dialog -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION))
                        .show();
            }
        }
        return permissionGranted;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location permission granted");

                    checkPermissions();

                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.bluetooth_locationpermission_notavailable_title);
                    builder.setMessage(R.string.bluetooth_locationpermission_notavailable_text);
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(dialog -> {
                    });
                    builder.show();
                }
                break;
            }
            default:
                break;
        }
    }
    // endregion

    // region Bluetooth Setup
    private boolean manageBluetoothAvailability() {
        boolean isEnabled = true;

        // Check Bluetooth HW status
        int errorMessageId = 0;
        final int bleStatus = BleUtils.getBleStatus(getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                errorMessageId = R.string.bluetooth_unsupported;
                isEnabled = false;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE: {
                errorMessageId = R.string.bluetooth_poweredoff;
                isEnabled = false;      // it was already off
                break;
            }
            case BleUtils.STATUS_BLUETOOTH_DISABLED: {
                isEnabled = false;      // it was already off
                // if no enabled, launch settings dialog to enable it (user should always be prompted before automatically enabling bluetooth)
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                startActivityForResult(enableBtIntent, kActivityRequestCode_EnableBluetooth);
                //TODO
                // execution will continue at onActivityResult()
                break;
            }
        }

        if (errorMessageId != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            AlertDialog dialog = builder.setMessage(errorMessageId)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
//            DialogUtils.keepDialogOnOrientationChanges(dialog);
        }

        return isEnabled;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == kActivityRequestCode_EnableBluetooth) {
            if (resultCode == Activity.RESULT_OK) {
                checkPermissions();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());     // getApplicationContext to avoid WindowManager$BadTokenException
                AlertDialog dialog = builder.setMessage(R.string.bluetooth_poweredoff)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
//                DialogUtils.keepDialogOnOrientationChanges(dialog);
            }
        }
    }
}
