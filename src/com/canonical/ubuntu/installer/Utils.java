package com.canonical.ubuntu.installer;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.StatFs;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

public class Utils {

    private final static String TAG = "Utils";
    private final static int SIGNATURE_SIZE = 490;

    public static void showToast(Context context, String message) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    public static void showToast(Context context, int message) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }	

    public static String httpDownload(String url) { // return null as error happens
        HttpClient httpclient = new DefaultHttpClient();
        // Prepare a request object
        HttpGet httpget = new HttpGet(url); 

        // Execute the request
        HttpResponse response;

        try {
            response = httpclient.execute(httpget);
            // Examine the response status
            Log.i(TAG, response.getStatusLine().toString());

            // Get hold of the response entity
            HttpEntity entity = response.getEntity();

            // If the response does not enclose an entity, there is no need
            // to worry about connection release

            if (entity != null) {
                // A Simple JSON Response Read
                InputStream instream = entity.getContent();
                String result= convertStreamToString(instream);
                // now you have the string representation of the HTML request

                // Log.i(TAG, result);

                instream.close();

                return result;
            }
        } catch (Exception e) {
            Log.i(TAG, e.toString());
        }
        return null;
    }

    private static int BUF_SIZE = 4096;
    public static String convertStreamToString(InputStream is)
            throws IOException {
        if (is != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[BUF_SIZE];
            try {
                Reader reader = new BufferedReader(
                        new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }
            return writer.toString();
        } else {
            return "";
        }
    }

    public static boolean writeInputStream2File(InputStream is, File f) {
        try {
            int r;
            byte b[] = new byte[BUF_SIZE];
            OutputStream os = new FileOutputStream(f);

            while((r = is.read(b)) != -1)
                os.write(b, 0, r);
            is.close();
            os.close();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static boolean copyAsset2File(AssetManager am, String filename, File targetFile) {
        try {
            InputStream in = am.open(filename);
            return writeInputStream2File(in, targetFile);
        } catch (Exception e) {
            return false;
        }
    }

    public static void extractExecutableAsset(Context context, String filename, 
            String destination, Boolean executable) throws IOException {
        AssetManager am = context.getAssets();
        File destinationDir = new File(destination);
        if (!destinationDir.exists()) {
            // create folder            
            destinationDir.mkdir();
        }
        File file = new File(destinationDir, filename);
        if (file.exists() && file.isFile()) {
            file.delete();
        }

        InputStream in = am.open(filename);
        BufferedInputStream bin = new BufferedInputStream(in);

        FileOutputStream fout = new FileOutputStream(file);

        byte buffer[] = new byte[10*1024];
        int bytesRead = bin.read(buffer);
        while (bytesRead > -1) {
            fout.write(buffer, 0, bytesRead);
            bytesRead = bin.read(buffer);
        }
        fout.flush();
        fout.close();

        bin.close();
        if (executable) {
            file.setExecutable(true);
        }
    }

    public static int calculateDownloadSize(List<JsonChannelParser.File> files) {
        int size = 0;
        for(JsonChannelParser.File f : files) {
            size += f.size;
            if (!f.signature.equals("")){
                size += SIGNATURE_SIZE;
            }
        }
        return size;
    }

    @SuppressWarnings("deprecation")
    public static long getFreeSpaceInBytes(String fsPath) {
        StatFs stats = new StatFs(fsPath);
        // not using getAvailableBytes() for it's not available in android 4.2
        int availableBlocks = stats.getAvailableBlocks();
        int blockSizeInBytes = stats.getBlockSize();
        long freeSpaceInBytes = ((long)availableBlocks) * ((long)blockSizeInBytes);
        return freeSpaceInBytes;
    }

    public static String getBootPartitionPath() {
        String deviceModel = Build.DEVICE.toLowerCase(Locale.US);
        if("nakasi".equals(deviceModel) || "tilapia".equals(deviceModel)) {
            // workaround for Nexus 7 3G.
            deviceModel = "grouper";
        }

        if ("flo".equals(deviceModel)) {
            return UbuntuInstallService.FLO_PARTITION_BOOT;
        } else if ("grouper".equals(deviceModel)) {
            return UbuntuInstallService.GROUPER_PARTITION_BOOT;
        } else if ("hammerhead".equals(deviceModel)) {
            return UbuntuInstallService.HAMMERHEAD_PARTITION_BOOT;
        } else if ("maguro".equals(deviceModel)) {
            return UbuntuInstallService.MAGURO_PARTITION_BOOT;
        } else if ("mako".equals(deviceModel)) {
            return UbuntuInstallService.MAKO_PARTITION_BOOT;
        } else if ("manta".equals(deviceModel)) {
            return UbuntuInstallService.MANTA_PARTITION_BOOT;
        }
        // FIXME: should throw a exception.
        return "";
    }

    public static String getRecoveryPartitionPath() {
        String deviceModel = Build.DEVICE.toLowerCase(Locale.US);
        if ("flo".equals(deviceModel)) {
            return UbuntuInstallService.FLO_PARTITION_RECOVERY;
        } else if ("grouper".equals(deviceModel)) {
            return UbuntuInstallService.GROUPER_PARTITION_RECOVERY;
        } else if ("hammerhead".equals(deviceModel)) {
            return UbuntuInstallService.HAMMERHEAD_PARTITION_RECOVERY;
        } else if ("maguro".equals(deviceModel)) {
            return UbuntuInstallService.MAGURO_PARTITION_RECOVERY;
        } else if ("mako".equals(deviceModel)) {
            return UbuntuInstallService.MAKO_PARTITION_RECOVERY;
        } else if ("manta".equals(deviceModel)) {
            return UbuntuInstallService.MANTA_PARTITION_RECOVERY;
        }
        // FIXME: should throw a exception.
        return "";
    }

    public static String getSha256Sum(File f) {
        MessageDigest md;
        FileInputStream fis = null;
        String sum = "";
        try {
            fis = new FileInputStream(f);
            md = MessageDigest.getInstance("SHA-256");

            byte[] dataBytes = new byte[1024];

            int nread = 0; 
            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            };
            byte[] mdbytes = md.digest();

            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < mdbytes.length; i++) {
                sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
            }

            sum = sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "error as doing checksum", e);
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
                // e.printStackTrace();
            }
        }

        return sum;
    }
}
