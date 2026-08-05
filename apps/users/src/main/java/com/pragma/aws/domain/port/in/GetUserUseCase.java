package com.pragma.aws.domain.port.in;

import com.pragma.aws.domain.model.User;
import com.pragma.aws.application.command.GetUserCommand;
import java.util.Optional;

public interface GetUserUseCase {
    Optional<User> execute(GetUserCommand command);
}
