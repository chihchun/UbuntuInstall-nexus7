/*
 * This file is part of Ubuntu for Android.
 * Copyright 2013 Canonical Ltd.
 * 
 * Ubuntu for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 *
 * Ubuntu for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY, SATISFACTORY QUALITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Ubuntu for Android. If not, see <http://www.gnu.org/licenses/>.
 */

package com.canonical.ubuntu.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.CheckBox;

import com.canonical.ubuntuinstaller.R;

public class UbuntuCheckBox extends CheckBox {

	public UbuntuCheckBox(Context context) {
		super(context);
	}

	public UbuntuCheckBox(Context context, AttributeSet attrs) {
		super(context, attrs);
		setCustomFont(context, attrs);
	}

	public UbuntuCheckBox(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setCustomFont(context, attrs);
	}

	private void setCustomFont(Context ctx, AttributeSet attrs) {
		TypedArray a = ctx.obtainStyledAttributes(attrs, R.styleable.UbuntuCheckBox);
		String customFont = a.getString(R.styleable.UbuntuCheckBox_customFont);
		setCustomFont(ctx, customFont);
		a.recycle();
	}

	public void setCustomFont(Context ctx, String asset) {
		try {
			Typeface tf = Typeface.createFromAsset(ctx.getAssets(), asset);
			setTypeface(tf);
		} catch (Exception e) {
			// Will use default font.
		}
	}

}
