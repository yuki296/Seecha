package com.seecha.web;

import com.seecha.dao.MemberDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/auth/register")
public final class RegisterServlet extends HttpServlet {
    private static final String VIEW = "/WEB-INF/views/register.jsp";
    private static final String TOKEN = "registrationToken";
    private static final String COMPLETE = "registrationComplete";
    private final MemberDao members = new MemberDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        var session = request.getSession();
        if (Boolean.TRUE.equals(session.getAttribute(COMPLETE))) {
            session.removeAttribute(COMPLETE);
            request.setAttribute("complete", true);
        }
        if (session.getAttribute(TOKEN) == null) {
            session.setAttribute(TOKEN, UUID.randomUUID().toString());
        }
        show(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        var session = request.getSession(false);
        if (session == null || session.getAttribute(TOKEN) == null
                || !session.getAttribute(TOKEN).equals(request.getParameter("token"))) {
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            response.setHeader("Location", request.getContextPath() + "/auth/register?expired=1");
            return;
        }

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String nickname = Nickname.clean(request.getParameter("nickname"));
        request.setAttribute("nickname", nickname);
        boolean adult = "yes".equals(request.getParameter("adult"));
        request.setAttribute("username", username);
        request.setAttribute("adult", adult);
        boolean privacyConsent = "yes".equals(request.getParameter("privacyConsent"));
        request.setAttribute("privacyConsent", privacyConsent);
        var profile = ProfileForm.read(request);
        ProfileForm.prepare(request, profile);

        if (username == null || !username.matches("[A-Za-z0-9_]{4,24}")) {
            reject(request, response, 400, "아이디는 영문, 숫자, 밑줄을 사용해 4~24자로 입력하세요.");
            return;
        }
        if (password == null || password.length() < 8 || password.indexOf(0) >= 0
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            reject(request, response, 400, "비밀번호는 8자 이상, UTF-8 기준 72바이트 이하로 입력하세요.");
            return;
        }
        if (!adult) {
            reject(request, response, 400, "만 19세 이상인지 확인해 주세요.");
            return;
        }

        if (!privacyConsent) {
            reject(request, response, 400, "개인정보 수집·이용 안내를 확인하고 동의해 주세요.");
            return;
        }

        if (!Nickname.valid(nickname) || nickname.equalsIgnoreCase(username)) {
            reject(request, response, 400, Nickname.HELP + " 아이디와 다른 이름을 사용해 주세요.");
            return;
        }
        if (!ProfileForm.valid(profile)) {
            reject(request, response, 400, "만 나이, 어릴 적 성장 지역, 현재 생활 단계를 선택해 주세요.");
            return;
        }

        try {
            var result = members.create(username, BCrypt.hashpw(password, BCrypt.gensalt(12)), nickname,
                    ProfileForm.forStorage(profile));
            if (result != MemberDao.RegistrationResult.CREATED) {
                String message = result == MemberDao.RegistrationResult.NICKNAME_TAKEN
                        ? Nickname.TAKEN : "이미 사용 중인 아이디예요.";
                reject(request, response, 409, message);
                return;
            }
        } catch (SQLException exception) {
            log("Could not save registration (SQL state " + exception.getSQLState() + ")");
            reject(request, response, 503, "지금은 가입 정보를 저장할 수 없어요. 잠시 후 다시 시도해 주세요.");
            return;
        }

        session.removeAttribute(TOKEN);
        session.setAttribute(COMPLETE, true);
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", response.encodeRedirectURL(request.getContextPath() + "/auth/register"));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws ServletException, IOException {
        response.setStatus(status);
        request.setAttribute("error", message);
        show(request, response);
    }

    private void show(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getAttribute("ages") == null) {
            ProfileForm.prepare(request, null);
        }
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher(VIEW).forward(request, response);
    }
}
