package ru.dragonlist.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Bounded fixed-window counters for one application instance. */
@Component
public class RequestLimiter {
  private record Counter(long start, int count) {}

  private final Map<String, Counter> counters = new LinkedHashMap<>(256, .75f, true);

  public synchronized boolean allow(String key, int limit, long windowMillis) {
    long now = System.currentTimeMillis();
    Counter old = counters.get(key);
    if (old == null || now - old.start >= windowMillis) {
      counters.put(key, new Counter(now, 1));
      trim();
      return true;
    }
    if (old.count >= limit) return false;
    counters.put(key, new Counter(old.start, old.count + 1));
    return true;
  }

  private void trim() {
    while (counters.size() > 20000) counters.remove(counters.keySet().iterator().next());
  }
}
