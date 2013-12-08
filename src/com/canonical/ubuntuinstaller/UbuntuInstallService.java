package com.canonical.ubuntuinstaller;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;



public class UbuntuInstallService extends IntentService {
	
	// Actions
    public static final String DOWNLOAD_RELEASE = "com.canonical.ubuntuinstaller.UbuntuInstallService.DOWNLOAD_RELEASE";
    public static final String CANCEL_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.CANCEL_DOWNLOAD";
    public static final String REMOVE_DOWNLOAD = "com.canonical.ubuntuinstaller.UbuntuInstallService.REMOVE_DOWNLOADED";
    public static final String IS_RELEASE_INSTALL = "com.canonical.ubuntuinstaller.UbuntuInstallService.IS_READY_TO_INSTALL";
    public static final String INSTALL_UBUNTU = "com.canonical.ubuntuinstaller.UbuntuInstallService.INSTALL_UBUNTU";

    // 
	
	public UbuntuInstallService() {
		super("UbuntuInstallService");
	}
	
    @Override
    public void onCreate() {
        super.onCreate();
        Context context = this.getApplicationContext();
        // SharedPreferences sharedPref = this.getSharedPreferences(, MODE_PRIVATE);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        Intent result = null;
        if (action.equals(DOWNLOAD_RELEASE)) {
        	
        } else if (action.equals(CANCEL_DOWNLOAD)) {
        	
        } else if (action.equals(REMOVE_DOWNLOAD)) {
        	
        	result = new Intent("");
        } else if (action.equals(INSTALL_UBUNTU)) {
        } else if (action.equals(IS_RELEASE_INSTALL)) {
        	
        } else {
        	
        }
        this.getBaseContext().sendBroadcast(result);
    }
}
