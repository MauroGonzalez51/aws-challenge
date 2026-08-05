package com.pragma.aws.domain.port.in;

import com.pragma.aws.domain.model.User;
import com.pragma.aws.application.command.CreateUserCommand;

public interface CreateUserUseCase {
    User execute(CreateUserCommand command);
}
