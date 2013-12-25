package com.canonical.ubuntu.installer;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import com.canonical.ubuntu.installer.R;
import com.canonical.ubuntu.installer.TextPickerDialog;
import com.canonical.ubuntu.installer.UbuntuInstallService;
import com.canonical.ubuntu.installer.JsonChannelParser.Image;
import com.canonical.ubuntu.installer.UbuntuInstallService.InstallerState;
import com.canonical.ubuntu.installer.VersionInfo.ReleaseType;
import com.canonical.ubuntu.widget.UbuntuButton;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;

public class InstallActivity extends Activity {
    private static final String TAG = "UbuntuInstaller";

    private UbuntuButton mInstallButton;
    private boolean mObserversRegistered;

    private InstallerState mStatus = InstallerState.READY;
    private VersionInfo mDownloadedVersion = null;
    
    private ProgressBar mProgressBar;
    private TextView mProgressText;
    
    private TextView mTerminal;
    private HashMap<String, String> mAvailableChannels = new HashMap<String, String>();

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
        mProgressBar.setEnabled(false);
        mProgressBar.setProgress(0);

        SharedPreferences pref = getSharedPreferences(UbuntuInstallService.SHARED_PREF, Context.MODE_PRIVATE);
        pref.edit().putBoolean(UbuntuInstallService.PREF_KEY_DEVELOPER, true).commit();
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
        filter.addAction(UbuntuInstallService.PROGRESS);
        filter.addAction(UbuntuInstallService.INSTALL_RESULT);
        filter.addAction(UbuntuInstallService.VERSION_UPDATE);
        filter.addAction(UbuntuInstallService.SERVICE_STATE);
        registerReceiver(mServiceObserver, filter);
        mObserversRegistered = true;

        // do we know last activity
        if (mStatus == InstallerState.DOWNLOADING || mStatus == InstallerState.INSTALLING) {
            // request last progress / status. this will update UI accordingly
            startService(new Intent(UbuntuInstallService.GET_PROGRESS_STATUS));
        } else {
            // READY + mDownloadedVersion != null => READY_TO_INSTALL
            mDownloadedVersion = UbuntuInstallService.getDownloadedVersion(this.getApplicationContext());
            if (mDownloadedVersion == null) {
                requestChannelList();
            }
        }
        requestServiceState();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // cancel observer if there is any
        if (mObserversRegistered) {
            mInstallButton.setOnClickListener(null);
            unregisterReceiver(mServiceObserver);
        }
        mObserversRegistered = false;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.installer_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete_download:
                // also attempt to uninstall ubuntu, since there should be none anyway, keep user data
                Intent action = new Intent(UbuntuInstallService.UNINSTALL_UBUNTU);
                action.putExtra(UbuntuInstallService.UNINSTALL_UBUNTU_EXTRA_REMOVE_USER_DATA, false);
                startService(action);
                deleteDownload();
                mDownloadedVersion = null;
                requestServiceState();
                break;
            case R.id.action_dump_terminal:
                CharSequence terminalText = mTerminal.getText();
                if (terminalText.length() == 0) {
                    Utils.showToast(this.getApplicationContext(), R.string.terminal_is_empty);
                    break;
                }

