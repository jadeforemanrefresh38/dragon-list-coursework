package ru.dragonlist.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import ru.dragonlist.auth.UserRepository;

@SpringBootTest(
    properties = {"spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class AuthenticationTests {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void registrationAndPasswordHash() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("login", "NewUser")
                .param("password", "Secret123!"))
        .andExpect(redirectedUrl("/login?registered"));
    var u = users.findByLogin("newuser").orElseThrow();
    assertThat(encoder.matches("Secret123!", u.getPassword())).isTrue();
    assertThat(u.getPassword()).isNotEqualTo("Secret123!");
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("username", "NEWUSER")
                .param("password", "Secret123!"))
        .andExpect(redirectedUrl("/notes"));
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("login", "newuser")
                .param("password", "Secret123!"))
        .andExpect(model().attributeHasFieldErrors("form", "login"));
  }

  @Test
  void anonymousAndCsrf() throws Exception {
    mvc.perform(get("/api/notes/")).andExpect(status().isUnauthorized());
    mvc.perform(post("/register").param("login", "bad").param("password", "Secret123!"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/login")).andExpect(status().isOk());
  }

  @Test
  void injectionCannotBypassLogin() throws Exception {
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("username", "admin' OR 1=1--")
                .param("password", "Secret123!"))
        .andExpect(redirectedUrl("/login?error"));
    mvc.perform(post("/register").with(csrf()).param("login", "x").param("password", "short"))
        .andExpect(model().attributeHasFieldErrors("form", "login", "password"));
  }
}
