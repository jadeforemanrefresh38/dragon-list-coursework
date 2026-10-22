package ru.dragonlist.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;

/** Trusts the client-IP header only when deployed behind the private ingress network. */
public class RequestProtectionFilter extends OncePerRequestFilter {
  private final RequestLimiter limiter;
  private final boolean proxy;

  public RequestProtectionFilter(RequestLimiter limiter, boolean proxy) {
    this.limiter = limiter;
    this.proxy = proxy;
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String ip = proxy ? req.getHeader("X-Dragon-Client-IP") : req.getRemoteAddr();
    if (ip == null) ip = req.getRemoteAddr();
    String path = req.getRequestURI();
    boolean post = "POST".equals(req.getMethod());
    boolean allowed = true;
    if (post && path.equals("/login")) {
      allowed = limiter.allow("login-ip:" + ip, 30, 300000);
      String login = req.getParameter("username");
      if (login != null && login.length() <= 64)
        allowed =
            limiter.allow("login-account:" + login.toLowerCase(Locale.ROOT), 8, 300000) && allowed;
    } else if (post && path.equals("/register"))
      allowed = limiter.allow("register:" + ip, 10, 3600000);
    else if (path.startsWith("/api/")) allowed = limiter.allow("api:" + ip, 600, 60000);
    if (!allowed) {
      res.setStatus(429);
      res.setHeader("Retry-After", "300");
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"detail\":\"Слишком много запросов. Повторите позже.\"}");
      return;
    }
    chain.doFilter(req, res);
  }
}
