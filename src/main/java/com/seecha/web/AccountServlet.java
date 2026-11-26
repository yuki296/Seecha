package com.seecha.web;

import com.seecha.dao.MemberDao;
import com.seecha.dao.ProfileDao;
import com.seecha.dao.ProfileDao.Profile;
import com.seecha.match.MatchQueue;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet({"/auth/login", "/auth/logout", "/profile", "/mypage", "/account/nickname"})
public final class AccountServlet extends HttpServlet {
    private static final String TOKEN = "accountFormToken";
    private final MemberDao members = new MemberDao();
    private final ProfileDao profiles = new ProfileDao();
    private final String dummyHash = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt(12));

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        var session = request.getSession();
        Long memberId = (Long) session.getAttribute("memberId");
        String path = request.getServletPath();
        if ("/auth/login".equals(path)) {
            if (memberId == null) {
                view(request, response, "login");
            } else {
                redirect(request, response, "/mypage");
            }
            return;
        }
        if ("/auth/logout".equals(path) || "/account/nickname".equals(path)) {
            response.setHeader("Allow", "POST");
            response.sendError(405);
            return;
        }
        if (memberId == null) {
            redirect(request, response, "/auth/login");
            return;
        }
        try {
            Profile profile = profiles.find(memberId);
            if ("/mypage".equals(path) && profile != null) {
                request.setAttribute("nickname", members.nickname(memberId));
                request.setAttribute("profile", ProfileForm.values(profile));
                request.setAttribute("ageLabel", ProfileForm.AGE_LABELS.getOrDefault(profile.ageGroup(), ""));
                view(request, response, "mypage");
            } else if ("/mypage".equals(path)) {
                redirect(request, response, "/profile");
            } else {
                profileView(request, response, profile);
            }
        } catch (SQLException exception) {
            unavailable(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(TOKEN) == null
                || !session.getAttribute(TOKEN).equals(request.getParameter("token"))) {
            if ("/auth/login".equals(request.getServletPath())) {
                redirect(request, response, "/auth/login?expired=1");
            } else {
                response.sendError(403, "페이지를 다시 열고 시도해 주세요.");
            }
            return;
        }

        String path = request.getServletPath();
        try {
            if ("/auth/login".equals(path)) {
                login(request, response, session);
                return;
            }
            Long memberId = (Long) session.getAttribute("memberId");
            if (memberId == null) {
                redirect(request, response, "/auth/login");
                return;
            }
            switch (path) {
                case "/auth/logout" -> {
                    MatchQueue.INSTANCE.cancel(memberId, System.currentTimeMillis());
                    session.invalidate();
                    redirect(request, response, "/");
                }
                case "/profile" -> saveProfile(request, response, memberId);
                case "/account/nickname" -> saveNickname(request, response, session, memberId);
                default -> {
                    response.setHeader("Allow", "GET");
                    response.sendError(405);
                }
            }
        } catch (SQLException exception) {
            unavailable(response, exception);
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws IOException, ServletException, SQLException {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        if (session.getAttribute("memberId") != null) {
            redirect(request, response, "/mypage");
            return;
        }
        request.setAttribute("username", username);
        if (username == null || !username.matches("[A-Za-z0-9_]{4,24}") || password == null
                || password.length() < 8 || password.indexOf(0) >= 0
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            invalidLogin(request, response);
            return;
        }
        var account = members.findByUsername(username);
        boolean correctPassword = BCrypt.checkpw(password, account == null ? dummyHash : account.passwordHash());
        if (account == null || !correctPassword) {
            invalidLogin(request, response);
            return;
        }
        String destination = profiles.find(account.id()) == null ? "/profile" : "/mypage";
        request.changeSessionId();
        session.setAttribute(TOKEN, UUID.randomUUID().toString());
        session.setAttribute("memberId", account.id());
        session.setAttribute("accountName", account.username());
        redirect(request, response, destination);
    }

    private void invalidLogin(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setStatus(401);
        request.setAttribute("error", "아이디와 비밀번호를 확인해 주세요.");
        view(request, response, "login");
    }

    private void saveNickname(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            long memberId) throws SQLException, IOException, ServletException {
        String nickname = Nickname.clean(request.getParameter("nickname"));
        boolean valid = Nickname.valid(nickname)
                && !nickname.equalsIgnoreCase((String) session.getAttribute("accountName"));
        if (valid && members.updateNickname(memberId, nickname)) {
            redirect(request, response, "/mypage");
            return;
        }
        var profile = profiles.find(memberId);
        if (profile == null) {
            redirect(request, response, "/profile");
            return;
        }
        response.setStatus(valid ? 409 : 400);
        request.setAttribute("nickname", nickname);
        request.setAttribute("nicknameError",
                valid ? Nickname.TAKEN : Nickname.HELP + " 아이디와 다른 이름을 사용해 주세요.");
        request.setAttribute("profile", ProfileForm.values(profile));
        request.setAttribute("ageLabel", ProfileForm.AGE_LABELS.getOrDefault(profile.ageGroup(), ""));
        view(request, response, "mypage");
    }

    private void saveProfile(HttpServletRequest request, HttpServletResponse response, long memberId)
            throws SQLException, IOException, ServletException {
        Profile profile = ProfileForm.read(request);
        if (!ProfileForm.valid(profile)) {
            response.setStatus(400);
            request.setAttribute("error", "만 나이, 어릴 적 성장 지역, 현재 생활 단계를 선택하고 입력값을 확인해 주세요.");
            profileView(request, response, profile);
            return;
        }
        profiles.save(memberId, ProfileForm.forStorage(profile));
        redirect(request, response, "/mypage");
    }

    private void profileView(HttpServletRequest request, HttpServletResponse response, Profile profile)
            throws IOException, ServletException {
        ProfileForm.prepare(request, profile);
        view(request, response, "profile");
    }

    private void view(HttpServletRequest request, HttpServletResponse response, String view)
            throws IOException, ServletException {
        var session = request.getSession();
        if (session.getAttribute(TOKEN) == null) {
            session.setAttribute(TOKEN, UUID.randomUUID().toString());
        }
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/views/" + view + ".jsp").forward(request, response);
    }

    private static void redirect(HttpServletRequest request, HttpServletResponse response, String path) {
        response.setStatus(303);
        response.setHeader("Location", response.encodeRedirectURL(request.getContextPath() + path));
    }

    private void unavailable(HttpServletResponse response, SQLException exception) throws IOException {
        log("Account database error (SQL state " + exception.getSQLState() + ", code " + exception.getErrorCode() + ")");
        response.sendError(503, "계정 정보를 불러올 수 없어요. 잠시 후 다시 시도해 주세요.");
    }
}
