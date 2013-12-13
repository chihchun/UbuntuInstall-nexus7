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
public class NumberPickerDialog extends AlertDialog implements OnClickListener {
    private static final String NUMBER = "number";

    /**
     * Callback interface called once user makes selection 
     */
    public interface OnNumberPicktListener {
        void onNumberSelected(Context context, int value);
    }

    private final NumberPicker mNumberPicker;
    private final OnNumberPicktListener mCallback;
    private final int mValues[];

    /**
     * @param context
     * @param callBack observer for result
     * @param dialogTitle title of the dialog
     * @param values values to fill picker with
     * @param defaultValue index of the default value
     */
    public NumberPickerDialog(Context context,
    int title,
    int positiveButtonText,
    int negativeButtontext,
            final int[] values,
            final int defaultValue,
            final OnNumberPicktListener callBack) {
    
        super(context);
        mCallback = callBack;
        mValues = values;

        setTitle(R.string.channel_picker_dialog_title);
        Resources r = context.getResources();
        setButton(DialogInterface.BUTTON_POSITIVE, r.getString(positiveButtonText), this);
        setButton(DialogInterface.BUTTON_NEGATIVE, r.getString(negativeButtontext), (OnClickListener) null);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.number_picker_dialog, null);
        setView(view);

        mNumberPicker = (NumberPicker) view.findViewById(R.id.number_picker);
        // check if we should show latest option

        // initialise state 
        mNumberPicker.setMinValue(0); // we start from 0
        mNumberPicker.setMaxValue(values.length - 1);
        mNumberPicker.setValue(0);
        mNumberPicker.setOnLongPressUpdateInterval(1); // will never have that many channels, set it to 1
        mNumberPicker.setWrapSelectorWheel(false);  // no wrapping
        
        String[] valueSet = new String[values.length];
        for (int i = 0; i < values.length; ++i) {
            valueSet[i] = String.valueOf(values[i]);
        }
        mNumberPicker.setDisplayedValues(valueSet);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (mCallback != null) {
            mNumberPicker.clearFocus();
            // store extra 
            mCallback.onNumberSelected(getContext(), mValues[mNumberPicker.getValue()]);
            dialog.dismiss();
        }
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(NUMBER, mNumberPicker.getValue());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int number = savedInstanceState.getInt(NUMBER);
        mNumberPicker.setValue(number);
    }
}
