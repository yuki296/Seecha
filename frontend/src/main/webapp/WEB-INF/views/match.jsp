<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>매칭 대기실 | 시차</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
    <c:url var="themeUrl" value="/assets/theme.css"/>
    <link href="<c:out value='${themeUrl}'/>" rel="stylesheet">
    <c:url var="scriptUrl" value="/assets/match.js"/>
    <script src="<c:out value='${scriptUrl}'/>" defer></script>
</head>
<body>
<c:url var="homeUrl" value="/"/>
<c:url var="matchApi" value="/api/match/"/>
<c:url var="profileUrl" value="/profile"/>
<c:url var="roomUrl" value="/room"/>
<c:url var="myPageUrl" value="/mypage"/>
<c:url var="loginUrl" value="/auth/login"/>
<main id="matching" class="container page-card" style="max-width: 420px"
      data-capacity="<c:out value='${roomCapacity}'/>"
      data-api="<c:out value='${matchApi}'/>" data-profile="<c:out value='${profileUrl}'/>"
      data-room="<c:out value='${roomUrl}'/>" data-mypage="<c:out value='${myPageUrl}'/>"
      data-login="<c:out value='${loginUrl}'/>" data-token="<c:out value='${sessionScope.matchFormToken}'/>">
    <a class="text-dark" href="<c:out value='${homeUrl}'/>">seecha</a>
    <h1 class="mt-4">매칭 대기실</h1>
    <p>서로 다른 배경의 참여자를 우선 연결해요.</p>
    <p id="error" class="alert alert-danger" role="alert" hidden></p>
    <div id="match-status" class="border rounded p-3 my-4" aria-live="polite" hidden>
        <p id="participants" class="fw-bold mb-2"></p>
        <p id="timer"></p>
        <p id="readiness" class="mb-0"></p>
        <ul id="participant-statuses" class="list-group list-group-flush mt-2" hidden></ul>
    </div>
    <p id="instruction" class="text-body-secondary" hidden></p>
    <button id="join" class="btn btn-dark w-100" hidden>매칭 신청</button>
    <button id="ready" class="btn btn-dark w-100" hidden>준비 완료</button>
    <button id="cancel" class="btn btn-outline-dark w-100 mt-2" hidden>매칭 취소</button>
    <c:url var="charactersUrl" value="/assets/seecha-characters.png"/>
    <aside class="lobby-story" aria-labelledby="lobby-note-title">
        <h2 id="lobby-note-title">같은 상황,<br>다른 이유</h2>
        <img class="lobby-characters" src="<c:out value='${charactersUrl}'/>"
             width="960" height="320" alt="">
        <p>우리는 각자 살아온 시간만큼 다른 눈으로 세상을 봐요.</p>
        <p>같은 질문 앞에서, 내가 미처 보지 못한 누군가의 이유를 만나보세요.</p>
        <p class="lobby-story-close">생각이 달라도, 서로를 조금 더 이해할 수 있어요.</p>
    </aside>
    <p class="lobby-guideline">
        <span aria-hidden="true">⚠︎</span>
        <span>욕설이나 비하 표현은 삼가고, 서로의 생각을 존중해 주세요.</span>
    </p>
</main>
</body>
</html>
