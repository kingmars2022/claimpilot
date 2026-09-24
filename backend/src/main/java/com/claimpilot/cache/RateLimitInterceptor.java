package com.claimpilot.cache;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Limits the requests that cost a model call or processing (questions, the assistant, claim guides
 * and forms, uploads) per user per minute, so one account cannot monopolise the model. Other
 * requests are not counted. Over the limit: 429 with Retry-After.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Set<String> POSTS = Set.of("/api/chat", "/api/assistant", "/api/claims", "/api/policies",
            "/api/receipts", "/api/forms");
    private static final Set<String> GETS = Set.of("/api/claims/guide");

    private final RateLimiter limiter;
    private final int perMinute;

    public RateLimitInterceptor(RateLimiter limiter, int perMinute) {
        this.limiter = limiter;
        this.perMinute = perMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!counted(request)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return true;
        }
        Duration wait = limiter.acquire("user:" + auth.getName(), perMinute, WINDOW);
        if (wait.isZero()) {
            return true;
        }
        long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(seconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"Too many requests\",\"status\":429,\"detail\":\"You have made "
                + perMinute + " requests that use the AI model in the last minute. Try again in " + seconds
                + " seconds.\"}");
        return false;
    }

    static boolean counted(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ("POST".equals(request.getMethod()) && POSTS.contains(path))
                || ("GET".equals(request.getMethod()) && GETS.contains(path));
    }
}
