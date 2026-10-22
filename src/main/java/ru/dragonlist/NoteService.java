package ru.dragonlist;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class NoteService {
  private final NoteRepository notes;
  private final UserRepository users;

  public NoteService(NoteRepository notes, UserRepository users) {
    this.notes = notes;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public Note owned(Long id, String login) {
    return notes
        .findByIdAndUserLogin(id, login)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public Long create(NoteForm form, String login) {
    return notes.save(new Note(users.findByLogin(login).orElseThrow(), form)).getId();
  }

  public void update(Long id, NoteForm form, String login) {
    owned(id, login).update(form);
  }

  public void delete(Long id, String login) {
    notes.delete(owned(id, login));
  }
}
