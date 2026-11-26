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
            <p class="home-brand">시차 <span>seecha</span></p>
            <h1 id="home-title">같은 상황,<br>서로 다른 시선</h1>
            <p class="home-intro">하나의 질문에서 시작하는 성인 익명 대화</p>
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
        </section>
        <section class="home-story" aria-labelledby="story-title">
            <h2 id="story-title">시차를 만든 이유</h2>
            <p>같은 일을 겪고도 누군가는 먼저 마음을 살피고, 누군가는 약속을 떠올려요. 그 차이가 어디서 오는지 궁금했어요.</p>
            <p>그래서 살아온 배경이 다른 사람들이 한 질문 앞에 모여, 각자의 선택과 그 이유를 편하게 이야기할 수 있는 자리를 만들었어요.</p>
            <p>대화가 끝나고 “아, 저렇게 생각할 수도 있구나” 하는 마음 하나가 남았으면 해요.</p>
        </section>
    </main>
</body>
</html>
