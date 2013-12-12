package com.canonical.ubuntu.installer;

import com.canonical.ubuntu.installer.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
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
    private static final String NUMBER = "number";

    /**
     * Callback interface called once user makes selection 
     */
    public interface OnChannelPicktListener {

        /**
         * @param channel selected channel
         * @param bootstrap status of bootstrap option
         */
        void onChannelPicked(String channel, boolean bootstrap);
    }

    private final NumberPicker mTextPicker;
    private final TextView mValueTitle;
    private final OnChannelPicktListener mCallback;
    private final CheckBox mBootstrap;


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
            final boolean bootstrap) {
        super(context);
        mCallback = callBack;

        setTitle(R.string.channel_picker_dialog_title);

        setButton(DialogInterface.BUTTON_POSITIVE, "Set", this);
        setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                (OnClickListener) null);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.text_picker_dialog, null);
        setView(view);

        mTextPicker = (NumberPicker) view.findViewById(R.id.text_picker);
        mValueTitle = (TextView) view.findViewById(R.id.text_picker_title);
        mValueTitle.setText(R.string.channel_picker_title);
        mBootstrap = (CheckBox) view.findViewById(R.id.checkBootstrap);
        mBootstrap.setChecked(bootstrap);

        // initialise state 
        mTextPicker.setMinValue(0); // we start from 0
        mTextPicker.setMaxValue(values.length - 1);
        mTextPicker.setValue(defaultValue);
        mTextPicker.setOnLongPressUpdateInterval(1); // will never have that many channels, set it to 1

        mTextPicker.setWrapSelectorWheel(false);  // no wrapping        
        mTextPicker.setDisplayedValues(values);
        getButton(BUTTON_POSITIVE).setText(R.string.action_install); 
    }

    public void onClick(DialogInterface dialog, int which) {
        if (mCallback != null) {
            mTextPicker.clearFocus();
            // store extra 
            mCallback.onChannelPicked(mTextPicker.getDisplayedValues()[mTextPicker.getValue()],
            		mBootstrap.isChecked());
            dialog.dismiss();
        }
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(NUMBER, mTextPicker.getValue());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int number = savedInstanceState.getInt(NUMBER);
        mTextPicker.setValue(number);
    }
}
