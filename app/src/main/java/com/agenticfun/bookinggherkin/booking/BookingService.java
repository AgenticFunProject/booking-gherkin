package com.agenticfun.bookinggherkin.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    private static final Set<String> SUPPORTED_EQUIPMENT_TYPES = Set.of("20FT", "40HC", "REEFER");

    private final AtomicLong idSequence = new AtomicLong();
    private final Map<String, BookingResponse> bookingsByReference = new ConcurrentHashMap<>();
    private final List<ExternalBookingValidator> externalValidators;
    private final Clock clock;

    public BookingService(List<ExternalBookingValidator> externalValidators) {
        this(externalValidators, Clock.systemUTC());
    }

    BookingService(List<ExternalBookingValidator> externalValidators, Clock clock) {
        this.externalValidators = externalValidators;
        this.clock = clock;
    }

    public BookingResponse create(CreateBookingRequest request) {
        rejectUnsupportedEquipment(request);
        externalValidators.forEach(validator -> validator.validate(request));

        long id = idSequence.incrementAndGet();
        Instant createdAt = Instant.now(clock);
        BookingResponse booking = new BookingResponse(
                id,
                request.customerId(),
                request.scheduleId(),
                request.quoteId(),
                request.customer(),
                request.cargo(),
                List.copyOf(request.equipment()),
                BookingStatus.PENDING,
                bookingReference(id, createdAt),
                createdAt);
        bookingsByReference.put(booking.bookingReference(), booking);
        return booking;
    }

    public BookingResponse getByReference(String bookingReference) {
        BookingResponse booking = bookingsByReference.get(bookingReference);
        if (booking == null) {
            throw new BookingNotFoundException(bookingReference);
        }
        return booking;
    }

    private void rejectUnsupportedEquipment(CreateBookingRequest request) {
        for (EquipmentLine line : request.equipment()) {
            if (!SUPPORTED_EQUIPMENT_TYPES.contains(line.type())) {
                throw new BadBookingRequestException("Unsupported equipment type: " + line.type());
            }
        }
    }

    private static String bookingReference(long id, Instant createdAt) {
        int year = createdAt.atZone(ZoneOffset.UTC).getYear();
        return "BKG-%d-%05d".formatted(year, id);
    }
}
