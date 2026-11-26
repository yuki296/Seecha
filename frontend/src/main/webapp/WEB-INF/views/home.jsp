<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${serviceName}"/> (seecha)</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css"
          rel="stylesheet">
    <c:url var="themeUrl" value="/assets/theme.css"/>
    <link href="<c:out value='${themeUrl}'/>" rel="stylesheet">
</head>
<body>
    <main class="home-shell">
        <section class="home-hero" aria-labelledby="home-title">
            <div class="home-hero-copy">
                <p class="home-brand">시차 <span>seecha</span></p>
                <h1 id="home-title">같은 상황,<br>서로 다른 시선</h1>
                <p class="home-intro">하나의 질문에서 시작하는 익명 대화</p>
                <div class="home-actions">
                    <c:url var="registerUrl" value="/auth/register"/>
                    <c:url var="loginUrl" value="/auth/login"/>
                    <c:url var="matchUrl" value="/match"/>
                    <c:url var="myPageUrl" value="/mypage"/>
                    <c:choose>
                        <c:when test="${empty sessionScope.memberId}">
                            <a class="btn btn-dark" href="<c:out value='${loginUrl}'/>">로그인</a>
                            <a class="btn btn-outline-dark" href="<c:out value='${registerUrl}'/>">회원가입</a>
                        </c:when>
                        <c:otherwise>
                            <a class="btn btn-dark" href="<c:out value='${matchUrl}'/>">매칭 시작</a>
                            <a class="btn btn-outline-dark" href="<c:out value='${myPageUrl}'/>">마이페이지</a>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
            <figure class="home-preview">
                <figcaption>이런 이야기로 시작해요</figcaption>
                <p class="preview-question">친구가 당신에게만 비밀을 털어놓았어요. 다른 친구가 그 일을 직접 묻는다면 어떻게 답하겠어요?</p>
                <p class="preview-reply">먼저 친구가 왜 비밀로 하고 싶은지 물어볼래요.</p>
                <p class="preview-reply">저는 직접 얘기할 기회를 주고 싶어요.</p>
            </figure>
        </section>
        <section class="home-flow" aria-labelledby="flow-title">
            <h2 id="flow-title">시차에서는 이렇게 만나요</h2>
            <ol>
                <li><strong>매칭하기</strong><span>서로 다른 배경의 사람을 만나요.</span></li>
                <li><strong>같은 질문 보기</strong><span>모두에게 같은 상황과 질문이 열려요.</span></li>
                <li><strong>익명으로 이야기하기</strong><span>닉네임으로 각자의 선택과 이유를 나눠요.</span></li>
            </ol>
        </section>
    </main>
</body>
</html>
