module com.example.clientserversocket {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;

    opens com.example.clientserversocket to javafx.fxml;
    exports com.example.clientserversocket;
}