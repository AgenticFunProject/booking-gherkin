package com.agenticfun.bookinggherkin.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    private static final Set<String> SUPPORTED_EQUIPMENT_TYPES = Set.of("20FT", "40HC", "REEFER");

    private final AtomicLong idSequence = new AtomicLong();
    private final Map<Long, BookingResponse> bookingsById = new ConcurrentHashMap<>();
    private final Map<String, BookingResponse> bookingsByReference = new ConcurrentHashMap<>();
    private final List<ExternalBookingValidator> externalValidators;
    private final EquipmentClient equipmentClient;
    private final Clock clock = Clock.systemUTC();

    public BookingService(List<ExternalBookingValidator> externalValidators, EquipmentClient equipmentClient) {
        this.externalValidators = externalValidators;
        this.equipmentClient = equipmentClient;
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
        store(booking);
        return booking;
    }

    public BookingResponse getByReference(String bookingReference) {
        BookingResponse booking = bookingsByReference.get(bookingReference);
        if (booking == null) {
            throw new BookingNotFoundException(bookingReference);
        }
        return booking;
    }

    public BookingResponse getById(long bookingId) {
        BookingResponse booking = bookingsById.get(bookingId);
        if (booking == null) {
            throw new BookingNotFoundException(bookingId);
        }
        return booking;
    }

    public BookingResponse confirm(long bookingId) {
        return transition(bookingId, BookingStatus.PENDING, BookingStatus.CONFIRMED, equipmentClient::reserve);
    }

    public BookingResponse start(long bookingId) {
        return transition(bookingId, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
    }

    public BookingResponse complete(long bookingId) {
        return transition(bookingId, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);
    }

    public BookingResponse cancel(long bookingId) {
        return transition(bookingId, Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED), BookingStatus.CANCELLED,
                booking -> {
                    if (booking.status() == BookingStatus.CONFIRMED) {
                        equipmentClient.release(booking);
                    }
                });
    }

    private BookingResponse transition(long bookingId, BookingStatus requiredCurrentStatus, BookingStatus targetStatus) {
        return transition(bookingId, Set.of(requiredCurrentStatus), targetStatus);
    }

    private BookingResponse transition(long bookingId, BookingStatus requiredCurrentStatus, BookingStatus targetStatus,
            Consumer<BookingResponse> beforeTransition) {
        return transition(bookingId, Set.of(requiredCurrentStatus), targetStatus, beforeTransition);
    }

    private BookingResponse transition(long bookingId, Set<BookingStatus> allowedCurrentStatuses,
            BookingStatus targetStatus) {
        return transition(bookingId, allowedCurrentStatuses, targetStatus, booking -> {
        });
    }

    private BookingResponse transition(long bookingId, Set<BookingStatus> allowedCurrentStatuses,
            BookingStatus targetStatus, Consumer<BookingResponse> beforeTransition) {
        return bookingsById.compute(bookingId, (id, current) -> {
            if (current == null) {
                throw new BookingNotFoundException(id);
            }
            if (!allowedCurrentStatuses.contains(current.status())) {
                throw new BookingLifecycleConflictException(id, current.status(), targetStatus);
            }

            beforeTransition.accept(current);
            BookingResponse updated = withStatus(current, targetStatus);
            bookingsByReference.put(updated.bookingReference(), updated);
            return updated;
        });
    }

    private void store(BookingResponse booking) {
        bookingsById.put(booking.id(), booking);
        bookingsByReference.put(booking.bookingReference(), booking);
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

    private static BookingResponse withStatus(BookingResponse booking, BookingStatus status) {
        return new BookingResponse(
                booking.id(),
                booking.customerId(),
                booking.scheduleId(),
                booking.quoteId(),
                booking.customer(),
                booking.cargo(),
                booking.equipment(),
                status,
                booking.bookingReference(),
                booking.createdAt());
    }
}
