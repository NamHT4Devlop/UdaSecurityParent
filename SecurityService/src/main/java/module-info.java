module com.udasecurity {
    requires com.udasecurity.service.image;
    requires java.desktop;
    requires com.google.common;
    requires java.prefs;
    requires com.google.gson;
    requires miglayout.swing;
    requires org.slf4j;
    // Existing opening for reflection-based access to the service package
    opens com.udasecurity.service to com.google.gson;

}
