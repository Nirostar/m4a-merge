package m4a_merge.model;

import java.time.Duration;

public class Chapter extends TableEntry {
    private int trackNr;

    public Chapter(String filename, int trackNr) {
        super(filename, Duration.ZERO);
        this.trackNr = trackNr;
    }

    @Override
    public EntryType getType() {
        return EntryType.CHAPTER;
    }

    @Override
    public Integer getTrackNr() {
        return trackNr;
    }

    public void setTrackNr(int trackNr) {
        this.trackNr = trackNr;
    }
}
