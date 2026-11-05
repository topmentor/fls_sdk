package com.sox.flssdk.fragment.position;

import android.net.Uri;
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
    EditText mPositioningServerOptionApiKey;

    ImageButton mCloseButton;
    ImageButton mSaveButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_position_setting, container, false);

        mMainActivity = (MainActivity) getActivity();

        mPositioningServerOptionIp = view.findViewById(R.id.editText_position_positioning_server_option_ip);
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
            String addressError = checkServerAddress(mPositioningServerOptionIp.getText().toString().trim());
            if (addressError != null) {
                Toast.makeText(getContext(), addressError, Toast.LENGTH_LONG).show();
                return;
            }

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
        mPositioningServerOptionApiKey.setText(CommonDoc.Position.positioningServerApiKey);
    }


    /**
     * 서버 주소 형식 검사. 문제가 없으면 null, 있으면 화면에 보일 이유를 반환한다.
     * 포트는 주소에 함께 적는다 (예: http://example.com:8080). 생략하면 http 80, https 443.
     */
    private String checkServerAddress(String address) {
        if (address.isEmpty()) {
            return "서버 주소를 입력해주세요. (예: http://example.com:8080)";
        }

        Uri uri = Uri.parse(address);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return "서버 주소는 http:// 또는 https:// 로 시작해야 합니다.";
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            return "서버 주소에 호스트가 없습니다.";
        }
        if (uri.getPort() == -1 && address.matches("^[a-zA-Z]+://[^/]*:.*")) {
            return "포트 번호가 올바르지 않습니다.";
        }
        return null;
    }

    /**
     * 현재 설정된 정보 가져오기
     * @return
     */
    public Map<String, Object> getSetting() {
        Map<String, Object> preferMap = new HashMap<>();

        preferMap.put(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_IP, mPositioningServerOptionIp.getText().toString().trim());
        preferMap.put(CommonDoc.Position.PREFER_KEY_POSITIONING_SERVER_OPTION_API_KEY, mPositioningServerOptionApiKey.getText().toString().trim());
        preferMap.put(CommonDoc.Position.PREFER_KEY_TARGET_OPTION, "gps");

        return preferMap;
    }

}
