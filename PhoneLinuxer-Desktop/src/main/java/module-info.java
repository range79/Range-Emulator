module com.range.phonelinuxerdesktop {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.range.phonelinuxerdesktop to javafx.fxml;
    exports com.range.phonelinuxerdesktop;
}