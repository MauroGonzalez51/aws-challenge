package com.pragma.aws.infraestructure.persistency.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByNoIdentification(String noIdentification);

    Optional<UserEntity> findByEmail(String email);
}
