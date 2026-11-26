<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>회원가입 | 시차</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
    <c:url var="themeUrl" value="/assets/theme.css"/>
    <link href="<c:out value='${themeUrl}'/>" rel="stylesheet">
</head>
<body>
<c:url var="homeUrl" value="/"/>
<c:url var="registerUrl" value="/auth/register"/>
<c:url var="loginUrl" value="/auth/login"/>
<main class="container page-card" style="max-width: 420px">
    <a class="text-dark" href="<c:out value='${homeUrl}'/>">seecha</a>
    <c:choose>
        <c:when test="${complete}">
            <h1 class="mt-4">가입이 완료됐어요</h1>
            <a class="btn btn-dark mt-3" href="<c:out value='${loginUrl}'/>">로그인하기</a>
        </c:when>
        <c:otherwise>
            <h1 class="mt-4">회원가입</h1>
            <p>만 19세 이상만 시차를 이용할 수 있어요.</p>
            <c:if test="${param.expired eq '1'}">
                <p class="alert alert-warning" role="alert">이전 화면이 만료됐어요. 이 화면에서 다시 입력해 주세요.</p>
            </c:if>
            <c:if test="${not empty error}">
                <p class="alert alert-danger" role="alert"><c:out value="${error}"/></p>
            </c:if>
            <form method="post" action="<c:out value='${registerUrl}'/>">
                <input type="hidden" name="token" value="<c:out value='${sessionScope.registrationToken}'/>">
                <div class="mb-3">
                    <label class="form-label" for="username">아이디</label>
                    <input class="form-control" id="username" name="username" required minlength="4"
                           maxlength="24" autocomplete="username" value="<c:out value='${username}'/>">
                    <div class="form-text">영문, 숫자, 밑줄 4~24자</div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="password">비밀번호</label>
                    <input class="form-control" id="password" name="password" type="password"
                           required minlength="8" autocomplete="new-password">
                </div>
                <div class="mb-3">
                    <label class="form-label" for="nickname">채팅 닉네임 · 필수</label>
                    <input class="form-control" id="nickname" name="nickname" required minlength="1"
                           maxlength="16" value="<c:out value='${nickname}'/>">
                    <div class="form-text">채팅에 표시될 이름이에요. 아이디와 다른 이름을 사용해 주세요.</div>
                </div>
                <div class="form-check mb-4">
                    <input class="form-check-input" id="adult" name="adult" type="checkbox" value="yes"
                           <c:if test="${adult}">checked</c:if> required>
                    <label class="form-check-label" for="adult">만 19세 이상입니다.</label>
                </div>
                <fieldset class="mt-4">
                    <legend class="h5">매칭 프로필</legend>
                    <p>필수 항목을 모두 입력하면 가입할 수 있어요.</p>
                    <%@ include file="profile-fields.jspf" %>
                </fieldset>
                <button class="btn btn-dark w-100" type="submit">가입하기</button>
            </form>
        </c:otherwise>
    </c:choose>
</main>
</body>
</html>
