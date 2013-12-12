package com.canonical.ubuntu.installer;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.canonical.ubuntu.installer.JsonChannelParser.Image;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.util.Log;
import android.webkit.URLUtil;



public class UbuntuInstallService extends IntentService {
    private static final String TAG = "UbuntuInstallService";
    
    // =================================================================================================
    // Shared preferences
    // =================================================================================================
    public final static String SHARED_PREF = "UInstallerPref";
    // Key for string value: absolute path to update file
    public final static String PREF_KEY_UPDATE_COMMAND = "update_command";
    // Key for String set value: version information: alias, Json, version, description 
    public final static String PREF_KEY_DOWNLOADED_VERSION = "d_version";
    // Key for String set value: version information: alias, Json, version, description
    public final static String PREF_KEY_INSTALLED_VERSION = "i_version";
    // Key for boolean value: true if developer option is enabled
    public final static String PREF_KEY_DEVELOPER = "developer";
    // Key for int value: estimated number of checkpoints for installation
    public final static String PREF_KEY_ESTIMATED_CHECKPOINTS = "est_checkpoints";

    // =================================================================================================
    // Service Actions
    // =================================================================================================
    // Get list of channels
    public static final String GET_CHANNEL_LIST = "com.canonical.ubuntuinstaller.UbuntuInstallService.GET_CHANNEL_LIST";
    // Download latest release from given channel
    public static final String DOWNLOAD_RELEASE = "com.canonical.ubuntuinstaller.UbuntuInstallService.DOWNLOAD_RELEASE";
    public static final String DOWNLOAD_RELEASE_EXTRA_CHANNEL_ALIAS = "alias"; // string
    public static final String DOWNLOAD_RELEASE_EXTRA_CHANNEL_URL = "url";     // string
    public static final String DOWNLOAD_RELEASE_EXTRA_BOOTSTRAP = "bootstrap"; // boolean 
    public static final String CANCEL_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.CANCEL_DOWNLOAD";
    public static final String PAUSE_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.PAUSE_DOWNLOAD";
    public static final String RESUME_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.RESUME_DOWNLOAD";
    public static final String CLEAN_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.CLEAN_DOWNLOADED";
    public static final String IS_RELEADY_TO_INSTALL = "com.canonical.ubuntuinstaller.UbuntuInstallService.IS_READY_TO_INSTALL";
    public static final String INSTALL_UBUNTU = "com.canonical.ubuntuinstaller.UbuntuInstallService.INSTALL_UBUNTU";
    public static final String UNINSTALL_UBUNTU = "com.canonical.ubuntuinstaller.UbuntuInstallService.UINSTALL_UBUNTU";
    public static final String UNINSTALL_UBUNTU_EXTRA_REMOVE_USER_DATA = "user_data";
    public static final String DELETE_UBUNTU_USER_DATA = "com.canonical.ubuntuinstaller.UbuntuInstallService.DELETE_USER_DATA";
    
