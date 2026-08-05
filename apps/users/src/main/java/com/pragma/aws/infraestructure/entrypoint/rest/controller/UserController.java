package com.pragma.aws.infraestructure.entrypoint.rest.controller;

import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;

import com.pragma.aws.domain.model.User;
import com.pragma.aws.application.command.CreateUserCommand;
import com.pragma.aws.application.command.GetUserCommand;
import com.pragma.aws.domain.port.in.CreateUserUseCase;
import com.pragma.aws.domain.port.in.GetUserUseCase;
import com.pragma.aws.infraestructure.entrypoint.rest.dto.request.CreateUserRequest;
import com.pragma.aws.infraestructure.entrypoint.rest.mapper.UserRestMapper;

@RestController
public class UserController {
    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final UserRestMapper mapper;

    public UserController(CreateUserUseCase createUserUseCase, GetUserUseCase getUserUseCase, UserRestMapper mapper) {
        this.createUserUseCase = createUserUseCase;
        this.getUserUseCase = getUserUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        Optional<User> result = this.getUserUseCase.execute(new GetUserCommand(id));

        return result.map(mapper::toResponse).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/user")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = this.createUserUseCase
                .execute(new CreateUserCommand(request.noIdentification(), request.name(), request.email()));

        return ResponseEntity.status(HttpStatus.CREATED).body(
                this.mapper.toCreateResponse(user));
    }
}
