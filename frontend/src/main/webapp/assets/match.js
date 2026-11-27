const page = document.getElementById("matching");
const matchStatus = document.getElementById("match-status");
const participants = document.getElementById("participants");
const participantStatuses = document.getElementById("participant-statuses");
const timer = document.getElementById("timer");
const readiness = document.getElementById("readiness");
const instruction = document.getElementById("instruction");
const error = document.getElementById("error");
const join = document.getElementById("join");
const ready = document.getElementById("ready");
const cancel = document.getElementById("cancel");
let snapshot;
let busy = false;
let requestNumber = 0;
let lastParticipantState = "";

function render(state) {
    snapshot = state;
    const waiting = state.state === "waiting";
    const started = state.state === "started";
    matchStatus.hidden = !waiting && !started;
    instruction.hidden = !waiting;
    join.hidden = waiting || started;
    ready.hidden = !waiting;
    cancel.hidden = !waiting && !started;
    ready.disabled = state.count < 2;
    ready.textContent = state.ready ? "준비 취소" : "준비 완료";
    cancel.textContent = started ? "나가기" : "매칭 취소";
    participants.textContent = state.count ? `참여자 ${state.count} / ${page.dataset.capacity}명` : "";
    const minutes = String(Math.floor(state.remainingSeconds / 60)).padStart(2, "0");
    const seconds = String(state.remainingSeconds % 60).padStart(2, "0");
    timer.textContent = waiting && state.count >= 2 ? `남은 시간 ${minutes}:${seconds}` : "";
    readiness.textContent = waiting && state.count >= 2 ? `준비 완료 ${state.readyCount} / ${state.count}명` : "";
    const roster = waiting ? state.participants : [];
    const rosterState = JSON.stringify(roster);
    if (rosterState !== lastParticipantState) {
        const rows = roster.map(participant => {
            const row = document.createElement("li");
            row.className = "list-group-item d-flex justify-content-between gap-2 px-0";
            const label = document.createElement("span");
            label.textContent = `${participant.nickname}${participant.self ? " · 나" : ""}`;
            const status = document.createElement("small");
            status.textContent = participant.ready ? "준비 완료" : "대기 중";
            row.append(label, status);
            return row;
        });
        participantStatuses.replaceChildren(...rows);
        participantStatuses.hidden = rows.length === 0;
        lastParticipantState = rosterState;
    }
    if (waiting && state.count < 2) {
        instruction.textContent = "2명이 모이면 준비 완료를 누를 수 있고, 4분 타이머가 시작돼요.";
    } else {
        instruction.textContent = "전원이 준비되거나, 4분이 지나고 2명 이상이면 시작해요.";
    }
}

async function update(action = "status") {
    if (busy) return;
    const currentRequest = ++requestNumber;
    const mutation = action !== "status";
    if (mutation) {
        busy = true;
        for (const button of [join, ready, cancel]) button.disabled = true;
    }
    try {
        const options = mutation ? {
            method: "POST",
            body: new URLSearchParams({
                token: page.dataset.token,
                ready: String(!snapshot?.ready),
                revision: String(snapshot?.revision ?? 0)
            })
        } : {};
        const response = await fetch(page.dataset.api + action, options);
        if (currentRequest !== requestNumber) return;
        if (response.status === 401) {
            location.assign(page.dataset.login);
            return;
        }
        if (response.status === 422) {
            location.assign(page.dataset.profile);
            return;
        }
        if (response.status === 409 && action === "join") {
            location.assign(page.dataset.mypage + "#nickname");
            return;
        }
        if (!response.ok) throw new Error("Matching request failed");
        const state = await response.json();
        if (currentRequest !== requestNumber) return;
        if (state.state === "started") {
            location.assign(page.dataset.room);
            return;
        }
        error.hidden = true;
        render(state);
    } catch {
        if (currentRequest === requestNumber) {
            error.textContent = "매칭 상태를 확인할 수 없어요. 잠시 후 다시 시도해 주세요.";
            error.hidden = false;
        }
    } finally {
        if (mutation) {
            busy = false;
            join.disabled = false;
            cancel.disabled = false;
            ready.disabled = !snapshot || snapshot.count < 2;
        }
    }
}

join.addEventListener("click", () => update("join"));
ready.addEventListener("click", () => update("ready"));
cancel.addEventListener("click", () => update("cancel"));
update();
setInterval(() => update(), 2000);
