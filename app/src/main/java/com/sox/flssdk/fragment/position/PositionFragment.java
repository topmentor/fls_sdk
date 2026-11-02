package com.sox.flssdk.fragment.position;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.sox.flssdk.CommonDoc;
import com.sox.flssdk.R;
import com.sox.flssdk.activity.MainActivity;
import com.sox.flssdk.manager.PositionScannerManager;
import com.sox.flssdk.scanner.GpsScanner;
import com.sox.flssdk.utils.DateTimeUtils;
import com.sox.flssdk.utils.geo.CoordTransformUtil;
import com.sox.flssdk.utils.geo.dataobject.GPoint;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

public class PositionFragment extends Fragment implements View.OnClickListener {
    MainActivity mMainActivity;
    Fragment mSettingFragment;

    public static ScanReceiveHandler sScanReceiveHandler;

    TextView mDateTextView;
    TextView mTimeTextView;
    TextView mGpsLatitudeTextView;
    TextView mGpsLongitudeTextView;
    TextView mLatitudeTextView;
    TextView mLongitudeTextView;
    TextView mReceiveStatusTextView;

    Button mPositioningButton;
    ImageButton mSettingButton;

    Timer mClockTimer;
    TimerTask mClockTimerTask;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_position, container, false);

        mMainActivity = (MainActivity) getActivity();
        mSettingFragment = new PositionSettingFragment();

        sScanReceiveHandler = new PositionFragment.ScanReceiveHandler(this);

        mDateTextView = view.findViewById(R.id.textView_position_date);
        mTimeTextView = view.findViewById(R.id.textView_position_time);
        mGpsLatitudeTextView = view.findViewById(R.id.textView_position_gps_latitude);
        mGpsLongitudeTextView = view.findViewById(R.id.textView_position_gps_longitude);
        mLatitudeTextView = view.findViewById(R.id.textView_position_latitude);
        mLongitudeTextView = view.findViewById(R.id.textView_position_longitude);
        mReceiveStatusTextView = view.findViewById(R.id.textView_position_receive_status);

        mPositioningButton = view.findViewById(R.id.button_position_send_positioning);
        mSettingButton = view.findViewById(R.id.imageButton_position_setting);

        mClockTimer = new Timer();
        mClockTimerTask = new ClockTimerTask();
        mClockTimer.schedule(mClockTimerTask, 0, 1000);

        setButtonEventListener();

        return view;
    }


    /**
     * Timer Task Inner Class
     */
    class ClockTimerTask extends TimerTask {
        private final Handler mUiHandler = new Handler(Looper.getMainLooper());

        @Override
        public void run() {
            String date = DateTimeUtils.getTimeDateNow("yyyy-MM-dd");
            String time = DateTimeUtils.getTimeDateNow("HH:mm:ss");
            String gpsLat = String.format("%.6f", GpsScanner.sLatitude);
            String gpsLng = String.format("%.6f", GpsScanner.sLongitude);
            mUiHandler.post(() -> {
                if (mDateTextView != null) mDateTextView.setText(date);
                if (mTimeTextView != null) mTimeTextView.setText(time);
                if (mGpsLatitudeTextView != null) mGpsLatitudeTextView.setText(gpsLat);
                if (mGpsLongitudeTextView != null) mGpsLongitudeTextView.setText(gpsLng);
            });
        }
    }


    private final class ScanReceiveHandler extends Handler {
        private final WeakReference<PositionFragment> reference;

        public ScanReceiveHandler(PositionFragment fragment) {
            reference = new WeakReference<>(fragment);
        }

        // PositionScannerManager가 보낸 메세지를 받음
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            PositionFragment fragment = reference.get();
            if (fragment != null) {
                Bundle bundle = msg.getData();

                String status = bundle.getString(CommonDoc.Position.HANDLER_STATUS_KEY);

                // 서버 전송 실패 시
                if (CommonDoc.Position.HANDLER_FAILED_RESULT.equals(status)) {
                    fragment.mReceiveStatusTextView.setText("서버 전송 오류");

                    CommonDoc.writelog("서버 전송 오류 end \n");
                    CommonDoc.writeFile();
                    return;
                }

                // 진행 상태 업데이트 (예: "서버 요청")
                if (CommonDoc.Position.HANDLER_STATUS_UPDATE.equals(status)) {
                    String payload = bundle.getString(CommonDoc.Position.HANDLER_RESULT_KEY);
                    if (payload != null) {
                        try {
                            JSONObject statusObject = new JSONObject(payload);
                            String message = statusObject.optString("message", "");
                            if (!message.isEmpty()) {
                                fragment.mReceiveStatusTextView.setText(message);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    return;
                }

                // 키와 결과 수신
                String key = bundle.getString(CommonDoc.Position.HANDLER_KEY);
                String response = bundle.getString(CommonDoc.Position.HANDLER_RESULT_KEY);

                ///////////////////////// @@ 서버 측위결과 받기 부분
                if (CommonDoc.Position.HANDLER_POSITION_RESULT.equals(key)) {
                    double latitude = GpsScanner.sLatitude;
                    double longitude = GpsScanner.sLongitude;

                    CommonDoc.writelog("GPS Location  : " + String.format("%.6f", longitude) + " , " + String.format("%.6f", latitude));
                    CommonDoc.writelog("Target Location  : " + String.format("%.6f", longitude) + " , " + String.format("%.6f", latitude));

                    int findType = 0;
                    double serverDist = -1;

                    try {
                        JSONObject responseObject = new JSONObject(response);
                        int result = responseObject.getInt("result_code");
                        String message = responseObject.getString("result_msg");

                        if (result != 999) {
                            double sLatitude = responseObject.getDouble("s_latitude");
                            double sLongitude = responseObject.getDouble("s_longitude");

                            findType = responseObject.getInt("findtype");

                            CommonDoc.writelog("response : \n" + response + "\n\n================================================\n");
                            CommonDoc.writelog("\n\nresult " + (result == 0 ? "(success)" : "(fail)") + " : \n" + message + (findType == 30 ? " - TGrid" : ""));
                            CommonDoc.writelog("Server Fused Location  : " + String.format("%.6f", sLongitude) + " , " + String.format("%.6f", sLatitude));

                            serverDist = CoordTransformUtil.getDistanceBetween(new GPoint(longitude, latitude), new GPoint(sLongitude, sLatitude), true);
                            CommonDoc.writelog("Fused Distance  : " + String.format("%.1f", serverDist) + "m");

                            fragment.mLatitudeTextView.setText(String.format("%.6f", sLatitude));
                            fragment.mLongitudeTextView.setText(String.format("%.6f", sLongitude));
                        } else {
                            CommonDoc.writelog("response (fail) : \n" + response);
                        }

                        fragment.mReceiveStatusTextView.setText(message + (findType == 30 ? "-T " : "") + " (" + String.format("%.1fm", serverDist) + ")");

                        CommonDoc.writelog("end " + result);
                        CommonDoc.writeFile();

                    } catch (JSONException e) {
                        e.printStackTrace();

                        fragment.mReceiveStatusTextView.setText("JSON parse 오류");
                        CommonDoc.writelog("end : JSON parse 오류 \n");
                        CommonDoc.writeFile();
                    }

                }
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mClockTimer != null) {
            mClockTimer.cancel();
            mClockTimer = null;
        }
        if (mClockTimerTask != null) {
            mClockTimerTask.cancel();
            mClockTimerTask = null;
        }
    }

    /**
     * 버튼 이벤트 등록
     */
    public void setButtonEventListener() {
        mSettingButton.setOnClickListener(this);
        mPositioningButton.setOnClickListener(this);
    }


    /**
     * 버튼 이벤트
     */
    @Override
    public void onClick(View v) {
        int button = v.getId();

        if (button == mSettingButton.getId()) {
            mMainActivity.changeFragment(mSettingFragment);

        } else if (button == mPositioningButton.getId()) {
            // [복합측위 시험] 버튼
            if (CommonDoc.Position.positioningServer != null && !"".equals(CommonDoc.Position.positioningServer)) {
                CommonDoc.Position.position_type = "server";

                CommonDoc.startLogging("server");
                CommonDoc.writelog("start to scan signal");

                PositionScannerManager positionScannerManager = new PositionScannerManager(getContext(), CommonDoc.Position.positioningServer);
                positionScannerManager.startScan();
                mReceiveStatusTextView.setText("신호 수집중");
            } else {
                Toast.makeText(getContext(), "서버 정보를 등록해주세요.", Toast.LENGTH_SHORT).show();
                mReceiveStatusTextView.setText("");
            }

        }
    }

}
