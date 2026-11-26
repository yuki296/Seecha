package com.seecha.web;

import com.google.gson.Gson;
import com.seecha.dao.ProfileDao;
import com.seecha.dao.MemberDao;
import com.seecha.match.MatchQueue;
import com.seecha.match.MatchQueue.Snapshot;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

@WebServlet({"/match", "/api/match/status", "/api/match/join", "/api/match/ready", "/api/match/cancel"})
public final class MatchServlet extends HttpServlet {
    private static final Gson JSON = new Gson();
    private static final String TOKEN = "matchFormToken";
    private final MemberDao members = new MemberDao();
    private final ProfileDao profiles = new ProfileDao();
    private final MatchQueue queue = MatchQueue.INSTANCE;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        var session = request.getSession(false);
        Long memberId = session == null ? null : (Long) session.getAttribute("memberId");
        String path = request.getServletPath();
        if ("/match".equals(path)) {
            if (memberId == null) {
                response.sendRedirect(request.getContextPath() + "/auth/login");
                return;
            }
            if (session.getAttribute(TOKEN) == null) {
                session.setAttribute(TOKEN, UUID.randomUUID().toString());
            }
            response.setHeader("Cache-Control", "no-store");
            request.getRequestDispatcher("/WEB-INF/views/match.jsp").forward(request, response);
        } else if (!"/api/match/status".equals(path)) {
            response.setHeader("Allow", "POST");
            response.sendError(405);
        } else if (memberId == null) {
            response.sendError(401);
        } else {
            json(response, queue.status(memberId, System.currentTimeMillis()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var session = request.getSession(false);
        Long memberId = session == null ? null : (Long) session.getAttribute("memberId");
        String path = request.getServletPath();
        if (memberId == null) {
            response.sendError(401);
            return;
        }
        Object token = session.getAttribute(TOKEN);
        if (token == null || !token.equals(request.getParameter("token"))) {
            response.sendError(403);
            return;
        }
        long now = System.currentTimeMillis();
        switch (path) {
            case "/api/match/join" -> {
                try {
                    var profile = profiles.find(memberId);
                    if (profile == null || profile.childhoodRegion() == null) {
                        response.sendError(422);
                        return;
                    }
                    String nickname = members.nickname(memberId);
                    if (nickname == null) {
                        response.sendError(409);
                        return;
                    }
                    json(response, queue.join(memberId, profile, nickname, System.currentTimeMillis()));
                } catch (SQLException exception) {
                    log("Match profile lookup failed", exception);
                    response.sendError(503);
                }
            }
            case "/api/match/ready" -> {
                String value = request.getParameter("ready");
                if (!"true".equals(value) && !"false".equals(value)) {
                    response.sendError(400);
                    return;
                }
                try {
                    long revision = Long.parseLong(request.getParameter("revision"));
                    json(response, queue.ready(memberId, Boolean.parseBoolean(value), revision, now));
                } catch (NumberFormatException exception) {
                    response.sendError(400);
                }
            }
            case "/api/match/cancel" -> json(response, queue.cancel(memberId, now));
            default -> {
                response.setHeader("Allow", "GET");
                response.sendError(405);
            }
        }
    }

    private static void json(HttpServletResponse response, Snapshot state) throws IOException {
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        JSON.toJson(state, response.getWriter());
    }
}
