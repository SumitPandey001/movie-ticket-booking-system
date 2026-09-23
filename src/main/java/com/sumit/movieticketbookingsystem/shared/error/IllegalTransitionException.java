package com.sumit.movieticketbookingsystem.shared.error;

public class IllegalTransitionException extends DomainException {

    public IllegalTransitionException(String entity, Enum<?> from, Enum<?> to) {
        super(ErrorCode.INVALID_STATE, entity + " cannot move from " + from + " to " + to);
    }
}
