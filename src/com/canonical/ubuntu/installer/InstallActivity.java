package com.canonical.ubuntu.installer;

import java.util.HashMap;

import com.canonical.ubuntu.installer.R;
import com.canonical.ubuntu.installer.TextPickerDialog;
import com.canonical.ubuntu.installer.UbuntuInstallService;
import com.canonical.ubuntu.widget.UbuntuButton;

import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;

public class InstallActivity extends Activity {
    private static final String TAG = "UbuntuInstaller";

    private UbuntuButton mInstallButton;
    private ProgressBar mProgressBar;
    private TextView mProgressText;
    
    private TextView mTerminal;
    private HashMap<String, String> mAvailableChannels;
    private boolean mReadyToInstall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // check is there is already Ubuntu installed
        checkIfUbuntuIsInstalled();
        if (isFinishing()) return;
        
        setContentView(R.layout.ubuntu_dualboot_install);
        mInstallButton = (UbuntuButton) findViewById(R.id.download);
        mProgressBar = (ProgressBar) findViewById(R.id.progress);
        mProgressText = (TextView) findViewById(R.id.status);     
        mTerminal = (TextView) findViewById(R.id.terminal);
        mTerminal.setMovementMethod(new ScrollingMovementMethod());
        startService(new Intent(UbuntuInstallService.GET_CHANNEL_LIST));
        mProgressBar.setEnabled(false);
        mProgressBar.setProgress(0);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // check is there is already Ubuntu installed
        checkIfUbuntuIsInstalled();
        if (isFinishing()) return;

