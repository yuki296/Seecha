<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>로그인 | 시차</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
    <c:url var="themeUrl" value="/assets/theme.css"/>
    <link href="<c:out value='${themeUrl}'/>" rel="stylesheet">
</head>
<body>
<c:url var="homeUrl" value="/"/>
<main class="container page-card" style="max-width: 420px">
    <a class="text-dark" href="<c:out value='${homeUrl}'/>">seecha</a>
    <h1 class="mt-4">로그인</h1>
    <p>로그인하고 익명 그룹 대화를 시작하세요.</p>
    <c:url var="loginUrl" value="/auth/login"/>
    <c:url var="registerUrl" value="/auth/register"/>
    <c:if test="${param.expired eq '1'}">
        <p class="alert alert-warning" role="alert">이전 화면이 만료됐어요. 이 화면에서 다시 입력해 주세요.</p>
    </c:if>
    <c:if test="${not empty error}">
        <p class="alert alert-danger" role="alert"><c:out value="${error}"/></p>
    </c:if>
    <form method="post" action="<c:out value='${loginUrl}'/>">
        <input type="hidden" name="token" value="<c:out value='${sessionScope.accountFormToken}'/>">
        <div class="mb-3">
            <label class="form-label" for="username">아이디</label>
            <input class="form-control" id="username" name="username" required maxlength="24"
                   autocomplete="username" value="<c:out value='${username}'/>">
        </div>
        <div class="mb-4">
            <label class="form-label" for="password">비밀번호</label>
            <input class="form-control" id="password" name="password" type="password" required
                   autocomplete="current-password">
        </div>
        <button class="btn btn-dark w-100">로그인</button>
    </form>
    <p class="mt-4">처음 이용하시나요? <a href="<c:out value='${registerUrl}'/>">회원가입</a></p>
</main>
</body>
</html>
