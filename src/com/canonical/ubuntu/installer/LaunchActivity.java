package com.canonical.ubuntu.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.canonical.ubuntu.widget.UbuntuButton;

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

        // check the upgradeable image.
        startService(new Intent(UbuntuInstallService.UPGRADE_UBUNTU));
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
                final Intent uninstall = new Intent(UbuntuInstallService.UNINSTALL_UBUNTU);
                createConfirmationDialog(R.string.uninstall_dialog_title, 
                        R.string.action_uninstall_button,
                        R.string.cancel,
                        uninstall,
                        R.string.uninstalling_ubuntu,
                        R.array.uninstall_options,
                        new boolean[]{UbuntuInstallService.DEFAULT_UNINSTALL_DEL_USER_DATA},
                        new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        // we have only one option here
                        uninstall.putExtra(UbuntuInstallService.UNINSTALL_UBUNTU_EXTRA_REMOVE_USER_DATA, isChecked);
                    }
                }).show();
                break;
            case R.id.action_del_user_data:
                Intent action = new Intent(UbuntuInstallService.DELETE_UBUNTU_USER_DATA);
                createConfirmationDialog(R.string.action_delete_user_data, 
                        R.string.action_delete_udata_button,
                        R.string.cancel,
                        action,
                        R.string.deleting_user_data,
                        -1,
                        null,
                        null).show();        
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Create a dialog for confirmation.
     * @param title
     * @param positiveButton
     * @param negativeButton
     * @param action
     * @param toastText
     * @param choiceItemsArray
     * @param defaultChoiceValues
     * @param choiceClickListener
     * @return AlertDialog
     */
    private AlertDialog createConfirmationDialog(final int title, 
                                         final int positiveButton, 
                                         final int negativeButton, 
                                         final Intent action,
                                         final int toastText,
                                         final int choiceItemsArray,
                                         final boolean[] defaultChoiceValues,
                                         final DialogInterface.OnMultiChoiceClickListener choiceClickListener ){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setPositiveButton(positiveButton, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                startService(action);
                Utils.showToast(getApplicationContext(), toastText);
               }
           });
        if (choiceItemsArray != -1) {
        builder.setMultiChoiceItems(choiceItemsArray, defaultChoiceValues, choiceClickListener);
        }
        builder.setNegativeButton(negativeButton, null);
        return builder.create();
    }

    public static void startFrom(Context context) {
        Intent intent = new Intent(context, LaunchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    private void fillInstalledVersionInfo() {
        mTextChannel.setText(mUbuntuVersion.getChannelAlias());
        mTextVersion.setText(Integer.toString(mUbuntuVersion.getVersion()));
        mTextDescription.setText(mUbuntuVersion.getDescription());
        mTextChannelLabel.setText(R.string.label_channel);
        mTextVersionLabel.setText(R.string.label_version);
        mTextDescriptionLabel.setText(R.string.label_description);
    }

    /**
     * Get installed version to check Ubuntu is installed
     */
    private void ensureUbuntuIsInstalled() {
        VersionInfo v = UbuntuInstallService.getInstalledVersion(this.getApplicationContext());
        if (v == null) {
            // go back to install screen, and release myself.
            InstallActivity.startFrom(this);
            finish();
        } else {
            mUbuntuVersion = v;
        }
    }
    
    OnClickListener mRebootButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            startService(new Intent(UbuntuInstallService.REBOOT_UBUNTU));
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
