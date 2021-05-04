package at.nirostar_labs.m4a_merge.service;

import java.util.Collection;
import java.util.Set;

public enum ITunesMetaDataTag {
    TITLE(0xa96e616d, "TIT1"),
    ALBUM_INTERPRET(0x61415254, "TPE2"),
    INTERPRET(0xa9415254, "TPE1"),
    COMPOSER(0xa9777274, "TCOM"),
    ALBUM_TITLE(0xa9616c62, "TALB"),
    YEAR(0xa9646179, "TDRC"),
    GENRE(0xa967656e, "TCON"),
    COPYRIGHT(0xa9637079, "TCOP"),
    TRACK_NUMBER(0x74726b6e, "TRCK"),
    COVER(0x636f7672, "COVER"),
    CODED_WITH(0xa9746f6f, "TENC");

    private final int itunesKey;
    private final String id3Key;

    ITunesMetaDataTag(int itunesKey, String id3Key) {
        this.itunesKey = itunesKey;
        this.id3Key = id3Key;
    }

    public static ITunesMetaDataTag fromKey(int key) {
        for (ITunesMetaDataTag value : ITunesMetaDataTag.values()) {
            if (value.itunesKey == key) {
                return value;
            }
        }
        throw new IllegalArgumentException("There is no tag with with id 0x" + Integer.toHexString(key));
    }

    public static Collection<ITunesMetaDataTag> getAlbumTags() {
        return Set.of(ALBUM_INTERPRET, INTERPRET, COMPOSER, ALBUM_TITLE, YEAR, GENRE, COPYRIGHT, COVER, CODED_WITH);
    }

    public int getItunesKey() {
        return itunesKey;
    }

    public String getID3Key() {
        return id3Key;
    }
}
