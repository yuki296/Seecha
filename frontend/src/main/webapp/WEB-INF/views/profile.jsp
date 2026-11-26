<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>프로필 등록·수정 | 시차</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
    <c:url var="themeUrl" value="/assets/theme.css"/>
    <link href="<c:out value='${themeUrl}'/>" rel="stylesheet">
</head>
<body>
<c:url var="homeUrl" value="/"/>
<main class="container page-card" style="max-width: 420px">
    <a class="text-dark" href="<c:out value='${homeUrl}'/>">seecha</a>
    <h1 class="mt-4">프로필 등록·수정</h1>
    <p>프로필은 매칭에 사용돼요. 채팅에서는 익명으로 표시돼요.</p>
    <c:url var="profileUrl" value="/profile"/>
    <c:if test="${not empty error}">
        <p class="alert alert-danger" role="alert"><c:out value="${error}"/></p>
    </c:if>
    <form method="post" action="<c:out value='${profileUrl}'/>">
        <input type="hidden" name="token" value="<c:out value='${sessionScope.accountFormToken}'/>">
        <%@ include file="profile-fields.jspf" %>
        <button class="btn btn-dark w-100 mt-2">프로필 저장</button>
    </form>
</main>
</body>
</html>
