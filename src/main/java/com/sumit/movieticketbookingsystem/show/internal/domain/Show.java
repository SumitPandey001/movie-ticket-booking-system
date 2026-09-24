package com.sumit.movieticketbookingsystem.show.internal.domain;

import com.sumit.movieticketbookingsystem.shared.persistence.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One screening of a movie on a screen. Catalog data is referenced by id only; the theater and city ids are
 * copied from the screen so browsing never has to join catalog tables.
 */
@Entity
public class Show extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long movieId;

    private Long screenId;

    private Long theaterId;

    private Long cityId;

    private Long layoutId;

    private LocalDate showDate;

    private Instant startTime;

    private Instant endTime;

    private Instant blockedUntil;

    private String language;

    private String format;

    @Enumerated(EnumType.STRING)
    private ShowStatus status;

    private int totalSeats;

    private Long priceFromPaise;

    private Long refundPolicyId;

    @Version
    private long version;

    protected Show() {
    }

    // refundPolicyId may be null; the default policy then applies when a booking is confirmed
    public Show(Placement placement, long movieId, Timing timing, String language, String format, int totalSeats,
            Long refundPolicyId) {
        this.movieId = movieId;
        this.screenId = placement.screenId();
        this.theaterId = placement.theaterId();
        this.cityId = placement.cityId();
        this.layoutId = placement.layoutId();
        this.showDate = timing.showDate();
        this.startTime = timing.start();
        this.endTime = timing.end();
        this.blockedUntil = timing.blockedUntil();
        this.language = language;
        this.format = format;
        this.totalSeats = totalSeats;
        this.refundPolicyId = refundPolicyId;
        this.status = ShowStatus.SCHEDULED;
    }

    public void open() {
        status = status.transitionTo(ShowStatus.OPEN);
    }

    public void cancel() {
        status = status.transitionTo(ShowStatus.CANCELLED);
    }

    /** The lowest category price, kept on the show so browsing doesn't have to ask pricing. */
    public void updatePriceFrom(long pricePaise) {
        this.priceFromPaise = pricePaise;
    }

    /** Where the show runs; the layout is the screen's active one at creation time and never changes. */
    public record Placement(long screenId, long theaterId, long cityId, long layoutId) {
    }

    /** blockedUntil is the end plus the cleaning buffer: the screen is taken until then. */
    public record Timing(LocalDate showDate, Instant start, Instant end, Instant blockedUntil) {
    }

    public Long getId() {
        return id;
    }

    public Long getMovieId() {
        return movieId;
    }

    public Long getScreenId() {
        return screenId;
    }

    public Long getTheaterId() {
        return theaterId;
    }

    public Long getCityId() {
        return cityId;
    }

    public Long getLayoutId() {
        return layoutId;
    }

    public LocalDate getShowDate() {
        return showDate;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public String getLanguage() {
        return language;
    }

    public String getFormat() {
        return format;
    }

    public ShowStatus getStatus() {
        return status;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public Long getRefundPolicyId() {
        return refundPolicyId;
    }

    public Long getPriceFromPaise() {
        return priceFromPaise;
    }
}
