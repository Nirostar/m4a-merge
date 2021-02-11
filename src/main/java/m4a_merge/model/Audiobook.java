package m4a_merge.model;

import java.time.Duration;

public class Audiobook extends TableEntry {
    public Audiobook() {
        super("Audiobook", Duration.ZERO);
    }

    @Override
    public EntryType getType() {
        return EntryType.AUDIOBOOK;
    }

    @Override
    public Integer getTrackNr() {
        return null;
    }
}
