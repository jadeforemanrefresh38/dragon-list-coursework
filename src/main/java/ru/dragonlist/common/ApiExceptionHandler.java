package ru.dragonlist.common;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(
    basePackages = {
      "ru.dragonlist.notes",
      "ru.dragonlist.tags",
      "ru.dragonlist.tasks",
      "ru.dragonlist.profile",
      "ru.dragonlist.media"
    })
public class ApiExceptionHandler {
  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalidInput() {
    return ResponseEntity.badRequest().body(Map.of("detail", "Некорректные данные запроса"));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> response(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("detail", e.getReason() == null ? "Запись не найдена" : e.getReason()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> duplicate() {
    return ResponseEntity.badRequest()
        .body(Map.of("detail", "Данные уже существуют или были изменены. Обновите страницу."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> tooLarge() {
    return ResponseEntity.status(413)
        .body(Map.of("detail", "Изображение должно быть не больше 5 МБ"));
  }

  @ExceptionHandler(java.io.IOException.class)
  ResponseEntity<?> invalidImage() {
    return ResponseEntity.badRequest()
        .body(Map.of("detail", "Не удалось прочитать изображение. Используйте PNG, JPEG или GIF."));
  }
}
