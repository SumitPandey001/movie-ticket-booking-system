package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.pricing.ShowPricing;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final ShowPricing SHOW = new ShowPricing(7, 42, 1, 3, LocalDate.parse("2026-10-17"));
    private static final long PREMIUM = 2;

    @Test
    void activeWindow() {
        ActiveWindowRule rule = new ActiveWindowRule(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(rule.violation(coupon(Set.of()), order(60000))).isEmpty();
        assertThat(rule.violation(couponValid(NOW.plusSeconds(60), NOW.plusSeconds(3600)), order(60000)))
                .contains("This coupon isn't valid yet");
        assertThat(rule.violation(couponValid(NOW.minusSeconds(3600), NOW), order(60000)))
                .contains("This coupon has expired");
        Coupon inactive = coupon(Set.of());
        inactive.deactivate();
        assertThat(rule.violation(inactive, order(60000))).contains("This coupon has expired");
    }

    @Test
    void minimumOrderValue() {
        MinOrderValueRule rule = new MinOrderValueRule();
        Coupon needs300 = coupon(Set.of());     // min order ₹300

        assertThat(rule.violation(needs300, order(30000))).isEmpty();
        assertThat(rule.violation(needs300, order(29999))).contains("This coupon needs an order of at least ₹300");
    }

    @Test
    void scopesAreOrWithinATypeAndAcrossTypes() {
        ScopeRule rule = new ScopeRule();

        assertThat(rule.violation(coupon(Set.of()), order(60000))).isEmpty();                     // works everywhere
        assertThat(rule.violation(coupon(Set.of(scope(CouponScope.Type.CITY, 9), scope(CouponScope.Type.CITY, 1))),
                order(60000))).isEmpty();                                                         // one of two cities
        Coupon rightCityWrongMovie = coupon(Set.of(scope(CouponScope.Type.CITY, 1), scope(CouponScope.Type.MOVIE, 99)));
        assertThat(rule.violation(rightCityWrongMovie, order(60000)))
                .contains("This coupon can't be used for this show");
        assertThat(rule.violation(coupon(Set.of(scope(CouponScope.Type.CATEGORY, PREMIUM))), order(60000))).isEmpty();
        assertThat(rule.violation(coupon(Set.of(scope(CouponScope.Type.CATEGORY, 3))), order(60000)))
                .contains("This coupon can't be used for this show");                             // no recliner seat
    }

    @Test
    void globalLimit() {
        GlobalLimitRule rule = new GlobalLimitRule();
        Coupon coupon = coupon(Set.of());                   // max 1000 uses

        assertThat(rule.violation(coupon, order(60000))).isEmpty();
        ReflectionTestUtils.setField(coupon, "usedCount", 1000);
        assertThat(rule.violation(coupon, order(60000))).contains("This coupon has been fully used");
    }

    @Test
    void discountsNeverExceedTheOrder() {
        FlatDiscount flat = new FlatDiscount();
        PercentageDiscount percent = new PercentageDiscount();
        Coupon fifty = coupon(Set.of());                                                          // ₹50 flat
        Coupon twentyPercentUpTo100 = new Coupon("PCT20", new Coupon.Terms(DiscountType.PERCENT, 20, 10000L, 0,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), null, 1, Set.of()));

        assertThat(flat.discount(fifty, 72000)).isEqualTo(5000);
        assertThat(flat.discount(fifty, 3000)).isEqualTo(3000);
        assertThat(percent.discount(twentyPercentUpTo100, 30000)).isEqualTo(6000);
        assertThat(percent.discount(twentyPercentUpTo100, 72000)).isEqualTo(10000);                // capped
    }

    @Test
    void everyDiscountTypeNeedsExactlyOneCalculator() {
        assertThatThrownBy(() -> new DiscountCalculatorRegistry(List.of(new FlatDiscount())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No discount calculator for [PERCENT]");
        assertThatThrownBy(() -> new DiscountCalculatorRegistry(
                List.of(new FlatDiscount(), new FlatDiscount(), new PercentageDiscount())))
                .isInstanceOf(IllegalStateException.class);
    }

    private static CouponContext order(long orderPaise) {
        return new CouponContext(SHOW, UUID.randomUUID(), orderPaise, Set.of(PREMIUM));
    }

    private static CouponScope scope(CouponScope.Type type, long id) {
        return new CouponScope(type, id);
    }

    /** ₹50 flat, min order ₹300, 1000 uses, valid around NOW. */
    private static Coupon coupon(Set<CouponScope> scopes) {
        return new Coupon("FIRST50", new Coupon.Terms(DiscountType.FLAT, 5000, null, 30000,
                NOW.minusSeconds(3600), NOW.plusSeconds(3600), 1000, 1, scopes));
    }

    private static Coupon couponValid(Instant from, Instant to) {
        return new Coupon("WINDOW", new Coupon.Terms(DiscountType.FLAT, 5000, null, 0, from, to, null, 1, Set.of()));
    }
}
