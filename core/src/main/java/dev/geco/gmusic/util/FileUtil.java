package dev.geco.gmusic.util;

import java.io.*;
import java.net.*;
import java.util.regex.*;

public class FileUtil {
    public boolean downloadFile(String urlString, File file) throws Exception {
        String googleDriveFieldId = extractGoogleDriveFileId(urlString);
        if(googleDriveFieldId != null){
            urlString = "https://drive.google.com/uc?export=download&id=" + googleDriveFieldId;
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setInstanceFollowRedirects(true);

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return true;
    }

    private static String extractGoogleDriveFileId(String url) {
        Pattern pattern = Pattern.compile("/d/([a-zA-Z0-9_-]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }


}
