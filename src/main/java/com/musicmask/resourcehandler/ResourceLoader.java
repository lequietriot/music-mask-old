package com.musicmask.resourcehandler;

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

    public static final String fileLink = "https://github.com/lequietriot/music-mask-hosting/raw/master/resources";

    public static byte[] getURLResource(String path) throws IOException {

        File downloadedFile = new File(System.getProperty("user.home") + "/MusicMaskData/" + path.replace("%20", " ").trim());

        if (new File(System.getProperty("user.home") + "/MusicMaskData/" + path.replace("%20", " ").trim()).mkdirs()) {

            URL url  = new URL(fileLink + path);
            URLConnection http = url.openConnection();
            Map<String, List<String>> header = http.getHeaderFields();

            while (isRedirected(header)) {
                url = new URL(fileLink + path);
                http = url.openConnection();
                header = http.getHeaderFields();
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            InputStream input = http.getInputStream();
            Files.copy(input, Paths.get(downloadedFile.toURI()), StandardCopyOption.REPLACE_EXISTING);
            byteArrayOutputStream.write(Files.readAllBytes(Paths.get(downloadedFile.toURI())));
            byteArrayOutputStream.close();
            return byteArrayOutputStream.toByteArray();
        }
        else {
            return Files.readAllBytes(Paths.get(downloadedFile.toURI()));
        }
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
