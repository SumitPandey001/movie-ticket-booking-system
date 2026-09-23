package com.sumit.movieticketbookingsystem;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

/**
 * Helpers for API tests: requests as the gateway would forward them, and a few response readers.
 */
public final class ApiRequests {

    public static final UUID ADMIN_ID = UUID.fromString("0f8d7c1a-0000-4000-8000-00000000a001");

    private ApiRequests() {
    }

    public static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN");
    }

    /** A customer nobody has seen before. */
    public static MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder request) {
        return asCustomer(request, UUID.randomUUID());
    }

    public static MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder request, UUID userId) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", userId)
                .header("X-User-Role", "CUSTOMER");
    }

    public static long idOf(MvcResult result) throws UnsupportedEncodingException {
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
    }

    // integration tests share one database, so names that must be unique get a random suffix
    public static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
