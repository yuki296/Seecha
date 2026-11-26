<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="ko" class="chat-page">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, interactive-widget=resizes-content">
    <title>채팅방 | 시차</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
    <c:url var="themeUrl" value="/assets/theme.css"/>
    <link href="<c:out value='${themeUrl}'/>" rel="stylesheet">
    <c:url var="styleUrl" value="/assets/room.css"/>
    <c:url var="scriptUrl" value="/assets/room.js"/>
    <link href="<c:out value='${styleUrl}'/>" rel="stylesheet">
    <script src="<c:out value='${scriptUrl}'/>" defer></script>
</head>
<body class="chat-page">
<c:url var="leaveUrl" value="/room/leave"/>
<c:url var="apiUrl" value="/api/room/"/>
<c:url var="matchUrl" value="/match"/>
<c:url var="loginUrl" value="/auth/login"/>
<main id="chat-room" class="container py-2" data-api="<c:out value='${apiUrl}'/>"
      data-match="<c:out value='${matchUrl}'/>" data-login="<c:out value='${loginUrl}'/>" data-token="<c:out value='${sessionScope.matchFormToken}'/>">
    <header class="d-flex justify-content-between align-items-center">
        <h1 class="h4 mb-0">채팅방</h1>
        <button type="button" id="room-menu" class="btn btn-sm btn-outline-dark" data-panel="menu" ${roomEnded ? 'hidden' : ''}>메뉴</button>
    </header>
    <div id="room-view"><jsp:include page="room-content.jsp"/></div>
    <p id="chat-connection" class="small text-body-secondary mb-0" role="status" hidden></p>
    <p id="chat-error" class="small text-danger mb-0" role="alert" hidden></p>
    <form id="composer" class="gap-2" ${roomEnded ? 'hidden' : ''}>
        <input class="form-control" name="message" maxlength="500" enterkeyhint="send" aria-label="메시지" placeholder="메시지 입력" required>
        <button class="btn btn-dark text-nowrap">전송</button>
    </form>
    <dialog id="chat-panel" aria-labelledby="chat-panel-title">
        <div class="d-flex align-items-center justify-content-between gap-3 mb-3">
            <h2 id="chat-panel-title" class="h5 mb-0"></h2>
            <form method="dialog"><button class="btn btn-sm btn-outline-dark">닫기</button></form>
        </div>
        <p id="chat-panel-error" class="small text-danger" role="alert" hidden></p>
        <section id="chat-panel-menu">
            <div class="d-grid gap-2">
                <button type="button" class="btn btn-outline-dark" data-panel="people">참여자 보기</button>
                <button type="button" class="btn btn-outline-dark" data-panel="question">보조 질문</button>
                <button type="button" class="btn btn-outline-dark" data-panel="end">조기 종료</button>
            </div>
        <form id="leave-room" method="post" action="<c:out value='${leaveUrl}'/>">
            <input type="hidden" name="token" value="<c:out value='${sessionScope.matchFormToken}'/>">
            <button class="btn btn-sm btn-outline-dark">나가기</button>
        </form>
        </section>
        <section id="chat-panel-body"></section>
    </dialog>
</main>
</body>
</html>
