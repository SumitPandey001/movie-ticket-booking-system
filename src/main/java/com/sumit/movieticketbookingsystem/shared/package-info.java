/**
 * Building blocks every module may use: money and time value objects, error handling, request logging and
 * the current user.
 * Open so that its sub-packages (error, user) are visible to other modules as well.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.sumit.movieticketbookingsystem.shared;

import org.springframework.modulith.ApplicationModule;
