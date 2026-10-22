package ru.dragonlist;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notes")
public class Note {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Tag tag;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Note() {}

  public Note(User user, NoteForm form) {
    this.user = user;
    this.createdAt = Instant.now();
    update(form);
  }

  public void update(NoteForm form) {
    title = form.getTitle().strip();
    content = form.getContent();
    tag = form.getTag();
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getContent() {
    return content;
  }

  public Tag getTag() {
    return tag;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
