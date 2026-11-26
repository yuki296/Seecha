const room = document.getElementById("chat-room");
const view = document.getElementById("room-view");
const composer = document.getElementById("composer");
const menuButton = document.getElementById("room-menu");
const panel = document.getElementById("chat-panel");
const panelTitle = document.getElementById("chat-panel-title");
const panelBody = document.getElementById("chat-panel-body");
const panelMenu = document.getElementById("chat-panel-menu");
const panelError = document.getElementById("chat-panel-error");
const panelTitles = {menu: "채팅방 메뉴", topic: "현재 질문", people: "참여자", question: "다음 질문", end: "조기 종료"};
let activePanel;
let lastPanelContent;
let panelTrigger;

const error = document.getElementById("chat-error");
const connection = document.getElementById("chat-connection");
const input = composer.elements.message;
let busy = false;
let pending = false;
let lastFragment = "";
let poll;
let pendingMessage;

const viewport = window.visualViewport;

function fitChatToViewport() {
    if (!viewport || Math.abs(viewport.scale - 1) >= .01) return;
    const messages = document.getElementById("messages");
    const atBottom = messages && messages.scrollHeight - messages.scrollTop - messages.clientHeight < 36;
    room.style.setProperty("--chat-visible-height", `${Math.floor(viewport.height)}px`);
    room.style.setProperty("--chat-visible-top", `${Math.max(0, viewport.offsetTop)}px`);
    if (atBottom) messages.scrollTop = messages.scrollHeight;
}

viewport?.addEventListener("resize", fitChatToViewport);
viewport?.addEventListener("scroll", fitChatToViewport);
window.addEventListener("resize", fitChatToViewport);
window.addEventListener("pageshow", fitChatToViewport);
fitChatToViewport();

function syncPanel() {
    const state = document.getElementById("room-state");
    menuButton.textContent = state?.dataset.votes === "true" ? "메뉴 · 동의 요청" : "메뉴";
    if (!activePanel || activePanel === "menu") return;
    const template = state?.querySelector(`template[data-content="${activePanel}"]`);
    if (template && template.innerHTML !== lastPanelContent) {
        panelBody.replaceChildren(template.content.cloneNode(true));
        lastPanelContent = template.innerHTML;
    }
}

function openPanel(name, trigger) {
    if (!Object.hasOwn(panelTitles, name)) return;
    if (!panel.open) panelTrigger = trigger;
    activePanel = name;
    lastPanelContent = undefined;
    panelTitle.textContent = panelTitles[name];
    panelMenu.hidden = name !== "menu";
    panelBody.hidden = name === "menu";
    panelBody.replaceChildren();
    showError("");
    syncPanel();
    if (!panel.open) panel.showModal();
    panel.querySelector("form[method=dialog] button")?.focus();
}

panel.addEventListener("close", () => {
    activePanel = undefined;
    if (panelTrigger?.isConnected) {
        panelTrigger.focus();
    } else {
        (view.querySelector("[data-panel=topic]") ?? menuButton).focus();
    }
});

function showError(message) {
    error.textContent = panel.open ? "" : message;
    error.hidden = !error.textContent;
    panelError.textContent = panel.open ? message : "";
    panelError.hidden = !panelError.textContent;
}

function showConnection(message) {
    if (connection.textContent !== message) connection.textContent = message;
    connection.hidden = !message;
}

async function refresh(force = false) {
    if (pending || (busy && !force)) return;
    pending = true;
    try {
        const response = await fetch(room.dataset.api + "view", {signal: AbortSignal.timeout(5000)});
        if (response.status === 401) {
            location.assign(room.dataset.login);
            return;
        }
        if (response.status === 404) {
            location.assign(room.dataset.match);
            return;
        }
        if (!response.ok) throw new Error();
        const html = await response.text();
        showConnection("");
        if (html === lastFragment) return;
        const replacement = new DOMParser().parseFromString(html, "text/html").getElementById("room-state");
        if (!replacement) throw new Error();
        const previous = document.getElementById("messages");
        const oldTop = previous?.scrollTop ?? 0;
        const atBottom = !previous || previous.scrollHeight - oldTop - previous.clientHeight < 36;
        const previousStep = document.getElementById("room-state")?.dataset.step;
        view.replaceChildren(replacement);
        syncPanel();
        if (panel.open && activePanel === "question" && previousStep !== replacement.dataset.step
                && replacement.dataset.ended !== "true") {
            openPanel("topic");
        }
        lastFragment = html;
        const messages = document.getElementById("messages");
        if (messages) messages.scrollTop = atBottom ? messages.scrollHeight : oldTop;
        if (replacement.dataset.ended === "true") {
            composer.hidden = true;
            menuButton.hidden = true;
            if (panel.open) panel.close();
            clearInterval(poll);
        }
    } catch {
        showConnection("연결이 끊겼어요. 대화 상태를 다시 확인하고 있어요.");
    } finally {
        pending = false;
    }
}

async function submit(action, data) {
    if (busy) return;
    busy = true;
    showError("");
    try {
        const response = await fetch(room.dataset.api + action, {
            method: "POST",
            signal: AbortSignal.timeout(12000),
            body: new URLSearchParams({
                token: room.dataset.token,
                revision: document.getElementById("room-state")?.dataset.revision ?? "",
                ...data
            })
        });
        if (response.status === 401) {
            location.assign(room.dataset.login);
            return;
        }
        if (response.status === 409) {
            if (action === "send") {
                pendingMessage = undefined;
                showError("채팅방 상태가 바뀌었어요. 메시지를 확인하고 다시 시도해 주세요.");
            }
            await refresh(true);
            return;
        }
        if (response.status === 422) {
            showError("부적절한 표현이나 연락처가 포함된 메시지는 보낼 수 없어요.");
            return;
        }
        if (response.status === 503) {
            showError("지금은 메시지를 검사할 수 없어요. 잠시 후 다시 시도해 주세요.");
            return;
        }
        if (!response.ok) {
            showError(response.status === 429 ? "잠시 기다렸다가 다시 시도해 주세요."
                : "요청을 처리할 수 없어요. 다시 시도해 주세요.");
            return;
        }
        if (action === "send") {
            pendingMessage = undefined;
            if (input.value.trim() === data.message) input.value = "";
        }
        await refresh(true);
    } catch {
        showError(action === "send" ? "전송을 확인하지 못했어요. 입력한 메시지를 다시 보낼 수 있어요."
            : "연결을 확인하고 다시 시도해 주세요.");
    } finally {
        busy = false;
    }
}

composer.addEventListener("submit", event => {
    event.preventDefault();
    const message = input.value.trim();
    if (!message || busy) return;
    const roomKey = document.getElementById("room-state")?.dataset.roomKey;
    if (!roomKey) return;
    if (pendingMessage?.message !== message || pendingMessage.roomKey !== roomKey) {
        pendingMessage = {message, roomKey, requestId: crypto.randomUUID()};
    }
    submit("send", pendingMessage);
});
room.addEventListener("click", event => {
    const trigger = event.target.closest("button[data-panel]");
    if (trigger) openPanel(trigger.dataset.panel, trigger);
    const button = event.target.closest("button[data-action]");
    if (button) submit(button.dataset.action, {agree: button.dataset.agree});
});
syncPanel();
if (!composer.hidden) {
    const messages = document.getElementById("messages");
    if (messages) messages.scrollTop = messages.scrollHeight;
    poll = setInterval(() => refresh(), 2000);
}
