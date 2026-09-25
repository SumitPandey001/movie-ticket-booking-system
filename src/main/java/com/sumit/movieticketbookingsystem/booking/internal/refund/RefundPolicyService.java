package com.sumit.movieticketbookingsystem.booking.internal.refund;

import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicy.Terms;
import com.sumit.movieticketbookingsystem.shared.error.AlreadyExistsException;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import com.sumit.movieticketbookingsystem.show.ShowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class RefundPolicyService {

    private static final Logger log = LoggerFactory.getLogger(RefundPolicyService.class);

    private final RefundPolicyRepository policies;
    private final ShowApi shows;

    RefundPolicyService(RefundPolicyRepository policies, ShowApi shows) {
        this.policies = policies;
        this.shows = shows;
    }

    @Transactional(readOnly = true)
    public List<RefundPolicy> policies() {
        return policies.findAllByOrderByName();
    }

    @Transactional(readOnly = true)
    public RefundPolicy policy(long id) {
        return find(id);
    }

    @Transactional
    public RefundPolicy create(Terms terms) {
        RefundPolicy policy = policies.save(new RefundPolicy(validated(terms, 0)));
        log.info("Refund policy {} created: {}", policy.getId(), terms);
        return policy;
    }

    /** Bookings already confirmed keep the terms they were sold under. */
    @Transactional
    public RefundPolicy update(long id, Terms terms) {
        RefundPolicy policy = find(id);
        policy.update(validated(terms, id));
        log.info("Refund policy {} updated: {}", id, terms);
        return policy;
    }

    /** The flush matters: the old default has to be cleared before refund_policy_one_default sees the new one. */
    @Transactional
    public RefundPolicy makeDefault(long id) {
        RefundPolicy policy = find(id);
        policies.findByDefaultPolicyTrue().filter(current -> !current.getId().equals(id)).ifPresent(current -> {
            current.makeDefault(false);
            policies.flush();
        });
        policy.makeDefault(true);
        log.info("Refund policy {} is now the default", id);
        return policy;
    }

    /** The show's own policy, or the default one when it has none. Joins the caller's transaction. */
    @Transactional(readOnly = true)
    public RefundPolicySnapshot snapshotForShow(long showId) {
        Long policyId = shows.show(showId).refundPolicyId();
        RefundPolicy policy = policyId == null
                ? policies.findByDefaultPolicyTrue().orElseThrow(() -> new IllegalStateException("No default policy"))
                : find(policyId);
        return policy.snapshot();
    }

    private Terms validated(Terms terms, long id) {
        String name = terms.name().strip();
        if (policies.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new AlreadyExistsException("Refund policy", name);
        }
        if (terms.type() != RefundPolicyType.SLAB) {
            if (!terms.slabs().isEmpty() || terms.refundFees()) {
                throw new ValidationException("Only SLAB policies have slabs and refundFees");
            }
            return new Terms(name, terms.type(), false, List.of());
        }
        List<RefundSlab> slabs = terms.slabs().stream()
                .sorted(Comparator.comparingInt(RefundSlab::minHoursBefore).reversed())
                .toList();
        if (slabs.isEmpty()) {
            throw new ValidationException("A SLAB policy needs at least one slab");
        }
        for (int i = 1; i < slabs.size(); i++) {
            RefundSlab earlier = slabs.get(i - 1);
            RefundSlab later = slabs.get(i);
            if (earlier.minHoursBefore() == later.minHoursBefore()) {
                throw new ValidationException("Two slabs start at " + later.minHoursBefore() + " hours");
            }
            if (later.percent() > earlier.percent()) {
                throw new ValidationException("Cancelling later can't refund more than cancelling earlier");
            }
        }
        return new Terms(name, RefundPolicyType.SLAB, terms.refundFees(), slabs);
    }

    private RefundPolicy find(long id) {
        return policies.findById(id).orElseThrow(() -> new NotFoundException("Refund policy", id));
    }
}
