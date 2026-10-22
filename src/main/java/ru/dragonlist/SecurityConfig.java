package ru.dragonlist;

import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/login",
                        "/register",
                        "/css/**",
                        "/js/**",
                        "/frontend/**",
                        "/images/**",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
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
        .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/notes", true).permitAll())
        .logout(l -> l.logoutSuccessUrl("/login?logout"))
        .build();
  }
}
