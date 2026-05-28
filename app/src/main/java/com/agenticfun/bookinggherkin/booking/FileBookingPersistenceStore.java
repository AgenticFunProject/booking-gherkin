package com.agenticfun.bookinggherkin.booking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FileBookingPersistenceStore {

    private static final TypeReference<List<BookingResponse>> BOOKING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final BookingPersistenceProperties properties;

    public FileBookingPersistenceStore(ObjectMapper objectMapper, BookingPersistenceProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public synchronized List<BookingResponse> load() {
        if (!properties.isEnabled()) {
            return List.of();
        }

        Path path = properties.getPath();
        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            return objectMapper.readValue(path.toFile(), BOOKING_LIST);
        } catch (IOException ex) {
            throw new BookingPersistenceException("Failed to load bookings from " + path, ex);
        }
    }

    public synchronized void save(Collection<BookingResponse> bookings) {
        if (!properties.isEnabled()) {
            return;
        }

        Path path = properties.getPath();
        Path parent = path.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<BookingResponse> ordered = bookings.stream()
                    .sorted(Comparator.comparingLong(BookingResponse::id))
                    .toList();
            Path temporaryFile = parent == null
                    ? Files.createTempFile("bookings-", ".json.tmp")
                    : Files.createTempFile(parent, "bookings-", ".json.tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), ordered);
            moveIntoPlace(temporaryFile, path);
        } catch (IOException ex) {
            throw new BookingPersistenceException("Failed to save bookings to " + path, ex);
        }
    }

    public synchronized void clear() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            Files.deleteIfExists(properties.getPath());
        } catch (IOException ex) {
            throw new BookingPersistenceException("Failed to clear bookings from " + properties.getPath(), ex);
        }
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
