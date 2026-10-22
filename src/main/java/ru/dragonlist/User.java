package ru.dragonlist;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String login;

  @Column(nullable = false, length = 100)
  private String password;

  protected User() {}

  public User(String login, String password) {
    this.login = login;
    this.password = password;
  }

  public Long getId() {
    return id;
  }

  public String getLogin() {
    return login;
  }

  public String getPassword() {
    return password;
  }
}
