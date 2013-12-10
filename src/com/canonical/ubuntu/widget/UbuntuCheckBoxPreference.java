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
import android.graphics.Typeface;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class UbuntuCheckBoxPreference extends CheckBoxPreference {

	public UbuntuCheckBoxPreference(Context context) {
		super(context);
	}

	public UbuntuCheckBoxPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public UbuntuCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected View onCreateView(ViewGroup parent) {
		View view = super.onCreateView(parent);

		Typeface font = Typeface.createFromAsset(
				getContext().getAssets(), "Ubuntu-R.ttf");

		TextView title = (TextView) view.findViewById(android.R.id.title);
		title.setTypeface(font);

		TextView summary = (TextView) view.findViewById(android.R.id.summary);
		summary.setTypeface(font);

		return view;
	}

}
