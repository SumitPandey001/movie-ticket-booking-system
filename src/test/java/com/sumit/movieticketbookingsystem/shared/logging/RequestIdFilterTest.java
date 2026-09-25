package com.sumit.movieticketbookingsystem.shared.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void keepsTheGatewaysId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            seen.set(MDC.get(RequestIdFilter.MDC_KEY));
            MDC.put("userId", "someone");
        });

        assertThat(seen.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("abc-123");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void replacesAnIdThatCouldForgeLogLines() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "x\nERROR fake line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void asyncTasksLogUnderTheCallersId() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MDC.put(RequestIdFilter.MDC_KEY, "abc-123");
        Runnable task = new MdcTaskDecorator().decorate(() -> seen.set(MDC.get(RequestIdFilter.MDC_KEY)));
        MDC.clear();

        Thread.ofVirtual().start(task).join();

        assertThat(seen.get()).isEqualTo("abc-123");
    }
}
