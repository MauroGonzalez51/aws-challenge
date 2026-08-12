package com.pragma.aws.infraestructure.entrypoint.rest.controller;

import com.pragma.aws.application.command.CreateUserCommand;
import com.pragma.aws.application.command.GetUserCommand;
import com.pragma.aws.domain.exceptions.EmailAlreadyInUse;
import com.pragma.aws.domain.model.User;
import com.pragma.aws.domain.port.in.CreateUserUseCase;
import com.pragma.aws.domain.port.in.GetUserUseCase;
import com.pragma.aws.infraestructure.entrypoint.rest.mapper.UserRestMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateUserUseCase createUserUseCase;

    @MockitoBean
    private GetUserUseCase getUserUseCase;

    @MockitoBean
    private UserRestMapper mapper;

    @Test
    void getUser_shouldReturn200_whenUserExists() throws Exception {
        User user = new User(1L, "123456", "John Doe", "john@example.com");
        when(getUserUseCase.execute(any(GetUserCommand.class))).thenReturn(Optional.of(user));
        when(mapper.toResponse(user)).thenCallRealMethod();

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.noIdentification").value("123456"));
    }

    @Test
    void getUser_shouldReturn404_whenUserDoesNotExist() throws Exception {
        when(getUserUseCase.execute(any(GetUserCommand.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUser_shouldReturn201_whenValidRequest() throws Exception {
        User created = new User(1L, "123456", "John Doe", "john@example.com");
        when(createUserUseCase.execute(any(CreateUserCommand.class))).thenReturn(created);
        when(mapper.toCreateResponse(created)).thenCallRealMethod();

        String requestBody = """
                {
                    "noIdentification": "123456",
                    "name": "John Doe",
                    "email": "john@example.com"
                }
                """;

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void createUser_shouldPropagateException_whenEmailAlreadyExists() {
        when(createUserUseCase.execute(any(CreateUserCommand.class)))
                .thenThrow(new EmailAlreadyInUse("john@example.com"));

        String requestBody = """
                {
                    "noIdentification": "123456",
                    "name": "John Doe",
                    "email": "john@example.com"
                }
                """;

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)));
    }
}
