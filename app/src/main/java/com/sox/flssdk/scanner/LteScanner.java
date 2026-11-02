package com.sox.flssdk.scanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.sox.flssdk.CommonDoc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class LteScanner implements Scanner {
    private static final String TAG = "LTE Scanner";

    private static final String DEFAULT_MCC = "450";
    private static final String DEFAULT_MNC = "00";

    private static volatile String sLastKnownMcc = null;
    private static volatile String sLastKnownMnc = null;

    private static final int UNAVAILABLE_SIGNAL_DBM = -100;
    private static final int UNAVAILABLE_RSRQ_DB = -20;

    private static final int PCI_MAX = 503;
    private static final int EARFCN_MAX = 262143;
    private static final int CI_MAX = 268435455;
    private static final int RSRP_MIN = -140, RSRP_MAX = -43;
    private static final int RSRQ_MIN = -34, RSRQ_MAX = 3;
    private static final int RSSI_MIN = -113, RSSI_MAX = -51;

    private final Context mContext;
    private volatile TelephonyManager mTelephonyManager;

    private volatile List<Map<String, Object>> mResultList;

    Timer mScanTimer;
    TimerTask mScanTimerTask;

    public LteScanner(Context context) {
        this.mContext = context;
        this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService(Context.TELEPHONY_SERVICE);
        this.mResultList = new ArrayList<>();
    }


    class ScanTimerTask extends TimerTask {

        @Override
        public void run() {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "ACCESS_FINE_LOCATION permission not granted - LTE scan skipped");
                return;
            }

            TelephonyManager telephonyManager = mTelephonyManager;
            if (telephonyManager == null) {
                return;
            }

            List<CellInfo> cellInfoList;
            try {
                cellInfoList = telephonyManager.getAllCellInfo();
            } catch (SecurityException e) {
                Log.e(TAG, "getAllCellInfo denied: " + e.getMessage());
                return;
            }

            List<CellInfoLte> lteCells = cellInfoList != null ? registeredFirst(cellInfoList) : new ArrayList<>();
            if (lteCells.isEmpty()) {
                mResultList = new ArrayList<>();
                return;
            }

            String[] mcc_mnc = getMCC_MNC(lteCells);
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (CellInfoLte cellInfo : lteCells) {
                CellSignalStrengthLte cellSignalStrengthLte = cellInfo.getCellSignalStrength();
                CellIdentityLte cellIdentityLte = cellInfo.getCellIdentity();

                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("mcc", mcc_mnc[0]);
                resultMap.put("mnc", mcc_mnc[1]);
                resultMap.put("pci", inRange(cellIdentityLte.getPci(), 0, PCI_MAX) ? cellIdentityLte.getPci() : -1);
                resultMap.put("band", "" + (inRange(cellIdentityLte.getEarfcn(), 0, EARFCN_MAX) ? cellIdentityLte.getEarfcn() : 0));

                int ci = cellIdentityLte.getCi();
                if (!inRange(ci, 1, CI_MAX)) {
                    resultMap.put("cid", "0");
                    resultMap.put("net_type", 0);
                } else {
                    resultMap.put("cid", "" + ci);
                    resultMap.put("net_type", 4);
                }

                resultMap.put("rssi", signalOrDefault(cellSignalStrengthLte.getRssi(), RSSI_MIN, RSSI_MAX, UNAVAILABLE_SIGNAL_DBM));
                resultMap.put("rsrp", signalOrDefault(cellSignalStrengthLte.getRsrp(), RSRP_MIN, RSRP_MAX, UNAVAILABLE_SIGNAL_DBM));
                resultMap.put("rsrq", signalOrDefault(cellSignalStrengthLte.getRsrq(), RSRQ_MIN, RSRQ_MAX, UNAVAILABLE_RSRQ_DB));
                resultMap.put("time", System.currentTimeMillis());

                resultList.add(resultMap);
            }

            mResultList = resultList;
        }
    }


    private static List<CellInfoLte> registeredFirst(List<CellInfo> cellInfoList) {
        List<CellInfoLte> registered = new ArrayList<>();
        List<CellInfoLte> others = new ArrayList<>();
        for (CellInfo cellInfo : cellInfoList) {
            if (!(cellInfo instanceof CellInfoLte)) continue;
            if (cellInfo.isRegistered()) {
                registered.add((CellInfoLte) cellInfo);
            } else {
                others.add((CellInfoLte) cellInfo);
            }
        }
        registered.addAll(others);
        return registered;
    }


    private static boolean inRange(int value, int min, int max) {
        return value >= min && value <= max;
    }


    private static int signalOrDefault(int value, int min, int max, int fallback){
        return inRange(value, min, max) ? value : fallback;
    }


    private static int parseDigits(String code, int minLength, int maxLength) {
        if (code == null) return -1;
        String s = code.trim();
        if (s.length() < minLength || s.length() > maxLength) return -1;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return -1;
        }
        return Integer.parseInt(s);
    }


    private String[] getMCC_MNC(List<CellInfoLte> cells){
        String mccStr = null;
        String mncStr = null;

        for (CellInfoLte cell : cells) {
            CellIdentityLte identity = cell.getCellIdentity();

            if (mccStr == null) {
                int mcc = parseDigits(identity.getMccString(), 3, 3);
                if (inRange(mcc, 200, 799)) mccStr = String.valueOf(mcc);
            }
            if (mncStr == null) {
                int mnc = parseDigits(identity.getMncString(), 1, 3);
                if (inRange(mnc, 1, 999)) mncStr = String.format("%02d", mnc);
            }

            if (mccStr != null && mncStr != null) break;
        }

        if (mccStr != null) {
            sLastKnownMcc = mccStr;
        } else {
            mccStr = sLastKnownMcc != null ? sLastKnownMcc : DEFAULT_MCC;
        }

        if (mncStr != null) {
            sLastKnownMnc = mncStr;
        } else {
            mncStr = sLastKnownMnc != null ? sLastKnownMnc : DEFAULT_MNC;
        }

        return new String[]{ mccStr, mncStr };
    }


    @Override
    public void startScan() {
        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        }
        if (mScanTimer == null) {
            mScanTimer = new Timer();
        }
        if (mScanTimerTask != null) {
            mScanTimerTask.cancel();
        }

        mScanTimerTask = new ScanTimerTask();
        mScanTimer.schedule(mScanTimerTask, 0, CommonDoc.Scanner.LTE_SCAN_INTERVAL);

        Log.i(TAG, "start scan");
    }


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

        mTelephonyManager = null;

        Log.i(TAG, "stop scan");
    }


    @Override
    public List<Map<String, Object>> getResult() {
        List<Map<String, Object>> resultList = mResultList;
        return !resultList.isEmpty()? resultList : null;
    }
}
