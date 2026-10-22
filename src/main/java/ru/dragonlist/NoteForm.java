package ru.dragonlist;

import jakarta.validation.constraints.*;

public class NoteForm {
  @NotBlank(message = "Введите заголовок")
  @Size(max = 200, message = "Не более 200 символов")
  private String title = "";

  @NotBlank(message = "Введите текст заметки")
  @Size(max = 20000, message = "Не более 20000 символов")
  private String content = "";

  @NotNull(message = "Выберите метку")
  private Tag tag;

  public NoteForm() {}

  public NoteForm(Note n) {
    title = n.getTitle();
    content = n.getContent();
    tag = n.getTag();
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String v) {
    content = v;
  }

  public Tag getTag() {
    return tag;
  }

  public void setTag(Tag v) {
    tag = v;
  }
}
