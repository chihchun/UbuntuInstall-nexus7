package com.canonical.ubuntu.installer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;


/**
 * Version holder
 *
 */
public class VersionInfo {
    
    private static final String ALIAS = "_alias";
    private static final String JSON = "_json";
    private static final String DESCRIPTION = "_description";
    private static final String VERSION = "_version";
    
    public final String mChannelAlias;
    public final String mChannelJson;
    public final String mDescription;
    public final int mVersion;
    
    // empty constructor
    public VersionInfo() {
        mChannelAlias = "";
        mChannelJson = "";
        mDescription = "";
        mVersion = -1;
    }
            
    public VersionInfo(String channelAlias,
                String channleJson,
                String description,
                int version) {
        mChannelAlias = channelAlias;
        mChannelJson = channleJson;
        mDescription = description;
        mVersion = version;
    }
    
    public VersionInfo( SharedPreferences sp, String set ) {
        mChannelAlias = sp.getString( set + ALIAS, "");
        mChannelJson = sp.getString( set + JSON, "");
        mDescription = sp.getString( set + DESCRIPTION, "");
        mVersion = sp.getInt( set + VERSION, -1);
    }
    
    public void storeVersion(SharedPreferences.Editor e, String set) {
        e.putString(set+ALIAS, mChannelAlias)
            .putString(set + JSON, mChannelJson)
            .putString(set + DESCRIPTION, mDescription)
            .putInt(set + VERSION, mVersion).commit();
    }

    public static void storeEmptyVersion(SharedPreferences.Editor e, String set) {
        e.putString(set+ALIAS, "")
            .putString(set + JSON, "")
            .putString(set + DESCRIPTION, "")
            .putInt(set + VERSION, -1).commit();
    }

    public static boolean hasValidVersion(SharedPreferences sp, String set) {
    return (-1 != sp.getInt(set + VERSION, -1));
    }
    
    public String getChannelAlias() {
        return mChannelAlias;
    }
    
    public String getChannelJson() {
        return mChannelJson;
    }
    
    public String getDescription() {
        return mDescription;
    }
    
    public int getVersion() {
        return mVersion;
    }
}