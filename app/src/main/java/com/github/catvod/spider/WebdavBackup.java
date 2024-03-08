package com.github.catvod.spider;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Notify;
import com.github.catvod.utils.Path;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class WebdavBackup extends Spider {
    private String scheme;
    private String host;
    private int port;
    private String path;
    private String username;
    private String password;
    private Sardine sardine;
    private String url;
    @Override
    public void init(Context context, String extend) {
        Uri uri = Uri.parse(extend);
        this.scheme = uri.getScheme();
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.path = uri.getPath();
        String userInfo = uri.getUserInfo();
        this.username = userInfo.split(":")[0];
        this.password = userInfo.split(":")[1];
        this.sardine = new OkHttpSardine();
        this.sardine.setCredentials(this.username, this.password);
        this.url = this.scheme + "://" + this.host + ":" + this.port + this.path;
        Init.checkPermission();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        Vod vodBackup = new Vod("backup", "备份", "");
        Vod vodRestore = new Vod("restore", "恢复", "");
        list.add(vodBackup);
        list.add(vodRestore);
        return Result.string(list);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        switch (id) {
            case "backup":
                backup();
//                Notify.show("备份完成");
                break;
            case "restore":
                restore();
//                Notify.show("恢复完成");
                break;
            default:
        }
        Init.run(this::finish);
        Vod vod = new Vod();
        vod.setVodPlayFrom("lefty");
        vod.setVodPlayUrl("lefty$lefty");
        return Result.string(vod);
    }

    private void finish() {
        try {
            Activity activity = Init.getActivity();
            if (activity != null) activity.finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void backup() throws Exception {
        File folder = Path.tv();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            // 递归压缩文件夹中的文件
            addFilesToZip(folder, folder.getName(), zipOutputStream);
        }
        byte[] zipBytes = byteArrayOutputStream.toByteArray();
        sardine.put(this.url + "backup.zip", zipBytes, "application/zip");
    }

    private static void addFilesToZip(File folder, String parentFolder, ZipOutputStream zipOutputStream) throws Exception {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                addFilesToZip(file, parentFolder + "/" + file.getName(), zipOutputStream);
                continue;
            }

            FileInputStream fileInputStream = new FileInputStream(file);
            ZipEntry zipEntry = new ZipEntry(parentFolder + "/" + file.getName());
            zipOutputStream.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fileInputStream.read(buffer)) > 0) {
                zipOutputStream.write(buffer, 0, length);
            }

            fileInputStream.close();
        }
    }

    private void restore() throws Exception {
        InputStream inputStream = sardine.get(this.url + "backup.zip");
        unzipAndSaveFiles(inputStream);
    }

    private static void unzipAndSaveFiles(InputStream inputStream) {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                // 获取文件名
                String filename = zipEntry.getName().substring(3);
                File file = new File(Path.tv(), filename);
                if (!zipEntry.isDirectory()) {
                    // 读取文件内容到字节数组
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = zipInputStream.read(buffer)) != -1) {
                        byteArrayOutputStream.write(buffer, 0, length);
                        if (zipInputStream.available() == 0) {
                            break;
                        }
                    }
                    byte[] fileBytes = byteArrayOutputStream.toByteArray();

                    // 调用自定义的Path.write方法保存文件
                    Path.write(file, fileBytes);
                } else {
                    file.mkdirs();
                }
                zipInputStream.closeEntry();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
