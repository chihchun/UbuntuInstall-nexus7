package com.canonical.ubuntu.installer;

import com.canonical.ubuntu.installer.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import android.widget.TextView;

/**
 * A text picker dialog that prompts the user for to select one of the text options
 */
public class TextPickerDialog extends AlertDialog implements OnClickListener {
    private static final String SELECTION = "text";

    /**
     * Callback interface called once user makes selection 
     */
    public interface OnChannelPicktListener {

        /**
         * @param channel selected channel
         * @param bootstrap status of bootstrap option
         */
        void onChannelPicked(Context context, String channel, boolean bootstrap, boolean latest);
    }

    private final NumberPicker mTextPicker;
    private final OnChannelPicktListener mCallback;
    private final CheckBox mBootstrap;
    private final CheckBox mLatest;
    private final boolean mDeveloper;


    /**
     * @param context
     * @param callBack observer for result
     * @param dialogTitle title of the dialog
     * @param pickerTitle title of the picker component
     * @param values values to fill picker with
     * @param defaultValue index of the default value
     */
    public TextPickerDialog(Context context,
    		final OnChannelPicktListener callBack,
            final String[] values,
            final int defaultValue,
            final boolean bootstrap,
            final boolean latest) {
        super(context);
        mCallback = callBack;

        setTitle(R.string.channel_picker_dialog_title);
        Resources r = context.getResources();
        setButton(DialogInterface.BUTTON_POSITIVE, r.getString(R.string.action_install), this);
        setButton(DialogInterface.BUTTON_NEGATIVE, r.getString(R.string.cancel), (OnClickListener) null);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.text_picker_dialog, null);
        setView(view);

        mTextPicker = (NumberPicker) view.findViewById(R.id.text_picker);
        mBootstrap = (CheckBox) view.findViewById(R.id.checkBootstrap);
        mBootstrap.setChecked(bootstrap);
        // check if we should show latest option
        SharedPreferences pref = context.getSharedPreferences( UbuntuInstallService.SHARED_PREF, Context.MODE_PRIVATE);
        mDeveloper = pref.getBoolean(UbuntuInstallService.PREF_KEY_DEVELOPER, false);
        mLatest = (CheckBox) view.findViewById(R.id.checkLatestVersion);
        if (mDeveloper) {
        	mLatest.setChecked(latest);
        	mLatest.setVisibility(View.VISIBLE);
        } else {
        	mLatest.setVisibility(View.GONE);
        	mLatest.setChecked(true);
        }

        // initialise state 
        mTextPicker.setMinValue(0); // we start from 0
        mTextPicker.setMaxValue(values.length - 1);
        mTextPicker.setValue(defaultValue);
        mTextPicker.setOnLongPressUpdateInterval(1); // will never have that many channels, set it to 1

        mTextPicker.setWrapSelectorWheel(false);  // no wrapping        
        mTextPicker.setDisplayedValues(values); 
    }

    public void onClick(DialogInterface dialog, int which) {
        if (mCallback != null) {
            mTextPicker.clearFocus();
            // store extra 
            mCallback.onChannelPicked(getContext(), mTextPicker.getDisplayedValues()[mTextPicker.getValue()],
            		mBootstrap.isChecked(),
            		mLatest.isChecked());
            dialog.dismiss();
        }
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(SELECTION, mTextPicker.getValue());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int number = savedInstanceState.getInt(SELECTION);
        mTextPicker.setValue(number);
    }
}
