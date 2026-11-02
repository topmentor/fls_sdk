package com.sox.flssdk.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.sox.flssdk.CommonDoc;
import com.sox.flssdk.R;
import com.sox.flssdk.fragment.position.PositionFragment;
import com.sox.flssdk.manager.FileManager;
import com.sox.flssdk.manager.PreferenceManager;
import com.sox.flssdk.utils.DateTimeUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_NEARBY_WIFI_DEVICES = 1001;
    private static final int REQUEST_BLUETOOTH_SCAN = 1002;
    private static final int REQUEST_BLUETOOTH_ADVERTISE = 1003;

    public Fragment mPositionFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // CommonDoc 초기화
        CommonDoc.init(this);

        mPositionFragment = new PositionFragment();

        // 설정 읽어오기
        PreferenceManager.readPositionSetting(this);

        // 측위시험 Fragment로 직접 시작
        changeFragment(mPositionFragment);

        // 애플리케이션 비정상 종료 시, Exception 내용을 파일로 저장
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());

        // Android 12+ 권한 요청
        requestAndroid12Permissions();
    }

    /**
     * Android 12+ (API 31+) 권한 요청
     */
    private void requestAndroid12Permissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // NEARBY_WIFI_DEVICES 권한 요청
            if (ContextCompat.checkSelfPermission(this, "android.permission.NEARBY_WIFI_DEVICES") != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{"android.permission.NEARBY_WIFI_DEVICES"}, REQUEST_NEARBY_WIFI_DEVICES);
            }

            // BLUETOOTH_SCAN 권한 요청
            if (ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{"android.permission.BLUETOOTH_SCAN"}, REQUEST_BLUETOOTH_SCAN);
            }

            // BLUETOOTH_CONNECT 권한 요청
            if (ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{"android.permission.BLUETOOTH_CONNECT"}, REQUEST_BLUETOOTH_ADVERTISE);
            }
        }
    }


    /**
     * Fragment 변경
     */
    public void changeFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment).commitAllowingStateLoss();
    }

    /**
     * Fragment를 스택에 추가하여 변경
     */
    public void changeFragmentWithStack(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commitAllowingStateLoss();
    }

    /**
     * 이전 Fragment로 돌아가기
     */
    public void goBackToPreviousFragment() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        }
    }


    /**
     * 권한 요청 결과 처리
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_NEARBY_WIFI_DEVICES:
                break;
            case REQUEST_BLUETOOTH_SCAN:
                break;
            case REQUEST_BLUETOOTH_ADVERTISE:
                break;
        }
    }


    /**
     * 비정상 종료 Exception Catch
     */
    class ExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
            String fileName = "log_" + DateTimeUtils.getTimeDateNow2() + ".txt";

            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            String message = stringWriter.toString();

            try {
                FileManager fileManager = new FileManager(CommonDoc.FILE_DOWNLOAD_PATH + CommonDoc.EXCEPTION_DIRECTORY);
                fileManager.write(fileName, message, false);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
}
