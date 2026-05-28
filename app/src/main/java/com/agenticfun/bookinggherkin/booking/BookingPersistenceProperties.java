package com.agenticfun.bookinggherkin.booking;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "booking.persistence")
public class BookingPersistenceProperties {

    private boolean enabled = true;
    private Path path = Path.of(System.getProperty("java.io.tmpdir"), "booking-gherkin-runtime", "bookings.json");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }
}
