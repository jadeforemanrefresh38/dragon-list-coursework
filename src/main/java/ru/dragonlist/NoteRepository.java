package ru.dragonlist;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NoteRepository extends JpaRepository<Note,Long> {
 List<Note> findByUserLoginOrderByCreatedAtDescIdDesc(String login);
 List<Note> findByUserLoginAndTagOrderByCreatedAtDescIdDesc(String login,Tag tag);
 Optional<Note> findByIdAndUserLogin(Long id,String login);
}
