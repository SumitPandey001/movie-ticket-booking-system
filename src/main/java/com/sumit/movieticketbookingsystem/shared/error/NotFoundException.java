package com.sumit.movieticketbookingsystem.shared.error;

public class NotFoundException extends DomainException {

    public NotFoundException(String entity, Object id) {
        super(ErrorCode.NOT_FOUND, entity + " " + id + " not found");
    }
}
