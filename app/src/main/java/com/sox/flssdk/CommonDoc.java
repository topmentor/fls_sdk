package com.sox.flssdk;

public class CommonDoc {
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

}
