package com.sox.flssdk.fragment.position;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.sox.flssdk.CommonDoc;
import com.sox.flssdk.R;
import com.sox.flssdk.activity.MainActivity;
import com.sox.flssdk.manager.PreferenceManager;

import java.util.HashMap;
import java.util.Map;

public class PositionSettingFragment extends Fragment implements View.OnClickListener {
    MainActivity mMainActivity;

    EditText mPositioningServerOptionIp;
    EditText mPositioningServerOptionPort;
    EditText mPositioningServerOptionApiKey;

    ImageButton mCloseButton;
    ImageButton mSaveButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_position_setting, container, false);

        mMainActivity = (MainActivity) getActivity();

        mPositioningServerOptionIp = view.findViewById(R.id.editText_position_positioning_server_option_ip);
        mPositioningServerOptionPort = view.findViewById(R.id.editText_position_positioning_server_option_port);
        mPositioningServerOptionApiKey = view.findViewById(R.id.editText_position_positioning_server_option_api_key);
        mCloseButton = view.findViewById(R.id.imageButton_position_close_setting);
        mSaveButton = view.findViewById(R.id.imageButton_position_save_setting);

        setButtonEventListener();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        setSetting();
    }


    /**
     * 버튼 이벤트 등록
     */
    public void setButtonEventListener() {
        mCloseButton.setOnClickListener(this);
        mSaveButton.setOnClickListener(this);
    }


    /**
     * 버튼 이벤트
     * @param v
     */
    @Override
    public void onClick(View v) {
        int button = v.getId();

        if (button == mCloseButton.getId()) {
            mMainActivity.changeFragment(mMainActivity.mPositionFragment);

        } else if (button == mSaveButton.getId()) {
            applyHintsIfEmpty();

            Map<String, Object> preferMap = getSetting();
            boolean isSaved = PreferenceManager.setPreferences(getContext(), CommonDoc.Position.PREFER_SETTING_FILE, preferMap);

            if (isSaved) {
                Toast.makeText(getContext(), "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show();

                PreferenceManager.readPositionSetting(getContext());

                mMainActivity.changeFragment(mMainActivity.mPositionFragment);
            } else {
                Toast.makeText(getContext(), "다시 시도해주세요.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    /**
     * 이전 설정 정보 입력
     * CommomDoc --> 변수
     */

    public void setSetting() {
        mPositioningServerOptionIp.setText(CommonDoc.Position.positioningServerIp);
        mPositioningServerOptionPort.setText(CommonDoc.Position.positioningServerPort);
        mPositioningServerOptionApiKey.setText(CommonDoc.Position.positioningServerApiKey);
    }


    /**
     * 빈 필드에 hint 값을 채워 넣는다 (초기 상태 자동 완성)
     */
    private void applyHintsIfEmpty() {
        fillIfEmpty(mPositioningServerOptionIp);
        fillIfEmpty(mPositioningServerOptionPort);    }

    private void fillIfEmpty(EditText field) {
        if (field.getText().toString().trim().isEmpty() && field.getHint() != null) {
            field.setText(field.getHint());
        }
    }

    /**
     * 현재 설정된 정보 가져오기
     * @return
     */
    public Map<String, Object> getSetting() {
        Map<String, Object> preferMap = new HashMap<>();

        preferMap.put(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_IP, mPositioningServerOptionIp.getText().toString());
        preferMap.put(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_PORT, mPositioningServerOptionPort.getText().toString());
        preferMap.put(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_API_KEY, mPositioningServerOptionApiKey.getText().toString().trim());
        preferMap.put(CommonDoc.Position.PREFER_KEY_TARGET_OPTION, "gps");

        return preferMap;
    }

}
