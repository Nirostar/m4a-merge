module m4a_merge {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.logging;
    requires java.desktop;

    requires jcodec;
    requires org.apache.commons.text;
    requires org.openjdk.nashorn;
    requires org.apache.commons.lang3;
    requires jaudiotagger;

    opens at.nirostar_labs.m4a_merge.ui to javafx.fxml;
    opens at.nirostar_labs.m4a_merge.model to javafx.base;

    exports at.nirostar_labs.m4a_merge;
}
