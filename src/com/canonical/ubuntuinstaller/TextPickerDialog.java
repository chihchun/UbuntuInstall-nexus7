package com.canonical.ubuntuinstaller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

/**
 * A text picker dialog that prompts the user for to select one of the text options
 */
public class TextPickerDialog extends AlertDialog implements OnClickListener {
    private static final String NUMBER = "number";

    /**
     * Callback interface called once called makes selection 
     */
    public interface OnTextSetListener {

        /**
         * @param text The text that was set.
         */
        void onTextSet(String text);
    }

    private final NumberPicker mTextPicker;
    private final TextView mValueTitle;
    private final OnTextSetListener mCallback;


    /**
     * @param context
     * @param callBack observer for result
     * @param dialogTitle title of the dialog
     * @param pickerTitle title of the picker component
     * @param values values to fill picker with
     * @param defaultValue index of the default value
     */
    public TextPickerDialog(Context context,
    		OnTextSetListener callBack,
            int dialogTitle,
            int pickerTitle,
            String[] values,
            int defaultValue) {
        super(context);
        mCallback = callBack;

        setTitle(dialogTitle);

        setButton(DialogInterface.BUTTON_POSITIVE, "Set", this);
        setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                (OnClickListener) null);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.text_picker_dialog, null);
        setView(view);

        mTextPicker = (NumberPicker) view.findViewById(R.id.text_picker);
        mValueTitle = (TextView) view.findViewById(R.id.text_picker_title);
        mValueTitle.setText(pickerTitle);

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
            mCallback.onTextSet(mTextPicker.getDisplayedValues()[mTextPicker.getValue()]);
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
