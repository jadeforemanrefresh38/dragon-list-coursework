package ru.dragonlist.config;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class HealthProbe {
  private HealthProbe() {}

  public static void main(String[] args) throws Exception {
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    var response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/healthz"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.discarding());
    if (response.statusCode() != 200) System.exit(1);
  }
}
