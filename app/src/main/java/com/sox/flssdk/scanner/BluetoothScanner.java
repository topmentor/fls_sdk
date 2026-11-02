package com.sox.flssdk.scanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.sox.flssdk.CommonDoc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class BluetoothScanner implements Scanner {
    private static final String TAG = "BLE Scanner";

    private static final long STALE_ENTRY_TTL = 60000;

    private final Context mContext;
    BluetoothManager mBluetoothManager;
    volatile BluetoothAdapter mBluetoothAdapter;
    BluetoothLeScanner mBluetoothLeScanner;
    BluetoothReceiver mBluetoothReceiver;
    BleScanCallback mBleScanCallback;
    volatile boolean mLeScanning = false;

    final Map<String, Map<String, Object>> mDeviceMap = new ConcurrentHashMap<>();

    Timer mScanTimer;
    TimerTask mScanTimerTask;

    public BluetoothScanner(Context context) {
        this.mContext = context;
        this.mBluetoothManager = (BluetoothManager) this.mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        this.mBleScanCallback = new BleScanCallback();
        refreshAdapter();
    }

    private void refreshAdapter() {
        mBluetoothAdapter = mBluetoothManager != null ? mBluetoothManager.getAdapter() : null;
        mBluetoothLeScanner = mBluetoothAdapter != null ? mBluetoothAdapter.getBluetoothLeScanner() : null;
    }

    private class BleScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            ScanRecord scanRecord = result.getScanRecord();

            if (device == null) return;
            if (!isRssiUsable(rssi)) {
                Log.d(TAG, "advertisement without usable RSSI (" + rssi + ") - skipped : " + device.getAddress());
                return;
            }

            String deviceName = safeDeviceName(device);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("rssi", rssi);
            resultMap.put("name", deviceName != null ? deviceName : "");
            resultMap.put("mac", device.getAddress());
            resultMap.put("time", System.currentTimeMillis());
            resultMap.put("type", "bluetooth low energy");
            resultMap.put("uuid", "");
            resultMap.put("major", "0");
            resultMap.put("minor", "0");

            if (scanRecord != null) {
                String[] uuidStr = parseIBeacon(scanRecord.getBytes());
                if (uuidStr != null && uuidStr.length == 3) {
                    resultMap.put("uuid", uuidStr[0]);
                    resultMap.put("major", uuidStr[1]);
                    resultMap.put("minor", uuidStr[2]);
                }
            }

            put(resultMap);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE scan failed with error code: " + errorCode);
            mLeScanning = false;
            super.onScanFailed(errorCode);
        }
    }


    class ScanTimerTask extends TimerTask {

        @Override
        public void run() {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH_SCAN permission not granted - discovery skipped");
                return;
            }

            BluetoothAdapter adapter = mBluetoothAdapter;
            if (adapter == null || !adapter.isEnabled()) return;

            try {
                adapter.startDiscovery();
            } catch (SecurityException e) {
                Log.e(TAG, "startDiscovery denied: " + e.getMessage());
            }
        }
    }


    private class BluetoothReceiver extends BroadcastReceiver {

        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                Map<String, Object> resultMap = new HashMap<>();

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                ScanRecord scanRecord = intent.getParcelableExtra(BluetoothDevice.EXTRA_UUID);

                if (device != null) {
                    String deviceName = safeDeviceName(device);
                    String deviceAddress = device.getAddress();

                    if (!intent.hasExtra(BluetoothDevice.EXTRA_RSSI)) {
                        Log.d(TAG, "classic device without RSSI - skipped : " + deviceAddress);
                        return;
                    }
                    short deviceRssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    if (!isRssiUsable(deviceRssi)) {
                        Log.d(TAG, "classic device with unusable RSSI (" + deviceRssi + ") - skipped : " + deviceAddress);
                        return;
                    }

                    resultMap.put("rssi", (int) deviceRssi);
                    resultMap.put("name", deviceName != null ? deviceName : "");
                    resultMap.put("uuid", "");
                    resultMap.put("major", "0");
                    resultMap.put("minor", "0");
                    resultMap.put("mac", deviceAddress);
                    resultMap.put("time", System.currentTimeMillis());
                    resultMap.put("type", "bluetooth classic");

                    if (scanRecord != null) {

                        String[] uuidStr = parseIBeacon(scanRecord.getBytes());
                        if(uuidStr != null && uuidStr.length == 3){
                            resultMap.put("uuid", uuidStr[0]);
                            resultMap.put("major", uuidStr[1]);
                            resultMap.put("minor", uuidStr[2]);
                        }
                    }

                    put(resultMap);
                }
            }
        }
    }


    private static boolean isRssiUsable(int rssi) {
        return rssi >= -127 && rssi <= 20;
    }


    private void put(Map<String, Object> resultMap) {
        String mac = (String) resultMap.get("mac");
        if (mac == null) {
            return;
        }

        mDeviceMap.put(mac, resultMap);
    }


    @SuppressLint("MissingPermission")
    private String safeDeviceName(BluetoothDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            return device.getName();
        } catch (SecurityException e) {
            return null;
        }
    }

    private String[] parseIBeacon(byte[] scanData) {
        String[] result = new String[3];

        if (scanData != null && scanData.length > 30) {
            int startByte = 2;
            boolean isIBeacon = false;

            while (startByte <= 5) {
                if (((scanData[startByte] & 0xFF) == 0x02) &&
                        ((scanData[startByte + 1] & 0xFF) == 0x15)) {
                    isIBeacon = true;
                    break;
                }
                startByte++;
            }

            if (isIBeacon) {
                byte[] uuidBytes = Arrays.copyOfRange(scanData, startByte + 2, startByte + 18);
                String uuid = bytesToHex(uuidBytes);

                if(uuid.length() == 32) {

                    result[0] = uuid;

                    int major = (scanData[startByte + 18] & 0xFF) * 0x100 + (scanData[startByte + 19] & 0xFF);
                    result[1] = "" + major;

                    int minor = (scanData[startByte + 20] & 0xFF) * 0x100 + (scanData[startByte + 21] & 0xFF);
                    result[2] = "" + minor;
                }

            }
        }else{
            return null;
        }

        return result;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }


    @SuppressLint("MissingPermission")
    @Override
    public void startScan() {
        if (!hasBleScanPermission()) {
            Toast.makeText(mContext, "블루투스 권한을 확인해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        refreshAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Toast.makeText(mContext, "블루투스를 활성화해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (mBluetoothLeScanner != null) {
                if (mLeScanning) {
                    try { mBluetoothLeScanner.stopScan(mBleScanCallback); } catch (Exception ignored) {}
                }

                ScanSettings scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                        .setReportDelay(0L)
                        .build();

                List<ScanFilter> scanFilters = new ArrayList<>();
                mBluetoothLeScanner.startScan(scanFilters, scanSettings, mBleScanCallback);
                mLeScanning = true;
                Log.i(TAG, "BLE scan started");
            } else {
                Log.e(TAG, "BluetoothLeScanner is null");
            }

            if (mBluetoothReceiver != null) {
                try { mContext.unregisterReceiver(mBluetoothReceiver); } catch (Exception ignored) {}
            }
            mBluetoothReceiver = new BluetoothReceiver();
            IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            mContext.registerReceiver(mBluetoothReceiver, intentFilter);

            if (mScanTimer == null) {
                mScanTimer = new Timer();
            }
            if (mScanTimerTask != null) {
                mScanTimerTask.cancel();
            }
            mScanTimerTask = new ScanTimerTask();
            mScanTimer.schedule(mScanTimerTask, 0, CommonDoc.Scanner.BLE_SCAN_INTERVAL);

            Log.i(TAG, "BLE scan started successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during BLE scan: " + e.getMessage());
            Toast.makeText(mContext, "BLE 스캔 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Exception during BLE scan: " + e.getMessage());
            Toast.makeText(mContext, "BLE 스캔을 시작할 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasBleScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }


    @SuppressLint("MissingPermission")
    @Override
    public void stopScan() {
        if (mScanTimerTask != null) {
            mScanTimerTask.cancel();
            mScanTimerTask = null;
        }

        if (mScanTimer != null) {
            mScanTimer.cancel();
            mScanTimer = null;
        }

        if (mBluetoothReceiver != null) {
            try {
                mContext.unregisterReceiver(mBluetoothReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Exception during receiver unregister: " + e.getMessage());
            }
            mBluetoothReceiver = null;
        }

        if (mBluetoothLeScanner != null && mLeScanning) {
            try {
                mBluetoothLeScanner.stopScan(mBleScanCallback);
                Log.i(TAG, "BLE scan stopped");
            } catch (Exception e) {
                Log.e(TAG, "Exception during BLE scan stop: " + e.getMessage());
            }
        }
        mLeScanning = false;

        try {
            if (mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during discovery cancel: " + e.getMessage());
        }

        Log.i(TAG, "BLE scan stopped successfully");
    }


    @Override
    public List<Map<String, Object>> getResult() {
        long currentTime = System.currentTimeMillis();

        List<Map<String, Object>> resultList = new ArrayList<>();
        Iterator<Map<String, Object>> it = mDeviceMap.values().iterator();
        while (it.hasNext()) {
            Map<String, Object> dataMap = it.next();
            long age = currentTime - (Long) dataMap.get("time");

            if (age < CommonDoc.Scanner.SCAN_THRESHOLD) {
                resultList.add(dataMap);
            } else if (age > STALE_ENTRY_TTL) {
                it.remove();
            }
        }

        return !resultList.isEmpty()? resultList : null;
    }
}
