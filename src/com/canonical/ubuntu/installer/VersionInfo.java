package com.canonical.ubuntu.installer;


import android.content.SharedPreferences;

/**
 * Version holder
 *
 */
public class VersionInfo {
    
    private static final String ALIAS = "_alias";
    private static final String JSON = "_json";
    private static final String DESCRIPTION = "_description";
    private static final String VERSION = "_version";
    private static final String TYPE = "type";
    
    public enum ReleaseType {
        FULL(0), 
        DELTA(1), 
        UNKNOWN(2);
        private final int value;

        private ReleaseType(final int newValue) {
            value = newValue;
        }

        public int getValue() {
            return value; 
        }
        
        public static ReleaseType fromValue(final int value) {
            switch (value) {
                case 0: // FULL
                    return FULL;
                case 1: // DELTA
                    return DELTA;
            }
            return UNKNOWN;
        }
    };
    
    public final String mChannelAlias;
    public final String mChannelJson;
    public final String mDescription;
    public final int mVersion;
    public final ReleaseType mReleaseType;
    
    // empty constructor
    public VersionInfo() {
        mChannelAlias = "";
        mChannelJson = "";
        mDescription = "";
        mVersion = -1;
        mReleaseType = ReleaseType.UNKNOWN;
    }
            
    public VersionInfo(String channelAlias,
                String channleJson,
                String description,
                int version, 
                ReleaseType type) {
        mChannelAlias = channelAlias;
        mChannelJson = channleJson;
        mDescription = description;
        mVersion = version;
        mReleaseType = type;
    }
    
    public VersionInfo( SharedPreferences sp, String set ) {
        mChannelAlias = sp.getString( set + ALIAS, "");
        mChannelJson = sp.getString( set + JSON, "");
        mDescription = sp.getString( set + DESCRIPTION, "");
        mVersion = sp.getInt( set + VERSION, -1);
        mReleaseType = ReleaseType.fromValue(sp.getInt(TYPE, ReleaseType.FULL.getValue())); // default is FULL
    }
    
    public void storeVersion(SharedPreferences.Editor e, String set) {
        e.putString(set+ALIAS, mChannelAlias)
            .putString(set + JSON, mChannelJson)
            .putString(set + DESCRIPTION, mDescription)
            .putInt(set + VERSION, mVersion)
            .putInt(set + TYPE, mReleaseType.ordinal())
            .commit();
    }

    public static void storeEmptyVersion(SharedPreferences.Editor e, String set) {
        e.putString(set+ALIAS, "")
            .putString(set + JSON, "")
            .putString(set + DESCRIPTION, "")
            .putInt(set + VERSION, -1)
            .putInt(set + TYPE, ReleaseType.UNKNOWN.ordinal())
            .commit();
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
    
    public boolean isFullUpdate() {
        return ReleaseType.FULL == mReleaseType;
    }
    
    public ReleaseType getReleaseType() {
        return mReleaseType;
    }
}