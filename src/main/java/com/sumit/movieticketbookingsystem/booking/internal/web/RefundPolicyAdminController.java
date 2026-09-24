package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicyService;
import jakarta.validation.Valid;
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
}
