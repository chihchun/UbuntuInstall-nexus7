package com.canonical.ubuntuinstaller;

import java.io.DataOutputStream;
import java.io.IOException;

import android.os.Bundle;
import android.os.PowerManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "UbuntuInstaller";

    private Button mDownloadButton;
    private Button mInstallButton;
    private Button mRebootToUbuntu;
    private TextView mInfoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDownloadButton = (Button) findViewById(R.id.downloadBbutton);
        mInstallButton = (Button) findViewById(R.id.installButton);
        mRebootToUbuntu = (Button) findViewById(R.id.rebootToUbuntu);
        mInfoText = (TextView) findViewById(R.id.intoText);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        mDownloadButton.setOnClickListener(mDownloadButtonListener);
        mInstallButton.setOnClickListener(mInstallButtonListener);
        mRebootToUbuntu.setOnClickListener(mRebootButton);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UbuntuInstallService.DOWNLOAD_PROGRESS);
        filter.addAction(UbuntuInstallService.DOWNLOAD_RESULT);
        filter.addAction(UbuntuInstallService.INSTALL_PROGRESS);
        filter.addAction(UbuntuInstallService.INSTALL_RESULT);
        filter.addAction(UbuntuInstallService.READY_TO_INSTALL);
        registerReceiver(mProgressObserver, filter);
        // check is there is Ubuntu installed
        SharedPreferences pref = getSharedPreferences( UbuntuInstallService.SHARED_PREF, Context.MODE_PRIVATE);
        String channel = pref.getString(UbuntuInstallService.PREF_KEY_INSTALLED_CHANNEL, "");
        if (channel.equals("")) {
        	mRebootToUbuntu.setEnabled(false);
        } else {
        	mRebootToUbuntu.setEnabled(true);
        	// TODO: show installed cannel in UI
        }

        // ask if we are ready to install - response will update UI
        startService(new Intent(UbuntuInstallService.IS_RELEADY_TO_INSTALL));
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mDownloadButton.setOnClickListener(null);
        mInstallButton.setOnClickListener(null);
        mRebootToUbuntu.setOnClickListener(null);
        // cancel observer if there is any
        unregisterReceiver(mProgressObserver);
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
            Intent startDownload = new Intent(UbuntuInstallService.DOWNLOAD_RELEASE);
            startService(startDownload);
        }
    };

    OnClickListener mInstallButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent startDownload = new Intent(UbuntuInstallService.INSTALL_UBUNTU);
            startService(startDownload);
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
                    // TODO: 
                    // stock ROM has habbit to reflash recovery with stock recovery
                    // we need to reflas ubuntu boot image here
                    // os.writeBytes("ubuntu_boot.img > /dev/block/platform/msm_sdcc.1/by-name/recovery\n");
                    // reboot
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
    
    BroadcastReceiver mProgressObserver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	String action = intent.getAction();
        	if(action.equals(UbuntuInstallService.INSTALL_PROGRESS)) {
        		String p = intent.getStringExtra(UbuntuInstallService.INSTALL_PROGRESS_EXTRA_TEXT);
        		updateInfoOnUiThread(p);
        		// TODO: add progress bar
        	} else if(action.equals(UbuntuInstallService.INSTALL_RESULT)) {
        		int r = intent.getIntExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_INT, -1);
        		if (r == 0) {
        			mRebootToUbuntu.setEnabled(true);
        			showToast("Download completed, ready to install Ubuntu");
        		} else {
        			showToast(intent.getStringExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_STR));
        		}
        	} else if(action.equals(UbuntuInstallService.DOWNLOAD_PROGRESS)) {
        		String p = intent.getStringExtra(UbuntuInstallService.DOWNLOAD_PROGRESS_EXTRA_TEXT);
        		updateInfoOnUiThread(p);
        		// TODO: add progress bar
        	} else if(action.equals(UbuntuInstallService.DOWNLOAD_RESULT)) {
        		int r = intent.getIntExtra(UbuntuInstallService.DOWNLOAD_RESULT_EXTRA_INT, -1);
        		if (r == 0) {
        			mInstallButton.setEnabled(true);
        			showToast("Installation completed");
        		} else {
        			mInstallButton.setEnabled(false);
        			showToast(intent.getStringExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_STR));
        		}
        		
        	} else if (action.equals(UbuntuInstallService.READY_TO_INSTALL)) {
        		boolean ready = intent.getBooleanExtra(
        				UbuntuInstallService.READY_TO_INSTALL_EXTRA_READY, false);
        		mInstallButton.setEnabled(ready);
        	}
        }
    };
}
