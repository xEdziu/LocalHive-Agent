module dev.adrian.goral.localhiveagent {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.swing;

    requires java.desktop;
    requires java.net.http;
    requires java.sql;

    requires com.github.oshi;
    requires org.slf4j;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.databind;

    requires eu.hansolo.tilesfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;

    exports dev.adrian.goral.localhiveagent.app;

    opens dev.adrian.goral.localhiveagent.config to tools.jackson.databind;
    opens dev.adrian.goral.localhiveagent.master.dto to tools.jackson.databind;
}
