/**
 * Holds, checkout, cancellations, refund policies and booking history.
 */
@ApplicationModule(allowedDependencies = {"catalog", "inventory", "payment", "pricing", "shared", "show"})
package com.sumit.movieticketbookingsystem.booking;

import org.springframework.modulith.ApplicationModule;