    // =================================================================================================
    // Service broadcast
    // =================================================================================================
    public static final String AVAILABLE_CHANNELS = "com.canonical.ubuntuinstaller.UbuntuInstallService.AVAILABLE_CHANNELS";
    public static final String AVAILABLE_CHANNELS_EXTRA_CHANNELS = "channels"; // HashMap<String,String> channel aliases and json url
    public static final String DOWNLOAD_RESULT = "com.canonical.ubuntuinstaller.UbuntuInstallService.DOWNLOAD_RESULT";
    public static final String DOWNLOAD_RESULT_EXTRA_INT = "res_int"; // 0-success, -1 fail
    public static final String DOWNLOAD_RESULT_EXTRA_STR = "res_str"; // empty for success, or error text
    public static final String DOWNLOAD_PROGRESS = "com.canonical.ubuntuinstaller.UbuntuInstallService.DOWNLOAD_PROGRESS";
    public static final String PROGRESS_EXTRA_TEXT = "text"; // value will carry name of file currently downloaded 
    public static final String PROGRESS_EXTRA_INT = "progress"; // value between 0-100 of current progress
    public static final String INSTALL_RESULT = "com.canonical.ubuntuinstaller.UbuntuInstallService.INSTALL_COMPLETED";
    public static final String INSTALL_RESULT_EXTRA_INT = "res_int"; // 0-success, -1 fail
    public static final String INSTALL_RESULT_EXTRA_STR = "res_str"; // empty for success, or error text
    public static final String INSTALL_PROGRESS = "com.canonical.ubuntuinstaller.UbuntuInstallService.INSTALL_PROGRESS";
                              // PROGRESS_EXTRA_TEXT; output text from install script
                              // PROGRESS_EXTRA_INT; // value between 0-100 of current progress
    public static final String READY_TO_INSTALL = "com.canonical.ubuntuinstaller.UbuntuInstallService.READY_TO_INSTALL";
    public static final String READY_TO_INSTALL_EXTRA_READY = "ready"; // boolean, true if ready
    public static final String VERSION_UPDATE = "com.canonical.ubuntuinstaller.UbuntuInstallService.VERSION_UPDATE";
    
    // =================================================================================================
    // Download url strings
    // =================================================================================================
    private static final String BASE_URL = "http://system-image.ubuntu.com";
    private static final String CHANNELS_JSON = "/channels.json";
    private static final String URL_IMAGE_MASTER = "gpg/image-master.tar.xz";
    private static final String URL_IMAGE_SIGNING = "gpg/image-signing.tar.xz";
    private static final String ASC_SUFFIX = ".asc";
    
    // =================================================================================================
    // Packed assets
    // =================================================================================================
    private static final String BUSYBOX = "busybox";
    private static final String GPG = "gpg";
    private static final String TAR = "tar";
    private static final String ANDROID_LOOP_MOUNT = "aloopmount";
    private static final String UPDATE_SCRIPT = "system-image-upgrader";
    private static final String ARCHIVE_MASTER = "archive-master.tar.xz";
    private static final String ARCHIVE_MASTER_ASC = "archive-master.tar.xz.asc";
    private static final String U_REBOOT_APP = "u-reboot-app.tar.xz";
    private static final String U_REBOOT_APP_ASC = "u-reboot-app.tar.xz.asc";

    // =================================================================================================
    // Update command file constants
    // =================================================================================================
    String UPDATE_COMMAND = "update_command";
    String COMMAND_FORMAT = "format";
    String COMMAND_MOUNT = "mount";
    String COMMAND_UMOUNT = "unmount";
    String COMMAND_LOAD_KEYRING = "load_keyring";
    String COMMAND_UPDATE = "update";
    String PARTITION_DATA = "data";
    String PARTITION_SYSTEM = "system";
    
    // other constants
    private static final String RELEASE_FOLDER = "/ubuntu_release";
    private static final String TEMP_FOLDER = "/uTemp";
    private static final int PROGRESS_UBUNTU_ADJUSTMENT = 563979;
    private static final int PROGRESS_DEVICE_ADJUSTMENT = 651093;
    private static final int PROGRESS_CUSTOM_ADJUSTMENT = 2036039;
    private static final int PROGRESS_SWAP_CREATION_ADJUSTMENT = 85; // equivalent of time tar --checkpoint=200
    private static final int PROGRESS_MKSWAP_ADJUSTMENT = 17; // equivalent of time tar --checkpoint=200
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private String mRootOfWorkPath;
    private boolean mIsCanceled;
    
    // progress values
    private long mProgress; // so far handled amount downloaded/processed 
    private int mLastSignalledProgress;
    private long mTotalSize; // calculated 
    
    public class Channel {
        String alias;
        File[] files;
        boolean hiden;
    };
    
