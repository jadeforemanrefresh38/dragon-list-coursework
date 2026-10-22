package ru.dragonlist.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionVersionFilter extends OncePerRequestFilter {
  private final JdbcTemplate db;
  private final boolean enabled;

  public SessionVersionFilter(JdbcTemplate db, boolean enabled) {
    this.db = db;
    this.enabled = enabled;
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (enabled
        && auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)) {
      var versions =
          db.queryForList(
              "SELECT session_version FROM users WHERE login=?", Long.class, auth.getName());
      var session = req.getSession(false);
      Object saved = session == null ? null : session.getAttribute("accountVersion");
      if (versions.isEmpty() || saved == null || !versions.get(0).equals(saved)) {
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"detail\":\"Войдите снова\",\"code\":\"not_authenticated\"}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
