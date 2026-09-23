package com.sumit.movieticketbookingsystem.pricing.internal.coupon;

import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.catalog.SeatCategoryInfo;
import com.sumit.movieticketbookingsystem.pricing.internal.coupon.Coupon.Terms;
import com.sumit.movieticketbookingsystem.shared.error.AlreadyExistsException;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
class CouponAdminService {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9]{3,30}");

    private final CouponRepository coupons;
    private final CatalogApi catalog;

    CouponAdminService(CouponRepository coupons, CatalogApi catalog) {
        this.coupons = coupons;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<Coupon> coupons() {
        return coupons.findAllByOrderByIdDesc();
    }

    @Transactional
    public Coupon create(String code, Terms terms) {
        String normalized = normalize(code);
        if (!CODE.matcher(normalized).matches()) {
            throw new ValidationException("Coupon codes are 3-30 letters and digits");
        }
        if (coupons.existsByCode(normalized)) {
            throw new AlreadyExistsException("Coupon", normalized);
        }
        validate(terms, 0);
        return coupons.save(new Coupon(normalized, terms));
    }

    /** @param code null, or the coupon's current code; codes never change */
    @Transactional
    public Coupon update(long id, String code, Terms terms) {
        Coupon coupon = find(id);
        if (code != null && !normalize(code).equals(coupon.getCode())) {
            throw new ValidationException("A coupon's code can't be changed");
        }
        validate(terms, coupon.getUsedCount());
        coupon.update(terms);
        return coupon;
    }

    @Transactional
    public void deactivate(long id) {
        find(id).deactivate();
    }

    private void validate(Terms terms, int usedSoFar) {
        switch (terms.discountType()) {
            case PERCENT -> {
                if (terms.discountValue() > 100) {
                    throw new ValidationException("A percentage discount can be at most 100%");
                }
            }
            case FLAT -> {
                if (terms.maxDiscountPaise() != null) {
                    throw new ValidationException("Only percentage coupons have a maximum discount");
                }
            }
        }
        if (!terms.validTo().isAfter(terms.validFrom())) {
            throw new ValidationException("validTo must be after validFrom");
        }
        if (terms.maxUses() != null && terms.maxUses() < usedSoFar) {
            throw new ValidationException("maxUses can't go below the " + usedSoFar + " uses already made");
        }
        terms.scopes().forEach(this::requireTarget);
    }

    private void requireTarget(CouponScope scope) {
        long id = scope.scopeId();
        switch (scope.scopeType()) {
            case CITY -> catalog.city(id);
            case MOVIE -> catalog.movie(id);
            case THEATER -> {
                if (catalog.theaters(Set.of(id)).isEmpty()) {
                    throw new NotFoundException("Theater", id);
                }
            }
            case CATEGORY -> {
                if (catalog.seatCategories().stream().map(SeatCategoryInfo::categoryId).noneMatch(c -> c == id)) {
                    throw new NotFoundException("Seat category", id);
                }
            }
        }
    }

    private static String normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private Coupon find(long id) {
        return coupons.findById(id).orElseThrow(() -> new NotFoundException("Coupon", id));
    }
}
