package com.seecha.match;

import com.seecha.chat.TopicRules;
import com.seecha.chat.TopicStory;
import com.seecha.dao.ProfileDao.Profile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MatchQueue {
    public static final MatchQueue INSTANCE = new MatchQueue();
    public static final int CAPACITY = 4;
    private static final long WAIT_MS = Duration.ofMinutes(4).toMillis();
    private static final long ROOM_MS = Duration.ofMinutes(60).toMillis();
    private static final long INACTIVE_MS = Duration.ofMinutes(2).toMillis();
    private final List<Group> groups = new ArrayList<>();
    private final Map<Long, Group> memberships = new HashMap<>();
    private long nextRevision;
    private final Map<Long, ClosedRoom> ended = new HashMap<>();
    private static final List<TopicStory> TOPICS = List.of(
            List.of("친구가 중요한 실수를 했다는 걸 알게 됐어요. 당신이라면 친구에게 어떻게 말할 건가요?",
                    "다른 사람이 이미 알고 있다면 어떻게 할 건가요?",
                    "친구가 먼저 의견을 물으면 어떻게 답할 건가요?",
                    "친구가 그 얘기에 상처받을 수 있다면 대화 방식이 달라질까요?"),
            List.of("오랜 친구가 둘만의 여행을 기대하고 있는데 연인이 같은 날 중요한 가족 행사에 함께 가 달라고 부탁했어요. 두 약속을 모두 지키기 어렵다면 누구에게 어떻게 이야기할 건가요?",
                    "친구가 이미 취소하기 어려운 숙소를 예약했다면 어떻게 할 건가요?",
                    "연인이 가족에게 당신이 올 거라고 미리 말했다면 판단이 달라질까요?",
                    "친구가 다른 날짜에 갈 수 있지만 혼자 준비를 다시 해야 한다면 무엇을 제안할 건가요?"),
            List.of("가족이 매주 일요일 함께 식사하길 바라지만, 당신에게는 일요일이 온전히 쉴 수 있는 유일한 날이에요. 가족에게 어떤 약속을 제안할 건가요?",
                    "가족 중 한 사람이 이 식사를 일주일 중 가장 기다린다면 어떻게 말할 건가요?",
                    "다른 가족이 번갈아 참석하자고 제안한다면 그 제안을 어떻게 받아들일 건가요?",
                    "앞으로 한 달 동안만 모두 모일 수 있다면 선택이 달라질까요?"),
            List.of("아파트 복도에 이웃이 유모차를 두기 시작했어요. 이웃은 집 안에 둘 곳이 없다고 하지만, 그 때문에 당신은 장바구니를 들고 지나다니기 불편해졌어요. 어떻게 해결하고 싶나요?",
                    "이웃이 일주일 뒤까지만 양해해 달라고 한다면 어떻게 답할 건가요?",
                    "다른 이웃이 단체 채팅방에 바로 문제를 올리자고 한다면 어떻게 행동할 건가요?",
                    "공용 보관 공간을 만들려면 주민 모두 조금씩 비용을 내야 한다면 어떤 제안을 할 건가요?")).stream().map(TopicStory::legacy).toList();
    private int topicCursor;
    private List<TopicStory> availableTopics = TOPICS;

    public synchronized void tick(long now) {
        advance(now);
    }

    public synchronized void useTopics(List<TopicStory> topics) {
        var mixed = new ArrayList<>(new java.util.LinkedHashSet<>(TOPICS));
        topics.stream().filter(topic -> TopicRules.eligible(topic.opening()))
                .filter(topic -> !mixed.contains(topic)).forEach(mixed::add);
        useManualTopics(mixed);
    }

    public synchronized void useManualTopics(List<TopicStory> topics) {
        var shuffled = new ArrayList<>(topics.isEmpty() ? TOPICS : topics);
        java.util.Collections.shuffle(shuffled);
        availableTopics = List.copyOf(shuffled);
        topicCursor = 0;
    }

    public synchronized Snapshot join(long memberId, Profile profile, String nickname, long now) {
        Snapshot current = status(memberId, now);
        if (!current.state().equals("idle")) {
            return current;
        }
        ended.remove(memberId);
        Group best = null;
        double bestScore = -1;
        for (Group group : groups) {
            if (group.members.size() >= CAPACITY
                    || group.members.values().stream().anyMatch(member -> member.nickname.equalsIgnoreCase(nickname))) {
                continue;
            }
            double score = group.members.values().stream()
                    .mapToInt(member -> difference(profile, member.profile)).average().orElse(0);
            if (score > bestScore) {
                best = group;
                bestScore = score;
            }
        }
        if (best == null) {
            best = new Group();
            groups.add(best);
        }
        var joiningMember = new Member(profile, nickname, now);
        joiningMember.visibleFrom = best.nextMessage;
        joiningMember.firstVisibleQuestion = Math.max(1, best.questionIndex);
        best.members.put(memberId, joiningMember);
        if (best.startedAt != 0) {
            append(best, null, nickname + "님이 들어왔어요.", now);
        }
        best.questionVotes.clear();
        best.endVotes.clear();
        memberships.put(memberId, best);
        best.members.values().forEach(member -> member.ready = false);
        if (best.startedAt == 0 && best.members.size() == 2) {
            best.deadline = now + WAIT_MS;
        }
        best.revision = ++nextRevision;
        return snapshot(best, memberId, now);
    }

    public synchronized Snapshot status(long memberId, long now) {
        Group group = memberships.get(memberId);
        if (group != null && group.startedAt == 0) {
            group.members.get(memberId).lastSeen = now;
        }
        advance(now);
        return snapshot(memberships.get(memberId), memberId, now);
    }

    public synchronized Snapshot ready(long memberId, boolean ready, long revision, long now) {
        status(memberId, now);
        Group group = memberships.get(memberId);
        if (group != null && group.startedAt == 0 && group.members.size() >= 2 && group.revision == revision) {
            group.members.get(memberId).ready = ready;
            if (group.members.values().stream().allMatch(member -> member.ready)) {
                start(group, now);
            }
        }
        return snapshot(group, memberId, now);
    }

    public synchronized Snapshot cancel(long memberId, long now) {
        advance(now);
        Group group = memberships.get(memberId);
        if (group != null) {
            String nickname = group.members.get(memberId).nickname;
            remove(group, memberId);
            if (group.startedAt != 0 && !group.members.isEmpty()) {
                append(group, null, nickname + "님이 나갔어요.", now);
            }
            if (group.members.isEmpty()) {
                finish(group, now);
                groups.remove(group);
            }
        }
        return snapshot(null, memberId, now);
    }

    public synchronized Room room(long memberId, long now) {
        advance(now);
        Group group = memberships.get(memberId);
        if (group == null || group.startedAt == 0) {
            var closed = ended.get(memberId);
            return closed == null ? null : Room.closed(closed.notice());
        }
        Member member = group.members.get(memberId);
        member.lastSeen = now;
        List<RevealedQuestion> questions = new ArrayList<>();
        for (int index = member.firstVisibleQuestion; index <= group.questionIndex; index++) {
            var stage = group.topic.stages().get(index - 1);
            questions.add(new RevealedQuestion(index, stage.scenario(), stage.question(), index == group.questionIndex));
        }
        return new Room(false, group.topic.opening(), List.copyOf(questions),
                group.questionIndex + 1, Math.max(0, (group.startedAt + ROOM_MS - now + 999) / 1000), group.revision, group.roomKey,
                group.members.values().stream().map(item -> new Participant(item.nickname, item.profile)).toList(),
                group.messages.stream().filter(message -> message.sequence() >= member.visibleFrom).toList(),
                group.questionVotes.size(), group.questionVotes.contains(memberId),
                group.endVotes.size(), group.endVotes.contains(memberId), member.nickname, "");
    }

    public synchronized SendResult checkSend(long memberId, long roomKey, UUID requestId, long now) {
        advance(now);
        Group group = memberships.get(memberId);
        if (group == null || group.startedAt == 0 || group.roomKey != roomKey) {
            return SendResult.ROOM_CHANGED;
        }
        if (group.sentRequests.getOrDefault(memberId, Set.of()).contains(requestId)) {
            return SendResult.SENT;
        }
        if (now - group.members.get(memberId).lastMessageAt < 750 || group.messages.size() >= 4000) {
            return SendResult.LIMITED;
        }
        return SendResult.NEW;
    }

    public synchronized SendResult send(long memberId, String text, long roomKey, UUID requestId, long now) {
        SendResult result = checkSend(memberId, roomKey, requestId, now);
        if (result != SendResult.NEW) {
            return result;
        }
        Group group = memberships.get(memberId);
        Member member = group.members.get(memberId);
        member.lastMessageAt = now;
        append(group, member.nickname, text, now);
        group.sentRequests.computeIfAbsent(memberId, ignored -> new HashSet<>()).add(requestId);
        return SendResult.SENT;
    }

    public synchronized void vote(long memberId, boolean ending, boolean agree, long revision, long now) {
        advance(now);
        Group group = memberships.get(memberId);
        if (group == null || group.startedAt == 0 || group.members.size() < 2 || group.revision != revision
                || (!ending && group.questionIndex >= 3)) {
            return;
        }
        var votes = ending ? group.endVotes : group.questionVotes;
        if (!agree) {
            if (!votes.isEmpty()) {
                votes.clear();
                group.revision = ++nextRevision;
            }
            return;
        }
        if (!votes.add(memberId)) {
            return;
        }
        String action = ending ? "조기 종료" : "보조 질문 공개";
        String nickname = group.members.get(memberId).nickname;
        String notice = votes.size() == 1 ? nickname + "님이 " + action + "를 요청했습니다."
                : nickname + "님이 " + action + "에 동의했습니다.";
        append(group, null, notice, now);
        if (votes.size() == group.members.size()) {
            if (ending) {
                finish(group, now, notice);
                groups.remove(group);
            } else {
                group.questionIndex++;
                group.questionVotes.clear();
                group.revision = ++nextRevision;
                append(group, null, "다음 보조 질문이 공개됐어요.", now);
            }
        }
    }

    private void start(Group group, long now) {
        group.startedAt = now;
        group.roomKey = ++nextRevision;
        group.topic = availableTopics.get(topicCursor);
        for (Member member : group.members.values()) {
            append(group, null, member.nickname + "님이 들어왔어요.", now);
        }
        topicCursor = (topicCursor + 1) % availableTopics.size();
    }

    private void finish(Group group, long now) {
        finish(group, now, "");
    }

    private void finish(Group group, long now, String notice) {
        group.messages.clear();
        group.sentRequests.clear();
        group.questionVotes.clear();
        group.endVotes.clear();
        group.members.keySet().forEach(id -> {
            memberships.remove(id);
            ended.put(id, new ClosedRoom(now, notice));
        });
        group.members.clear();
    }

    private static void append(Group group, String nickname, String text, long now) {
        group.messages.add(new Message(group.nextMessage++, nickname, text, now));
    }

    private void advance(long now) {
        ended.values().removeIf(room -> now - room.endedAt() >= ROOM_MS);
        groups.removeIf(group -> {
            var expired = group.members.entrySet().stream()
                    .filter(entry -> now - entry.getValue().lastSeen >= INACTIVE_MS)
                    .map(Map.Entry::getKey).toList();
            if (group.startedAt != 0) {
                if (expired.size() == group.members.size() || now - group.startedAt >= ROOM_MS) {
                    finish(group, now);
                    return true;
                }
                boolean hadVote = !group.questionVotes.isEmpty() || !group.endVotes.isEmpty();
                for (long id : expired) {
                    String nickname = group.members.get(id).nickname;
                    remove(group, id);
                    append(group, null, nickname + "님의 연결이 끊겨 퇴장했어요.", now);
                }
                if (!expired.isEmpty() && hadVote) {
                    append(group, null, "참여자가 바뀌어 진행 중이던 투표가 취소됐어요.", now);
                }
                return false;
            }
            expired.forEach(id -> remove(group, id));
            if (group.members.size() >= 2 && now >= group.deadline) {
                start(group, now);
            }
            return group.members.isEmpty();
        });
    }

    private void remove(Group group, long memberId) {
        group.members.remove(memberId);
        group.sentRequests.remove(memberId);
        group.questionVotes.clear();
        group.endVotes.clear();
        memberships.remove(memberId);
        group.members.values().forEach(member -> member.ready = false);
        if (group.members.size() < 2) {
            group.deadline = 0;
        }
        group.revision = ++nextRevision;
    }

    private static Snapshot snapshot(Group group, long memberId, long now) {
        if (group == null) {
            return new Snapshot("idle", 0, 0, 0, false, 0, List.of());
        }
        int ready = (int) group.members.values().stream().filter(member -> member.ready).count();
        int remaining = (int) Math.max(0, (group.deadline - now + 999) / 1000);
        List<Readiness> participants = group.startedAt == 0 ? group.members.entrySet().stream()
                .map(entry -> new Readiness(entry.getValue().nickname, entry.getKey() == memberId,
                        entry.getValue().ready)).toList() : List.of();
        return new Snapshot(group.startedAt == 0 ? "waiting" : "started", group.members.size(),
                ready, remaining, group.members.get(memberId).ready, group.revision, participants);
    }

    private static int difference(Profile a, Profile b) {
        return different(a.ageGroup(), b.ageGroup()) + different(a.mbti(), b.mbti())
                + 2 * different(a.childhoodRegion(), b.childhoodRegion())
                + 2 * different(a.lifeStage(), b.lifeStage()) + 2 * different(a.field(), b.field());
    }

    private static int different(String a, String b) {
        return a != null && b != null && !a.equals("모름") && !b.equals("모름") && !a.equals(b) ? 1 : 0;
    }

    private static final class Group {
        final Map<Long, Member> members = new LinkedHashMap<>();
        long deadline;
        long startedAt;
        long roomKey;
        final Map<Long, Set<UUID>> sentRequests = new HashMap<>();
        long revision;
        long nextMessage = 1;
        int questionIndex;
        TopicStory topic;
        final List<Message> messages = new ArrayList<>();
        final java.util.Set<Long> questionVotes = new java.util.HashSet<>();
        final java.util.Set<Long> endVotes = new java.util.HashSet<>();

    }

    private static final class Member {
        final Profile profile;
        final String nickname;
        long lastSeen;
        long visibleFrom;
        int firstVisibleQuestion;
        long lastMessageAt;
        boolean ready;

        Member(Profile profile, String nickname, long now) {
            this.profile = profile;
            this.nickname = nickname;
            lastSeen = now;
        }
    }

    public enum SendResult { NEW, SENT, LIMITED, ROOM_CHANGED }

    public record Message(long sequence, String nickname, String text, long sentAt) {}

    private record ClosedRoom(long endedAt, String notice) {}

    public record Participant(String nickname, Profile profile) {}

    public record RevealedQuestion(int number, String scenario, String text, boolean current) {}

    public record Room(boolean ended, String scenario, List<RevealedQuestion> questions, int step, long remainingSeconds,
                       long revision, long roomKey, List<Participant> participants, List<Message> messages,
                       int questionVotes, boolean questionVoted, int endVotes, boolean endVoted, String nickname, String endNotice) {
        static Room closed(String notice) {
            return new Room(true, "", List.of(), 0, 0, 0, 0, List.of(), List.of(), 0, false, 0, false, "", notice);
        }
    }

    public record Readiness(String nickname, boolean self, boolean ready) {}
    public record Snapshot(String state, int count, int readyCount, int remainingSeconds, boolean ready,
                           long revision, List<Readiness> participants) {}
}
