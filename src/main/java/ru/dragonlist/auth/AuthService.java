package ru.dragonlist.auth;

import static ru.dragonlist.common.InputValidation.require;

import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public AuthService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @Transactional
  public void register(RegistrationForm form) {
    String login = form.getLogin().toLowerCase(Locale.ROOT);
    require(!users.existsByLogin(login), "Этот логин уже занят");
    require(users.count() < 200, "Регистрация временно ограничена");
    users.saveAndFlush(new User(login, encoder.encode(form.getPassword())));
  }
}
