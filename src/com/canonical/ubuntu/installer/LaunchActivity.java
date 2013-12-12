package com.canonical.ubuntu.installer;

import java.io.DataOutputStream;
import java.io.IOException;

import com.canonical.ubuntu.widget.UbuntuButton;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;


public class LaunchActivity extends Activity {
    private static final String TAG = "UbuntuLaunchActivity";
    
    private UbuntuButton mRebootButton;
    private TextView mTextChannel;
    private TextView mTextChannelLabel;
    private TextView mTextVersion;
    private TextView mTextVersionLabel;
    private TextView mTextTitle;
    private TextView mTextDescriptionLabel;
    private TextView mTextDescription;
    private VersionInfo mUbuntuVersion;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // check if Ubuntu is installed
        ensureUbuntuIsInstalled();
        if (isFinishing()) return;
        
        setContentView(R.layout.ubuntu_dualboot_launch);
        mRebootButton = (UbuntuButton) findViewById(R.id.download);
        mTextChannel = (TextView) findViewById(R.id.channel);
        mTextVersion = (TextView) findViewById(R.id.version);
        mTextChannelLabel = (TextView) findViewById(R.id.channel_label);
        mTextVersionLabel = (TextView) findViewById(R.id.version_label);
        mTextTitle = (TextView) findViewById(R.id.title);
        mTextDescriptionLabel = (TextView) findViewById(R.id.description_label);
        mTextDescription = (TextView) findViewById(R.id.description);
        mTextTitle.setText(R.string.launch_title);
        mRebootButton.setText(R.string.reboot_button_label);
        fillInstalledVersionInfo();
    }
        
    @Override
    public void onResume() {
        super.onResume();
        // check is there is Ubuntu installed
        ensureUbuntuIsInstalled();
        if (isFinishing()) return;
        
        mRebootButton.setOnClickListener(mRebootButtonListener);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UbuntuInstallService.VERSION_UPDATE);
        registerReceiver(mServiceObserver, filter);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mRebootButton.setOnClickListener(null);
        // cancel observer if there is any
        unregisterReceiver(mServiceObserver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.launcher_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_uninstall:
            // show dialog
        	final boolean delUserData = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.uninstall_dialog_title);
            builder.setPositiveButton(R.string.action_uninstall, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Intent startUninstall = new Intent(UbuntuInstallService.UNINSTALL_UBUNTU);
                    startService(startUninstall);
                    Utils.showToast(getApplicationContext(), "Uninstalling Ubuntu");
                   }
               });
            builder.setMultiChoiceItems(R.array.uninstall_options, new boolean[]{delUserData}, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                	// TODO: wire up delete user data option
                }
            });
            builder.setNegativeButton(R.string.cancel, null);
            AlertDialog dialog = builder.create();
            dialog.show();
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    public static void startFrom(Context context) {
        Intent intent = new Intent(context, LaunchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    private void fillInstalledVersionInfo() {
        SharedPreferences pref = getSharedPreferences( UbuntuInstallService.SHARED_PREF, Context.MODE_PRIVATE);        
        mTextChannel.setText(mUbuntuVersion.getChannelAlias());
        mTextVersion.setText(Integer.toString(mUbuntuVersion.getVersion()));
        mTextDescription.setText(mUbuntuVersion.getDescription());
        mTextChannelLabel.setText(R.string.label_channel);
        mTextVersionLabel.setText(R.string.label_version);
        mTextDescriptionLabel.setText(R.string.label_description);
    }
    
    private void ensureUbuntuIsInstalled() {
        SharedPreferences pref = getSharedPreferences( UbuntuInstallService.SHARED_PREF, Context.MODE_PRIVATE);
        mUbuntuVersion = new VersionInfo( pref.getStringSet(UbuntuInstallService.PREF_KEY_INSTALLED_VERSION, 
                                                               new VersionInfo().getSet()));

        if (mUbuntuVersion.getChannelAlias().equals("")) {
            // go back to install screen
            InstallActivity.startFrom(this);
        }
    }
    
    OnClickListener mRebootButtonListener = new OnClickListener() {
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
                                 Utils.showToast(v.getContext(), "Rebooting to Ubuntu");
                             }
                             else {
                                 Utils.showToast(v.getContext(), "No permissions to reboot to recovery");      
                             }   
                     } catch (InterruptedException ee) {
                         Utils.showToast(v.getContext(), "No permissions to reboot to recovery");
                     }
                  } catch (IOException eee) {
                      Utils.showToast(v.getContext(), "No permissions to reboot to recovery");   
                  }
            }
        }
    };
    BroadcastReceiver mServiceObserver = new BroadcastReceiver() {
        @SuppressWarnings("unchecked")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(UbuntuInstallService.VERSION_UPDATE)) {
                ensureUbuntuIsInstalled();
            }
        }
    };
}
