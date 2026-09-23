package com.sumit.movieticketbookingsystem.pricing;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.catalog.CatalogFixtures;
import com.sumit.movieticketbookingsystem.pricing.internal.coupon.Coupon;
import com.sumit.movieticketbookingsystem.pricing.internal.coupon.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static com.sumit.movieticketbookingsystem.ApiRequests.asAdmin;
import static com.sumit.movieticketbookingsystem.ApiRequests.asCustomer;
import static com.sumit.movieticketbookingsystem.ApiRequests.idOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CouponAdminIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CouponRepository coupons;

    @Autowired
    private TransactionTemplate tx;

    private long cityId;
    private String code;

    @BeforeEach
    void setUp() throws Exception {
        cityId = new CatalogFixtures(mvc).city();
        code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    @Test
    void createsACouponWithScopes() throws Exception {
        create("""
                {"code": "%s", "discountType": "FLAT", "discountValue": 5000, "minOrderPaise": 30000,
                 "validFrom": "2026-10-01T00:00:00+05:30", "validTo": "2026-10-31T23:59:59+05:30",
                 "maxUses": 1000, "scopes": [{"scopeType": "CITY", "scopeId": %d}]}
                """.formatted(code.toLowerCase(), cityId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))                       // stored upper-case
                .andExpect(jsonPath("$.perUserLimit").value(1))                  // default
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.scopes[0].scopeType").value("CITY"));

        mvc.perform(asAdmin(get("/api/v1/admin/coupons")))
                .andExpect(jsonPath("$[*].code").value(hasItem(code)));
    }

    @Test
    void codesAreUniqueAndFixed() throws Exception {
        long id = idOf(create(flat(code)).andExpect(status().isCreated()).andReturn());

        create(flat(code.toLowerCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"));
        mvc.perform(asAdmin(put("/api/v1/admin/coupons/{id}", id)).content(flat("OTHER" + code.substring(5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A coupon's code can't be changed"));
    }

    @Test
    void updateReplacesTermsButNeverTheUsageCount() throws Exception {
        long id = idOf(create(flat(code)).andExpect(status().isCreated()).andReturn());
        jdbc.sql("UPDATE coupon SET used_count = 5 WHERE id = ?").param(id).update();   // five customers used it

        mvc.perform(asAdmin(put("/api/v1/admin/coupons/{id}", id)).content("""
                        {"discountType": "PERCENT", "discountValue": 10, "maxDiscountPaise": 15000,
                         "validFrom": "2026-10-01T00:00:00Z", "validTo": "2026-12-31T00:00:00Z", "maxUses": 50,
                         "perUserLimit": 2, "scopes": [{"scopeType": "CITY", "scopeId": %d}]}
                        """.formatted(cityId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").value("PERCENT"))
                .andExpect(jsonPath("$.usedCount").value(5))
                .andExpect(jsonPath("$.scopes", hasSize(1)));
        assertThat(jdbc.sql("SELECT used_count FROM coupon WHERE id = ?").param(id).query(Integer.class).single())
                .isEqualTo(5);

        String fewerUses = flat(code).replace("\"maxUses\": 100", "\"maxUses\": 3");
        mvc.perform(asAdmin(put("/api/v1/admin/coupons/{id}", id)).content(fewerUses))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("maxUses can't go below the 5 uses already made"));
    }

    @Test
    void anEditDoesntLoseAUseRecordedWhileTheCouponWasLoaded() throws Exception {
        long id = idOf(create(flat(code)).andExpect(status().isCreated()).andReturn());

        tx.executeWithoutResult(status -> {
            Coupon coupon = coupons.findById(id).orElseThrow();                    // admin loads it (0 uses)
            // meanwhile a customer redeems it
            jdbc.sql("UPDATE coupon SET used_count = used_count + 1 WHERE id = ?").param(id).update();
            coupon.deactivate();                                                    // admin saves an edit
        });

        assertThat(jdbc.sql("SELECT used_count FROM coupon WHERE id = ?").param(id).query(Integer.class).single())
                .isEqualTo(1);
    }

    @Test
    void deactivate() throws Exception {
        long id = idOf(create(flat(code)).andExpect(status().isCreated()).andReturn());

        mvc.perform(asAdmin(post("/api/v1/admin/coupons/{id}/deactivate", id))).andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT active FROM coupon WHERE id = ?").param(id).query(Boolean.class).single())
                .isFalse();
    }

    @Test
    void rejectsCouponsThatDontMakeSense() throws Exception {
        create(flat(code).replace("\"FLAT\"", "\"PERCENT\"").replace("5000", "150"))
                .andExpect(status().isBadRequest());                                            // over 100%
        create(flat(code).replace("\"maxUses\"", "\"maxDiscountPaise\": 100, \"maxUses\""))
                .andExpect(status().isBadRequest());                                            // cap on a flat coupon
        create(flat(code).replace("2026-12-31T00:00:00Z", "2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest());                                            // ends before it starts
        create(flat("X!"))
                .andExpect(status().isBadRequest());
        create(flat(code).replace("[]", "[{\"scopeType\": \"MOVIE\", \"scopeId\": " + Long.MAX_VALUE + "}]"))
                .andExpect(status().isNotFound());
        create(flat(code).replace("\"code\": \"" + code + "\", ", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A new coupon needs a code"));
    }

    @Test
    void onlyAdminsManageCoupons() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/admin/coupons")).content(flat(code))).andExpect(status().isForbidden());
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(asAdmin(post("/api/v1/admin/coupons")).content(json));
    }

    private static String flat(String code) {
        return """
                {"code": "%s", "discountType": "FLAT", "discountValue": 5000,
                 "validFrom": "2026-10-01T00:00:00Z", "validTo": "2026-12-31T00:00:00Z", "maxUses": 100, "scopes": []}
                """.formatted(code);
    }
}
