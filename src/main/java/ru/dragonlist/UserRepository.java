package ru.dragonlist;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByLogin(String login);

  boolean existsByLogin(String login);
}
