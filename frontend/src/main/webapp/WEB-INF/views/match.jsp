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
    <c:url var="letterUrl" value="/assets/yuki-letter.png"/>
    <details class="developer-story">
        <summary>
            <img src="<c:out value='${letterUrl}'/>" width="384" height="384" alt="">
            <span class="developer-story-preview">
                <strong>Yuki가 전하는 이야기</strong>
                <span class="developer-story-open">이야기 읽기 <span aria-hidden="true">›</span></span>
                <span class="developer-story-close">이야기 접기</span>
            </span>
        </summary>
        <div class="developer-story-content">
            <p>안녕하세요. 시차를 만든 Yuki입니다.</p>
            <p>여러분은 누군가의 이야기를 듣고 <strong>"어떻게 저렇게 생각할 수 있지?"</strong> 하고 느껴본 적이 있으신가요?</p>
            <p>저는 사람들과 이야기를 나누다 보면 같은 상황을 두고도 생각과 판단이 정말 다르다는 걸 느낍니다. 그런데 요즘은 그런 차이를 궁금해하기보다, 자신과 생각이나 행동이 다르다는 이유만으로 상대방을 쉽게 판단하고, 때로는 혐오하는 모습을 자주 보게 됩니다.</p>
            <p>저는 이런 모습을 보면서 <strong>우리 사회가 서로의 다름을 존중하는 태도를 점점 잃어가고 있는 것은 아닐까</strong> 생각했습니다.</p>
            <p>사람은 저마다 다른 환경에서 자라고, 다른 경험을 하며 살아갑니다. 그렇기에 같은 상황을 마주하더라도 서로 다른 생각을 하고, 다른 선택을 할 수 있다고 생각합니다.</p>
            <p>그런데 우리는 왜 상대방이 그렇게 생각하는 이유를 알아보기도 전에, 나와 다르다는 이유만으로 틀렸다고 판단하는 걸까요?</p>
            <p>그래서 고민했습니다. <strong>내가 할 수 있는 일은 무엇일까?</strong></p>
            <p>그렇게 만든 공간이 바로 '시차(seecha)'입니다.</p>
            <p>저는 여러분이 이곳에서 다양한 사람들과 이야기하며 <strong>"세상에는 이렇게 생각하는 사람도 있구나", "이런 선택을 하는 사람에게는 이런 이유가 있구나"</strong> 하고 알아갔으면 좋겠습니다.</p>
            <p>자신의 생각을 솔직하게 이야기하되, <strong>상대방을 억지로 설득하거나 자신의 생각만이 옳다고 주장하지는 않았으면 합니다.</strong></p>
            <p>서로의 의견이 끝까지 달라도 괜찮습니다. 상대방을 반드시 이해하거나 동의할 필요도 없습니다. 다만 <strong>나와 다른 생각이 존재한다는 사실만큼은 존중할 수 있었으면 좋겠습니다.</strong></p>
            <p>시차를 이용한 사람들이 적어도 누군가를 쉽게 판단하기보다, 그 사람의 생각과 행동에도 나름의 이유가 있을 수 있다는 것을 한 번쯤 떠올리게 되었으면 좋겠습니다.</p>
            <p><strong>같은 상황, 서로 다른 시선.</strong></p>
            <p>이것이 제가 시차를 만든 이유입니다.</p>
        </div>
    </details>
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
