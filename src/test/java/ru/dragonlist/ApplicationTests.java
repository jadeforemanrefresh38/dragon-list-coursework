package ru.dragonlist;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class ApplicationTests {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired NoteRepository notes;
  @Autowired PasswordEncoder encoder;

  @Test
  void registrationAndLogin() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("login", "NewUser")
                .param("password", "Secret123!"))
        .andExpect(redirectedUrl("/login?registered"));
    User u = users.findByLogin("newuser").orElseThrow();
    assertThat(u.getPassword()).isNotEqualTo("Secret123!");
    assertThat(encoder.matches("Secret123!", u.getPassword())).isTrue();
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
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors("form", "login"));
    mvc.perform(post("/register").with(csrf()).param("login", "x").param("password", "short"))
        .andExpect(model().attributeHasFieldErrors("form", "login", "password"));
  }

  @Test
  void anonymousAndCsrf() throws Exception {
    mvc.perform(get("/notes")).andExpect(status().is3xxRedirection());
    mvc.perform(post("/register").param("login", "bad").param("password", "Secret123!"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/login")).andExpect(status().isOk());
    mvc.perform(get("/register")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "alice")
  void lifecycleAndIsolation() throws Exception {
    users.saveAndFlush(new User("alice", encoder.encode("Secret123!")));
    User bob = users.saveAndFlush(new User("bob", encoder.encode("Secret123!")));
    NoteForm secret = new NoteForm();
    secret.setTitle("Bob private");
    secret.setContent("secret");
    secret.setTag(Tag.PERSONAL);
    Long foreign = notes.saveAndFlush(new Note(bob, secret)).getId();
    mvc.perform(
            post("/notes")
                .with(csrf())
                .param("title", "<script>alert(1)</script>")
                .param("content", "Текст\nВторая строка")
                .param("tag", "STUDY"))
        .andExpect(status().is3xxRedirection());
    Note own = notes.findByUserLoginOrderByCreatedAtDescIdDesc("alice").get(0);
    Long id = own.getId();
    var date = own.getCreatedAt();
    mvc.perform(get("/notes"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Bob private"))));
    mvc.perform(get("/notes/" + id))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
    mvc.perform(get("/notes/new")).andExpect(status().isOk());
    mvc.perform(get("/notes/" + id + "/edit")).andExpect(status().isOk());
    for (String suffix : new String[] {"", "/edit"})
      mvc.perform(get("/notes/" + foreign + suffix)).andExpect(status().isNotFound());
    mvc.perform(
            post("/notes/" + foreign + "/edit")
                .with(csrf())
                .param("title", "hack")
                .param("content", "hack")
                .param("tag", "IDEAS"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/notes/" + foreign + "/delete").with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/notes/" + id + "/edit")
                .with(csrf())
                .param("title", "Изменено")
                .param("content", "Новый текст")
                .param("tag", "IDEAS"))
        .andExpect(redirectedUrl("/notes/" + id));
    assertThat(notes.findById(id).orElseThrow().getCreatedAt()).isEqualTo(date);
    mvc.perform(get("/notes/table").param("tag", "STUDY"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Изменено"))));
    mvc.perform(get("/notes/table").param("tag", "IDEAS"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Изменено")));
    mvc.perform(
            post("/notes")
                .with(csrf())
                .param("title", " ")
                .param("content", " ")
                .param("tag", "UNKNOWN"))
        .andExpect(model().attributeHasFieldErrors("form", "title", "content", "tag"));
    mvc.perform(post("/notes/" + id + "/delete")).andExpect(status().isForbidden());
    mvc.perform(post("/notes/" + id + "/delete").with(csrf())).andExpect(redirectedUrl("/notes"));
    assertThat(notes.existsById(id)).isFalse();
    assertThat(notes.existsById(foreign)).isTrue();
  }
}
