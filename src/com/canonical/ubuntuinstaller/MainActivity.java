package com.canonical.ubuntuinstaller;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import android.os.Bundle;
import android.os.PowerManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "UbuntuInstaller";

    private Button mInstallButton;
    private Button mRebootToUbuntu;
    private TextView mInfoText;
    private HashMap<String, String> mAvailableChannels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mInstallButton = (Button) findViewById(R.id.installUbuntuButton);
        mRebootToUbuntu = (Button) findViewById(R.id.rebootToUbuntu);
        mInfoText = (TextView) findViewById(R.id.intoText);
        startService(new Intent(UbuntuInstallService.GET_CHANNEL_LIST));
    }
    
    @Override
    public void onResume() {
        super.onResume();
        mInstallButton.setOnClickListener(mDownloadButtonListener);
        mRebootToUbuntu.setOnClickListener(mRebootButton);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UbuntuInstallService.AVAILABLE_CHANNELS);
        filter.addAction(UbuntuInstallService.DOWNLOAD_RESULT);
        filter.addAction(UbuntuInstallService.DOWNLOAD_PROGRESS);
        filter.addAction(UbuntuInstallService.INSTALL_RESULT);
        filter.addAction(UbuntuInstallService.INSTALL_PROGRESS);
        filter.addAction(UbuntuInstallService.READY_TO_INSTALL);
        
        registerReceiver(mServiceObserver, filter);
        // check is there is Ubuntu installed
        SharedPreferences pref = getSharedPreferences( UbuntuInstallService.SHARED_PREF, Context.MODE_PRIVATE);
        String channel = pref.getString(UbuntuInstallService.PREF_KEY_INSTALLED_CHANNEL_ALIAS, "");
        if (channel.equals("")) {
        	mRebootToUbuntu.setEnabled(false);
        } else {
        	mRebootToUbuntu.setEnabled(true);
        	// TODO: show installed channel in UI
        }
        if (mAvailableChannels == null) {
        	mInstallButton.setText(Html.fromHtml("Install Ubuntu<br/><small>fetching channel list</small>"));
        	mInstallButton.setEnabled(false);
        } else {
        	mInstallButton.setText(R.string.install_button_label);
        	mInstallButton.setEnabled(true);
        }
        // ask if we are ready to install - response will update UI
        startService(new Intent(UbuntuInstallService.IS_RELEADY_TO_INSTALL));
        
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mInstallButton.setOnClickListener(null);
        mRebootToUbuntu.setOnClickListener(null);
        // cancel observer if there is any
        unregisterReceiver(mServiceObserver);
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
        	// get list of aliases as array
        	String channels[] = mAvailableChannels.keySet().toArray(new String[mAvailableChannels.size()]);
        	new TextPickerDialog(v.getContext(), 
        			             mInstallDialogListener, 
        						 R.string.channel_picker_dialog_title, 
        						 R.string.channel_picker_title, 
        						 channels,
        						 0).show();
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
                    // stock ROM has habit to reflash recovery with stock recovery
                    // we need to reflas ubuntu boot image here
                    // TODO: this should per device
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
    
    TextPickerDialog.OnTextSetListener mInstallDialogListener 
    							= new TextPickerDialog.OnTextSetListener() {
    	public void onTextSet(String channel) {
    		// get channel 
    		
          Intent startDownload = new Intent(UbuntuInstallService.DOWNLOAD_RELEASE);
          startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_CHANNEL_ALIAS, channel);
          startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_CHANNEL_URL, 
        		  mAvailableChannels.get(channel));
          startService(startDownload);
        }
    };

    
    BroadcastReceiver mServiceObserver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	String action = intent.getAction();
        	// List of available channels fetched 
        	if (action.equals(UbuntuInstallService.AVAILABLE_CHANNELS)) {
        		mAvailableChannels = (HashMap<String, String>) 
        				intent.getSerializableExtra(UbuntuInstallService.AVAILABLE_CHANNELS_EXTRA_CHANNELS);
        		Log.v(TAG, "AvailableChanlles count:" + mAvailableChannels.size());
            	mInstallButton.setText(R.string.install_button_label);
            	mInstallButton.setEnabled(true);

            // Handle install progress
        	} else if (action.equals(UbuntuInstallService.INSTALL_PROGRESS)) {
        		String p = intent.getStringExtra(UbuntuInstallService.INSTALL_PROGRESS_EXTRA_TEXT);
        		updateInfoOnUiThread(p);
        		// TODO: add progress bar

        	// Handle install result 
        	} else if(action.equals(UbuntuInstallService.INSTALL_RESULT)) {
        		int r = intent.getIntExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_INT, -1);
        		if (r == 0) {
        			mRebootToUbuntu.setEnabled(true);
        			showToast("Install completed");
        		} else {
        			showToast("Installation failed:");
        			String reason = intent.getStringExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_STR);
        			showToast(reason);
        			updateInfoOnUiThread(reason);
        		}
        		
            // Handle Download progress
        	} else if(action.equals(UbuntuInstallService.DOWNLOAD_PROGRESS)) {
        		String p = intent.getStringExtra(UbuntuInstallService.DOWNLOAD_PROGRESS_EXTRA_TEXT);
        		updateInfoOnUiThread(p);
        		// TODO: add progress bar
        		
            // Handle download result
        	} else if(action.equals(UbuntuInstallService.DOWNLOAD_RESULT)) {
        		int r = intent.getIntExtra(UbuntuInstallService.DOWNLOAD_RESULT_EXTRA_INT, -1);
        		if (r == 0) {
        			showToast("Download completed, ready to install Ubuntu");
        			// TODO: check if we have battery and progress to install
                    Intent startInstall = new Intent(UbuntuInstallService.INSTALL_UBUNTU);
                    startService(startInstall);
        			showToast("Starting Ubuntu installation");
        		} else {
        			showToast("Downlload failed:");
        			String reason = intent.getStringExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_STR);
        			showToast(reason);
        			updateInfoOnUiThread(reason);
        			// TODO: handle this better way
        		}
            
            // Handle ready install 
        	} else if (action.equals(UbuntuInstallService.READY_TO_INSTALL)) {
        		// TODO: if we get here, it means installation was paused, or refused for
        		// some reasons (battery mpety?) check we can start install here
        		boolean ready = intent.getBooleanExtra(
        				UbuntuInstallService.READY_TO_INSTALL_EXTRA_READY, false);
        	}
        }
    };
}
