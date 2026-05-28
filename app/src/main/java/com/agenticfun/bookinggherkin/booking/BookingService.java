package com.agenticfun.bookinggherkin.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    private static final Pattern BOOKING_REFERENCE = Pattern.compile("BKG-[0-9]{4}-[0-9]{5}");
    private static final Set<String> SUPPORTED_EQUIPMENT_TYPES = Set.of("20FT", "40FT", "40HC", "REEFER");

    private final AtomicLong idSequence = new AtomicLong();
    private final Map<Long, BookingResponse> bookingsById = new ConcurrentHashMap<>();
    private final Map<String, BookingResponse> bookingsByReference = new ConcurrentHashMap<>();
    private final List<ExternalBookingValidator> externalValidators;
    private final Clock clock = Clock.systemUTC();

    public BookingService(List<ExternalBookingValidator> externalValidators) {
        this.externalValidators = externalValidators;
    }

    public BookingResponse create(CreateBookingRequest request) {
        return create(request, BookingStatus.PENDING);
    }

    BookingResponse create(CreateBookingRequest request, BookingStatus status) {
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
                status,
                bookingReference(id, createdAt),
                createdAt,
                createdAt);
        bookingsById.put(booking.id(), booking);
        bookingsByReference.put(booking.bookingReference(), booking);
        return booking;
    }

    public BookingResponse getByIdentifier(String identifier) {
        if (identifier.chars().allMatch(Character::isDigit)) {
            long id;
            try {
                id = Long.parseLong(identifier);
            } catch (NumberFormatException ex) {
                throw invalidIdentifier(identifier);
            }
            return Optional.ofNullable(bookingsById.get(id))
                    .orElseThrow(() -> BookingNotFoundException.withId(id));
        }

        if (BOOKING_REFERENCE.matcher(identifier).matches()) {
            return Optional.ofNullable(bookingsByReference.get(identifier))
                    .orElseThrow(() -> BookingNotFoundException.withReference(identifier));
        }

        throw invalidIdentifier(identifier);
    }

    public BookingPageResponse list(Long customerId, String status, int page, int size, String sort) {
        if (page < 0) {
            throw new BadBookingRequestException("Parameter 'page' must be greater than or equal to 0");
        }
        if (size < 1) {
            throw new BadBookingRequestException("Parameter 'size' must be greater than or equal to 1");
        }

        BookingStatus parsedStatus = parseStatus(status);
        List<BookingResponse> filtered = bookingsById.values().stream()
                .filter(booking -> customerId == null || booking.customerId() == customerId)
                .filter(booking -> parsedStatus == null || booking.status() == parsedStatus)
                .sorted(comparator(sort))
                .toList();

        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size);
        boolean last = totalPages == 0 || page >= totalPages - 1;

        return new BookingPageResponse(
                filtered.subList(fromIndex, toIndex),
                page,
                size,
                filtered.size(),
                totalPages,
                last);
    }

    void clear() {
        idSequence.set(0);
        bookingsByReference.clear();
        bookingsById.clear();
    }

    private static BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new BadBookingRequestException("Parameter 'status' must be a valid BookingStatus");
        }
    }

    private static Comparator<BookingResponse> comparator(String sort) {
        String[] parts = Optional.ofNullable(sort).orElse("createdAt,desc").split(",", -1);
        String property = parts.length > 0 ? parts[0] : "createdAt";
        String direction = parts.length > 1 ? parts[1] : "desc";

        Comparator<BookingResponse> comparator = switch (property) {
            case "id" -> Comparator.comparingLong(BookingResponse::id);
            case "bookingReference" -> Comparator.comparing(BookingResponse::bookingReference);
            default -> Comparator.comparing(BookingResponse::createdAt).thenComparingLong(BookingResponse::id);
        };

        if ("desc".equalsIgnoreCase(direction)) {
            return comparator.reversed();
        }
        return comparator;
    }

    private static BadBookingRequestException invalidIdentifier(String identifier) {
        return new BadBookingRequestException("Invalid booking identifier: " + identifier
                + ". Expected numeric ID or booking reference in format BKG-YYYY-NNNNN");
    }

    public BookingResponse getById(long bookingId) {
        BookingResponse booking = bookingsById.get(bookingId);
        if (booking == null) {
            throw new BookingNotFoundException(bookingId);
        }
        return booking;
    }

    public BookingResponse confirm(long bookingId) {
        return transition(bookingId, BookingStatus.PENDING, BookingStatus.CONFIRMED);
    }

    public BookingResponse start(long bookingId) {
        return transition(bookingId, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
    }

    public BookingResponse complete(long bookingId) {
        return transition(bookingId, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);
    }

    public BookingResponse cancel(long bookingId) {
        return transition(bookingId, Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED), BookingStatus.CANCELLED);
    }

    private BookingResponse transition(long bookingId, BookingStatus requiredCurrentStatus, BookingStatus targetStatus) {
        return transition(bookingId, Set.of(requiredCurrentStatus), targetStatus);
    }

    private BookingResponse transition(long bookingId, Set<BookingStatus> allowedCurrentStatuses, BookingStatus targetStatus) {
        return bookingsById.compute(bookingId, (id, current) -> {
            if (current == null) {
                throw new BookingNotFoundException(id);
            }
            if (!allowedCurrentStatuses.contains(current.status())) {
                throw new BookingLifecycleConflictException(id, current.status(), targetStatus);
            }

            BookingResponse updated = withStatus(current, targetStatus);
            bookingsByReference.put(updated.bookingReference(), updated);
            return updated;
        });
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

    private BookingResponse withStatus(BookingResponse booking, BookingStatus status) {
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
                booking.createdAt(),
                Instant.now(clock));
    }
}
