<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>마이페이지 | 시차</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
    <c:url var="themeUrl" value="/assets/theme.css"/>
    <link href="<c:out value='${themeUrl}'/>" rel="stylesheet">
</head>
<body>
<c:url var="homeUrl" value="/"/>
<main class="container page-card" style="max-width: 420px">
    <a class="text-dark" href="<c:out value='${homeUrl}'/>">seecha</a>
    <h1 class="mt-4">마이페이지</h1>
    <c:url var="matchUrl" value="/match"/>
    <c:url var="profileUrl" value="/profile"/>
    <c:url var="logoutUrl" value="/auth/logout"/>
    <p>계정 아이디 <strong><c:out value="${sessionScope.accountName}"/></strong></p>
    <c:url var="nicknameUrl" value="/account/nickname"/>
    <form class="my-4" method="post" action="<c:out value='${nicknameUrl}'/>">
        <input type="hidden" name="token" value="<c:out value='${sessionScope.accountFormToken}'/>">
        <label class="form-label" for="nickname">채팅 닉네임</label>
        <input class="form-control" id="nickname" name="nickname" required minlength="1"
               maxlength="16" value="<c:out value='${nickname}'/>">
        <c:if test="${not empty nicknameError}"><p class="text-danger" role="alert"><c:out value="${nicknameError}"/></p></c:if>
        <button class="btn btn-outline-dark mt-2">닉네임 저장</button>
    </form>
    <h2 class="h5 mt-4">내 매칭 프로필</h2>
    <dl class="row mt-3">
        <dt class="col-6">만 나이</dt>
        <dd class="col-6"><c:out value="${empty ageLabel ? '미입력' : ageLabel}"/></dd>
        <dt class="col-6">어릴 적 성장 지역</dt>
        <dd class="col-6"><c:out value="${empty profile.childhoodRegion ? '미입력' : profile.childhoodRegion}"/></dd>
        <dt class="col-6">현재 생활 단계</dt>
        <dd class="col-6"><c:out value="${empty profile.lifeStage ? '미입력' : profile.lifeStage}"/></dd>
        <dt class="col-6">MBTI</dt>
        <dd class="col-6"><c:out value="${empty profile.mbti ? '미입력' : profile.mbti}"/></dd>
        <dt class="col-6">전공·직무 분야</dt>
        <dd class="col-6"><c:out value="${empty profile.field ? '미입력' : profile.field}"/></dd>
    </dl>
    <a class="btn btn-dark w-100 mb-3" href="<c:out value='${profileUrl}'/>">프로필 수정</a>
    <a class="btn btn-dark w-100 mb-3" href="<c:out value='${matchUrl}'/>">매칭 시작</a>
    <form method="post" action="<c:out value='${logoutUrl}'/>">
        <input type="hidden" name="token" value="<c:out value='${sessionScope.accountFormToken}'/>">
        <button class="btn btn-outline-dark w-100">로그아웃</button>
    </form>
</main>
</body>
</html>
