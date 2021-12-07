package workshop.cinema.reservation.domain;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import workshop.cinema.base.domain.Clock;
import workshop.cinema.reservation.domain.ShowCommand.CancelSeatReservation;
import workshop.cinema.reservation.domain.ShowCommand.CreateShow;
import workshop.cinema.reservation.domain.ShowCommand.ReserveSeat;
import workshop.cinema.reservation.domain.ShowEvent.SeatReservationCancelled;
import workshop.cinema.reservation.domain.ShowEvent.SeatReserved;
import workshop.cinema.reservation.domain.ShowEvent.ShowCreated;

import java.io.Serializable;

import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;
import static workshop.cinema.reservation.domain.ShowCommandError.SEAT_NOT_AVAILABLE;
import static workshop.cinema.reservation.domain.ShowCommandError.SEAT_NOT_EXISTS;
import static workshop.cinema.reservation.domain.ShowCommandError.SEAT_NOT_RESERVED;
import static workshop.cinema.reservation.domain.ShowCommandError.SHOW_ALREADY_EXISTS;

public record Show(ShowId id, String title, Map<SeatNumber, Seat> seats) implements Serializable {

    public static Show create(ShowCreated showCreated) {
        InitialShow initialShow = showCreated.initialShow();
        return new Show(initialShow.id(), initialShow.title(), initialShow.seats());
    }

    public Either<ShowCommandError, List<ShowEvent>> process(ShowCommand command, Clock clock) {
        return switch (command) {
            case CreateShow ignored -> left(SHOW_ALREADY_EXISTS);
            case ReserveSeat reserveSeat -> handleReservation(reserveSeat, clock);
            case CancelSeatReservation cancelSeatReservation -> handleCancellation(cancelSeatReservation, clock);
        };
    }

    private Either<ShowCommandError, List<ShowEvent>> handleReservation(ReserveSeat reserveSeat, Clock clock) {
        SeatNumber seatNumber = reserveSeat.seatNumber();
        return seats.get(seatNumber).<Either<ShowCommandError, List<ShowEvent>>>map(seat -> {
            if (seat.isAvailable()) {
                return right(List.of(new SeatReserved(id, clock.now(), seatNumber)));
            } else {
                return left(SEAT_NOT_AVAILABLE);
            }
        }).getOrElse(left(SEAT_NOT_EXISTS));
    }

    private Either<ShowCommandError, List<ShowEvent>> handleCancellation(CancelSeatReservation cancelSeatReservation, Clock clock) {
        SeatNumber seatNumber = cancelSeatReservation.seatNumber();
        return seats.get(seatNumber).<Either<ShowCommandError, List<ShowEvent>>>map(seat -> {
            if (seat.isReserved()) {
                return right(List.of(new SeatReservationCancelled(id, clock.now(), seatNumber)));
            } else {
                return left(SEAT_NOT_RESERVED);
            }
        }).getOrElse(left(SEAT_NOT_EXISTS));
    }

    public Show apply(ShowEvent event) {
        return switch (event) {
            case ShowCreated ignored -> throw new IllegalStateException("Show is already created, use Show.create instead.");
            case SeatReserved seatReserved -> applyReserved(seatReserved);
            case SeatReservationCancelled seatReservationCancelled -> applyReservationCancelled(seatReservationCancelled);
        };
    }

    private Show applyReservationCancelled(SeatReservationCancelled seatReservationCancelled) {
        Seat seat = getSeatOrThrow(seatReservationCancelled.seatNumber());
        return new Show(id, title, seats.put(seat.number(), seat.available()));
    }

    private Show applyReserved(SeatReserved seatReserved) {
        Seat seat = getSeatOrThrow(seatReserved.seatNumber());
        return new Show(id, title, seats.put(seat.number(), seat.reserved()));
    }

    private Seat getSeatOrThrow(SeatNumber seatNumber) {
        return seats.get(seatNumber).getOrElseThrow(() -> new IllegalStateException("Seat not exists %s".formatted(seatNumber)));
    }
}

