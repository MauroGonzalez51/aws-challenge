package com.pragma.aws.application.service;

import com.pragma.aws.application.command.CreateUserCommand;
import com.pragma.aws.domain.model.User;
import com.pragma.aws.domain.port.in.CreateUserUseCase;
import com.pragma.aws.domain.port.out.UserRepositoryPort;
import com.pragma.aws.domain.exceptions.*;

import org.springframework.stereotype.Service;

@Service
public class CreateUserService implements CreateUserUseCase {
    private final UserRepositoryPort userRepository;

    public CreateUserService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User execute(CreateUserCommand command) {
        if (this.userRepository.findByEmail(command.email()).isPresent()) {
            throw new EmailAlreadyInUse(command.email());
        }

        if (this.userRepository.findByNoIdentification(command.noIdentification()).isPresent()) {
            throw new NoIdentificationAlreadyInUse(command.noIdentification());
        }

        User saved = this.userRepository
                .save(new User(null, command.noIdentification(), command.name(), command.email()));

        return saved;
    }
}
