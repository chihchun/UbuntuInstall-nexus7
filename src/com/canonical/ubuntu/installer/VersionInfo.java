package com.canonical.ubuntu.installer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * Version holder
 *
 */
public class VersionInfo {
    private String mVersion[];
    // empty constructor
    public VersionInfo() {
        mVersion = new String[] {"", "", "", "0"};
    }
            
    public VersionInfo(String channelAlias,
                String channleJson,
                String description,
                int version) {
        mVersion = new String[] {channelAlias, channleJson, description, Integer.toString(version)};
    }
    
    public VersionInfo(Set<String> set) {
        if (set.size() == 4) {
            mVersion = set.toArray(new String[4]);
        } else {
            mVersion = new String[] {"", "", "", "0"};
        }
    }
    
    public Set<String> getSet() {
        return new HashSet<String>(Arrays.asList(mVersion));
    }
    
    public String getChannelAlias() {
        return mVersion[0];
    }
    
    public String getChannelJson() {
        return mVersion[1];
    }
    
    public String getDescription() {
        return mVersion[2];
    }
    
    public int getVersion() {
        return Integer.getInteger(mVersion[3],0);
    }
}