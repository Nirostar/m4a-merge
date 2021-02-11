package m4a_merge.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.time.Duration;

public abstract class TableEntry {
    private SimpleStringProperty filename = new SimpleStringProperty(this, "filename");
    private SimpleObjectProperty<Duration> length = new SimpleObjectProperty<>(this, "length");
    private SimpleDoubleProperty progress = new SimpleDoubleProperty(this, "progress");

    public TableEntry(String filename, Duration length) {
        this.filename.set(filename);
        this.length.set(length);
        this.progress.setValue(0d);
    }

    public String getFilename() {
        return filename.get();
    }

    public void setFilename(String filename) {
        this.filename.set(filename);
    }

    public Duration getLength() {
        return length.get();
    }

    public void setLength(Duration length) {
        this.length.set(length);
    }

    public abstract EntryType getType();

    public abstract Integer getTrackNr();

    public double getProgress() {
        return progress.get();
    }

    public SimpleDoubleProperty progressProperty() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress.set(progress);
    }
}
