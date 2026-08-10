package dev.geco.gmusic.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtil {

    private static final Pattern GOOGLE_DRIVE_FILE_ID_PATTERN = Pattern.compile("/d/([a-zA-Z0-9_-]+)");
    private static final String GOOGLE_DRIVE_DOWNLOAD_URL = "https://drive.google.com/uc?export=download&id=";
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;

    public boolean downloadFile(String url, File file) throws IOException {
        String googleDriveFileId = extractGoogleDriveFileId(url);
        if(googleDriveFileId != null) url = GOOGLE_DRIVE_DOWNLOAD_URL + googleDriveFileId;

        File parentDir = file.getParentFile();
        if(parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) return false;

        URLConnection connection = openConnection(url);
        try {
            if(connection instanceof HttpURLConnection httpConnection && httpConnection.getResponseCode() / 100 != 2) return false;

            try(InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
        } finally {
            if(connection instanceof HttpURLConnection httpConnection) httpConnection.disconnect();
        }

        return true;
    }

    private URLConnection openConnection(String url) throws IOException {
        for(int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            URLConnection connection = URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            if(!(connection instanceof HttpURLConnection httpConnection)) return connection;

            httpConnection.setInstanceFollowRedirects(true);
            if(httpConnection.getResponseCode() / 100 != 3) return httpConnection;

            // HttpURLConnection does not follow redirects between http and https on its own
            String location = httpConnection.getHeaderField("Location");
            httpConnection.disconnect();
            if(location == null) throw new IOException("Redirect without a location header");
            url = URI.create(url).resolve(location).toString();
        }
        throw new IOException("Too many redirects");
    }

    private String extractGoogleDriveFileId(String url) {
        if(!url.contains("drive.google.com")) return null;
        Matcher matcher = GOOGLE_DRIVE_FILE_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

}
