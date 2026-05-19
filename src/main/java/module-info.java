module dev.adrian.goral.localhiveagent {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.kordamp.ikonli.javafx;
    requires eu.hansolo.tilesfx;

    opens dev.adrian.goral.localhiveagent to javafx.fxml;
    exports dev.adrian.goral.localhiveagent;
}