                Calendar c = Calendar.getInstance();
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US);
                String formattedDate = df.format(c.getTime());

                String filename = "UbuntuInstaller-" + formattedDate + ".log";
                File dumpFile = null;
                try {
                    File vSDCard;
                    if(Environment.getExternalStorageState().equals(Environment.MEDIA_REMOVED)) {
                        Utils.showToast(this.getApplicationContext(), R.string.external_storage_unavailable);
                        break;
                    } else {
                        vSDCard = Environment.getExternalStorageDirectory();
                    }

                    dumpFile = new File(vSDCard + "/" + filename);
                    FileWriter dumpFileWriter = new FileWriter(dumpFile);
                    dumpFileWriter.write("Device: " + android.os.Build.FINGERPRINT);
                    dumpFileWriter.write(terminalText.toString());
                    dumpFileWriter.close();
                    Utils.showToast(this.getApplicationContext(), getResources().getString(R.string.terminal_dump_succ) + ": " + dumpFile.getPath());
                } catch (Exception e) {
                    Utils.showToast(this.getApplicationContext(), getResources().getString(R.string.terminal_dump_fail) + ": " + dumpFile.getPath());
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    public static void startFrom(Context context) {
        Intent intent = new Intent(context, InstallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    private void checkIfUbuntuIsInstalled() {
        // check is there is Ubuntu installed
        if (UbuntuInstallService.isUbuntuInstalled(this.getApplicationContext())) {
            // go to launch screen, and kill this activity.
            LaunchActivity.startFrom(this);
            finish();
        }
    }

    private void requestChannelList() {
        startService(new Intent(UbuntuInstallService.GET_CHANNEL_LIST));
    }
    
    private void requestServiceState() {
        startService(new Intent(UbuntuInstallService.GET_SERVICE_STATE));
    }
    
    private void deleteDownload() {
        Intent action = new Intent(UbuntuInstallService.CLEAN_DOWNLOAD);
        startService(action);
    }
    
    OnClickListener mInstallButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // do we need to download release, or there is already one downloaded
            // user might have missed SU request, then we have downloaded release and we just need deploy it
            // TODO: we will need to handle also download resume
            if (mStatus == InstallerState.DOWNLOADING) {
                Intent startInstall = new Intent(UbuntuInstallService.CANCEL_DOWNLOAD);
                startService(startInstall);
            } else if (mStatus == InstallerState.INSTALLING) {
                Intent startInstall = new Intent(UbuntuInstallService.CANCEL_INSTALL);
                startService(startInstall);
            } else if (UbuntuInstallService.checkifReadyToInstall(v.getContext())) {
                startInstallationIfPossible();
            } else if (0 != mAvailableChannels.size()) {
                // get list of aliases as array
                String channels[] = mAvailableChannels.keySet().toArray(new String[mAvailableChannels.size()]);
                // look for "trusty" as default channel
                int defSelection = channels.length/2;
                for (int i = 0 ; i < channels.length ; ++i) {
                    if (channels[i].equals(UbuntuInstallService.DEFAULT_CHANNEL_ALIAS)){
                        defSelection = i;
                        break;
                    }
                }
                new TextPickerDialog(v.getContext(), 
                                     mInstallDialogListener, 
                                     channels,
                                     defSelection,
                                     UbuntuInstallService.DEFAULT_INSTALL_BOOTSTRAP, /*default bootstrap settings*/
                                     true /* default latest settings*/).show();
            } else {
                // there are no channels to pick from, this was mistake, disable button
                requestServiceState();
            }
        }
    };
    
    private void updateInfoOnUiThread(final String text) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // keep old text in there 
                mTerminal.setText(mTerminal.getText().toString() + "\n" + text);
                final int scrollAmount = mTerminal.getLayout().getLineTop(mTerminal.getLineCount()) - mTerminal.getHeight();
                mTerminal.scrollTo(0, scrollAmount);
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
    }
    
    TextPickerDialog.OnChannelPicktListener mInstallDialogListener 
                                = new TextPickerDialog.OnChannelPicktListener() {
        public void onChannelPicked(Context context, String channel, boolean bootstrap, boolean latest) {
            // if we should not do latest, we need to fetch list of available versions
            if (!latest) {
                downloadVersion(context, channel, bootstrap);
            } else {
                startDownload(channel, bootstrap, -1);
            }
        }
    };

    private void startDownload(final String channel, final boolean bootstrap, final int version) {
        Intent startDownload = new Intent(UbuntuInstallService.DOWNLOAD_RELEASE);
        startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_CHANNEL_ALIAS, channel);
        startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_CHANNEL_URL, 
              mAvailableChannels.get(channel));
        startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_BOOTSTRAP, bootstrap);
        if (version != -1) {
            startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_VERSION, version);
        }
        startDownload.putExtra(UbuntuInstallService.DOWNLOAD_RELEASE_EXTRA_TYPE, ReleaseType.FULL.getValue());
        startService(startDownload);
        mTerminal.setText(R.string.downloading_starting);
        mProgressBar.setProgress(0);
    }
    
    private void downloadVersion(final Context context, final String channel, final boolean bootstrap) {
        final ProgressDialog progress = ProgressDialog.show(this, 
        "Fetching versions", 
        "Checking list of availanble versions for choosen channel", true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String jsonStr = Utils.httpDownload(UbuntuInstallService.BASE_URL 
                + mAvailableChannels.get(channel));
                // TODO: handle malformed JSON
                final List<Image> releases = JsonChannelParser.getAvailableReleases(jsonStr, ReleaseType.FULL);
                
                runOnUiThread(new Runnable() {
            @Override
                    public void run() {
                        progress.dismiss();
                        // if there are available releases, show number picker
                        if (releases.size() != 0 && releases.get(0).files.length != 0) {
                        int[] values = new int[releases.size()];
                        for (int i = 0 ; i < values.length ; ++i) {
                        values[i] = releases.get(i).version;
                        }
                        new NumberPickerDialog(context, 
                        R.string.version_picker_dialog_title,
                        R.string.action_install,
                        R.string.cancel,
                        values,
                        0, 
                        new NumberPickerDialog.OnNumberPicktListener() {
        @Override
        public void onNumberSelected(Context context, int value) {
        startDownload(channel, bootstrap, value);
        }
        }).show();;
                        }                        
                    }
                });
            }
        }).start();
    }

    private void updateUiElements() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG,"updateUiElements(" + mStatus + ")");
                switch (mStatus) {
                    case READY:
                    {
                        if (mDownloadedVersion != null) {
                            mInstallButton.setText(R.string.install_button_label_resume);
                            mInstallButton.setEnabled(true);
                            mProgressBar.setEnabled(false);
                            mProgressBar.setProgress(0);
                            mProgressText.setText("");
                        } else if (mAvailableChannels.size() > 0) {
                            mInstallButton.setText(R.string.install_button_label_install);
                            mInstallButton.setEnabled(true);
                            mProgressBar.setEnabled(false);
                            mProgressBar.setProgress(0);
                            mProgressText.setText("");
                        } else {
                            mInstallButton.setText(Html.fromHtml(getResources().getString(R.string.install_button_label_no_channel)));
                            mInstallButton.setEnabled(false);
                            mProgressBar.setEnabled(false);
                            mProgressBar.setProgress(0);
                            mProgressText.setText("");
                        }
                    }
                        break;
                    case FETCHING_CHANNELS:
                        mInstallButton.setText(Html.fromHtml(getResources().getString(R.string.install_button_label_fetching)));
                        mInstallButton.setEnabled(false);
                        mProgressBar.setEnabled(false);
                        mProgressBar.setProgress(0);
                        mProgressText.setText("");
                        break;                
                    case DOWNLOADING:
                        mInstallButton.setText(R.string.install_button_label_cancel_download);
                        mInstallButton.setEnabled(true);
                        mProgressBar.setEnabled(true);
                        mProgressText.setText(R.string.downloading_release);
                        break;
                    case INSTALLING:
                        mInstallButton.setText(R.string.install_button_label_cancel_install);
                        mInstallButton.setEnabled(true);
                        mProgressBar.setEnabled(true);
                        mProgressText.setText(R.string.installing_release);
                        break;
                }
            }
        });
    }
    
    BroadcastReceiver mServiceObserver = new BroadcastReceiver() {
        
        @SuppressWarnings("unchecked")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG,"mServiceObserver.onReceive(" + action + ")");
            // List of available channels fetched 
            if (action.equals(UbuntuInstallService.AVAILABLE_CHANNELS)) {
                // ignore channel list if we have already downloaded release
                if (!UbuntuInstallService.checkifReadyToInstall(context)) {
                    mAvailableChannels = (HashMap<String, String>) 
                            intent.getSerializableExtra(UbuntuInstallService.AVAILABLE_CHANNELS_EXTRA_CHANNELS);
                    mStatus = InstallerState.READY;
                    updateUiElements();
                }
            // Handle progress
            } else if (action.equals(UbuntuInstallService.PROGRESS)) {
                String p = intent.getStringExtra(UbuntuInstallService.PROGRESS_EXTRA_TEXT);
                int progress = intent.getIntExtra(UbuntuInstallService.PROGRESS_EXTRA_INT, -1);
                if (progress != -1) {
                    mProgressBar.setProgress(progress);
                    Log.v(TAG, "Progress:" + progress);
                }
                if (p != null  && !p.equals("")) {
                    // update terminal with progress text
                    updateInfoOnUiThread(p);
                }

            // Handle install result 
            } else if(action.equals(UbuntuInstallService.INSTALL_RESULT)) {
                int r = intent.getIntExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_INT, -1);
                if (r == 0) {
                    Utils.showToast(context, "Install completed");
                    mProgressBar.setProgress(100);
                    deleteDownload();
                    // go to launch screen, and kill this activity.
                    LaunchActivity.startFrom(context);
                    finish();
                } else {
                    Utils.showToast(context, "Installation failed:");
                    String reason = intent.getStringExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_STR);
                    Utils.showToast(context, reason);
                    updateInfoOnUiThread(reason);
                    // if we still have download go back to resume installation
                    // TODO: we should distinguish between resume/retry
                    mDownloadedVersion = UbuntuInstallService.getDownloadedVersion(context);
                    if (UbuntuInstallService.checkifReadyToInstall(context)) {
                        mDownloadedVersion = UbuntuInstallService.getDownloadedVersion(context);
                    } else {
                        deleteDownload();
                        mDownloadedVersion = null;
                        requestChannelList();
                    }
                    updateUiElements();
                }
                
            // Handle download result
            } else if(action.equals(UbuntuInstallService.DOWNLOAD_RESULT)) {
                int r = intent.getIntExtra(UbuntuInstallService.DOWNLOAD_RESULT_EXTRA_INT, -1);
                if (r == 0) {
                    Utils.showToast(context, "Download completed, ready to install Ubuntu");
                    startInstallationIfPossible();
                } else {
                    String reason = intent.getStringExtra(UbuntuInstallService.INSTALL_RESULT_EXTRA_STR);
                    // make sure it was not cancelled by user
                    if (r != -2) {
                        reason = "Download failed: " + reason;
                    }
                    Utils.showToast(context, reason);
                    updateInfoOnUiThread(reason);
                    requestChannelList();
                }
            } else if (action.equals(UbuntuInstallService.VERSION_UPDATE)) {
                checkIfUbuntuIsInstalled();
                if (!isFinishing()) {
                    // check what button should be shown
                    if (UbuntuInstallService.checkifReadyToInstall(context)) {
                        mDownloadedVersion = UbuntuInstallService.getDownloadedVersion(context);
                    }
                    updateUiElements();
                }
            } else if (action.equals(UbuntuInstallService.SERVICE_STATE)) {
                checkIfUbuntuIsInstalled();
                if (!isFinishing()) {
                    mStatus = InstallerState.fromOrdian(intent.getIntExtra(UbuntuInstallService.SERVICE_STATE, 0));
                    mDownloadedVersion = UbuntuInstallService.getDownloadedVersion(context);
                    updateUiElements();
                }
            }
        }
    };
}