    public UbuntuInstallService() {
        super("UbuntuInstallService");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        mPowerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        // SharedPreferences sharedPref = this.getSharedPreferences(, MODE_PRIVATE);
        // do we have cache permissions?
        File testDir = new File("/cache/testDir");
        if (testDir.mkdir()) {
            testDir.delete();
            mRootOfWorkPath = "/cache";
        } else {
            mRootOfWorkPath = getFilesDir().toString(); //  "/data/data/com.canonical.ubuntuinstaller/files";
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent.getAction().equals(CANCEL_DOWNLOAD)) {
            // set the cancel flag, but let it remove downloaded files on worker thread
            mIsCanceled = true;
        }
        return super.onStartCommand(intent, flags, startId);
    }
    
    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        Intent result = null;
        if (action.equals(GET_CHANNEL_LIST)) {
            result = doGetChannelList(intent);
        } else if (action.equals(DOWNLOAD_RELEASE)) {
            result = doDownloadRelease(intent);
        } else if (action.equals(CANCEL_DOWNLOAD)) {
            // TODO: handle download
        } else if (action.equals(PAUSE_DOWNLOAD)) {
            // TODO: handle download
        } else if (action.equals(RESUME_DOWNLOAD)) {
            // TODO: handle download
        } else if (action.equals(CLEAN_DOWNLOAD)) {
            result = doRemoreDownload(intent);
        } else if (action.equals(IS_RELEADY_TO_INSTALL)) {
            result = checkifReadyToInstall(intent);
        } else if (action.equals(INSTALL_UBUNTU)) {
            result = doInstallUbuntu(intent);
        } else if (action.equals(UNINSTALL_UBUNTU)) {
            result = doUninstallUbuntu(intent);
        } else if (action.equals(DELETE_UBUNTU_USER_DATA)) {    

        } else {
        }
        if (result != null) {
            sendBroadcast(result);
        }
    }
    
    private Intent doGetChannelList(Intent intent) {
        Intent result = new Intent(AVAILABLE_CHANNELS);
        // 
        HashMap<String, String> channels= new HashMap<String, String>();
        boolean includeHidden = getSharedPreferences( SHARED_PREF, Context.MODE_PRIVATE).getBoolean(PREF_KEY_DEVELOPER, false);
        String deviceModel = "mako"; // TODO: get device from build properties
        String channelJsonStr = Utils.httpDownload(BASE_URL + CHANNELS_JSON);
        if (channelJsonStr != null) {
            JSONObject list;
            try {
                list = (JSONObject) new JSONTokener(channelJsonStr).nextValue();
                Iterator<String> keys = list.keys();
                while(keys.hasNext()){
                    String key = keys.next();
                    JSONObject channel = list.optJSONObject(key);
                    if (channel != null) {
                        JSONObject devices = channel.optJSONObject("devices");
                        if (devices != null) {
                            JSONObject device = devices.optJSONObject(deviceModel);
                            if (device != null) {
                                String url = device.optString("index");
                                if (url != null) {
                                    // bingo, add to list if not hidden or developer
                                    boolean hidden = channel.optBoolean("hiden"); // by default not hidden
                                    String alias = channel.optString("alias");
                                    if (alias == null || alias.equals("")) {
                                        alias = key; // use key instead
                                    }
                                    Log.v(TAG, "Channel:" + alias + "  url:" + url);                    
                                    if (!hidden || includeHidden) {
                                        channels.put(alias, url);
                                    }
                                }
                            }
                        }
                    }
                }                
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        result.putExtra(AVAILABLE_CHANNELS_EXTRA_CHANNELS, channels);
        return result;
    }
    
    private Intent checkifReadyToInstall(Intent intent) {
        SharedPreferences pref = getSharedPreferences( SHARED_PREF, Context.MODE_PRIVATE);
        String command = pref.getString(PREF_KEY_UPDATE_COMMAND, "");
        boolean ready = false;
        if (!command.equals("")){
            File f = new File(command);
            ready = f.exists();
        }
        Intent i = new Intent(READY_TO_INSTALL);
        i.putExtra(READY_TO_INSTALL_EXTRA_READY, ready);
        return i;
    }
    
    private Intent doRemoreDownload(Intent intent) {
        Intent result = new Intent();
        String s = deleteRelease();
        if (s!= null) {
            // delete failed
        }
        return result; 
    }

    private Intent doInstallUbuntu(Intent intent) {
        Log.w(TAG, "doInstallUbuntu");        
        Intent result = new Intent(INSTALL_RESULT);
        // get update command file
        SharedPreferences pref = getSharedPreferences( SHARED_PREF, Context.MODE_PRIVATE);
        String updateCommand = // "/cache/ubuntu_release/update_command";
          pref.getString(PREF_KEY_UPDATE_COMMAND,"");
        mTotalSize = pref.getInt(PREF_KEY_ESTIMATED_CHECKPOINTS, 0);
        mLastSignalledProgress = 0;
        if (updateCommand.equals("") || ! new File(updateCommand).exists()) {
            result.putExtra(INSTALL_RESULT_EXTRA_INT, -1);
            result.putExtra(INSTALL_RESULT_EXTRA_STR, "Missing update command");
            return result;
        }
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ubuntu-installing");
        try {
            File rootFolder = new File(mRootOfWorkPath);
            File supportingFiles = new File(rootFolder, RELEASE_FOLDER);
            broadcastProgress(INSTALL_PROGRESS, 0, "Extracting supporting files");
            try {
                Utils.extractExecutableAsset(this, BUSYBOX, supportingFiles.toString(), true);
                Utils.extractExecutableAsset(this, GPG, supportingFiles.toString(), true);
                Utils.extractExecutableAsset(this, TAR, supportingFiles.toString(), true);
                Utils.extractExecutableAsset(this, UPDATE_SCRIPT, supportingFiles.toString(), true);
                Utils.extractExecutableAsset(this, ANDROID_LOOP_MOUNT, supportingFiles.toString(), true);
                Utils.extractExecutableAsset(this, ARCHIVE_MASTER, supportingFiles.toString(), false);
                Utils.extractExecutableAsset(this, ARCHIVE_MASTER_ASC, supportingFiles.toString(), false);
                Utils.extractExecutableAsset(this, U_REBOOT_APP, supportingFiles.toString(), false);
                Utils.extractExecutableAsset(this, U_REBOOT_APP_ASC, supportingFiles.toString(), false);
            } catch (IOException e) {
                e.printStackTrace();
                result.putExtra(INSTALL_RESULT_EXTRA_INT, -1);
                result.putExtra(INSTALL_RESULT_EXTRA_STR, "Failed to extract supporting assets");
                return result;
            }
            // get superuser and run update script
            broadcastProgress(INSTALL_PROGRESS, -1, "Starting update script");
            try {
                Process process = Runtime.getRuntime().exec("su", null, supportingFiles);
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                os.writeBytes("sh " + UPDATE_SCRIPT 
                                    + " " + updateCommand 
                                    + " " + getFilesDir().toString() + "\n"); 
                // close terminal
                os.writeBytes("exit\n");
                os.flush();
                InputStream is = process.getInputStream();
                InputStream es = process.getErrorStream();
                int read = 0;
                byte[] buff = new byte[4096];
                boolean running = true;
                boolean scriptExecuted = false;
                do {
                    while( is.available() > 0) {
                        read = is.read(buff);
                        if ( read <= 0 ) {
                            break;
                        }
                        scriptExecuted = true;
                        String seg = new String(buff,0,read);
                        Log.i(TAG, "Script Output: " + seg);
                        broadcastProgress(INSTALL_PROGRESS, -1, seg);
                    }
                    while( es.available() > 0) {
                        read = es.read(buff);
                        if ( read <= 0 ) {
                            break;
                        }
                        scriptExecuted = true;
                        String seg = new String(buff,0,read);
                        if (seg.startsWith("SWAP-file-missing")) {
                            // this is signal that we will also install swap, adjust progress estimates
                            mTotalSize += PROGRESS_MKSWAP_ADJUSTMENT + PROGRESS_SWAP_CREATION_ADJUSTMENT;
                        } else {
                            mProgress++;
                            if ( mLastSignalledProgress < (mProgress * 100 / mTotalSize)) {
                                // update and signal new progress
                                mLastSignalledProgress = (int) (mProgress * 100 / mTotalSize);
                                broadcastProgress(INSTALL_PROGRESS, mLastSignalledProgress, null);
                            }
                        }                        
                        // Log.i(TAG, "Stderr Output: " + seg);
                    }
                    
                    try {
                        int ret = process.exitValue();
                        Log.v(TAG, "Worker thread exited with: " + ret);
                          // if script was not executed, then user did not granted SU permissions
                        if (ret == 255 || !scriptExecuted ) {
                            result.putExtra(INSTALL_RESULT_EXTRA_INT, -1);
                            result.putExtra(INSTALL_RESULT_EXTRA_STR, "Failed to get SU permissions");
                            return result;
                        } else if (ret != 0) {
                            result.putExtra(INSTALL_RESULT_EXTRA_INT, -1);
                            result.putExtra(INSTALL_RESULT_EXTRA_STR, "Instalation failed");
                            return result;
                        }
                        running =false;
                    } catch (IllegalThreadStateException e) {
                        // still running, wait a bit
                        try { Thread.sleep(200); } catch(Exception ex) {}
                    }
                } while (running);            
            }catch (IOException e) {
                e.printStackTrace();
                Log.w(TAG, "Update failed");
                result.putExtra(INSTALL_RESULT_EXTRA_INT, -1);
                result.putExtra(INSTALL_RESULT_EXTRA_STR, "Install failed");
                return result;

            }
        } finally {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(PREF_KEY_UPDATE_COMMAND, "");
        editor.putStringSet(PREF_KEY_INSTALLED_VERSION, pref.getStringSet(PREF_KEY_DOWNLOADED_VERSION, 
                                                            new VersionInfo().getSet()));
        editor.commit();

        result.putExtra(INSTALL_RESULT_EXTRA_INT, 0);
        return result;
    }

    private Intent doUninstallUbuntu(Intent intent) {
        Intent result = new Intent(VERSION_UPDATE);
        File rootFolder = new File(mRootOfWorkPath);
        File workingFolder = new File(rootFolder, TEMP_FOLDER);
        if (!workingFolder.exists() && !workingFolder.mkdir()) {
            // TODO: failed to get temp folder
            return null;
        }
        try {
            Utils.extractExecutableAsset(this, ANDROID_LOOP_MOUNT, workingFolder.toString(), true);
        } catch (IOException e) {
            e.printStackTrace();
            // TODO: what to do now?
            return null;
        }
        
        boolean removeUserData = intent.getBooleanExtra(UNINSTALL_UBUNTU_EXTRA_REMOVE_USER_DATA, false);
        // run uninstall steps
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ubuntu-installing");
        try {
            Process process = Runtime.getRuntime().exec("su", null, workingFolder);
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("echo -- \n");
            if (removeUserData) {
                os.writeBytes( String.format("echo \"%s %s\n %s %s\" > %s\n", 
                                        COMMAND_FORMAT, PARTITION_DATA,
                                        COMMAND_UMOUNT, PARTITION_SYSTEM,
                                        UPDATE_COMMAND));
            } else {
                os.writeBytes( String.format("echo \"%s %s\" > %s\n", 
                        COMMAND_UMOUNT, PARTITION_SYSTEM,
                        UPDATE_COMMAND));                
            }
            File updateCommand = new File(workingFolder, UPDATE_COMMAND);
            os.writeBytes("sh " + UPDATE_SCRIPT 
                                + " " + updateCommand.getAbsolutePath() 
                                + " " + getFilesDir().toString() + "\n");
            os.writeBytes("rm -rf /data/system.img\n");
            os.writeBytes("rm -rf /data/SWAP.img\n");
            os.writeBytes(String.format("rm -rf %s\n", workingFolder.getAbsolutePath()));
            // close terminal
            os.writeBytes("exit\n");
            os.flush();
            boolean running = true;
            do {
                try {
                    int ret = process.exitValue();
                    Log.v(TAG, "Worker thread exited with: " + ret);
                        // if script was not executed, then user did not granted SU permissions
                    if (ret == 255 ) {
                        // TODO:
                        return null;
                    } else if (ret != 0) {
                        // TODO:                        
                        return null;
                    }
                    running =false;
                } catch (IllegalThreadStateException e) {
                    // still running, wait a bit
                    try { Thread.sleep(200); } catch(Exception ex) {}
                }
            } while (running);            
        }catch (IOException e) {
            e.printStackTrace();
            // TODO:
            return null;

        } finally {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        // delete installed version in preferences
        SharedPreferences pref = getSharedPreferences( SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(PREF_KEY_UPDATE_COMMAND, "");
        editor.putStringSet(PREF_KEY_INSTALLED_VERSION, new VersionInfo().getSet());
        editor.commit();        
        return result;
    }
    
    private Intent doDownloadRelease(Intent intent) {
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ufa-downloading");
        mIsCanceled = false;
        Intent result = new Intent(DOWNLOAD_RESULT);
        try {
            File rootFolder = new File(mRootOfWorkPath);
            
            // first get from JSON list of files to download
            String alias = intent.getStringExtra(DOWNLOAD_RELEASE_EXTRA_CHANNEL_ALIAS);
            String jsonUrl = intent.getStringExtra(DOWNLOAD_RELEASE_EXTRA_CHANNEL_URL);
            boolean bootstrap = intent.getBooleanExtra(DOWNLOAD_RELEASE_EXTRA_BOOTSTRAP,true); // default bootstrap on
            boolean fullRelease = true; // TODO: delta updates should be detected dynamicaly here
            String jsonStr = Utils.httpDownload(BASE_URL + jsonUrl);
            List<Image> releases = JsonChannelParser.getAvailableReleases(jsonStr, JsonChannelParser.ReleaseType.FULL);
            if (releases.size() == 0 || releases.get(0).files.length == 0 ){
                // something is wrong, empty release
                Log.e(TAG, "Empty releas");
                result.putExtra(DOWNLOAD_RESULT_EXTRA_INT, -1);
                result.putExtra(DOWNLOAD_RESULT_EXTRA_STR, "Empty release");
                return result;
            }
            // get first since that is most recent one
            JsonChannelParser.File updateFiles[] = releases.get(0).files;           
            // sort update files
            List<JsonChannelParser.File> filesArray = new LinkedList<JsonChannelParser.File>();
            for(JsonChannelParser.File f: updateFiles) {
                filesArray.add(f);
            }
            Collections.sort(filesArray, JsonChannelParser.fileComparator());
            String updateFilenames[] = new String[updateFiles.length * 2];

            // get list of keyrings to download
            String keyrings[] = {
                    String.format("%s/%s",BASE_URL, URL_IMAGE_MASTER),
                    String.format("%s/%s",BASE_URL, URL_IMAGE_SIGNING),                    
            };            
            String keyringsFilenames[] = new String[keyrings.length * 2];
                              
            // First delete old release if it exists
            String s = deleteRelease();
            if (s != null) {
                // remove failed
                result.putExtra(DOWNLOAD_RESULT_EXTRA_INT, -1);
                result.putExtra(DOWNLOAD_RESULT_EXTRA_STR, s);
                return result;  
            }
            // make sure release folder exists
            File release = new File(rootFolder,RELEASE_FOLDER);
            release.mkdir();
            // download release
            long time = System.currentTimeMillis();
            mLastSignalledProgress = 0;
            mProgress = 0;
            broadcastProgress(DOWNLOAD_PROGRESS, mLastSignalledProgress, null);
            mTotalSize = Utils.calculateDownloadSize(filesArray);
            // mProgressSteps = mTotalDownloadSize / 100; // we want 1% steps
            try {
                int i = 0;
                for(String url : keyrings){
                    keyringsFilenames[i++] = doDownloadUrl(new URL(url),release);
                    // download signature
                    keyringsFilenames[i++] = doDownloadUrl(new URL(url+ASC_SUFFIX),release);
                }
                
                // download all update images
                i = 0;
                for (JsonChannelParser.File file : filesArray){
                    updateFilenames[i++] = doDownloadUrl(new URL(BASE_URL + file.path),release);
                    updateFilenames[i++] = doDownloadUrl(new URL(BASE_URL + file.signature),release);
                }
            } catch (MalformedURLException e) {
                Log.e(TAG, "Failed to download release:", e);
                result.putExtra(DOWNLOAD_RESULT_EXTRA_INT, -1);
                result.putExtra(DOWNLOAD_RESULT_EXTRA_STR, "Malformed release url");
                return result;
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to download release:", e);
                result.putExtra(DOWNLOAD_RESULT_EXTRA_INT, -1);
                result.putExtra(DOWNLOAD_RESULT_EXTRA_STR, "File not found");
                return result;
            } catch (IOException e){
                Log.e(TAG, "Failed to download release:", e);
                result.putExtra(DOWNLOAD_RESULT_EXTRA_INT, -1);
                result.putExtra(DOWNLOAD_RESULT_EXTRA_STR, "IO Error");
                return result;
            }

            Log.i(TAG, "Download done in " + (System.currentTimeMillis() - time )/1000 + " seconds");
            broadcastProgress(DOWNLOAD_PROGRESS, 99, "Generating update command");

            // generate update_command
            File updateCommand = new File(release, UPDATE_COMMAND);
            try {
                FileOutputStream fos = new FileOutputStream(updateCommand);
                try {
                    if (bootstrap) {
                        fos.write((String.format("%s %s\n", COMMAND_FORMAT, PARTITION_DATA)).getBytes());
                    }
                    if (fullRelease) {
                        fos.write((String.format("%s %s\n", COMMAND_FORMAT, PARTITION_SYSTEM)).getBytes());
                    }
                    // load keyrings
                    int i = 0;
                    while (i < keyringsFilenames.length) {
                        fos.write((String.format("%s %s %s\n", 
                                COMMAND_LOAD_KEYRING, 
                                keyringsFilenames[i++], 
                                keyringsFilenames[i++])).getBytes());
                    }
                    fos.write((String.format("%s %s\n", COMMAND_MOUNT, PARTITION_SYSTEM)).getBytes());

                    // add update commands
                    i = 0;
                    while (i < updateFilenames.length ) {
                        fos.write((String.format("%s %s %s\n", 
                                COMMAND_UPDATE, 
                                updateFilenames[i++], 
                                updateFilenames[i++])).getBytes());
                    }
                    
                    // add Ubuntu reboot app update package
                    if (fullRelease) {
                        fos.write((String.format("%s %s %s\n", 
                                COMMAND_UPDATE, 
                                U_REBOOT_APP, 
                                U_REBOOT_APP_ASC)).getBytes());
                    }

                    fos.write((String.format("%s %s\n", COMMAND_UMOUNT, PARTITION_SYSTEM)).getBytes());
                    fos.flush();
                } finally {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                result.putExtra(DOWNLOAD_RESULT_EXTRA_INT, -1);
                result.putExtra(DOWNLOAD_RESULT_EXTRA_STR, "Failed to generate update command");
                return result;
            }
            broadcastProgress(DOWNLOAD_PROGRESS, -1, "Download done in " + (System.currentTimeMillis() - time )/1000 + " seconds");
            int estimatedCheckCount = 0;
            for (JsonChannelParser.File file : filesArray){
                 if (file.path.contains("ubuntu-")) {
                     estimatedCheckCount += (file.size / PROGRESS_UBUNTU_ADJUSTMENT);
                 } else if (file.path.contains("device-")) {
                     estimatedCheckCount += (file.size / PROGRESS_DEVICE_ADJUSTMENT);
                 } else if (file.path.contains("custom-")) {
                     estimatedCheckCount += (file.size / PROGRESS_CUSTOM_ADJUSTMENT);
                 }
            }
            // store update command
            VersionInfo v = new VersionInfo(alias, jsonUrl, releases.get(0).description, releases.get(0).version);
            SharedPreferences pref = getSharedPreferences( SHARED_PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putStringSet(PREF_KEY_DOWNLOADED_VERSION, v.getSet());
            editor.putString(PREF_KEY_UPDATE_COMMAND, updateCommand.getAbsolutePath());
            editor.putInt(PREF_KEY_ESTIMATED_CHECKPOINTS, estimatedCheckCount);
            editor.commit();
            
        } finally {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        result.putExtra(DOWNLOAD_RESULT_EXTRA_INT, 0);
        return result;
    }
    
    class DownloadTask {
        URL url;
        File targetFolder;
    }

    private String doDownloadUrl(URL url, File targerLocation) throws MalformedURLException,
    FileNotFoundException, IOException {
        Log.v(TAG, "Downloading:" + url.toString());
        URLConnection conn = url.openConnection();
        String fileName = URLUtil.guessFileName(url.toString(), null, null);
        // TODO: update progress accordingly
        broadcastProgress(DOWNLOAD_PROGRESS, mLastSignalledProgress, "Downloading: " + fileName);        
        File file = new File(targerLocation, fileName);
        if (file.exists() && file.isFile()) {
            file.delete();
        }
        
        FileOutputStream output = new FileOutputStream(file);
        
        InputStream input = conn.getInputStream();
        
        byte[] buffer = new byte[1024];
        int len = 0;
        
        while ((len = input.read(buffer)) > 0) {
            if (mIsCanceled) {
                break;
            }
            output.write(buffer, 0, len);
            mProgress += len;
            // shall we broadcast progress?
            if ( mLastSignalledProgress < (mProgress * 100 / mTotalSize)) {
                // update and signal new progress
                mLastSignalledProgress = (int) (mProgress * 100 / mTotalSize);
                broadcastProgress(DOWNLOAD_PROGRESS, mLastSignalledProgress, null);
            }
        }
        output.flush();
        output.close();
        input.close();
        return fileName;
    }

    /**
     * 
     * @return null if success or error
     */
    private String deleteRelease() {
        // First delete old release if it exists
        File rootFolder = new File(mRootOfWorkPath);
        File release = new File(rootFolder,RELEASE_FOLDER);
        if (release.exists()) {
            try {
                String command = "rm -rf " + release.getAbsolutePath();
                Process p = Runtime.getRuntime().exec(command , null, rootFolder);
                try {   
                    p.waitFor();
                    int r = p.exitValue();
                    if (r == 255) {
                        return "failed to remove old download";
                    } 
                } catch (InterruptedException e) {   
                }   
            }catch (IOException e) {
                e.printStackTrace();
                Log.w(TAG, "failed to remove old download");
                return "failed to remove old download";
            }
            SharedPreferences pref = getSharedPreferences( SHARED_PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(PREF_KEY_UPDATE_COMMAND, "");
            editor.putStringSet(PREF_KEY_DOWNLOADED_VERSION, new VersionInfo().getSet());
            editor.commit();
        }
        return null;
    }
    
    private void broadcastProgress(String a, int val, String progress) {
        Intent i = new Intent(a);
        i.putExtra(PROGRESS_EXTRA_INT, val);
        if (progress!= null) {
            i.putExtra(PROGRESS_EXTRA_TEXT, progress);
        }
        sendBroadcast(i);
    }
}
