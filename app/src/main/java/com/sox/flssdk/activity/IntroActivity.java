package com.sox.flssdk.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.sox.flssdk.CommonDoc;
import com.sox.flssdk.R;

import java.util.ArrayList;
import java.util.List;

public class IntroActivity extends AppCompatActivity {
    private static final Class ACTIVITY = MainActivity.class;
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
    };

    private boolean mEnablePublicPermission;
    private boolean mEnableGps;

    private boolean mIsRequestPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        mIsRequestPermission = false;
    }


    @Override
    protected void onStart() {
        super.onStart();
    }


    @Override
    protected void onResume() {
        super.onResume();

        // 권한 설정 확인
        mEnablePublicPermission = isEnablePublicPermission();
        mEnableGps = isEnableGps();

        if (checkPermissions()) {
            nextActivity(1000);
        } else {
            if (mIsRequestPermission) {
                // 권한 요청을 한 경우
                if (!mEnablePublicPermission) {
                    Toast.makeText(this, "권한을 설정해주세요.", Toast.LENGTH_SHORT).show();
                }

                mIsRequestPermission = false;

                finishActivity(800);
            } else {
                // 권한 요청을 하지 않은 경우
                requestPermissions();

                mIsRequestPermission = true;
            }

            CommonDoc.enableGps = mEnableGps;
            if (!mEnableGps) {
                Toast.makeText(this, "GPS를 확인해주세요.", Toast.LENGTH_SHORT).show();

            }
        }
    }


    /**
     * 권한 확인
     * @return
     */
    private boolean checkPermissions() {
        return mEnablePublicPermission;
    }


    /**
     * 권한 요청
     */
    private void requestPermissions() {
        if (!mEnablePublicPermission) {
            showPermissionSetting();
        }
    }


    /**
     * 권한 활성화 여부 확인
     * @return
     */
    private boolean isEnablePublicPermission() {
        List<String> deniedList = new ArrayList<>();

        for (String permission : PERMISSIONS) {
            int checkPermission = ContextCompat.checkSelfPermission(this, permission);
            if (checkPermission == PackageManager.PERMISSION_DENIED) {
                deniedList.add(permission);
            }
        }

        return !(deniedList.size() > 0);
    }


    /**
     * GPS 활성화 여부 확인
     * @return
     */
    private boolean isEnableGps() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    /**
     * 권한 확인 및 권한 부여 요청
     */
    public void showPermissionSetting() {
        List<String> deniedList = new ArrayList<>();

        for (String permission : PERMISSIONS) {
            int checkPermission = ContextCompat.checkSelfPermission(this, permission);
            if (checkPermission == PackageManager.PERMISSION_DENIED) {
                deniedList.add(permission);
            }
        }

        String[] deniedPermissions = new String[deniedList.size()];
        deniedList.toArray(deniedPermissions);

        if (deniedPermissions.length > 0) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, deniedPermissions[0])) {
                // 사용자가 권한을 허용하지 않은 경우, 권한 설정 화면으로 이동
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            } else {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 101);
            }
        }
    }


    /**
     * delay 이후 Activity 전환
     * @param delay     딜레이 (milliseconds)
     */
    public void nextActivity(int delay) {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(getApplicationContext(), ACTIVITY);
                startActivity(intent);

                finish();
            }
        }, delay);
    }


    /**
     * delay 이후 Activity 종료
     * @param delay
     */
    public void finishActivity(int delay) {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, delay);
    }
}
