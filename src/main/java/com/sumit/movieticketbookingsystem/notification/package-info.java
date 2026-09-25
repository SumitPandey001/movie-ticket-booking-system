/**
 * Emails and SMS, driven only by booking and payment events.
 */
@ApplicationModule(allowedDependencies = {"booking", "payment", "shared"})
package com.sumit.movieticketbookingsystem.notification;

import org.springframework.modulith.ApplicationModule;
