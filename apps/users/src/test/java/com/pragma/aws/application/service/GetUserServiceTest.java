package com.pragma.aws.application.service;

import com.pragma.aws.application.command.GetUserCommand;
import com.pragma.aws.domain.model.User;
import com.pragma.aws.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private GetUserService getUserService;

    @Test
    void execute_shouldReturnUser_whenUserExists() {
        Long userId = 1L;
        User user = new User(userId, "123456", "John Doe", "john@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Optional<User> result = getUserService.execute(new GetUserCommand(userId));

        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        verify(userRepository).findById(userId);
    }

    @Test
    void execute_shouldReturnEmpty_whenUserDoesNotExist() {
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<User> result = getUserService.execute(new GetUserCommand(userId));

        assertTrue(result.isEmpty());
        verify(userRepository).findById(userId);
    }
}
