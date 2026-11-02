package com.sox.flssdk.manager;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import android.Manifest;

import com.sox.flssdk.CommonDoc;
import com.sox.flssdk.utils.DateTimeUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileManager {
    private static final String TAG = "FileManager";

    public String directoryPath;

    public FileManager(String directoryPath) {
        this.directoryPath = directoryPath;
        ensureDirectory();
    }

    private boolean ensureDirectory() {
        File directory = new File(directoryPath);
        if (directory.exists()) {
            return true;
        }
        boolean ok = directory.mkdirs();
        if (!ok) {
            Log.w(TAG, "mkdirs failed for: " + directoryPath
                    + " (storageState=" + Environment.getExternalStorageState()
                    + ", isExternalStorageManager=" + Environment.isExternalStorageManager() + ")");
        }
        return ok;
    }


    /**
     * 파일 저장
     * @param fileName  파일 이름
     * @param contents  내용
     * @param append    이어쓰기
     * @throws IOException
     */
    public void write(String fileName, String contents, boolean append) throws IOException {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            throw new IOException("External storage not mounted: " + Environment.getExternalStorageState());
        }

        ensureDirectory();

        File file = new File(directoryPath + "/" + fileName);

        if (file.exists() && !append) {
            file = rename(file);
        }

        try (FileWriter fw = new FileWriter(file, append)) {
            fw.write(contents);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + file.getAbsolutePath()
                    + " (isExternalStorageManager=" + Environment.isExternalStorageManager() + ")", e);
            throw e;
        }

        Log.i(TAG, "Wrote " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
        notifyMediaScanner(file);
    }


    /**
     * MediaStore 가 새 파일을 인덱싱하도록 알림. Java File API 로 공용 Downloads 폴더에 쓴 파일은
     * MediaStore 자동 스캔이 일어나지 않아 시스템 Files 앱에 즉시 보이지 않는 문제를 해결한다.
     */
    private static void notifyMediaScanner(File file) {
        Context ctx = CommonDoc.getAppContext();
        if (ctx == null) {
            return;
        }
        try {
            MediaScannerConnection.scanFile(ctx,
                    new String[]{file.getAbsolutePath()},
                    null,
                    (path, uri) -> Log.i(TAG, "MediaScanner indexed: " + path + " uri=" + uri));
        } catch (Exception e) {
            Log.w(TAG, "MediaScanner failed for " + file.getAbsolutePath(), e);
        }
    }


    /**
     * 파일 데이터 읽기
     * @param fileName  파일 이름
     * @return          데이터
     * @throws IOException
     */
    public String readFileToString(String fileName) throws IOException {
        StringBuilder sb = new StringBuilder();

        BufferedReader br = new BufferedReader(new FileReader(directoryPath + "/" + fileName));

        String line;
        while (true) {
            line = br.readLine();

            if (line == null) {
                break;
            }

            sb.append(line + "\n");
        }

        br.close();

        return sb.toString();
    }


    /**
     * 파일 목록 가져오기
     * @param isDescSort    내림차순 정렬 여부
     * @return              파일 목록
     */
    public List<File> getDataList(boolean isDescSort) {
        File file = new File(directoryPath);
        File[] files = file.listFiles();

        if (files != null) {
            // 내림차순 정렬
            if (isDescSort) {
                Arrays.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
                        return file2.compareTo(file1);
                    }
                });
            }

            return new ArrayList<>(Arrays.asList(files));
        }

        return new ArrayList<>();
    }


    /**
     * 파일 삭제
     * @param fileName 파일 이름
     * @return
     */
    public boolean delete(String fileName) {
        File file = new File(directoryPath + "/" + fileName);

        if (file.exists()) {
            return file.delete();
        }

        return false;
    }


    /**
     * 파일 압축
     * @param fileList      파일 목록
     */
    public boolean compress(List<File> fileList) {
        String filePath = this.directoryPath + "/compress";
        File directory = new File(filePath);
            
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        String datetime = DateTimeUtils.getDateTimeNow(DateTimeUtils.DATETIME_FILENAME_PATTERN);
        String fileName = "data_" + datetime + ".zip";

        File compressFile = new File(filePath, fileName);
        try (FileOutputStream fos = new FileOutputStream(new File(filePath, fileName))) {
            ZipOutputStream zipOutputStream = new ZipOutputStream(fos);

            for (File file : fileList) {
                if (file.isFile()) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zipOutputStream.putNextEntry(zipEntry);

                    FileInputStream fileInputStream = new FileInputStream(file);
                    byte[] buffer = new byte[1024];

                    int length;
                    while ((length = fileInputStream.read(buffer)) > 0) {
                        zipOutputStream.write(buffer, 0, length);
                    }

                    fileInputStream.close();
                    zipOutputStream.closeEntry();

                    file.delete();
                }
            }

            zipOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return compressFile.exists();
    }


    /**
     * 파일 이름이 같은 경우, 파일명 뒤에 (숫자)를 추가
     * @param file
     * @return
     */
    public static File rename(File file) {
        int i = 2;
        String newName;

        String filePath = file.getParentFile().getPath();
        String fileName = file.getName();

        int index = fileName.lastIndexOf(".");

        String name = fileName.substring(0, index);
        String extension = fileName.substring(index + 1);

        do {
            newName = String.format("%s (%d).%s", name, i++, extension);

            file = new File(filePath, newName);
        } while (file.exists());

        return file;
    }
}
