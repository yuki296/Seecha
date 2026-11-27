<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:url var="matchUrl" value="/match"/>
<c:url var="homeUrl" value="/"/>
<c:choose>
    <c:when test="${roomEnded}">
        <section id="room-state" data-ended="true" class="py-4">
            <h2>대화가 종료됐어요</h2>
            <c:if test="${not empty endNotice}"><p><c:out value="${endNotice}"/></p></c:if>
            <p>채팅 기록을 삭제했어요.</p>
            <a class="btn btn-dark" href="<c:out value='${matchUrl}'/>">다시 매칭하기</a>
            <a class="btn btn-outline-dark" href="<c:out value='${homeUrl}'/>">홈으로</a>
        </section>
    </c:when>
    <c:otherwise>
        <section id="room-state" data-ended="false" data-votes="${room.questionVotes gt 0 or room.endVotes gt 0}" data-step="${room.step}" data-room-key="<c:out value='${room.roomKey}'/>" data-revision="<c:out value='${room.revision}'/>">
            <div class="d-flex justify-content-between align-items-center mb-2">
                <strong><c:out value="${room.step}"/>/4</strong>
                <button type="button" class="btn btn-link p-0 text-reset" data-panel="people">참여자 <c:out value="${room.participants.size()}"/> / <c:out value="${roomCapacity}"/>명</button>
                <span>남은 시간 <c:out value="${remainingLabel}"/></span>
            </div>
            <c:if test="${showEndingSoon}"><p class="small mb-2">대화 종료까지 5분 이하 남았어요.</p></c:if>
            <button type="button" class="current-question" data-panel="topic" aria-label="메인 질문과 현재 질문 보기">
                <span class="question-notice-preview"><c:out value="${room.mainQuestion}"/></span>
                <span aria-hidden="true">›</span>
            </button>
            <template data-content="topic">
                <div class="topic-card">
                    <section>
                        <h3>상황</h3>
                        <p><c:out value="${room.scenario}"/></p>
                    </section>
                    <section>
                        <h3>메인 질문</h3>
                        <p><strong><c:out value="${room.mainQuestion}"/></strong></p>
                    </section>
                    <c:if test="${not empty room.questions}">
                        <section>
                            <h3>공개된 보조 질문</h3>
                            <ol>
                                <c:forEach var="question" items="${room.questions}">
                                    <li value="<c:out value='${question.number}'/>">
                                        <c:if test="${not empty question.scenario}">
                                            <p class="mb-1"><c:out value="${question.scenario}"/></p>
                                        </c:if>
                                        <strong><c:out value="${question.text}"/></strong>
                                    </li>
                                </c:forEach>
                            </ol>
                        </section>
                    </c:if>
                </div>
                <c:if test="${room.step lt 4}">
                    <p class="small text-body-secondary mt-2 mb-0">다음 보조 질문은 참여자 전원이 동의하면 공개돼요.</p>
                </c:if>
            </template>
            <template data-content="people">
                <ul class="list-group list-group-flush">
                    <c:forEach var="person" items="${room.participants}">
                        <li class="list-group-item">
                            <details>
                                <summary><c:out value="${person.nickname}"/></summary>
                                <dl class="participant-profile">
                                    <dt>만 나이대</dt><dd><c:out value="${person.ageGroup}"/></dd>
                                    <dt>어릴 적 성장 지역</dt><dd><c:out value="${person.childhoodRegion}"/></dd>
                                    <dt>생활 단계</dt><dd><c:out value="${person.lifeStage}"/></dd>
                                    <dt>MBTI</dt><dd><c:out value="${person.mbti}"/></dd>
                                    <dt>전공</dt><dd><c:out value="${person.field}"/></dd>
                                </dl>
                            </details>
                        </li>
                    </c:forEach>
                </ul>
            </template>
            <template data-content="question">
                <c:choose>
                    <c:when test="${room.step lt 4}">
                        <p>전원이 동의하면 보조 질문 <c:out value="${room.step}"/>이 공개돼요.</p>
                        <p class="small">동의 <c:out value="${room.questionVotes}"/> / <c:out value="${room.participants.size()}"/>명</p>
                        <button type="button" class="btn btn-dark" data-action="question"
                                data-agree="true" ${room.questionVoted or room.participants.size() lt 2 ? 'disabled' : ''}><c:out value="${room.questionVotes eq 0 ? '공개 요청' : '공개에 동의'}"/></button>
                        <button type="button" class="btn btn-outline-dark" data-action="question"
                                data-agree="false" ${room.questionVotes eq 0 ? 'disabled' : ''}>현재 질문 유지</button>
                    </c:when>
                    <c:otherwise><p>보조 질문 3개가 모두 공개됐어요.</p></c:otherwise>
                </c:choose>
            </template>
            <template data-content="end">
                <p>전원이 동의하면 대화가 종료되고 채팅 기록을 삭제해요.</p>
                <p class="small">동의 <c:out value="${room.endVotes}"/> / <c:out value="${room.participants.size()}"/>명</p>
                <button type="button" class="btn btn-dark" data-action="end"
                        data-agree="true" ${room.endVoted or room.participants.size() lt 2 ? 'disabled' : ''}><c:out value="${room.endVotes eq 0 ? '조기 종료 요청' : '종료에 동의'}"/></button>
                <button type="button" class="btn btn-outline-dark" data-action="end"
                        data-agree="false" ${room.endVotes eq 0 ? 'disabled' : ''}>대화 계속하기</button>
            </template>
            <div id="messages" tabindex="0" aria-label="대화 메시지">
                <c:if test="${empty chatMessages}"><p class="text-body-secondary">메시지를 보내 대화를 시작해 보세요.</p></c:if>
                <c:forEach var="message" items="${chatMessages}">
                    <c:choose>
                        <c:when test="${empty message.nickname}">
                            <p class="text-center small text-body-secondary my-3">
                                <c:out value="${message.text}"/>
                                <time class="chat-time ms-1"><c:out value="${message.time}"/></time>
                            </p>
                        </c:when>
                        <c:otherwise>
                            <article class="chat-message ${message.mine ? 'mine' : ''}">
                                <c:if test="${not message.mine}">
                                    <strong class="chat-nickname"><c:out value="${message.nickname}"/></strong>
                                </c:if>
                                <div class="chat-message-line">
                                    <p class="chat-bubble"><c:out value="${message.text}"/></p>
                                    <time class="chat-time"><c:out value="${message.time}"/></time>
                                </div>
                            </article>
                        </c:otherwise>
                    </c:choose>
                </c:forEach>
            </div>
        </section>
    </c:otherwise>
</c:choose>
