package com.pragma.aws.infraestructure.persistency.jpa;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pragma.aws.domain.model.User;
import com.pragma.aws.domain.port.out.UserRepositoryPort;

@Component
public class UserPersistencyAdapter implements UserRepositoryPort {
    private final UserJpaRepository userRepository;

    public UserPersistencyAdapter(UserJpaRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = this.userRepository.save(toEntity(user));
        return toDomain(entity);
    }

    @Override
    public Optional<User> findById(Long id) {
        this.userRepository.findById(id).map(this::toDomain);
        // return Optional.empty();
    }

    private UserEntity toEntity(User user) {
        return new UserEntity(user.getId(), user.getNoIdentification(), user.getName(), user.getEmail());
    }

    private User toDomain(UserEntity entity) {
        return new User(entity.getId(), entity.getNoIdentification(), entity.getName(), entity.getEmail());
    }
}
