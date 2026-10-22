package ru.dragonlist;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:workspace;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
    })
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "apiowner")
class WorkspaceApiTests {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired WorkspaceService service;
  @Autowired jakarta.persistence.EntityManager entities;

  @BeforeEach
  void users() {
    users.saveAndFlush(new User("apiowner", encoder.encode("Secret123!")));
    users.saveAndFlush(new User("apiforeign", encoder.encode("Secret123!")));
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private long create(String type) throws Exception {
    return body(mvc.perform(
                post("/api/notes/")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "title",
                                "API note",
                                "content",
                                "# Heading\nText",
                                "note_type",
                                type,
                                "tag_names",
                                List.of("Учеба", "Custom")))))
            .andExpect(status().isCreated())
            .andReturn())
        .get("id")
        .asLong();
  }

  @Test
  void notesTagsSearchDraftTrashAndRestore() throws Exception {
    long id = create("note");
    mvc.perform(get("/api/notes/").param("tag", "Custom").param("search", "Heading"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].tags.length()").value(2));
    mvc.perform(
            patch("/api/notes/" + id + "/")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"title\":\"Changed\",\"content\":\"\",\"is_pinned\":true,\"status\":\"draft\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.is_pinned").value(true));
    mvc.perform(get("/api/notes/")).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/notes/").param("status", "draft"))
        .andExpect(jsonPath("$[0].title").value("Changed"));
    mvc.perform(post("/api/notes/" + id + "/hard_delete/").with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/notes/bulk/")
                .with(csrf())
                .contentType("application/json")
                .content("{\"ids\":[" + id + "],\"action\":\"trash\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/notes/").param("status", "trashed"))
        .andExpect(jsonPath("$[0].deleted_at").isNotEmpty());
    mvc.perform(post("/api/notes/" + id + "/restore/").with(csrf()))
        .andExpect(jsonPath("$.status").value("active"));
    mvc.perform(delete("/api/notes/" + id + "/").with(csrf())).andExpect(status().isNoContent());
    mvc.perform(post("/api/notes/" + id + "/hard_delete/").with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/notes/" + id + "/")).andExpect(status().isNotFound());
  }

  @Test
  void tagsReorderAndOwnership() throws Exception {
    long first = create("note"), second = create("note");
    long foreign =
        service.create(service.user("apiforeign"), Map.of("title", "Private")).get("id")
                instanceof Number n
            ? n.longValue()
            : 0;
    mvc.perform(
            post("/api/notes/reorder/")
                .with(csrf())
                .contentType("application/json")
                .content("{\"order\":[" + second + "," + first + "]}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/notes/")).andExpect(jsonPath("$[0].id").value(second));
    for (String action : List.of("restore", "hard_delete"))
      mvc.perform(post("/api/notes/" + foreign + "/" + action + "/").with(csrf()))
          .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/notes/" + foreign + "/")
                .with(csrf())
                .contentType("application/json")
                .content("{\"title\":\"hack\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/notes/bulk/")
                .with(csrf())
                .contentType("application/json")
                .content("{\"ids\":[" + first + "," + foreign + "],\"action\":\"trash\"}"))
        .andExpect(status().isNotFound());
    assertThat(service.note(first, service.user("apiowner")).get("status")).isEqualTo("active");
    long tag =
        body(mvc.perform(
                    post("/api/tags/")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Test tag\",\"color\":\"#abcdef\"}"))
                .andExpect(status().isCreated())
                .andReturn())
            .get("id")
            .asLong();
    mvc.perform(
            patch("/api/tags/" + tag + "/")
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Renamed\"}"))
        .andExpect(jsonPath("$.name").value("Renamed"));
    mvc.perform(delete("/api/tags/" + tag + "/").with(user("apiforeign")).with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/tags/" + tag + "/").with(csrf())).andExpect(status().isNoContent());
    mvc.perform(
            post("/api/notes/")
                .with(csrf())
                .contentType("application/json")
                .content("{\"note_type\":\"mindmap\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tasksImagesAndIsolation() throws Exception {
    long note = create("todo");
    long todo =
        body(mvc.perform(
                    post("/api/todos/")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"note\":" + note + ",\"text\":\"Task\"}"))
                .andExpect(status().isCreated())
                .andReturn())
            .get("id")
            .asLong();
    mvc.perform(
            patch("/api/todos/" + todo + "/")
                .with(csrf())
                .contentType("application/json")
                .content("{\"done\":true,\"text\":\"Renamed\"}"))
        .andExpect(jsonPath("$.done").value(true));
    mvc.perform(delete("/api/todos/" + todo + "/").with(user("apiforeign")).with(csrf()))
        .andExpect(status().isNotFound());
    var image = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image);
    long media =
        body(mvc.perform(
                    multipart("/api/images/")
                        .file(
                            new MockMultipartFile(
                                "image", "test.png", "image/png", image.toByteArray()))
                        .param("note", "" + note)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn())
            .get("id")
            .asLong();
    mvc.perform(get("/api/media/" + media))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
    mvc.perform(get("/api/media/" + media).with(user("apiforeign")))
        .andExpect(status().isNotFound());
    mvc.perform(
            multipart("/api/images/")
                .file(
                    new MockMultipartFile("image", "bad.svg", "image/svg+xml", "<svg/>".getBytes()))
                .param("note", "" + note)
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/todos/" + todo + "/").with(csrf())).andExpect(status().isNoContent());
  }

  @Test
  void profileAndPassword() throws Exception {
    mvc.perform(get("/api/profile/")).andExpect(jsonPath("$.username").value("apiowner"));
    mvc.perform(
            multipart("/api/profile/save/")
                .param("username", "renameduser")
                .param("email", "user@example.com")
                .param("bio", "About")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("renameduser"));
    mvc.perform(
            post("/api/profile/change_password/")
                .with(user("renameduser"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"old_password\":\"Secret123!\",\"new_password1\":\"NewSecret123!\",\"new_password2\":\"NewSecret123!\"}"))
        .andExpect(status().isOk());
    entities.clear();
    assertThat(
            encoder.matches(
                "NewSecret123!", users.findByLogin("renameduser").orElseThrow().getPassword()))
        .isTrue();
  }

  @Test
  void csrfAndInvalidFields() throws Exception {
    mvc.perform(post("/api/notes/").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/notes/")
                .with(csrf())
                .contentType("application/json")
                .content("{\"title\":null}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/notes/").param("status", "unknown")).andExpect(status().isBadRequest());
  }
}
