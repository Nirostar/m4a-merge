package at.nirostar_labs.m4a_merge.model;

import java.io.File;
import java.time.Duration;

public class Track extends TableEntry {
    private File file;
    private int trackNr;

    public Track(String filename, Duration length, File file, int trackNr) {
        super(filename, length);
        this.file = file;
        this.trackNr = trackNr;
    }

    @Override
    public EntryType getType() {
        return EntryType.TRACK;
    }

    @Override
    public Integer getTrackNr() {
        return trackNr;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
