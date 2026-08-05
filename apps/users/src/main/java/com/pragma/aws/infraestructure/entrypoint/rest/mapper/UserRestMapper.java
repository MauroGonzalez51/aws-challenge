package com.pragma.aws.infraestructure.entrypoint.rest.mapper;

import org.springframework.stereotype.Component;

import com.pragma.aws.domain.model.User;
import com.pragma.aws.infraestructure.entrypoint.rest.dto.response.*;

@Component
public class UserRestMapper {
    public GetUserResponse toResponse(User user) {
        return new GetUserResponse(user.getId(), user.getNoIdentification(), user.getName(), user.getEmail());
    }

    public CreateUserResponse toCreateResponse(User user) {
        return new CreateUserResponse(user.getId(), user.getNoIdentification(), user.getName(), user.getEmail());
    }
}
