package com.musicmask.resourcehandler;

import com.musicmask.MusicMaskPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

public class ResourceLoader {

    public static byte[] getURLResource(String path) {
        try {
            File downloadedFile = new File(MusicMaskPlugin.RESOURCES_DIR + File.separator + path.replace("%20", " ").trim());
            if (!downloadedFile.exists()) {
                if (downloadedFile.mkdirs()) {

                    URL url = new URL(MusicMaskPlugin.RAW_GITHUB + path);
                    URLConnection http = url.openConnection();

                    Map<String, List<String>> header = http.getHeaderFields();

                    if (header.get(null).contains("404") || header.get(null).contains("410")) {
                        return null;
                    }

                    while (isRedirected(header)) {
                        url = new URL(MusicMaskPlugin.RAW_GITHUB + path);
                        http = url.openConnection();
                        header = http.getHeaderFields();
                    }

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    InputStream input = http.getInputStream();
                    Files.copy(input, Paths.get(downloadedFile.toURI()), StandardCopyOption.REPLACE_EXISTING);
                    byteArrayOutputStream.write(Files.readAllBytes(Paths.get(downloadedFile.toURI())));
                    byteArrayOutputStream.close();
                    input.close();
                    return byteArrayOutputStream.toByteArray();
                }
            }
            else {
                if (!downloadedFile.isDirectory()) {
                    return Files.readAllBytes(Paths.get(downloadedFile.toURI()));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean isRedirected(Map<String, List<String>> header) {
        for(String hv : header.get(null)) {
            if (hv.contains("301") || hv.contains("302")) {
                return true;
            }
        }
        return false;
    }
}
