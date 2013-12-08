package com.canonical.ubuntuinstaller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "UbuntuInstaller";
    
    String BINARY_DESTINATION = "ubuntu";
    String RELEASE_FOLDER = "/ubuntu_release";
    String BASE_URL = "http://system-image.ubuntu.com";
    String URL_IMAGE_MASTER = "gpg/image-master";
    String URL_IMAGE_SIGNING = "gpg/image-signing";
    String URL_DEVICE_IMAGE_BASE = "pool/device";
    String URL_UBUNTU_IMAGE_BASE = "pool/ubuntu";
    String URL_MAKO_DEVEL = "devel/mako";
    String ASC_SUFFIX = ".asc";
    String IMAGE_SUFFIX = "tar.xz";
    
    String BUSYBOX = "busybox";
    String GPG = "gpg";
    String ANDROID_LOOP_MOUNT = "aloopmount";
    String UPDATE_SCRIPT = "system-image-upgrader";
    String ARCHIVE_MASTER = "archive-master.tar.xz";
    String ARCHIVE_MASTER_ASC = "archive-master.tar.xz.asc";
    
    String UPDATE_COMMAND = "update_command";
    String COMMAND_FORMAT = "format";
    String COMMAND_MOUNT = "mount";
    String COMMAND_UMOUNT = "unmount";
    String COMMAND_LOAD_KEYRING = "load_keyring";
    String COMMAND_UPDATE = "update";
    String PARTITION_DATA = "data";
    String PARTITION_SYSTEM = "system";

    private Button mDownloadButton;
    String mRootOfWorkPath;
    private Button mInstallButton;
    private Button mRebootToUbuntu;
    private TextView mInfoText;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private String mUpdateCommand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDownloadButton = (Button) findViewById(R.id.downloadBbutton);
        mInstallButton = (Button) findViewById(R.id.installButton);
        mRebootToUbuntu = (Button) findViewById(R.id.rebootToUbuntu);
        mInfoText = (TextView) findViewById(R.id.intoText);
        mPowerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        Log.d(TAG, "Local path " + getFilesDir().toString());
        // do we have cache permissions?
        File testDir = new File("/cache/testDir");
        if (testDir.mkdir()) {
        	testDir.delete();
        	mRootOfWorkPath = "/cache";
        } else {
        	mRootOfWorkPath = getFilesDir().toString(); //  "/data/data/com.canonical.ubuntuinstaller/files";
        }
        mUpdateCommand = mRootOfWorkPath + RELEASE_FOLDER + "/" + UPDATE_COMMAND;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        mDownloadButton.setOnClickListener(mDownloadButtonListener);
        mInstallButton.setOnClickListener(mInstallButtonListener);
        mRebootToUbuntu.setOnClickListener(mRebootButton);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mDownloadButton.setOnClickListener(null);
        mInstallButton.setOnClickListener(null);
        mRebootToUbuntu.setOnClickListener(null);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    OnClickListener mDownloadButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            handleDownloadButton();
        }
    };

    OnClickListener mInstallButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            handleInstallButton();
        }
    };

    OnClickListener mRebootButton = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // Reboot to recovery to complete update, try power manager if we have permissions
            try {
                PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                powerManager.reboot("recovery");
            } catch (Exception e) {
                // try it with SU permissions
                try {
                    Process process = Runtime.getRuntime().exec("su", null, getFilesDir());
                    DataOutputStream os = new DataOutputStream(process.getOutputStream());
                    os.writeBytes("reboot recovery\n");
                    os.flush();
                    try {
                        process.waitFor();
                             if (process.exitValue() != 255) { 
                                 showToast("Rebooting to Ubuntu");
                             }
                             else {
                                 showToast("No permissions to reboot to recovery");      
                             }   
                     } catch (InterruptedException ee) {
                         showToast("No permissions to reboot to recovery");
                     }
                  } catch (IOException eee) {
                      showToast("No permissions to reboot to recovery");   
                  }
            }
        }
    };
    
    private void handleDownloadButton(){
        Log.w(TAG, "handleDownloadButton");
        Thread task = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                doDownloadRelease();
                Looper.loop();
           }
        };
        task.start();
    }

    private void handleInstallButton() {
        Log.w(TAG, "handleInstallButton");
        Thread task = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                doInstallRelease();
                Looper.loop();
           }
        };
        task.start();        
    }
    
    private void doDownloadRelease() {
        Log.w(TAG, "doDownloadRelease");
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "ufa-installing");
        try {
               updateInfoOnUiThread("...starting download.....");
            showToast("Starting dowload");
            File rootFolder = new File(mRootOfWorkPath);
            
            // TODO: replace with dynamic tokens
            String RELEASE_UBUNTU_TOKEN = "b353d65b0369a5203757726d5c70b1ff3e601f05605c38fc55f92c584f19f6a1";
            String RELEASE_DEVICE_TOKEN = "68eeb610020edc5d49fb5e22c2e03ca3b88f7c817894e74fd660ac56635a8eb9";
            String RELEASE_VERSION_TOKEN = "version-32";
            
            String keyrings[] = {
                    String.format("%s/%s.%s",BASE_URL, URL_IMAGE_MASTER, IMAGE_SUFFIX ),
                    String.format("%s/%s.%s",BASE_URL, URL_IMAGE_SIGNING, IMAGE_SUFFIX),                    
            };
            String keyringsFilenames[] = new String[keyrings.length];
            String updateImages[] = {
                    String.format("%s/%s-%s.%s", BASE_URL, URL_UBUNTU_IMAGE_BASE, RELEASE_UBUNTU_TOKEN, IMAGE_SUFFIX),
                    String.format("%s/%s-%s.%s", BASE_URL, URL_DEVICE_IMAGE_BASE, RELEASE_DEVICE_TOKEN, IMAGE_SUFFIX),
                    String.format("%s/%s/%s.%s", BASE_URL, URL_MAKO_DEVEL, RELEASE_VERSION_TOKEN, IMAGE_SUFFIX)
            };
            String updateFilenames[] = new String[updateImages.length];
                    
            // First delete old release if it exists
            File release = new File(rootFolder,RELEASE_FOLDER);
            try {
                Process p = Runtime.getRuntime().exec("rm -rf " + release.getAbsolutePath() , null, rootFolder);
                try {   
                    p.waitFor();   
                    if (p.exitValue() != 255) {   
                        // TODO: removed successfully  
                    } else {   
                        // TODO: failed to remove old release, does it exist?  
                    }   
                } catch (InterruptedException e) {   
                }   
            }catch (IOException e) {
                e.printStackTrace();
                Log.w(TAG, "Untar failed");
            }
            mUpdateCommand = null;
            // make sure release folder exists
            release.mkdir();
            // download release
            long time = System.currentTimeMillis();
            try {
                int i = 0;
                for(String url : keyrings){
                    updateInfoOnUiThread(url);
                    keyringsFilenames[i++] = doDownloadUrl(new URL(url),release);
                    // download signature
                    doDownloadUrl(new URL(url+ASC_SUFFIX),release);             
                }
                i = 0;
                for(String url : updateImages) {
                    updateInfoOnUiThread(url);
                    updateFilenames[i++] = doDownloadUrl(new URL(url),release);
                    // download signature
                    doDownloadUrl(new URL(url+ASC_SUFFIX),release);
                }
            } catch (MalformedURLException e) {
                Log.e(TAG, "Failed to download release:", e);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to download release:", e);
            } catch (IOException e){
                Log.e(TAG, "Failed to download release:", e);
            }

            Log.i(TAG, "Download done in " + (System.currentTimeMillis() - time )/1000 + " seconds");
            updateInfoOnUiThread("Download finished in " + (System.currentTimeMillis() - time )/1000 + " seconds ! Ready to install");
            showToast("Download finished");
            
            // generate update_command
            File updateCommand = new File(release, UPDATE_COMMAND);
            try {
                FileOutputStream fos = new FileOutputStream(updateCommand);
                try {                    
                    fos.write((String.format("%s %s\n", COMMAND_FORMAT, PARTITION_DATA)).getBytes());
                    fos.write((String.format("%s %s\n", COMMAND_FORMAT, PARTITION_SYSTEM)).getBytes());
                    // load keyrings
                    for (String keyring : keyringsFilenames ) {
                        fos.write((String.format("%s %s %s%s\n", 
                                COMMAND_LOAD_KEYRING, keyring, keyring, ASC_SUFFIX )).getBytes());
                    }
                    fos.write((String.format("%s %s\n", COMMAND_MOUNT, PARTITION_SYSTEM)).getBytes());
                    // add update commands
                    for (String image : updateFilenames) {
                        fos.write((String.format("%s %s %s%s\n", 
                                COMMAND_UPDATE, image, image, ASC_SUFFIX )).getBytes());                        
                    }
                    fos.write((String.format("%s %s\n", COMMAND_UMOUNT, PARTITION_SYSTEM)).getBytes());
                    fos.flush();
                } finally {
                    fos.close();
                }
            } catch (IOException e) {
                // ignore
                e.printStackTrace();
            }
            mUpdateCommand = updateCommand.getAbsolutePath();
            
        } finally {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
    }
    
    private void doInstallRelease(){
        Log.w(TAG, "doInstallRelease:" + mUpdateCommand);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "ufa-installing");

        // extract supporting files
        if (mUpdateCommand == null) {
            // there is no release downloaded to be installed
            showToast("No release to install!");
            return;
        }
        File rootFolder = new File(mRootOfWorkPath);
        File supportingFiles = new File(rootFolder, RELEASE_FOLDER);
        supportingFiles.mkdir();        
        try {
            extractExecutableAsset(this, BUSYBOX, supportingFiles.toString(), true);
            extractExecutableAsset(this, GPG, supportingFiles.toString(), true);
            extractExecutableAsset(this, UPDATE_SCRIPT, supportingFiles.toString(), true);
            extractExecutableAsset(this, ANDROID_LOOP_MOUNT, supportingFiles.toString(), true);
            extractExecutableAsset(this, ARCHIVE_MASTER, supportingFiles.toString(), false);
            extractExecutableAsset(this, ARCHIVE_MASTER_ASC, supportingFiles.toString(), false);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // get superuser and run update script
        Log.w(TAG, "doInstallRelease-run update script");
        try {
            Process process = Runtime.getRuntime().exec("su", null, supportingFiles);
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            String updateCommand = "sh " + UPDATE_SCRIPT + " " + mUpdateCommand + "\n";
            os.writeBytes(updateCommand); 
            // close terminal
            os.writeBytes("Exit\n");
            os.flush();
            InputStream is = process.getInputStream();
            int read = 0;
            byte[] buff = new byte[4096];
            
            boolean running = true;
            do {
                while( is.available() > 0) {
                    read = is.read(buff);
                    if ( read <= 0 ) {
                        break;
                    }
                    String seg = new String(buff,0,read);
                    final String progress = seg;
                    Log.i(TAG, "Extract progress: " + seg);
                    updateInfoOnUiThread("Install progress: " + progress);
                } 
                try {
                    int ret = process.exitValue();
                    if (ret != 255 ) {
                        showToast("Install finished");
                    } else {
                        showToast("Install was not granted root");
                    }
                    running =false;
                } catch (IllegalThreadStateException e) {
                    // still running, wait a bit
                    try { Thread.sleep(200); } catch(Exception ex) {}
                }
            } while (running);
            int ret = process.exitValue();            
        }catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Update failed");
        } finally {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        showToast("Install finished");
    }
    
    private void updateInfoOnUiThread(final String text) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mInfoText.setText(text);
            }
        });
    }

    private void showToast(String message) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, message, duration);
        toast.show();
    }

    
    
    private String doDownloadUrl(URL url, File targerLocation) throws MalformedURLException,
    FileNotFoundException, IOException {
        
        URLConnection conn = url.openConnection();
        String fileName = URLUtil.guessFileName(url.toString(), null, null);
        
        // Using StorageWrapper to obtain OutPutStream automatically
        // attempts to delete any existing files with the same name.
        File file = new File(targerLocation, fileName);
        if (file.exists() && file.isFile()) {
            file.delete();
        }
        
        // DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fullPath)));
//        OutputStream output = storageWrapper_.openFile(filename);
        FileOutputStream output = new FileOutputStream(file);
        
        InputStream input = conn.getInputStream();
        
        byte[] buffer = new byte[1024];
        int len1 = 0;
        
        while ((len1 = input.read(buffer)) > 0) {
            output.write(buffer, 0, len1);
        }
        output.flush();
        output.close();
        input.close();
        return fileName;
    }

    
    public static void extractExecutableAsset(Context context, String filename, String destination, Boolean executable)
            throws IOException {
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
}
