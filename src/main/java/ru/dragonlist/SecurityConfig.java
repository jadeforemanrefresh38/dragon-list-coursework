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
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService userDetailsService(UserRepository users){return login -> users.findByLogin(login.toLowerCase(Locale.ROOT))
  .map(u -> org.springframework.security.core.userdetails.User.withUsername(u.getLogin()).password(u.getPassword()).roles("USER").build())
  .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));}
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http.authorizeHttpRequests(a -> a.requestMatchers("/login","/register","/css/**","/images/**","/error").permitAll().anyRequest().authenticated())
   .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/notes",true).permitAll())
   .logout(l -> l.logoutSuccessUrl("/login?logout")).build();
 }
}
