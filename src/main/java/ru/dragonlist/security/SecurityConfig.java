package ru.dragonlist.security;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.dragonlist.auth.UserRepository;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(UserRepository users) {
    return login ->
        users
            .findByLogin(login.toLowerCase(Locale.ROOT))
            .map(
                u ->
                    org.springframework.security.core.userdetails.User.withUsername(u.getLogin())
                        .password(u.getPassword())
                        .roles("USER")
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      RequestLimiter limiter,
      JdbcTemplate db,
      @Value("${app.security.trusted-proxy:false}") boolean proxy,
      @Value("${app.security.session-check:true}") boolean sessionCheck)
      throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/login",
                        "/register",
                        "/healthz",
                        "/css/**",
                        "/js/**",
                        "/frontend/**",
                        "/images/**",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new SessionVersionFilter(db, sessionCheck), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new RequestProtectionFilter(limiter, proxy), UsernamePasswordAuthenticationFilter.class)
        .headers(
            h ->
                h.contentSecurityPolicy(
                        c ->
                            c.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'"
                                    + " 'unsafe-inline'; img-src 'self' data: blob:; font-src"
                                    + " 'self'; connect-src 'self'; object-src 'none';"
                                    + " frame-ancestors 'none'; base-uri 'self'; form-action"
                                    + " 'self'"))
                    .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=()")))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (request, response, error) -> {
                      if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType("application/json;charset=UTF-8");
                        response
                            .getWriter()
                            .write(
                                "{\"detail\":\"Сессия"
                                    + " завершилась\",\"code\":\"not_authenticated\"}");
                      } else response.sendRedirect("/login");
                    }))
        .formLogin(
            f ->
                f.loginPage("/login")
                    .successHandler(
                        (request, response, authentication) -> {
                          request
                              .getSession()
                              .setAttribute(
                                  "accountVersion",
                                  db.queryForObject(
                                      "SELECT session_version FROM users WHERE login=?",
                                      Long.class,
                                      authentication.getName()));
                          response.sendRedirect("/notes");
                        })
                    .permitAll())
        .logout(l -> l.logoutSuccessUrl("/login?logout"))
        .build();
  }
}
