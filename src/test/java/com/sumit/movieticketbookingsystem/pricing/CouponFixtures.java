package com.sumit.movieticketbookingsystem.pricing;

import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creates coupons through the admin API with a fresh code and a validity window that covers any test run.
 */
public class CouponFixtures {

    private final MockMvc mvc;

    public CouponFixtures(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * @param terms the rest of the coupon's JSON, e.g. {@code "discountType": "FLAT", "discountValue": 5000}
     * @return the new coupon's code
     */
    public String coupon(String terms) throws Exception {
        String code = "C" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        mvc.perform(asAdmin(post("/api/v1/admin/coupons")).content("""
                        {"code": "%s", "validFrom": "2020-01-01T00:00:00Z", "validTo": "2099-01-01T00:00:00Z",
                         %s}
                        """.formatted(code, terms)))
                .andExpect(status().isCreated());
        return code;
    }
}
