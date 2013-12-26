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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.canonical.ubuntu.installer.JsonChannelParser.Image;
import com.canonical.ubuntu.installer.VersionInfo.ReleaseType;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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
    private final static String PREF_KEY_DOWNLOADED_VERSION = "d_version";
    // Key for String set value: version information: alias, Json, version, description
    private final static String PREF_KEY_INSTALLED_VERSION = "i_version";
    // Key for boolean value: true if developer option is enabled
    public final static String PREF_KEY_DEVELOPER = "developer";
    // Key for int value: estimated number of checkpoints for installation
    public final static String PREF_KEY_ESTIMATED_CHECKPOINTS = "est_checkpoints";
    
    // =================================================================================================
    // Default values to be used
    // =================================================================================================
    public final static boolean DEFAULT_UNINSTALL_DEL_USER_DATA = false;
    public final static boolean DEFAULT_INSTALL_BOOTSTRAP = false;
    public final static String DEFAULT_CHANNEL_ALIAS = "trusty";
    public final static String UBUNTU_BOOT_IMG = "ubuntu-boot.img";
    public final static String ANDROID_REOCVERY_IMG = "android-recovery.img";
    public final static String FLO_PARTITION_BOOT = "/dev/block/platform/msm_sdcc.1/by-name/boot";
    public final static String FLO_PARTITION_RECOVERY = "/dev/block/platform/msm_sdcc.1/by-name/recovery";
    public final static String GROUPER_PARTITION_BOOT = "/dev/block/platform/sdhci-tegra.3/by-name/LNX";
    public final static String GROUPER_PARTITION_RECOVERY = "/dev/block/platform/sdhci-tegra.3/by-name/SOS";
    public final static String HAMMERHEAD_PARTITION_BOOT = "/dev/block/platform/msm_sdcc.1/by-name/boot";
    public final static String HAMMERHEAD_PARTITION_RECOVERY = "/dev/block/platform/msm_sdcc.1/by-name/recovery";
    public final static String MAGURO_PARTITION_BOOT = "/dev/block/platform/omap/omap_hsmmc.0/by-name/boot";
    public final static String MAGURO_PARTITION_RECOVERY = "/dev/block/platform/omap/omap_hsmmc.0/by-name/recovery";
    public final static String MAKO_PARTITION_BOOT = "/dev/block/platform/msm_sdcc.1/by-name/boot";
    public final static String MAKO_PARTITION_RECOVERY = "/dev/block/platform/msm_sdcc.1/by-name/recovery";
    public final static String MANTA_PARTITION_BOOT = "/dev/block/platform/dw_mmc.0/by-name/boot";
    public final static String MANTA_PARTITION_RECOVERY = "/dev/block/platform/dw_mmc.0/by-name/recovery";
    
    // =================================================================================================
    // Service Actions
    // =================================================================================================
    // Get list of channels
    public static final String GET_CHANNEL_LIST = "com.canonical.ubuntuinstaller.UbuntuInstallService.GET_CHANNEL_LIST";
    // Download latest release from given channel
    public static final String DOWNLOAD_RELEASE = "com.canonical.ubuntuinstaller.UbuntuInstallService.DOWNLOAD_RELEASE";
    public static final String DOWNLOAD_RELEASE_EXTRA_CHANNEL_ALIAS = "alias"; // string
    public static final String DOWNLOAD_RELEASE_EXTRA_CHANNEL_URL = "url"; // string
    public static final String DOWNLOAD_RELEASE_EXTRA_BOOTSTRAP = "bootstrap"; // boolean
    public static final String DOWNLOAD_RELEASE_EXTRA_VERSION = "version"; // int
    public static final String DOWNLOAD_RELEASE_EXTRA_TYPE = "type"; // JsonChannelParser.ReleaseType
    public static final String CANCEL_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.CANCEL_DOWNLOAD";
    public static final String PAUSE_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.PAUSE_DOWNLOAD";
    public static final String RESUME_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.RESUME_DOWNLOAD";
    public static final String CLEAN_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.CLEAN_DOWNLOADED";
    public static final String INSTALL_UBUNTU = "com.canonical.ubuntuinstaller.UbuntuInstallService.INSTALL_UBUNTU";
    public static final String CANCEL_INSTALL = "com.canonical.ubuntuinstaller.UbuntuInstallService.CANCEL_INSTALL";
    public static final String UNINSTALL_UBUNTU = "com.canonical.ubuntuinstaller.UbuntuInstallService.UINSTALL_UBUNTU";
    public static final String IS_UBUNTU_UPGRADABLE = "com.canonical.ubuntuinstaller.UbuntuInstallService.IS_UBUNTU_UPGRADABLE";
    public static final String UNINSTALL_UBUNTU_EXTRA_REMOVE_USER_DATA = "user_data";
    public static final String CHECK_FOR_UPDATE = "com.canonical.ubuntuinstaller.UbuntuInstallService.CHECK_FOR_UPDATE";
    public static final String DELETE_UBUNTU_USER_DATA = "com.canonical.ubuntuinstaller.UbuntuInstallService.DELETE_USER_DATA";
    public static final String GET_SERVICE_STATE = "com.canonical.ubuntuinstaller.UbuntuInstallService.GET_SERVICE_STATE";
    public static final String GET_PROGRESS_STATUS = "com.canonical.ubuntuinstaller.UbuntuInstallService.GET_PROGRESS_STATUS";
    public static final String REBOOT_UBUNTU = "com.canonical.ubuntuinstaller.UbuntuInstallService.REBOOT_UBUNTU";

    // =================================================================================================
    // Service broadcast
    // =================================================================================================
    public static final String SERVICE_STATE = "com.canonical.ubuntuinstaller.UbuntuInstallService.SERVICE_STATE";
    public static final String SERVICE_STATE_EXTRA_STATE = "state"; // InstallerState enum
    public static final String AVAILABLE_CHANNELS = "com.canonical.ubuntuinstaller.UbuntuInstallService.AVAILABLE_CHANNELS";
    public static final String AVAILABLE_CHANNELS_EXTRA_CHANNELS = "channels"; // HashMap<String,String> channel aliases and json url
    public static final String DOWNLOAD_RESULT = "com.canonical.ubuntuinstaller.UbuntuInstallService.DOWNLOAD_RESULT";
    public static final String DOWNLOAD_RESULT_EXTRA_INT = "res_int"; // 0-success, -1 fail
    public static final String DOWNLOAD_RESULT_EXTRA_STR = "res_str"; // empty for success, or error text
    public static final String PROGRESS = "com.canonical.ubuntuinstaller.UbuntuInstallService.PROGRESS";
    public static final String PROGRESS_EXTRA_TEXT = "text"; // value will carry name of file currently downloaded 
    public static final String PROGRESS_EXTRA_INT = "progress"; // value between 0-100 of current progress
    public static final String INSTALL_RESULT = "com.canonical.ubuntuinstaller.UbuntuInstallService.INSTALL_COMPLETED";
    public static final String INSTALL_RESULT_EXTRA_INT = "res_int"; // 0-success, -1 fail
    public static final String INSTALL_RESULT_EXTRA_STR = "res_str"; // empty for success, or error text
    public static final String VERSION_UPDATE = "com.canonical.ubuntuinstaller.UbuntuInstallService.VERSION_UPDATE";
    public static final String VERSION_UPDATE_EXTRA_VERSION = "version"; // int new version
    public static final String VERSION_UPDATE_EXTRA_DESCRIPTION = "description"; // string
    public static final String VERSION_UPDATE_EXTRA_ALIAS = "alias"; // string
    
    // =================================================================================================
    // Download url strings
    // =================================================================================================
    public static final String BASE_URL = "http://system-image.ubuntu.com";
    private static final String CHANNELS_JSON = "/channels.json";
    private static final String URL_IMAGE_MASTER = "gpg/image-master.tar.xz";
    private static final String URL_IMAGE_SIGNING = "gpg/image-signing.tar.xz";
    private static final String ASC_SUFFIX = ".asc";

    // 2G for file system, 512M for swap.
    private static long INSTALL_SIZE_REQUIRED = (2048L + 512L) * 1024L * 1024L;
    // 15M extra space to keep it safe.
    private static long EXTRA_SIZE_REQUIRED = 15 * 1024 * 1024;

    
    /**
     * State of the service 
     */
    public enum InstallerState {
        READY, FETCHING_CHANNELS, DOWNLOADING, INSTALLING, UNINSTALLING, DELETING_USER_DATA;
        public static InstallerState fromOrdian(int ordianl) {
            return InstallerState.values()[ordianl];
         }
    }

    // =================================================================================================
    // Packed assets
    // =================================================================================================
    private static final String BUSYBOX = "busybox";
    private static final String GPG = "gpg";
    private static final String TAR = "u_tar";
    private static final String ANDROID_LOOP_MOUNT = "aloopmount";
    private static final String ANDROID_BOOTMGR = "bootmgr";
    private static final String UPGRADECHECKER = "upgrade-checker";
    private static final String UPDATE_SCRIPT = "system-image-upgrader";
    private static final String ARCHIVE_MASTER = "archive-master.tar.xz";
    private static final String ARCHIVE_MASTER_ASC = "archive-master.tar.xz.asc";
    private static final String U_REBOOT_APP = "u-reboot-app.tar.xz";
    private static final String U_REBOOT_APP_ASC = "u-reboot-app.tar.xz.asc";

    // =================================================================================================
    // Update command file constants
    // =================================================================================================
    private static final String UPDATE_COMMAND = "update_command";
    private static final String COMMAND_FORMAT = "format";
    private static final String COMMAND_MOUNT = "mount";
    private static final String COMMAND_UMOUNT = "unmount";
    private static final String COMMAND_LOAD_KEYRING = "load_keyring";
    private static final String COMMAND_UPDATE = "update";
    private static final String PARTITION_DATA = "data";
    private static final String PARTITION_SYSTEM = "system";
    
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
    // FIXME make workPath in Cache a private function
    private boolean workPathInCache = false;
    private String mRootOfWorkPath;
    private boolean mIsCanceled;
    
    // progress values
    private long mProgress; // so far handled amount downloaded/processed 
    private int mLastSignalledProgress;
    private long mTotalSize; // calculated
    private InstallerState mInstallerState;
    
    public class Channel {
        String alias;
        File[] files;
        boolean hiden;
    };
    
    class ECancelException extends Exception {
        long mDownloadedSize;
        
        public ECancelException(long downloadedSize){
            super();
            mDownloadedSize = downloadedSize;
        }
    };

    class ESumNotMatchException extends Exception {
        public ESumNotMatchException(){
            super();
        }

        public ESumNotMatchException(String string) {
            super(string);
        }
    };

    class EShellExecException extends Exception {
        public EShellExecException(){
            super();
        }

        public EShellExecException(String string) {
            super(string);
        }
    };

    public UbuntuInstallService() {
        super(TAG);
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
            workPathInCache = true;
        } else {
            mRootOfWorkPath = getFilesDir().toString(); //  "/data/data/com.canonical.ubuntu.installer/files";
            workPathInCache = false;
        }
        mInstallerState = InstallerState.READY;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // if service is not in ready state, handle specific requests here
        if (mInstallerState != InstallerState.READY) {
            String action = intent.getAction(); 
            if (action.equals(CANCEL_DOWNLOAD)) {
                // set the cancel flag, but let it remove downloaded files on worker thread
                mIsCanceled = true;
            } else if (action.equals(GET_PROGRESS_STATUS)) {
                broadcastProgress(mLastSignalledProgress, ""); 
            } else if (action.equals(GET_SERVICE_STATE)) {
                broadcastInstallerState();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }
    
    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        Intent result = null;

        Log.d(TAG, this.toString() + " onHandleIntent: " + action);
        if (action.equals(GET_CHANNEL_LIST)) {
            updateInstallerState(InstallerState.FETCHING_CHANNELS);
            result = doGetChannelList(intent);
        } else if (action.equals(DOWNLOAD_RELEASE)) {
            updateInstallerState(InstallerState.DOWNLOADING);
            result = doDownloadRelease(intent);
        } else if (action.equals(CANCEL_DOWNLOAD)) {
            // download should be already cancelled, don't delete files for might resume latter
            // result = doRemoreDownload(intent);
            result = new Intent(SERVICE_STATE);
            result.putExtra(SERVICE_STATE_EXTRA_STATE, mInstallerState.ordinal());
        } else if (action.equals(PAUSE_DOWNLOAD)) {
            // TODO: handle download
        } else if (action.equals(RESUME_DOWNLOAD)) {
            updateInstallerState(InstallerState.DOWNLOADING);
            // TODO: handle download
        } else if (action.equals(CLEAN_DOWNLOAD)) {
            result = doRemoreDownload(intent);
        } else if (action.equals(INSTALL_UBUNTU)) {
            updateInstallerState(InstallerState.INSTALLING);
            result = doInstallUbuntu(intent);
        } else if (action.equals(IS_UBUNTU_UPGRADABLE)) {
            // check if the upgradeable images available.
            if(findUpgradeable()) {
                Log.d(TAG, "There is a upgradeable file. send VERSION_UPDATE");
                result = new Intent(VERSION_UPDATE);
            }
        } else if (action.equals(CANCEL_INSTALL)) {
            // install should be already cancelled, try to delete it now
            updateInstallerState(InstallerState.UNINSTALLING);
            result = doUninstallUbuntu(intent);
        } else if (action.equals(UNINSTALL_UBUNTU)) {
            updateInstallerState(InstallerState.UNINSTALLING);
            result = doUninstallUbuntu(intent);
        } else if (action.equals(DELETE_UBUNTU_USER_DATA)) {  
            updateInstallerState(InstallerState.DELETING_USER_DATA);
            result = doDeleteUbuntuUserData(intent);
        } else if(action.equals(REBOOT_UBUNTU)) {
            Log.d(TAG, this.toString() + ": REBOOT_UBUNTU");
            doReboot(intent);
            return;
        } else {
            // for any other request broadcast service state
            result = new Intent(SERVICE_STATE);
            result.putExtra(SERVICE_STATE_EXTRA_STATE, mInstallerState.ordinal());
        }
        if (result != null) {
            sendBroadcast(result);
        }
        updateInstallerState(InstallerState.READY);
        Log.d(TAG, this.toString() + " onHandleIntent: " + action + " END");
    }

    private Intent doGetChannelList(Intent intent) {
        Intent result = new Intent(AVAILABLE_CHANNELS);

        HashMap<String, String> channels= new HashMap<String, String>();
        boolean includeHidden = getSharedPreferences( SHARED_PREF, Context.MODE_PRIVATE).getBoolean(PREF_KEY_DEVELOPER, false);
        String deviceModel = Build.DEVICE.toLowerCase(Locale.US);
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
                                    // Log.v(TAG, "Channel:" + alias + "  url:" + url);                    
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
    
    private Intent doRemoreDownload(Intent intent) {
        Intent result = new Intent(SERVICE_STATE);
        String s = deleteRelease();
        if (s!= null) {
            broadcastProgress(-1, s);
            // delete failed
        }
        result.putExtra(SERVICE_STATE, InstallerState.READY.ordinal());
        return result;
    }

    private Intent doInstallUbuntu(Intent intent) {
        Log.w(TAG, "doInstallUbuntu");
        Intent result = new Intent(INSTALL_RESULT);

        // get update command file
        String updateCommand = getUpdateCommand();

        // 1. check if update command exist.
        // 2. However, if the udpate command is in "/cache",
        // the app can not access to /cache sometimes.
        if (updateCommand.equals("") ||
                (!new File(updateCommand).exists() &&
                 !updateCommand.startsWith("/cache"))) {
            return handleInstallFail(result, -1, "Missing update command");
        }

        SharedPreferences pref = getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        mTotalSize = pref.getInt(PREF_KEY_ESTIMATED_CHECKPOINTS, 0);
        mLastSignalledProgress = 0;

        List<String> shellcmds = new ArrayList<String>();
        {
            shellcmds.add("echo Installing\n");
            // run system-image-upgrader.
            shellcmds.add(String.format("%s %s %s\n",
                    UPDATE_SCRIPT,
                    updateCommand,
                    getFilesDir().getAbsolutePath()
                    ));
            // Only backup original recovery, when there is no backuped file.
            if(!new File(getFilesDir().toString(), ANDROID_REOCVERY_IMG).exists()) {
                shellcmds.add(String.format("%s -b %s %s/%s\n",
                        ANDROID_BOOTMGR,
                        Utils.getRecoveryPartitionPath(),
                        getFilesDir().getAbsolutePath(),
                        ANDROID_REOCVERY_IMG
                        ));
            }
            // overwrite the recovery partition.
            shellcmds.add(String.format("%s -b %s/%s %s\n",
                    ANDROID_BOOTMGR,
                    getFilesDir().getAbsolutePath(),
                    UBUNTU_BOOT_IMG,
                    Utils.getRecoveryPartitionPath()
                    ));
            shellcmds.add("exit");
        }

        broadcastProgress(-1, "Starting update script - " + updateCommand);
        try {
            int ret = executeSUCommands(shellcmds.toArray(new String[shellcmds.size()]));
            result.putExtra(INSTALL_RESULT_EXTRA_INT, ret);
        } catch (EShellExecException e) {
            return handleInstallFail(result, -1, e.getMessage());
        }

        // we done.
        cleanUpdateCommand();
        VersionInfo v = new VersionInfo(pref, PREF_KEY_DOWNLOADED_VERSION);
        v.storeVersion(pref.edit(), PREF_KEY_INSTALLED_VERSION);
        mProgress = 100;
        return result;
    }

    private Intent handleInstallFail(Intent i, int res, String failReason) {
        i.putExtra(INSTALL_RESULT_EXTRA_INT, -1);
        i.putExtra(INSTALL_RESULT_EXTRA_STR, failReason);
        // we don't want to unstainll if we failed to install a update.
        // doUninstallUbuntu(i);
        return i;
    }

    private Intent doUninstallUbuntu(Intent intent) {
        File workingFolder = new File(mRootOfWorkPath, TEMP_FOLDER);
        File updateCommand = new File(workingFolder, UPDATE_COMMAND);
        Intent result = new Intent(VERSION_UPDATE);
        boolean removeUserData = intent.getBooleanExtra(UNINSTALL_UBUNTU_EXTRA_REMOVE_USER_DATA, false);
        Log.d(TAG, "doUninstallUbuntu");

        String format_cmd = null;
        if (removeUserData) {
            Log.d(TAG, "removing user data.");
            format_cmd = String.format("echo \"%s %s\n %s %s\" > %s\n",
                                    COMMAND_FORMAT, PARTITION_DATA,
                                    COMMAND_UMOUNT, PARTITION_SYSTEM,
                                    UPDATE_COMMAND);
        } else {
            format_cmd = String.format("echo \"%s %s\" > %s\n",
                    COMMAND_UMOUNT, PARTITION_SYSTEM,
                    UPDATE_COMMAND);
        }

        // 1. force unmount
        // 2. restore android recovery partition, and deleted it.
        // 3. delete system.img and SWAP.img.
        try {
            int r = executeSUCommands(
                    new String[]{
                        ("echo Uninstalling\n"),
                        format_cmd,
                        ("sh " + UPDATE_SCRIPT 
                            + " " + updateCommand.getAbsolutePath() 
                            + " " + getFilesDir().toString() + "\n"),
                            String.format("%s -b %s/%s %s || true\n",
                                    ANDROID_BOOTMGR,
                                    getFilesDir().toString(),
                                    ANDROID_REOCVERY_IMG,
                                    Utils.getRecoveryPartitionPath()),
                            (String.format("rm -f %s/%s  || true\n", getFilesDir().toString(), ANDROID_REOCVERY_IMG)),
                            ("rm -rf /data/system.img || true\n"),
                            ("rm -rf /data/SWAP.img || true\n"),
            } );

            if (r == 0) {
                // delete installed version in preferences
                cleanUpdateCommand();
                VersionInfo.storeEmptyVersion(
                        getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE).edit(), PREF_KEY_INSTALLED_VERSION);
            }
            result.putExtra("result", r);
        } catch (EShellExecException e) {
            result.putExtra("result", -1);
        }

        return result;
    }

    private Intent doDeleteUbuntuUserData(Intent intent) {
        Intent result = new Intent(VERSION_UPDATE);
        File workingFolder = new File(mRootOfWorkPath, TEMP_FOLDER);
        File updateCommand = new File(workingFolder, UPDATE_COMMAND);

        try {
            int r = executeSUCommands(new String[]{
                String.format("echo \"%s %s\" > %s\n", COMMAND_FORMAT, PARTITION_DATA, UPDATE_COMMAND),
                ("sh " + UPDATE_SCRIPT + " " + updateCommand.getAbsolutePath() + " " + getFilesDir().toString() + "\n")
            } );
            result.putExtra("result", r);
        } catch (EShellExecException e) {
            result.putExtra("fail_description", e.getMessage());
            result.putExtra("result", -1);
        }
        return result;
    }

    private void doReboot(Intent intent) {
        // Reboot to recovery to complete update, try power manager if we have permissions
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            powerManager.reboot("recovery");
        } catch (SecurityException e) {
            // FIXME: in Android 4.4, we do not get power manager permission.
            // try it with SU permissions
            try {
                int r = executeSUCommands(new String[] {
                        String.format("%s -b %s/%s %s || true\n",
                                ANDROID_BOOTMGR,
                                getFilesDir().getAbsolutePath(),
                                UBUNTU_BOOT_IMG,
                                Utils.getRecoveryPartitionPath()),
                        "reboot recovery\n"
                });
                if(r != 255) {
                    Utils.showToast(this.getApplicationContext(), "Rebooting to Ubuntu");
                } else {
                    Utils.showToast(this.getApplicationContext(),  "No permissions to reboot to recovery");
                }
            } catch (EShellExecException e1) {
                Utils.showToast(this.getApplicationContext(),  "No permissions to reboot to recovery");
            }
        }
    }

    /**
     * This command exec commands in the working folder with supporting utils.
     *
     * @param commands commands to be executed
     * @return shell script exit value.
     * @throws EShellExecException
     */
    private int executeSUCommands(String[] commands) throws EShellExecException {
        int ret = 0;
        File rootFolder = new File(mRootOfWorkPath);
        File workingFolder = new File(rootFolder, TEMP_FOLDER);
        String workingFolderPath = workingFolder.getAbsolutePath();

        if (!workingFolder.exists() && !workingFolder.mkdir()) {
            throw(new EShellExecException("Failed to create working folder"));
        }

        broadcastProgress(0, "Extracting supporting files at " + workingFolder.getAbsolutePath());
        try {
            // extract utils into working folder.
            Utils.extractExecutableAsset(this, ANDROID_BOOTMGR, workingFolderPath, true);
            Utils.extractExecutableAsset(this, ANDROID_LOOP_MOUNT, workingFolderPath, true);
            Utils.extractExecutableAsset(this, ARCHIVE_MASTER_ASC, workingFolderPath, false);
            Utils.extractExecutableAsset(this, ARCHIVE_MASTER, workingFolderPath, false);
            Utils.extractExecutableAsset(this, BUSYBOX, workingFolderPath, true);
            Utils.extractExecutableAsset(this, GPG, workingFolderPath, true);
            Utils.extractExecutableAsset(this, TAR, workingFolderPath, true);
            Utils.extractExecutableAsset(this, UPDATE_SCRIPT, workingFolderPath, true);
            Utils.extractExecutableAsset(this, U_REBOOT_APP_ASC, workingFolderPath, false);
            Utils.extractExecutableAsset(this, U_REBOOT_APP, workingFolderPath, false);
            Utils.extractExecutableAsset(this, UPGRADECHECKER, workingFolderPath, true);
        } catch (IOException e) {
            throw(new EShellExecException("Failed to extract supporting utils"));
        }

        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shelling");
        try {
            Process process = Runtime.getRuntime().exec("su", null, workingFolder);
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            // debug purpose.
            os.writeBytes("set -x\n");

            os.writeBytes("echo \"SU granted\"\n");
            // make sure we are in work folder
            os.writeBytes(String.format("cd %s\n", workingFolder.getAbsolutePath()));
            // setup search path for commands
            os.writeBytes(String.format("export PATH=%s:$PATH\n", workingFolder.getAbsolutePath()));

            for (String cmd : commands) {
                Log.d(TAG, "Executing: " + cmd + "\n");
                os.writeBytes(cmd + "\n");
                os.writeBytes("CMDSTATES=$?\n");
            }
            // clean up supporting utils.
            os.writeBytes(String.format("rm -rf %s\n", workingFolder.getAbsolutePath()));
            os.writeBytes("exit $CMDSTATES\n");
            os.flush();

            int read = 0;
            byte[] buff = new byte[4096];
            InputStream is = process.getInputStream();
            InputStream es = process.getErrorStream();
            boolean running = true;
            boolean scriptExecuted = false;
            do {
                while( is.available() > 0) {
                    read = is.read(buff);
                    if ( read <= 0 ) {
                        break;
                    }
                    scriptExecuted = true;
                    String seg = new String(buff, 0, read);
                    Log.i(TAG, "Script Output: " + seg);
                    broadcastProgress(-1, seg);
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
                        if (mTotalSize > 0 && mLastSignalledProgress < (mProgress * 100 / mTotalSize)) {
                            // update and signal new progress
                            mLastSignalledProgress = (int) (mProgress * 100 / mTotalSize);
                            broadcastProgress(mLastSignalledProgress, null);
                        }
                    }

                    Log.i(TAG, "Stderr Output: " + seg);
                }
                try {
                    ret = process.exitValue();
                    Log.d(TAG, "Worker thread exited with: " + ret);
                    // if script was not executed, then user did not granted SU permissions
                    if (!scriptExecuted) {
                        throw new EShellExecException("Failed to get SU permissions");
                    }
                    running = false;
                } catch (IllegalThreadStateException e) {
                    // still running, wait a bit
                    try { Thread.sleep(200); } catch(Exception ex) {}
                }
            } while (running);
        } catch (IOException e) {
            throw(new EShellExecException("Script execution exception " + e.getMessage()));
        } finally {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        return ret;
    }

    private Intent doDownloadRelease(Intent intent) {
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ufa-downloading");
        mIsCanceled = false;
        SharedPreferences.Editor editor = getSharedPreferences( SHARED_PREF, Context.MODE_PRIVATE).edit();

        Intent result = new Intent(DOWNLOAD_RESULT);
        VersionInfo prevDownload = getDownloadVersion(this.getApplicationContext());
        try {
            File rootFolder = new File(mRootOfWorkPath);
            
            // first get from JSON list of files to download
            String alias = intent.getStringExtra(DOWNLOAD_RELEASE_EXTRA_CHANNEL_ALIAS);
            String jsonUrl = intent.getStringExtra(DOWNLOAD_RELEASE_EXTRA_CHANNEL_URL);
            boolean bootstrap = intent.getBooleanExtra(DOWNLOAD_RELEASE_EXTRA_BOOTSTRAP,true); // default bootstrap on
            int version = intent.getIntExtra(DOWNLOAD_RELEASE_EXTRA_VERSION,  -1);
            ReleaseType releaseType = ReleaseType.fromValue(
                    intent.getIntExtra(DOWNLOAD_RELEASE_EXTRA_TYPE, ReleaseType.FULL.getValue())); // by default look for full releases

            String jsonStr = Utils.httpDownload(BASE_URL + jsonUrl);
            List<Image> releases = JsonChannelParser.getAvailableReleases(jsonStr, ReleaseType.FULL);
            if (releases.size() == 0 || releases.get(0).files.length == 0 ) {
                // something is wrong, empty release
                Log.e(TAG, "Empty releas");
                return handleDownloadError(result, -1, "Empty release");
            }
            // get right version, otherwise first since that is most recent one
            Image choosenRelease = null;
            if (version != -1) {
                // look for given release
                for (Image i : releases) {
                    if (i.version == version) {
                        choosenRelease = i;
                        break;
                    }
                }
                if (choosenRelease == null) {
                    Log.e(TAG, "wrong release vwersion");
                    return handleDownloadError(result, -1, "wrong release vwersion");
                }
            } else {
                choosenRelease = releases.get(0);
            }
            JsonChannelParser.File updateFiles[] = choosenRelease.files;
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
            {
                boolean toDeleteOld = true;
                if (prevDownload != null) {
                    if (prevDownload.equals(jsonUrl, choosenRelease.version, releaseType) &&
                            prevDownload.mDownloadedSize > 0) {
                        toDeleteOld = false;
                    }
                }
                if (toDeleteOld) {
                    String s = deleteRelease();
                    if (s != null) {
                        // remove failed
                        return handleDownloadError(result, -1, s);
                    }
                }
            }

            // make sure release folder exists
            File release = new File(rootFolder, RELEASE_FOLDER);
            release.mkdir();
            // download release
            long time = System.currentTimeMillis();
            mLastSignalledProgress = 0;
            mProgress = 0;
            broadcastProgress(mLastSignalledProgress, null);
            mTotalSize = Utils.calculateDownloadSize(filesArray);
            long neededSize = mTotalSize;
            if (prevDownload != null) {
                neededSize = mTotalSize - prevDownload.mDownloadedSize;
            }
            boolean isStorageEnough = isStorageSpaceEnoughBFDownload(neededSize);
            if (! isStorageEnough) {
                String msg = "Need more storage: ";
                if (workPathInCache) {
                    msg += "/cache need " + String.valueOf(mTotalSize) + " bytes for download and /data need 2.5G for system";
                } else {
                    msg += "/data need 2.5G for system plus " + String.valueOf(mTotalSize) + " bytes for download";
                }
                Log.i(TAG, msg);
                return handleDownloadError(result, -1, msg);
            }

            // mProgressSteps = mTotalDownloadSize / 100; // we want 1% steps
            long downloadedSize = 0;
            JsonChannelParser.File currentDownloadingFile = null;
            try {
                int i = 0;
                for(String url : keyrings){
                    keyringsFilenames[i++] = doDownloadUrl(new URL(url), release);
                    // download signature
                    keyringsFilenames[i++] = doDownloadUrl(new URL(url+ASC_SUFFIX), release);
                }

                // download all update images
                i = 0;
                for (JsonChannelParser.File file : filesArray){
                    URL url = new URL(BASE_URL + file.path);
                    String fileName = URLUtil.guessFileName(url.toString(), null, null);
                    File f = new File(release, fileName);

                    boolean fileNeedDownload = true;
                    if (prevDownload != null) {
                        long length = f.length();
                        if (length == file.size) {
                            fileNeedDownload = false;
                        } else if (length > file.size) {
                            f.delete();
                        }
                    }
                    if (fileNeedDownload) {
                        currentDownloadingFile = file;
                        updateFilenames[i] = doDownloadFile(file, release);
                        currentDownloadingFile = null;
                    } else {
                        updateFilenames[i] = fileName;
                        mProgress += file.size;
                        mLastSignalledProgress = (int) (mProgress * 100 / mTotalSize);
                        broadcastProgress(mLastSignalledProgress, null);
                    }

                    // check file size and check sum
                    long length = f.length();
                    if (length != file.size) {
                        f.delete();
                        throw new ESumNotMatchException();
                    }
                    broadcastProgress(mLastSignalledProgress, "Checksum Verifying: " + fileName);
                    String sha256sum = Utils.getSha256Sum(f);
                    if (! sha256sum.equals(file.checksum)) {
                        broadcastProgress(mLastSignalledProgress, "Checksum Verify failed: " + fileName);
                        f.delete();
                        throw new ESumNotMatchException();
                    }
                    broadcastProgress(mLastSignalledProgress, "Checksum Verified: " + fileName);
                    downloadedSize += file.size;
                    i++;

                    // signature file size is not accounted for resume since it's quite small
                    updateFilenames[i] = doDownloadFileSignature(file, release);
                    i++;
                }
            } catch (MalformedURLException e) {
                Log.e(TAG, "Failed to download release:", e);
                return handleDownloadError(result, -1, "Malformed release url");
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to download release:", e);
                return handleDownloadError(result, -1, "File not found");
            } catch (IOException e){
                if (currentDownloadingFile != null) {
                    try {
                        URL url = new URL(BASE_URL + currentDownloadingFile.path);
                        String fileName = URLUtil.guessFileName(url.toString(), null, null);
                        File f = new File(release, fileName);
                        downloadedSize += f.length();
                    } catch (MalformedURLException e1) {
                        // shouldn't happen for it should already happen.
                    }
                }

                if (downloadedSize > 0) {
                    VersionInfo v = new VersionInfo(alias, jsonUrl, choosenRelease.description, choosenRelease.version,
                            downloadedSize, releaseType);
                    v.storeVersion(editor, PREF_KEY_DOWNLOADED_VERSION);
                } else {
                    editor.putString(PREF_KEY_UPDATE_COMMAND, "");
                    VersionInfo.storeEmptyVersion(editor, PREF_KEY_DOWNLOADED_VERSION);
                }

                Log.e(TAG, "Failed to download release:", e);
                return handleDownloadError(result, -1, "IO Error");
            } catch (ESumNotMatchException e) {
                // Download file check sum error !!
                return handleDownloadError(result, -1, "Download check sum error");
            } catch (ECancelException e) {
                // Download was cancelled by user
                downloadedSize += e.mDownloadedSize;
                if (downloadedSize > 0) {
                    VersionInfo v = new VersionInfo(alias, jsonUrl, choosenRelease.description, choosenRelease.version,
                            downloadedSize, releaseType);
                    v.storeVersion(editor, PREF_KEY_DOWNLOADED_VERSION);
                }
                return handleDownloadError(result, -2, "Download cancelled by user");
            }

            Log.i(TAG, "Download done in " + (System.currentTimeMillis() - time )/1000 + " seconds");
            broadcastProgress(-1, "Generating update command");

            // generate update_command
            File updateCommand = new File(release, UPDATE_COMMAND);
            try {
                FileOutputStream fos = new FileOutputStream(updateCommand);
                try {
                    if (bootstrap) {
                        fos.write((String.format("%s %s\n", COMMAND_FORMAT, PARTITION_DATA)).getBytes());
                    }
                    if (releaseType == ReleaseType.FULL) {
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
                    while (i < updateFilenames.length) {
                        fos.write((String.format("%s %s %s\n",
                                COMMAND_UPDATE,
                                updateFilenames[i++],
                                updateFilenames[i++])).getBytes());
                    }

                    // add Ubuntu reboot app update package
                    if (releaseType == ReleaseType.FULL) {
                        fos.write((String.format("%s %s %s\n",
                                COMMAND_UPDATE,
                                U_REBOOT_APP,
                                U_REBOOT_APP_ASC)).getBytes());
                    }
                    if(releaseType == ReleaseType.DELTA) {
                        // TODO:
                    }
                    fos.write((String.format("%s %s\n", COMMAND_UMOUNT, PARTITION_SYSTEM)).getBytes());
                    fos.flush();
                } finally {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return handleDownloadError(result, -1, "Failed to generate update command");
            }
            broadcastProgress(-1, "Download done in " + (System.currentTimeMillis() - time )/1000 + " seconds");
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
            setUpdateCommand(updateCommand.getAbsolutePath());

            // updated downloaded information.
            VersionInfo v = new VersionInfo(alias, jsonUrl, choosenRelease.description, choosenRelease.version, 0, releaseType);

            editor.putInt(PREF_KEY_ESTIMATED_CHECKPOINTS, estimatedCheckCount);
            v.storeVersion(editor, PREF_KEY_DOWNLOADED_VERSION);
            mProgress = 100;
        } finally {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        result.putExtra(DOWNLOAD_RESULT_EXTRA_INT, 0);
        return result;
    }
    
    private Intent handleDownloadError(Intent i, int res, String reason) {
        i.putExtra(DOWNLOAD_RESULT_EXTRA_INT, res);
        i.putExtra(DOWNLOAD_RESULT_EXTRA_STR, reason);
        return i;
    }

    private String doDownloadFile(JsonChannelParser.File file, File targetLocation) throws MalformedURLException,
    FileNotFoundException, IOException, ECancelException {
        URL url = new URL(BASE_URL + file.path);
        return doDownloadUrl(url, targetLocation, true);
    }

    private String doDownloadFileSignature(JsonChannelParser.File file, File targetLocation) throws MalformedURLException,
    FileNotFoundException, IOException, ECancelException {
        URL url = new URL(BASE_URL + file.signature);
        return doDownloadUrl(url, targetLocation);
    }
    
    private String doDownloadUrl(URL url, File targertLocation) throws MalformedURLException,
    FileNotFoundException, IOException, ECancelException {
        return doDownloadUrl(url, targertLocation, false);
    }

    private String doDownloadUrl(URL url, File targertLocation, boolean resume) throws MalformedURLException,
    FileNotFoundException, IOException, ECancelException {
        Log.v(TAG, "Downloading:" + url.toString());
        URLConnection conn = url.openConnection();
        String fileName = URLUtil.guessFileName(url.toString(), null, null);
        // TODO: update progress accordingly
        broadcastProgress(mLastSignalledProgress, "Downloading: " + fileName);
        File file = new File(targertLocation, fileName);
        if ((! resume) && file.exists() && file.isFile()) {
            file.delete();
        }

        long resumePosition = 0;
        if (resume) {
            resumePosition = file.length();
            if (resumePosition > 0) {
                String rangeHeader = String.format(Locale.US, "bytes=%d-", resumePosition);
                conn.setRequestProperty("Range", rangeHeader);
                Log.i(TAG, String.format("Resuming download from %d bytes.", resumePosition));
                mProgress += resumePosition;
            }
        }

        // resumePosition > 0 ==> append mode
        FileOutputStream output = new FileOutputStream(file, resumePosition > 0);

        InputStream input = conn.getInputStream();

        byte[] buffer = new byte[1024];
        int len = 0;

        while ((len = input.read(buffer)) > 0) {
            if (mIsCanceled) {
                output.close();
                conn = null;
                input.close();
                long flen = file.length();
                if (flen > 0) {
                    try {
                        file.delete();
                        flen = 0;
                    } catch (Exception e) {}
                }
                // don't account file that won't consider for resume !
                if (flen > 0 && resume) {
                    throw new ECancelException(flen);
                }
                throw new ECancelException(0);
            }
            output.write(buffer, 0, len);
            mProgress += len;
            // shall we broadcast progress?
            if (mLastSignalledProgress < (mProgress * 100 / mTotalSize)) {
                // update and signal new progress
                mLastSignalledProgress = (int) (mProgress * 100 / mTotalSize);
                broadcastProgress(mLastSignalledProgress, null);
            }
        }
        output.flush();
        output.close();
        input.close();
        return fileName;
    }


    private static  boolean deleteDirectory(File path) {
        if( path.exists() ) {
            File[] files = path.listFiles();
            for(int i=0; i<files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return( path.delete() );
    }

    /**
     * @return null if success or error
     */
    private String deleteRelease() {
        // First delete old release if it exists
        File rootFolder = new File(mRootOfWorkPath);
        File release = new File(rootFolder, RELEASE_FOLDER);
        if (release.exists()) {
            deleteDirectory(release);
            // cleanup update command
            cleanUpdateCommand();
            // clean up version number.
            VersionInfo.storeEmptyVersion(getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE).edit(), PREF_KEY_DOWNLOADED_VERSION);
        }
        return null;
    }

    private void broadcastInstallerState() {
        Intent i = new Intent(SERVICE_STATE);
        i.putExtra(SERVICE_STATE, mInstallerState.ordinal());
        sendBroadcast(i);
    }
    
    private void updateInstallerState(InstallerState newState) {
        mInstallerState = newState;
        Intent i = new Intent(SERVICE_STATE);
        i.putExtra(SERVICE_STATE, mInstallerState.ordinal());
        sendBroadcast(i);        
    }

    /**
     * Check whether storage free space is enough.
     * @param downloadSize: download size from json. 0 means file already downloaded.
     * @return true if stoarge size is ok to go.
     */
    private boolean isStorageSpaceEnoughBFDownload(long downloadSize) {
        long dataSizeRequired = INSTALL_SIZE_REQUIRED;

        if (workPathInCache) {
            if (downloadSize > 0) {
                long cacheFreeSpace = Utils.getFreeSpaceInBytes("/cache");
                if (cacheFreeSpace < EXTRA_SIZE_REQUIRED + downloadSize) {
                    return false;
                }
            }
        } else {
            dataSizeRequired += downloadSize;
        }

        long dataFreeSpace = Utils.getFreeSpaceInBytes("/data");
        if (dataFreeSpace < EXTRA_SIZE_REQUIRED + dataSizeRequired) {
            return false;
        }
        return true;
    }

    private static VersionInfo getVersionWithPrefKey(Context c, String prefKey) {
        SharedPreferences pref = c.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);

        if (VersionInfo.hasValidVersion(pref, prefKey)) {
            return new VersionInfo(pref, prefKey);
        }
        return null;
    }

    /**
     * Internal helper function to get current DOWNLOAD_VERSION even download is partial
     * @param context
     * @return version info for download image.
     */
    private static VersionInfo getDownloadVersion(Context context) {
        return getVersionWithPrefKey(context, PREF_KEY_DOWNLOADED_VERSION);
    }
    
    /**
     * To get current DOWNLOAD_VERSION for completed download.
     * @param context
     * @return Version info for download-ed image.
     */
    public static VersionInfo getDownloadedVersion(Context context) {
        VersionInfo v = getVersionWithPrefKey(context, PREF_KEY_DOWNLOADED_VERSION);
        if (v != null) {
            if (v.mDownloadedSize == 0) return v;
        }
        return null;
    }

    public static VersionInfo getInstalledVersion(Context c) {
        return getVersionWithPrefKey(c, PREF_KEY_INSTALLED_VERSION);
    }
 
    public static boolean isUbuntuInstalled(Context c) {
        SharedPreferences pref = c.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        if (VersionInfo.hasValidVersion(pref, PREF_KEY_INSTALLED_VERSION)) {
            // go to launch screen
            return true;
        }
        return false;
    }

    /**
     *  check if update_command available for upgrade.
     *
     * @param c
     * @return
     */
    public static boolean isUpgradeable(Context c) {
        String cmd = c.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE).getString(PREF_KEY_UPDATE_COMMAND, "");
        return (cmd.startsWith("/cache/"));
    }

    /**
     * check if recovery command is exist on the system.
     * system-image in Ubuntu Touch downloaded new version of image at /cache/recovery.
     * This function check if there is a ubuntu_command file in /cache/recovery.
     *
     * The ubuntu_command  will be renamed or removed after installation.
     *
     * @return if there is upgradeable images stored in /cache.
     */
    public boolean findUpgradeable() {
        String[] candidates = {
                "/cache/recovery/ubuntu_command",
                "/cache/ubunturecovery/ubuntu_command",
        };
        boolean ret = false;
        for(String command: candidates) {
            if(new File("/cache").canRead()) {
                // if we have permission, we can read /cache.
                File cmd = new File(command);
                if(cmd.exists() && cmd.isFile()) {
                    Log.d(TAG, "Found upgrade command - " + cmd.getAbsoluteFile().toString());
                    // find the upgradeable file, stored into pref.
                    setUpdateCommand(cmd.getAbsolutePath());
                    ret = true;
                }
            } else {
                // check the file with su
                File workingFolder = new File(mRootOfWorkPath + "/" + TEMP_FOLDER);
                if (!workingFolder.exists() && !workingFolder.mkdir()) {
                    Log.e(TAG, "can not create working folder");
                    ret = false;
                }
                try {
                    int r = executeSUCommands(new String[] {
                            String.format("%s %s\n", UPGRADECHECKER, command),
                    });
                    if(r == 1) {
                        Log.d(TAG, "Found upgradeable file - " + command);
                        setUpdateCommand(command);
                        ret = true;
                    }
                } catch (EShellExecException e) {
                    ret = false;
                }
            }
        }
        if(ret) {
            // FIXME we don't know what's the version in /cache.
            SharedPreferences pref = getApplication().
                    getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
            VersionInfo v = new VersionInfo(pref, PREF_KEY_INSTALLED_VERSION);
            v.storeVersion(pref.edit(), PREF_KEY_DOWNLOADED_VERSION);
        }
        return ret;
    }

    /**
     * set update command string stored in shared preferences.
     * @file absolute file path of Ubuntu Command file.
     */
    private String getUpdateCommand(){
        return getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE).getString(PREF_KEY_UPDATE_COMMAND, "");
    }

    /**
     * set update command string stored in shared preferences.
     * @file absolute file path of Ubuntu Command file.
     */
    private void setUpdateCommand(String file){
        getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE).
        edit().
        putString(PREF_KEY_UPDATE_COMMAND, file).
        commit();
    }

    /**
     * clean update command string stored in shared preferences.
     */
    private void cleanUpdateCommand() {
        this.setUpdateCommand("");
    }
    
    private void broadcastProgress(int val, String progress) {
        Intent i = new Intent(PROGRESS);
        i.putExtra(PROGRESS_EXTRA_INT, val);
        if (progress!= null) {
            i.putExtra(PROGRESS_EXTRA_TEXT, progress);
        }
        sendBroadcast(i);
    }

    /**
     * Check if there is downloaded release ready to install.
     * If command file is not exist, reset downloaded version.
     * @param context
     * @return true if there is downloaded release ready to install
     */
    public static boolean checkifReadyToInstall(Context context) {
        SharedPreferences pref = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        VersionInfo versionInfo = getDownloadVersion(context);
        if (versionInfo == null) return false;
        if (versionInfo.getDownloadedSize() != 0) return false;
        
        String command = pref.getString(PREF_KEY_UPDATE_COMMAND, "");
        Log.d(TAG, "checkifReadyToInstall");
        if (!command.equals("")) {
            if (new File(command).exists() || command.startsWith("/cache")) {
                Log.d(TAG, "checkifReadyToInstall - found command file " + command);
                return true;
            } else {
                pref.edit().putString(PREF_KEY_UPDATE_COMMAND, "").commit();
                VersionInfo.storeEmptyVersion(pref.edit(), PREF_KEY_DOWNLOADED_VERSION);
                return false;
            }
        }
        VersionInfo.storeEmptyVersion(pref.edit(), PREF_KEY_DOWNLOADED_VERSION);
        return false;
    }
}
