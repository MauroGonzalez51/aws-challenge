package com.pragma.aws.domain.port.out;

import java.util.Optional;
import com.pragma.aws.domain.model.User;

public interface UserRepositoryPort {
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByNoIdentification(String noIdentification);

    Optional<User> findByEmail(String email);
}
