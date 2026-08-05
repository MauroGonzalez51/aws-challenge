package com.pragma.aws.domain.exceptions;

import lombok.Getter;

@Getter
public class NoIdentificationAlreadyInUse extends DomainException {
    private final String noIdentification;

    public NoIdentificationAlreadyInUse(String noIdentification) {
        super("NO_IDENTIFICATION_ALREADY_IN_USE", noIdentification);
        this.noIdentification = noIdentification;
    }
}
