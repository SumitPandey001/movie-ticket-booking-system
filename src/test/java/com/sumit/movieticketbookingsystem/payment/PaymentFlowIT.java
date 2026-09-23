package com.sumit.movieticketbookingsystem.payment;

import com.sumit.movieticketbookingsystem.TestcontainersConfiguration;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures;
import com.sumit.movieticketbookingsystem.booking.BookingFixtures.BookableShow;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService;
import com.sumit.movieticketbookingsystem.booking.internal.service.HoldService.CreateHold;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails.UpiDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentFlowIT {

    private static final UpiDetails UPI = new UpiDetails("asha@okbank");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private HoldService holds;

    @Autowired
    private PaymentApi payments;

    @Autowired
    private TransactionTemplate tx;

    private UUID bookingId;
    private UUID customer;

    @BeforeEach
    void holdSeats() throws Exception {
        BookableShow show = new BookingFixtures(mvc, jdbc).openShow();
        customer = UUID.randomUUID();
        bookingId = holds.createHold(new CreateHold(customer, show.id(), show.seats("A1"), null)).getId();
    }

    @Test
    void successfulPaymentIsRecordedWithMaskedDetails() {
        UUID paymentId = initiate();

        PaymentResult result = payments.execute(paymentId, UPI, SimulatedOutcome.SUCCESS);

        assertThat(result.status()).isEqualTo(PaymentResult.Status.SUCCESS);
        assertThat(result.providerTxnId()).startsWith("SIM-");
        assertThat(row(paymentId)).containsEntry("status", "SUCCESS").containsEntry("masked_details", "asha@okbank");
    }

    @Test
    void declinedPaymentIsANormalResult() {
        UUID paymentId = initiate();

        PaymentResult result = payments.execute(paymentId, UPI, SimulatedOutcome.FAILURE);

        assertThat(result.status()).isEqualTo(PaymentResult.Status.FAILED);
        assertThat(result.failureReason()).isEqualTo("Declined by issuer (simulated)");
        assertThat(row(paymentId)).containsEntry("status", "FAILED");
    }

    @Test
    void aBookingIsNeverChargedTwice() {
        payments.execute(initiate(), UPI, SimulatedOutcome.SUCCESS);
        UUID second = initiate();

        assertThatThrownBy(() -> payments.execute(second, UPI, SimulatedOutcome.SUCCESS))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("payment_one_success");
    }

    @Test
    void theGatewayIsNeverCalledInsideATransaction() {
        UUID paymentId = initiate();

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                payments.execute(paymentId, UPI, SimulatedOutcome.SUCCESS)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private UUID initiate() {
        return tx.execute(status ->
                payments.initiate(new InitiatePayment(bookingId, customer, "BKTEST01", 25960, UPI)));
    }

    private Map<String, Object> row(UUID paymentId) {
        return jdbc.sql("SELECT status, masked_details FROM payment WHERE id = ?").param(paymentId).query().singleRow();
    }
}
