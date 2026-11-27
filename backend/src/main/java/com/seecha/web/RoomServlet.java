package com.seecha.web;

import com.seecha.match.MatchQueue;
import com.seecha.match.MatchQueue.SendResult;
import com.seecha.chat.ChatProviders;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@WebServlet({"/room", "/api/room/view", "/room/leave", "/api/room/send", "/api/room/question", "/api/room/end"})
public final class RoomServlet extends HttpServlet {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"));
    private final MatchQueue queue = MatchQueue.INSTANCE;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        var session = request.getSession(false);
        long member = (Long) session.getAttribute("memberId");
        var room = queue.room(member, System.currentTimeMillis());
        boolean fragment = request.getServletPath().startsWith("/api/");
        if (room == null) {
            if (fragment) {
                response.setStatus(404);
            } else {
                response.sendRedirect(request.getContextPath() + "/match");
            }
            return;
        }
        if (!fragment && session.getAttribute("matchFormToken") == null) {
            session.setAttribute("matchFormToken", UUID.randomUUID().toString());
        }
        request.setAttribute("roomCapacity", MatchQueue.CAPACITY);
        request.setAttribute("roomEnded", room.ended());
        request.setAttribute("endNotice", room.endNotice());
        if (room != null && !room.ended()) {
            String[] topic = room.scenario().split("\\R\\s*Q\\.\\s*", 2);
            if (topic.length == 1 && topic[0].stripTrailing().endsWith("?")) {
                topic = room.scenario().split("(?<=[.!。])\\s+(?=[^.!?]+\\?\\s*$)", 2);
            }
            request.setAttribute("room", Map.ofEntries(
                    Map.entry("scenario", topic[0].strip()),
                    Map.entry("mainQuestion", topic.length == 2 ? topic[1].strip() : "당신이라면 어떻게 행동하겠어요?"), Map.entry("questions", room.questions().stream()
                            .map(question -> Map.of("number", question.number(), "text", question.text(), "scenario", question.scenario(),
                                    "current", question.current())).toList()),
                    Map.entry("step", room.step()), Map.entry("revision", room.revision()),
                    Map.entry("roomKey", room.roomKey()),
                    Map.entry("participants", room.participants().stream().map(participant -> {
                        var profile = participant.profile();
                        return Map.of("nickname", participant.nickname(), "ageGroup", java.util.Objects.toString(profile.ageGroup(), "미입력"),
                                "childhoodRegion", java.util.Objects.toString(profile.childhoodRegion(), "미입력"), "lifeStage", java.util.Objects.toString(profile.lifeStage(), "미입력"),
                                "mbti", java.util.Objects.toString(profile.mbti(), "미입력"), "field", java.util.Objects.toString(profile.field(), "미입력"));
                    }).toList()), Map.entry("questionVotes", room.questionVotes()),
                    Map.entry("questionVoted", room.questionVoted()), Map.entry("endVotes", room.endVotes()),
                    Map.entry("endVoted", room.endVoted())));
            request.setAttribute("showEndingSoon", room.remainingSeconds() <= 300);
            request.setAttribute("remainingLabel", String.format(Locale.ROOT, "%02d:%02d",
                    room.remainingSeconds() / 60, room.remainingSeconds() % 60));
            request.setAttribute("chatMessages", room.messages().stream().map(message -> Map.of(
                    "nickname", message.nickname() == null ? "" : message.nickname(),
                    "mine", room.nickname().equals(message.nickname()), "text", message.text(),
                    "time", CLOCK.format(Instant.ofEpochMilli(message.sentAt())))).toList());
        }
        request.getRequestDispatcher(fragment ? "/WEB-INF/views/room-content.jsp" : "/WEB-INF/views/room.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        var session = request.getSession(false);
        Object token = session.getAttribute("matchFormToken");
        if (token == null || !token.equals(request.getParameter("token"))) {
            response.setStatus(403);
            return;
        }
        long member = (Long) session.getAttribute("memberId");
        long now = System.currentTimeMillis();
        String path = request.getServletPath();
        if ("/room/leave".equals(path)) {
            queue.cancel(member, now);
            response.setStatus(303);
            response.setHeader("Location", request.getContextPath() + "/match");
            return;
        }
        var room = queue.room(member, now);
        if (room == null || room.ended()) {
            response.setStatus(409);
            return;
        }
        if ("/api/room/send".equals(path)) {
            String message = request.getParameter("message");
            if (message == null || message.isBlank() || message.length() > 500) {
                response.setStatus(400);
                return;
            }
            UUID requestId;
            long roomKey;
            try {
                requestId = UUID.fromString(request.getParameter("requestId"));
                roomKey = Long.parseLong(request.getParameter("roomKey"));
            } catch (IllegalArgumentException | NullPointerException exception) {
                response.setStatus(400);
                return;
            }
            SendResult result = queue.checkSend(member, roomKey, requestId, now);
            if (result == SendResult.NEW) {
                var decision = ChatProviders.moderate(message.strip());
                if (decision != ChatProviders.Decision.ALLOWED) {
                    response.setStatus(decision == ChatProviders.Decision.BLOCKED ? 422 : 503);
                    return;
                }
                result = queue.send(member, message.strip(), roomKey, requestId, System.currentTimeMillis());
            }
            response.setStatus(switch (result) {
                case SENT -> 204;
                case ROOM_CHANGED -> 409;
                case LIMITED -> 429;
                default -> 500;
            });
            return;
        } else {
            String agree = request.getParameter("agree");
            long revision;
            try {
                revision = Long.parseLong(request.getParameter("revision"));
            } catch (NumberFormatException exception) {
                response.setStatus(400);
                return;
            }
            if (!"true".equals(agree) && !"false".equals(agree)) {
                response.setStatus(400);
                return;
            }
            queue.vote(member, "/api/room/end".equals(path), Boolean.parseBoolean(agree), revision, now);
        }
        response.setStatus(204);
    }
}
