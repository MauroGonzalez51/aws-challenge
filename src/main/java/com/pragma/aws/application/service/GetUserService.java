package com.pragma.aws.application.service;

import org.springframework.stereotype.Service;

import com.pragma.aws.application.command.GetUserCommand;
import com.pragma.aws.domain.model.User;
import com.pragma.aws.domain.port.in.GetUserUseCase;
import com.pragma.aws.domain.port.out.UserRepositoryPort;

import java.util.Optional;

@Service
public class GetUserService implements GetUserUseCase {
    private final UserRepositoryPort userRepository;

    public GetUserService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> execute(GetUserCommand command) {
        return this.userRepository.findById(command.userId());
    }
}
