package com.pragma.aws.domain.port.in;

import com.pragma.aws.domain.model.User;
import com.pragma.aws.application.command.GetUserCommand;

public interface GetUserUseCase {
    User execute(GetUserCommand command);
}
