package com.canonical.ubuntu.installer;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import com.canonical.ubuntu.installer.VersionInfo.ReleaseType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonChannelParser {
    
    public final static String FULL_RELEASE = "full";
    public final static String DELTA_RELEASE = "delta";
    
    // JSON supporting classes 
    public class File {
        String checksum;
        Integer order;
        String path;
        String signature;
        Integer size;
    }

    public class Image {
        String description;
        Integer version;
        String type;
        File[] files;
    }

    /**
     * Gather info on all available releases on server
     * @param jsonStr string holding json data
     * @param filter type of releases to add to the list
     * @return
     */
    static public List<Image> getAvailableReleases(String jsonStr, ReleaseType filter) {
        LinkedList<Image> releases = new LinkedList<Image>();
        
        JsonObject index =  new JsonParser().parse(jsonStr).getAsJsonObject();
        JsonArray images = index.get("images").getAsJsonArray();
        int size = images.size();

        for(int j = 0; j < size; j++) {
            Image image = new Gson().fromJson(images.get(j), Image.class);
            ReleaseType type = ReleaseType.UNKNOWN;
            if (FULL_RELEASE.equals(image.type)) {
                type = ReleaseType.FULL;
            } else if (DELTA_RELEASE.equals(image.type)) {
                type = ReleaseType.DELTA;
            }
            if (filter == type) {
                releases.add(image);
            }
        }
        // sort list
        Collections.sort(releases, imageComparator());
        return releases;
    }

    public static Comparator<Image> imageComparator() {
        Comparator<Image> imageComparator = new Comparator<Image>(){
            @Override
            public int compare(Image o1, Image o2) {
                return o2.version - o1.version;
            }
        };
        return imageComparator;
    }
    
    public static Comparator<File> fileComparator() {
        Comparator<File> imageComparator = new Comparator<File>(){
            @Override
            public int compare(File f1, File f2) {
                return f1.order - f2.order;
            }
        };
        return imageComparator;
    }

}
