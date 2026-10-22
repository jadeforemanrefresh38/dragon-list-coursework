package ru.dragonlist.common;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class InputValidation {
  private InputValidation() {}

  public static void require(boolean valid, String message) {
    if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  public static String text(
      Map<String, Object> data, String key, String fallback, int max, boolean blank) {
    Object value = data.getOrDefault(key, fallback);
    require(value instanceof String, "Некорректное поле " + key);
    String result = (String) value;
    require(
        result.length() <= max && (blank || !result.isBlank()),
        "Проверьте поле " + key + " (до " + max + " символов)");
    return result;
  }

  public static long number(Object value) {
    try {
      long n = Long.parseLong(String.valueOf(value));
      require(n > 0, "Некорректный идентификатор");
      return n;
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный идентификатор");
    }
  }

  public static int position(Object value) {
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная позиция");
    }
  }

  public static boolean bool(Object v) {
    require(v instanceof Boolean, "Ожидалось логическое значение");
    return (Boolean) v;
  }
}
