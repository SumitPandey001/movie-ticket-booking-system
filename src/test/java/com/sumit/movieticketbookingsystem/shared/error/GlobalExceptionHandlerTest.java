package com.sumit.movieticketbookingsystem.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void notFound() throws Exception {
        mvc.perform(get("/test/bookings/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value("Booking 42 not found"))
                .andExpect(jsonPath("$.instance").value("/test/bookings/42"));
    }

    @Test
    void illegalTransitionIsAConflict() throws Exception {
        mvc.perform(get("/test/transition"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.detail").value("Booking cannot move from CANCELLED to CONFIRMED"));
    }

    @Test
    void businessValidation() throws Exception {
        mvc.perform(get("/test/too-many-seats"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("At most 10 seats per booking"));
    }

    @Test
    void beanValidationListsEveryBadField() throws Exception {
        mvc.perform(post("/test/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showId": 0, "note": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("showId", "note")));
    }

    @Test
    void unreadableJsonStillGetsACode() throws Exception {
        mvc.perform(post("/test/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownPathGetsACode() throws Exception {
        mvc.perform(get("/test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Something went wrong. Please try again."));
    }

    enum Status { CANCELLED, CONFIRMED }

    record HoldRequest(@Min(1) long showId, @NotBlank String note) {
    }

    // Non-static on purpose: component scanning skips inner classes, so the app context never sees it.
    @RestController
    class ThrowingController {

        @GetMapping("/test/bookings/{id}")
        String booking(@PathVariable long id) {
            throw new NotFoundException("Booking", id);
        }

        @GetMapping("/test/transition")
        String transition() {
            throw new IllegalTransitionException("Booking", Status.CANCELLED, Status.CONFIRMED);
        }

        @GetMapping("/test/too-many-seats")
        String tooManySeats() {
            throw new ValidationException("At most 10 seats per booking");
        }

        @PostMapping("/test/holds")
        String hold(@Valid @RequestBody HoldRequest request) {
            return "ok";
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("database password is hunter2");
        }
    }
}
