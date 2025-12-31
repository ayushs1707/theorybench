module com.schoolproject {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.sql;

    opens com.schoolproject to javafx.fxml;
    exports com.schoolproject;
}

