package com.sumit.movieticketbookingsystem.shared.error;

/**
 * The operation isn't allowed in the entity's current state, e.g. editing a layout that's already active.
 * For a rejected status change use {@link IllegalTransitionException}.
 */
public class InvalidStateException extends DomainException {

    public InvalidStateException(String message) {
        super(ErrorCode.INVALID_STATE, message);
    }
}
