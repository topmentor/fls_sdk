package com.sox.flssdk;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.sox.flssdk.manager.FileManager;
import com.sox.flssdk.utils.DateTimeUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CommonDoc {
    /**
     * 모든 로그/JSON/이미지/모델 파일의 기본 저장 위치.
     */
    public static final String FILE_DOWNLOAD_PATH =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
                    + "/FLSSDK";
    public static final String EXCEPTION_DIRECTORY = "/exception";
    public static final String POSITION_LOG_DIRECTORY = "/Log";
    public static final String MODEL_DOWNLOAD_DIRECTORY = "/models";


    public static final String REQUEST_ONDEVICE_MODEL_URL = "";  // 앱 설정 화면에서 입력

    public static boolean enableGps = true;

    public static class ServerCall {
        public static int WAIT_TIME = 60000;   // 대기시간
        public static int WAIT_TIME2 = 30000;   // 대기시간
        public static int RETRY = 0;   // 재시도
        public static int RETRY2 = 3;   // 재시도

        public static String DEFAULT_URL = "";
        public static int DEFAULT_PORT = 0;
    }


    /**
     * 스캐너
     */
    public static class Scanner {
        public static final int GPS_SCAN_INTERVAL = 1000;
        public static final int LTE_SCAN_INTERVAL = 3000;
        public static final int WIFI_SCAN_INTERVAL = 30000;
        public static final int BLE_SCAN_INTERVAL = 12000;

        public static final String GPS_SCAN_RESULT_KEY = "gps_measurements";

        public static final String GFLP_SCAN_RESULT_KEY = "gflp_measurements";
        public static final String LTE_SCAN_RESULT_KEY = "lte_measurements";
        public static final String WIFI_SCAN_RESULT_KEY = "wifi_measurements";
        public static final String BLE_SCAN_RESULT_KEY = "bluetooth_measurements";

        public static final int SCAN_THRESHOLD = 5000;
    }


    /**
     * 측위
     */
    public static class Position {

        public static final String PREFER_SETTING_FILE = "flssdk_position";

        public static final int SCAN_INTERVAL = 3000;

        public static final String HANDLER_KEY = "key";
        public static final String HANDLER_STATUS_KEY = "status";
        public static final String HANDLER_RESULT_KEY = "result";

        public static final String HANDLER_SUCCEED_RESULT = "succeed";
        public static final String HANDLER_FAILED_RESULT = "failed";

        public static final String HANDLER_LOG_RESULT = "log";
        public static final String HANDLER_POSITION_RESULT = "position";
        public static final String HANDLER_STATUS_UPDATE = "status_update";

        public static final String PREFER_KEY_POSITIONING_SERVER_OPTION_IP = "positioning_server_option_ip";
        // 이전 버전에서 포트를 따로 저장하던 키. 읽어서 주소로 옮긴 뒤 지운다.
        public static final String PREFER_KEY_POSITIONING_SERVER_OPTION_PORT = "positioning_server_option_port";
        public static final String PREFER_KEY_POSITIONING_SERVER_OPTION_API_KEY = "positioning_server_option_api_key";

        public static final String PREFER_KEY_TARGET_OPTION = "target_option";

        public static String position_type = "server";  // server || ondevice || logging
        public static String target_type = "gps";


        // 측위 API 경로. 설정 화면에서 입력한 서버 주소:포트 뒤에 붙인다.
        public static final String POSITIONING_API_URI = "/LocationDBService/getPosition.do";

        public static String positioningServer;
        // 설정 화면에서 입력한 서버 주소 (포트 포함)
        public static String positioningServerIp;
        // 측위 요청 시 X-API-KEY 헤더로 보낸다. 비어 있으면 헤더를 넣지 않는다.
        public static String positioningServerApiKey;

    }

    //////////////////////////////////////////////////////////////////
    // 로그 파일 만들기

    private static String header = "";
    private static StringBuffer result;
    private static String datetime = "__";
    private static Context mContext;

    public static void init(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * 앱 컨텍스트 반환 (MediaScanner 등 시스템 서비스 접근용).
     */
    public static Context getAppContext() {
        return mContext;
    }

    public static void startLogging(String headerStr){
        header = headerStr;
        result = new StringBuffer();
        datetime = DateTimeUtils.getTimeDateNow();
    }

    public static void writelog(String logStr){
        if(result == null) {
            result = new StringBuffer();
        }

        String currentTime = DateTimeUtils.getTimeDateNow();
        result.append("[ ").append(currentTime).append(" ] ");

        result.append(logStr);
        result.append("\n\n");
    }

    public static void writeFile() {
        if (result == null) {
            Log.w("CommonDoc", "writeFile called but result buffer is null — startLogging not invoked?");
            return;
        }
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(new FileCreationTask(header, result.toString()));
    }

    private static class FileCreationTask implements Runnable {
        private static final String TAG = "CommonDoc.FileTask";

        private final String result;
        private final String header;

        public FileCreationTask(String headerStr, String resultStr) {
            this.result = resultStr == null ? "" : resultStr;
            this.header = headerStr == null ? "log" : headerStr;
        }

        @Override
        public void run() {
            String datetime = DateTimeUtils.getTimeDateNow2();
            String fileName = header + "_" + datetime + ".txt";
            String logDirPath = FILE_DOWNLOAD_PATH + POSITION_LOG_DIRECTORY;

            try {
                FileManager fileManager = new FileManager(logDirPath);
                fileManager.write(fileName, result, false);

                File saved = new File(logDirPath, fileName);
                Log.i(TAG, "Position log saved: " + saved.getAbsolutePath() + " (" + saved.length() + " bytes)");
                showToastOnUi("측위 로그 저장: " + saved.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Position log save failed at " + logDirPath, e);
                showToastOnUi("측위 로그 저장 실패: " + e.getMessage());
            }
        }
    }

    private static void showToastOnUi(String msg) {
        if (mContext == null) return;
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show());
    }


    /**
     * 모든 로그/JSON/이미지/모델 파일의 공통 다운로드 경로 반환.
     */
    public static String getDownloadPath() {
        return FILE_DOWNLOAD_PATH;
    }

}
