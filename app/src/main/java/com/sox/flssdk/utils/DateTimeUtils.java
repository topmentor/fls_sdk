package com.sox.flssdk.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateTimeUtils {
    public static final String FORMAT_DATETIME_DASHEDATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String DATETIME_FILENAME_PATTERN = "yyyyMMdd_HHmmss";

    public static String getDateTimeNow(String pattern) {
        if (pattern == null || "".equals(pattern)) {
            pattern = FORMAT_DATETIME_DASHEDATETIME;
        }

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        return simpleDateFormat.format(System.currentTimeMillis());
    }


    /**
     *      날짜 관련 주요 함수
     *         Calendar cal = Calendar.getInstance();
     *         int year = cal.get(Calendar.YEAR);
     *         int mon = cal.get(Calendar.MONTH);
     *         int day = cal.get(Calendar.DAY_OF_MONTH);
     *         int hour = cal.get(Calendar.HOUR_OF_DAY);
     *         int min = cal.get(Calendar.MINUTE);
     *         int sec = cal.get(Calendar.SECOND);
     *
     */


    /**
     * 시간 재기 기능
     *  Date start_time = new Date(System.currentTimeMillis());
     *  System.out.println("total time = " + DateTimeUtils.getTimeDifferenceNow(start_time));
     */


    /**
     * dateformat
     * yyyy/MM/dd/HH:mm:ss
     * YYYY-MM-dd HH-mm-ss
     * yyyy-MM-dd HH:mm:ss
     * yyyyMMdd_HHmmss
     */


    public static String convertTimestampToDate(long timestamp) {
        if(timestamp < 10000000000L){
            timestamp *= 1000L;
        }
        Date date = new java.util.Date(timestamp);
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT+9"));
        String formattedDate = sdf.format(date);
        return formattedDate;
    }


    /**
     * 현재 시간 얻기
     * @return
     *
     * 현재 시간 구하기 방법 들
     * Calendar calendar = Calendar.getInstance(); // gets current instance of the calendar
     * Date date = new Date(); // this object contains the current date value
     * System.currentTimeMillis();   Date date = new Date(System.currentTimeMillis());
     * LocalDate date = LocalDate.now(); // gets the current date
     * LocalTime time = LocalTime.now(); // gets the current time
     * LocalDateTime dateTime = LocalDateTime.now(); // gets the current date and time    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
     */


    public static String getTimeDateNow(String formatStr){

        if(formatStr.equals("")){
            formatStr = FORMAT_DATETIME_DASHEDATETIME ;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(formatStr);
        Date date = new Date();
        String timeValue = sdf.format(date);

        return timeValue;
    }

    public static String getTimeDateNow(){   // yyyy-MM-dd HH:mm:ss

        return getTimeDateNow(FORMAT_DATETIME_DASHEDATETIME);
    }


    // yyyyMMdd_HHmmss
    public static String getTimeDateNow2(){  // yyyyMMdd_HHmmss
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date date = new Date();
        String timeValue = sdf.format(date);

        return timeValue;
    }


}
