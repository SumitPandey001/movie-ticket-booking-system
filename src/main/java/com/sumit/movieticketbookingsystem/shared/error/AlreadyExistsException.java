package com.sumit.movieticketbookingsystem.shared.error;

public class AlreadyExistsException extends DomainException {

    public AlreadyExistsException(String entity, String name) {
        super(ErrorCode.ALREADY_EXISTS, entity + " '" + name + "' already exists");
    }
}
