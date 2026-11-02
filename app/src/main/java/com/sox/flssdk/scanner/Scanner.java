package com.sox.flssdk.scanner;

import org.json.JSONArray;

import java.util.List;
import java.util.Map;

public interface Scanner {
    void startScan();
    void stopScan();
    List<Map<String, Object>> getResult();
}
