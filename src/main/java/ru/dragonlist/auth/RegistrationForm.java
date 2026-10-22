package ru.dragonlist.auth;

import jakarta.validation.constraints.*;

public class RegistrationForm {
  @NotBlank(message = "Введите логин")
  @Pattern(regexp = "[a-zA-Z0-9_]{3,32}", message = "Логин: 3–32 латинские буквы, цифры или _")
  private String login = "";

  @NotBlank(message = "Введите пароль")
  @Size(min = 8, max = 64, message = "Пароль: от 8 до 64 символов")
  @Pattern(regexp = "[\\x20-\\x7E]*", message = "Пароль: латинские буквы, цифры и знаки")
  private String password = "";

  public String getLogin() {
    return login;
  }

  public void setLogin(String v) {
    login = v;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String v) {
    password = v;
  }
}