        mInstallButton.setOnClickListener(mInstallButtonListener);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UbuntuInstallService.AVAILABLE_CHANNELS);
        filter.addAction(UbuntuInstallService.DOWNLOAD_RESULT);
        filter.addAction(UbuntuInstallService.DOWNLOAD_PROGRESS);
        filter.addAction(UbuntuInstallService.INSTALL_RESULT);
        filter.addAction(UbuntuInstallService.INSTALL_PROGRESS);
        filter.addAction(UbuntuInstallService.READY_TO_INSTALL);
        filter.addAction(UbuntuInstallService.VERSION_UPDATE);
        
        registerReceiver(mServiceObserver, filter);
        if (mAvailableChannels == null) {
            mInstallButton.setText(Html.fromHtml(getResources().getString(R.string.install_button_label_fetching)));
            mInstallButton.setEnabled(false);
        } else {
            mInstallButton.setText(R.string.install_button_label_install);
            mInstallButton.setEnabled(true);
        }
        // ask if we are ready to install - response will update UI
        startService(new Intent(UbuntuInstallService.IS_RELEADY_TO_INSTALL));
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mInstallButton.setOnClickListener(null);
        // cancel observer if there is any
        unregisterReceiver(mServiceObserver);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.installer_menu, menu);
        return true;
    }
    
    public static void startFrom(Context context) {
        Intent intent = new Intent(context, InstallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
    
    private void checkIfUbuntuIsInstalled() {
        // check is there is Ubuntu installed
        SharedPreferences pref = getSharedPreferences( UbuntuInstallService.SHARED_PREF, Context.MODE_PRIVATE);
        VersionInfo version = new VersionInfo( pref.getStringSet(UbuntuInstallService.PREF_KEY_INSTALLED_VERSION, 
                                                               new VersionInfo().getSet()));

        if (!version.getChannelAlias().equals("")) {
            LaunchActivity.startFrom(this);
        }
    }
    
    OnClickListener mInstallButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // do we need to download release, or there is already one downloaded
            // user might have missed SU request, then we have downloaded release and we just need deploy it
            // TODO: we will need to handle also download resume 
            if (mReadyToInstall) {
                
                startInstallationIfPossible();
            } else if (0 != mAvailableChannels.size()) {
                // get list of aliases as array
                String channels[] = mAvailableChannels.keySet().toArray(new String[mAvailableChannels.size()]);
                new TextPickerDialog(v.getContext(), 
                                     mInstallDialogListener, 
                                     channels,
                                     channels.length/2,
                                     true /*default bootstrap settings*/).show();
            } else {
                // there are no channels to pick from, this was mistake, disable button
                mInstallButton.setText(Html.fromHtml(getResources().getString(R.string.install_button_label_no_channel)));
                mInstallButton.setEnabled(false);
            }
        }
    };
    
    private void updateInfoOnUiThread(final String text) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // keep old text in there 
                mTerminal.setText(mTerminal.getText().toString() 
                        + "\n" + text);
            }
        });
    }

    private void startInstallationIfPossible() {
        // TODO: check if we have battery and progress to install
        Intent startInstall = new Intent(UbuntuInstallService.INSTALL_UBUNTU);
        startService(startInstall);
        Utils.showToast(this, "Starting Ubuntu installation");
        // reset progress bar
        mProgressBar.setProgress(0);
        mProgressText.setText(R.string.installing_release);
    }
    
    TextPickerDialog.OnChannelPicktListener mInstallDialogListener 
                                = new TextPickerDialog.OnChannelPicktListener() {
        public void onChannelPicked(String channel, boolean bootstrap) {
            Intent startDownload = new Intent(UbuntuInstallService.DOWNLOAD_RELEASE);
            startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_CHANNEL_ALIAS, channel);
            startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_CHANNEL_URL, 
                  mAvailableChannels.get(channel));
            startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_BOOTSTRAP, bootstrap);
            startService(startDownload);
            mTerminal.setText(R.string.downloading_starting);
            mProgressBar.setProgress(0);
            mProgressText.setText(R.string.downloading_release);
            mProgressBar.setEnabled(true);
            mInstallButton.setText(R.string.install_button_label_cancel);
        }
    };

    
    BroadcastReceiver mServiceObserver = new BroadcastReceiver() {
        @SuppressWarnings("unchecked")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // List of available channels fetched 
            if (action.equals(UbuntuInstallService.AVAILABLE_CHANNELS)) {
                // ignore channel list if we have already downloaded release
                if (!mReadyToInstall) {
                    mAvailableChannels = (HashMap<String, String>) 
                            intent.getSerializableExtra(UbuntuInstallService.AVAILABLE_CHANNELS_EXTRA_CHANNELS);
                    if (0 != mAvailableChannels.size()) {
                        mInstallButton.setText(R.string.install_button_label_install);
                        mInstallButton.setEnabled(true);
                    } else {
                        // we have no channels to choose from
                        mInstallButton.setText(Html.fromHtml(getResources().getString(R.string.install_button_label_no_channel)));
                        mInstallButton.setEnabled(false);                        
                    }
                }

            // Handle install progress
            } else if (action.equals(UbuntuInstallService.INSTALL_PROGRESS)) {
                String p = intent.getStringExtra(UbuntuInstallService.PROGRESS_EXTRA_TEXT);
                int progress = intent.getIntExtra(UbuntuInstallService.PROGRESS_EXTRA_INT, -1);
                if (progress != -1) {
                    mProgressBar.setProgress(progress);
                    Log.v(TAG, "Install progress:" + progress);
                }
                if (p != null  && !p.equals("")) {
                    updateInfoOnUiThread(p);
                }

            // Handle install result 
            } else if(action.equals(UbuntuInstallService.INSTALL_RESULT)) {
                int r = intent.getIntExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_INT, -1);
                if (r == 0) {
                    Utils.showToast(context, "Install completed");
                    mProgressBar.setProgress(100);
                    // TODO: backup boot image
                    // TODO: delete download
                    // TODO: start launch activity
                    LaunchActivity.startFrom(context);
                } else {
                    Utils.showToast(context, "Installation failed:");
                    String reason = intent.getStringExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_STR);
                    Utils.showToast(context, reason);
                    updateInfoOnUiThread(reason);
                }
                
            // Handle Download progress
            } else if(action.equals(UbuntuInstallService.DOWNLOAD_PROGRESS)) {
                String p = intent.getStringExtra(UbuntuInstallService.PROGRESS_EXTRA_TEXT);
                int progress = intent.getIntExtra(UbuntuInstallService.PROGRESS_EXTRA_INT, -1);
                if (progress != -1) {
                    mProgressBar.setProgress(progress);
                }
                if (p != null  && !p.equals("")) {
                    updateInfoOnUiThread(p);
                }
                
            // Handle download result
            } else if(action.equals(UbuntuInstallService.DOWNLOAD_RESULT)) {
                int r = intent.getIntExtra(UbuntuInstallService.DOWNLOAD_RESULT_EXTRA_INT, -1);
                if (r == 0) {
                    Utils.showToast(context, "Download completed, ready to install Ubuntu");
                    startInstallationIfPossible();
                } else {
                    Utils.showToast(context, "Downlload failed:");
                    String reason = intent.getStringExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_STR);
                    Utils.showToast(context, reason);
                    updateInfoOnUiThread(reason);
                    mInstallButton.setEnabled(true);
                    mInstallButton.setText(R.string.install_button_label_install);
                    // TODO: handle this better way
                }
            
            // Handle ready install 
            } else if (action.equals(UbuntuInstallService.READY_TO_INSTALL)) {
                // TODO: if we get here, it means installation was paused, or refused for
                // some reasons (battery mpety?) check we can start install here
                mReadyToInstall = intent.getBooleanExtra(
                        UbuntuInstallService.READY_TO_INSTALL_EXTRA_READY, false);
                if (mReadyToInstall) {
                    mInstallButton.setText(R.string.install_button_label_resume);
                    mInstallButton.setEnabled(true);
                }
            } else if (action.equals(UbuntuInstallService.VERSION_UPDATE)) {
                checkIfUbuntuIsInstalled();
            }
        }
    };
}

