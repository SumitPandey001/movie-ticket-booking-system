package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicy;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicy.Terms;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicyService;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicyType;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundSlab;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/refund-policies")
class RefundPolicyAdminController {

    private final RefundPolicyService policyService;

    RefundPolicyAdminController(RefundPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    List<RefundPolicyResponse> policies() {
        return policyService.policies().stream().map(RefundPolicyResponse::from).toList();
    }

    @GetMapping("/{id}")
    RefundPolicyResponse policy(@PathVariable long id) {
        return RefundPolicyResponse.from(policyService.policy(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RefundPolicyResponse create(@Valid @RequestBody RefundPolicyRequest request) {
        return RefundPolicyResponse.from(policyService.create(request.toTerms()));
    }

    @PutMapping("/{id}")
    RefundPolicyResponse update(@PathVariable long id, @Valid @RequestBody RefundPolicyRequest request) {
        return RefundPolicyResponse.from(policyService.update(id, request.toTerms()));
    }

    @PostMapping("/{id}/make-default")
    RefundPolicyResponse makeDefault(@PathVariable long id) {
        return RefundPolicyResponse.from(policyService.makeDefault(id));
    }

    /**
     * {@code slabs} only for SLAB, e.g. {@code [{"minHoursBefore": 24, "percent": 100}, {"minHoursBefore": 0,
     * "percent": 0}]}; cancelling with less time left than the smallest slab refunds nothing.
     * {@code refundFees} (SLAB only) also gives back the convenience fee.
     */
    record RefundPolicyRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull RefundPolicyType type,
            Boolean refundFees,
            List<@Valid @NotNull SlabRequest> slabs) {

        Terms toTerms() {
            List<RefundSlab> terms = slabs == null ? List.of()
                    : slabs.stream().map(slab -> new RefundSlab(slab.minHoursBefore(), slab.percent())).toList();
            return new Terms(name, type, Boolean.TRUE.equals(refundFees), terms);
        }
    }

    record SlabRequest(@NotNull @Min(0) Integer minHoursBefore, @NotNull @Min(0) @Max(100) Integer percent) {
    }

    record RefundPolicyResponse(long id, String name, RefundPolicyType type, boolean refundFees,
                                boolean isDefault, List<RefundSlab> slabs) {

        static RefundPolicyResponse from(RefundPolicy policy) {
            return new RefundPolicyResponse(policy.getId(), policy.getName(), policy.getType(),
                    policy.isRefundFees(), policy.isDefaultPolicy(), List.copyOf(policy.getSlabs()));
        }
    }
}
