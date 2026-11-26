package com.seecha.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@WebFilter("/*")
public final class RequestSecurityFilter implements Filter {
    private static final Set<String> PUBLIC_GET = Set.of("", "/", "/auth/login", "/auth/register", "/assets/match.js", "/assets/room.js", "/assets/room.css", "/assets/theme.css");
    private static final Set<String> PUBLIC_POST = Set.of("/auth/login", "/auth/register");
    private static final Set<String> PRIVATE_GET = Set.of(
            "/profile", "/mypage", "/match", "/room", "/api/match/status", "/api/room/view");
    private static final Set<String> PRIVATE_POST = Set.of(
            "/profile", "/auth/logout", "/account/nickname", "/room/leave",
            "/api/match/join", "/api/match/ready", "/api/match/cancel",
            "/api/room/send", "/api/room/question", "/api/room/end");
    private static final long WINDOW_MS = 60_000;
    private final Map<String, Integer> attemptsByAddress = new HashMap<>();
    private long windowStart;

    @Override
    public void doFilter(ServletRequest incoming, ServletResponse outgoing, FilterChain chain)
            throws IOException, ServletException {
        var request = (HttpServletRequest) incoming;
        var response = (HttpServletResponse) outgoing;
        setHeaders(response);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        boolean read = "GET".equals(method) || "HEAD".equals(method);
        boolean write = "POST".equals(method);
        boolean readable = PUBLIC_GET.contains(path) || PRIVATE_GET.contains(path);
        boolean writable = PUBLIC_POST.contains(path) || PRIVATE_POST.contains(path);

        if (!readable && !writable) {
            reject(response, 404);
            return;
        }
        if (!(read && readable) && !(write && writable)) {
            response.setHeader("Allow", readable ? (writable ? "GET, HEAD, POST" : "GET, HEAD") : "POST");
            reject(response, 405);
            return;
        }
        boolean requiresAccount = read ? PRIVATE_GET.contains(path) : PRIVATE_POST.contains(path);
        if (requiresAccount) {
            var session = request.getSession(false);
            if (session == null || session.getAttribute("memberId") == null) {
                if (path.startsWith("/api/") || write) {
                    reject(response, 401);
                } else {
                    response.sendRedirect(request.getContextPath() + "/auth/login");
                }
                return;
            }
        }
        if (write) {
            String contentType = request.getContentType();
            if (contentType == null || !contentType.split(";", 2)[0].strip()
                    .equalsIgnoreCase("application/x-www-form-urlencoded")) {
                reject(response, 415);
                return;
            }
            if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
                reject(response, 403);
                return;
            }
            if (PUBLIC_POST.contains(path) && !allowAccountAttempt(request.getRemoteAddr(), path)) {
                response.setHeader("Retry-After", "60");
                reject(response, 429);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allowAccountAttempt(String address, String path) {
        long now = System.currentTimeMillis();
        if (now - windowStart >= WINDOW_MS) {
            windowStart = now;
            attemptsByAddress.clear();
        }
        String key = path + ':' + address;
        if (!attemptsByAddress.containsKey(key) && attemptsByAddress.size() >= 4096) {
            return false;
        }
        int attempts = attemptsByAddress.getOrDefault(key, 0);
        int limit = "/auth/login".equals(path) ? 20 : 12;
        if (attempts >= limit) {
            return false;
        }
        attemptsByAddress.put(key, attempts + 1);
        return true;
    }

    private static void setHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                + "img-src 'self' data:; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
    }

    private static void reject(HttpServletResponse response, int status) {
        response.setStatus(status);
    }
}
