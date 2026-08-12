package com.pragma.aws.application.service;

import com.pragma.aws.application.command.CreateUserCommand;
import com.pragma.aws.domain.exceptions.EmailAlreadyInUse;
import com.pragma.aws.domain.exceptions.NoIdentificationAlreadyInUse;
import com.pragma.aws.domain.model.User;
import com.pragma.aws.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @InjectMocks
    private CreateUserService createUserService;

    private CreateUserCommand command;

    @BeforeEach
    void setUp() {
        command = new CreateUserCommand("123456", "John Doe", "john@example.com");
    }

    @Test
    void execute_shouldCreateUser_whenEmailAndNoIdentificationAreUnique() {
        when(userRepository.findByEmail(command.email())).thenReturn(Optional.empty());
        when(userRepository.findByNoIdentification(command.noIdentification())).thenReturn(Optional.empty());

        User savedUser = new User(1L, command.noIdentification(), command.name(), command.email());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = createUserService.execute(command);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(command.name(), result.getName());
        assertEquals(command.email(), result.getEmail());
        assertEquals(command.noIdentification(), result.getNoIdentification());

        verify(userRepository).findByEmail(command.email());
        verify(userRepository).findByNoIdentification(command.noIdentification());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void execute_shouldThrowEmailAlreadyInUse_whenEmailExists() {
        User existing = new User(2L, "999", "Existing", command.email());
        when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(existing));

        EmailAlreadyInUse exception = assertThrows(EmailAlreadyInUse.class,
                () -> createUserService.execute(command));

        assertEquals(command.email(), exception.getEmail());
        verify(userRepository).findByEmail(command.email());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void execute_shouldThrowNoIdentificationAlreadyInUse_whenNoIdentificationExists() {
        when(userRepository.findByEmail(command.email())).thenReturn(Optional.empty());

        User existing = new User(3L, command.noIdentification(), "Existing", "other@example.com");
        when(userRepository.findByNoIdentification(command.noIdentification())).thenReturn(Optional.of(existing));

        NoIdentificationAlreadyInUse exception = assertThrows(NoIdentificationAlreadyInUse.class,
                () -> createUserService.execute(command));

        assertEquals(command.noIdentification(), exception.getNoIdentification());
        verify(userRepository).findByEmail(command.email());
        verify(userRepository).findByNoIdentification(command.noIdentification());
        verify(userRepository, never()).save(any(User.class));
    }
}
