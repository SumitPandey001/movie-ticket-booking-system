package com.sumit.movieticketbookingsystem.payment;

/**
 * Lets a demo or test choose what the simulated gateway answers.
 */
public enum SimulatedOutcome {
    SUCCESS,
    FAILURE,
    /** the gateway answers "pending" and the payment succeeds a little later, like a slow UPI approval */
    DELAYED
}
