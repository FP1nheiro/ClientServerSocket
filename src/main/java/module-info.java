module com.example.clientserversocket {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;


    requires org.controlsfx.controls;
    requires java.sql;

    opens com.example.clientserversocket.Client to javafx.graphics, javafx.fxml;
    exports com.example.clientserversocket;
}