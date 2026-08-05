package com.pragma.aws.domain.exceptions;

import lombok.Getter;

@Getter
public class EmailAlreadyInUse extends DomainException {
    private final String email;

    public EmailAlreadyInUse(String email) {
        super("EMAIL_ALREADY_IN_USE", email);
        this.email = email;
    }
}
