/**
 * Taking payments and refunding them. Knows nothing about bookings beyond their id.
 */
@ApplicationModule(allowedDependencies = {"shared"})
package com.sumit.movieticketbookingsystem.payment;

import org.springframework.modulith.ApplicationModule;
