package com.sox.flssdk;

import android.app.Application;
import android.content.Context;
import androidx.multidex.MultiDex; // MultiDex를 사용하는 경우 이 import 문을 추가합니다

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 앱 초기화 로직을 여기에 추가합니다.
        // 예를 들어, 라이브러리 초기화, 전역 변수 설정 등


    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // MultiDex가 필요한 경우, 이 메서드에서 MultiDex를 초기화합니다.
        // 앱의 메서드 수가 65K를 초과할 때 MultiDex가 필요합니다.
        MultiDex.install(this); // MultiDex를 사용하지 않는다면 이 줄은 제거해도 됩니다.
    }
}