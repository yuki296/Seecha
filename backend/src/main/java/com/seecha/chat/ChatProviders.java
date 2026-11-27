package com.seecha.chat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class ChatProviders {
    private static final Gson JSON = new Gson();
    private static final String OPENAI_KEY = System.getenv("OPENAI_API_KEY");
    private static final String GEMINI_KEY = System.getenv("GEMINI_API_KEY");
    private static final String GEMINI_MODEL = System.getenv().getOrDefault("SEECHA_GEMINI_MODEL", "gemini-3.1-flash-lite");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final Semaphore CHECKS = new Semaphore(4);
    private static final List<String> TOPIC_CATEGORIES =
            List.of("우정", "가족", "연애", "이웃", "일상", "공정성", "학업", "일");
    private static final Pattern ABUSE_SEPARATORS = Pattern.compile("[\\p{Z}\\p{P}\\p{Cf}\\s]+");
    private static final Pattern LOCAL_ABUSE = Pattern.compile(
            "씨발|씨벌|씨팔|씨바(?=$|[라야])|시팔|지랄|시발(?:놈|년)|좆|병신|개새끼|새끼야|미친(?:놈|년)|꺼져|죽여버릴");
    private static final Pattern ABUSE_SHORTHAND = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:시발|ㅅ[\\s\\p{P}]*ㅂ|ㅂ[\\s\\p{P}]*ㅅ|ㅈ[\\s\\p{P}]*ㄹ)(?![\\p{L}\\p{N}])");
    private static final Pattern NEUTRAL_MENTION = Pattern.compile(
            "(?:(?:피해자|욕설)|(?:(?:[0-9]{1,3}|한|두|세|네|몇|여러)\\s*명의?\\s*)?"
                    + "피해자(?:가|는)?\\s*(?:발생했(?:군요|네요|어요|습니다)|생겼(?:군요|네요|어요|습니다)))"
                    + "[.!?。…]*");
    private static final Pattern CONTACT = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+[.][A-Z]{2,}|(?<![0-9])01[016789][ -]?[0-9]{3,4}[ -]?[0-9]{4}(?![0-9])",
            Pattern.CASE_INSENSITIVE);

    private ChatProviders() {}

    public static Decision moderate(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        String compact = ABUSE_SEPARATORS.matcher(normalized).replaceAll("");
        // NFKC changes standalone compatibility jamo, so check shorthand on the original text.
        if (LOCAL_ABUSE.matcher(compact).find() || ABUSE_SHORTHAND.matcher(text).find()
                || CONTACT.matcher(normalized).find()) {
            return Decision.BLOCKED;
        }
        if (!configured(OPENAI_KEY)) {
            return Decision.ALLOWED;
        }
        if (!CHECKS.tryAcquire()) {
            return Decision.UNAVAILABLE;
        }
        try {
            JsonObject response = post("https://api.openai.com/v1/moderations", "Authorization",
                    "Bearer " + OPENAI_KEY, Duration.ofSeconds(8), Map.of("model", "omni-moderation-latest", "input", text));
            return assessModeration(response.getAsJsonArray("results").get(0).getAsJsonObject(), normalized);
        } catch (IOException | RuntimeException exception) {
            return Decision.UNAVAILABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Decision.UNAVAILABLE;
        } finally {
            CHECKS.release();
        }
    }

    private static Decision assessModeration(JsonObject result, String normalized) {
        if (!result.get("flagged").getAsBoolean()) {
            return Decision.ALLOWED;
        }
        var categories = result.getAsJsonObject("categories");
        // Only exempt a whole neutral phrase when non-graphic violence is the sole classifier flag.
        if (NEUTRAL_MENTION.matcher(normalized.strip()).matches() && categories.has("violence")
                && categories.get("violence").getAsBoolean()
                && categories.entrySet().stream().noneMatch(entry ->
                        !entry.getKey().equals("violence") && entry.getValue().getAsBoolean())) {
            return Decision.ALLOWED;
        }
        return Decision.BLOCKED;
    }

    public static boolean generationEnabled() {
        return configured(GEMINI_KEY);
    }

    public static Topic generate() throws IOException, InterruptedException {
        if (!GEMINI_MODEL.matches("[a-zA-Z0-9._-]+")) {
            throw new IOException("Invalid Gemini model");
        }
        int pick = ThreadLocalRandom.current().nextInt(20);
        String category = pick < 18 ? TOPIC_CATEGORIES.get(pick / 3) : TOPIC_CATEGORIES.get(pick - 12);
        String prompt = """
                시차(seecha)의 성인 익명 그룹 채팅에서 사용할 한국어 스토리형 대화 주제 1개를 생성하세요.

                **지정 주제: %s**

                시차는 서로 다른 환경과 경험을 가진 사람들이 같은 상황을 어떻게 받아들이는지 이야기하며, 각자의 판단과 가치관이 어디에서 비롯되었는지 이해하는 서비스입니다.

                대화의 목적은 정답을 찾거나 상대를 설득하는 것이 아닙니다. 서로 다른 선택을 이해하는 것이 중요하며, 끝까지 의견이 달라도 괜찮습니다.

                ## 1. 소재 선정

                - 현실에서 일어날 법한 흥미로운 윤리적 딜레마를 만드세요.
                - 최근 사회적 이슈, 세대 차이, 인간관계, 생활환경의 차이에서 소재를 찾으세요.
                - 신뢰와 정직, 개인의 자유와 책임, 공정성과 배려, 진심과 표현 방식 등 서로 충돌하는 가치를 중심으로 구성하세요.
                - 참여자의 경험과 가치관에 따라 서로 다른 판단이 나올 수 있어야 합니다.
                - 뻔한 도덕 문제나 누가 봐도 한쪽이 잘못한 상황은 피하세요.
                - 팀플, 마감 갈등, 단순한 약속 조율 등 반복적인 소재는 제외하세요.
                - 회사와 업무를 배경으로 하는 이야기는 '일' 주제에서만 사용하세요.
                - 지나치게 극단적이거나 자극적인 사건은 피하세요.
                - 개인적인 상처나 민감한 경험을 공개해야만 대화할 수 있는 상황은 만들지 마세요.

                ## 2. 스토리 구성

                하나의 사건을 총 4단계로 구성하세요.

                **1단계 — 사건의 시작**

                - 현실적인 사건과 핵심 갈등을 제시하세요.
                - 모든 인물의 사정이나 사건의 전말을 처음부터 공개하지 마세요.
                - 참여자가 첫인상을 바탕으로 자신의 행동이나 판단을 이야기할 수 있도록 하세요.

                **2단계 — 새로운 시선**

                - 처음에는 알 수 없었던 인물의 경험이나 사정을 공개하세요.
                - 해당 인물이 왜 그런 행동을 했는지 이해할 수 있는 정보를 추가하세요.
                - 해당 행동을 정당화하거나 참여자의 기존 판단이 틀렸다고 유도하지 마세요.

                **3단계 — 또 다른 시선**

                - 다른 이해관계자의 입장이나 새로운 정보를 공개하세요.
                - 앞선 단계와 다른 가치관이나 판단 기준을 생각할 수 있도록 하세요.
                - 단순히 찬성하는 사람과 반대하는 사람의 의견을 나열하지 말고, 구체적인 경험이나 상황을 보여주세요.

                **4단계 — 확장된 시선**

                - 앞선 상황과 자연스럽게 연결되는 새로운 정보를 제시하세요.
                - 지금까지 드러난 여러 입장을 고려하며 자신의 생각을 더 깊게 이야기할 수 있도록 하세요.
                - 반드시 결론을 내리거나 문제를 해결하도록 요구하지 마세요.
                - 모든 사람을 만족시키는 타협안을 찾도록 유도하지 마세요.
                - 처음의 판단을 유지하거나 바꾸는 것 모두 자연스럽게 허용하세요.

                각 단계는 하나의 이야기로 연결되어야 하지만, 앞선 질문에 참여자가 어떤 답변을 했는지 가정하지 마세요.

                ## 3. 상황 작성 규칙

                - 각 단계의 상황 설명은 공백 포함 80~150자 내외, 최대 3문장으로 작성하세요.
                - 인물 관계와 사건을 쉽게 이해할 수 있도록 작성하세요.
                - 실제 친구에게 이야기를 들려주듯 자연스럽고 간결한 한국어를 사용하세요.
                - 불필요한 배경 설명, 수식어, 감정 해설을 제거하세요.
                - 구체적인 행동과 사건으로 갈등을 보여주세요.
                - 새로운 정보가 기존 사실을 뒤집거나 특정 인물을 갑자기 악인으로 만들지 않도록 하세요.
                - 등장인물 모두를 무조건 이해하거나 배려해야 한다는 방향으로 작성하지 마세요.
                - 상황 설명에서 해결 방법이나 결론을 미리 제시하지 마세요.

                **금지 표현 예시**

                - "어떤 결정을 내려야 할지 고민이 깊어집니다."
                - "모두가 만족할 수 있는 해결책을 찾아야 합니다."
                - "현실적인 대안을 마련해야 하는 상황입니다."
                - "우정과 신뢰, 공정성 사이에서 갈등하게 됩니다."
                - "서로의 입장을 이해하고 원만하게 해결해야 합니다."

                ## 4. 질문 작성 규칙

                각 단계마다 질문을 정확히 1개 생성하세요.

                - 해당 단계의 구체적인 사건이나 인물과 직접 연결된 질문이어야 합니다.
                - 질문은 간결하고 자연스러운 존댓말 한 문장으로 작성하세요.
                - 참여자가 자신의 행동, 감정, 판단 기준 또는 가치관을 이야기할 수 있도록 하세요.
                - 행동을 묻는 질문과 가치관을 묻는 질문을 자연스럽게 혼합하세요.
                - 모든 단계에서 반드시 행동이나 해결 방법만 묻지 마세요.
                - 정답, 찬반 또는 특정 선택을 유도하지 마세요.
                - 추상적인 가치를 나열하거나 지나치게 격식 있는 표현을 사용하지 마세요.
                - 질문마다 새로운 대화가 이어질 수 있어야 하며, 앞선 질문을 단순히 반복하지 마세요.

                **피해야 할 질문**

                - "당신이라면 어떻게 하시겠습니까?"
                - "어떤 선택이 가장 올바를까요?"
                - "이 문제를 어떤 방식으로 풀어나가고 싶으신가요?"
                - "모두가 만족할 수 있는 해결책은 무엇일까요?"
                - "이러한 상황에서 신뢰와 책임 사이의 균형을 어떻게 맞추겠습니까?"

                **좋은 질문의 방향**

                - "친구가 AI로 사과문을 작성했다는 사실을 알게 된다면 그 사과를 어떻게 받아들이겠어요?"
                - "상대방의 사정을 이해하는 것과 그 행동을 용서하는 것은 다르다고 생각하나요?"
                - "친한 친구라도 잘못된 행동을 했다면 어디까지 편을 들어줄 수 있나요?"
                - "당신은 사과할 때 표현 방식과 그 안에 담긴 진심 중 무엇을 더 중요하게 생각하나요?"

                위 질문들은 문체와 방향을 보여주는 예시이며 그대로 복제하지 마세요.

                **특히 4단계의 마지막 질문은 문제 해결이 아니라 자신의 판단을 돌아보는 대화가 되도록 작성하세요.**

                새로운 사실을 모두 알게 되었다고 해서 반드시 의견이 달라져야 하는 것은 아닙니다.

                ## 5. 단계별 제목 생성

                각 단계에 짧고 자연스러운 제목을 생성하세요.

                - 제목은 4~15자 내외로 작성하세요.
                - 해당 단계에서 드러나는 사건이나 관점을 표현하세요.
                - 제목만으로 다음 전개나 결론이 드러나지 않도록 하세요.
                - 모든 스토리에 동일한 제목을 반복하지 마세요.
                - '새로운 시선', '또 다른 시선', '확장된 판단' 같은 형식적인 제목보다 스토리에 맞는 표현을 사용하세요.

                예시:

                - "AI가 써준 사과"
                - "친구가 숨긴 이유"
                - "서로 다른 진심의 기준"
                - "사과 이후의 관계"

                위 제목은 예시이므로 생성한 스토리에 맞춰 새롭게 작성하세요.

                ## 6. 출력 전 검증

                응답하기 전에 다음 사항을 확인하세요.

                - 사람들이 실제로 의견을 나누고 싶을 만큼 흥미로운 상황인가?
                - 단순한 문제 해결 과제로 끝나지 않는가?
                - 서로 다른 행동과 판단에 각각 납득할 만한 이유가 있는가?
                - 2~4단계에서 새로운 사실이나 관점이 공개되는가?
                - 질문이 자연스럽고 구체적인가?
                - 모든 질문이 비슷한 표현으로 반복되지 않는가?
                - 마지막 질문이 억지로 해결책이나 결론을 요구하지 않는가?
                - 다른 의견을 이해하면서도 자신의 생각을 유지할 수 있는가?
                - 단계별 제목이 스토리 내용과 자연스럽게 연결되는가?

                조건을 충족하지 않으면 내부적으로 수정한 뒤 최종 결과만 출력하세요.

                ## 7. 출력 형식

                반드시 다음 구조의 유효한 JSON 객체 하나만 반환하세요.
                ```json
                {
                  "stageTitle": "1단계 제목",
                  "scenario": "1단계 상황 설명",
                  "mainQuestion": "1단계 메인 질문",
                  "questions": [
                    {
                      "title": "2단계 제목",
                      "scenario": "2단계 추가 상황",
                      "question": "2단계 보조질문"
                    },
                    {
                      "title": "3단계 제목",
                      "scenario": "3단계 추가 상황",
                      "question": "3단계 보조질문"
                    },
                    {
                      "title": "4단계 제목",
                      "scenario": "4단계 추가 상황",
                      "question": "4단계 보조질문"
                    }
                  ]
                }
                ```

                `questions` 배열에는 정확히 3개의 객체를 포함하세요.

                JSON 외의 설명, 제목, 해설, 번호, 정답, 선택지 또는 마크다운 코드 블록은 출력하지 마세요.
                """.formatted(category);
        var string = Map.of("type", "string");
        var stage = Map.of("type", "object", "required", List.of("title", "scenario", "question"),
                "properties", Map.of("title", string, "scenario", string, "question", string));
        var schema = Map.of("type", "object", "required", List.of("stageTitle", "scenario", "mainQuestion", "questions"),
                "properties", Map.of("stageTitle", string, "scenario", string, "mainQuestion", string, "questions",
                        Map.of("type", "array", "items", stage, "minItems", 3, "maxItems", 3)));
        var response = post("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
                "x-goog-api-key", GEMINI_KEY, Duration.ofSeconds(30),
                Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                        "generationConfig", Map.of("responseMimeType", "application/json", "responseJsonSchema", schema)));
        var parts = response.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .getAsJsonObject("content").getAsJsonArray("parts");
        StringBuilder generated = new StringBuilder();
        for (var part : parts) {
            var object = part.getAsJsonObject();
            if (object.has("text") && !(object.has("thought") && object.get("thought").getAsBoolean())) {
                generated.append(object.get("text").getAsString());
            }
        }
        return parseStory(category, JsonParser.parseString(generated.toString()).getAsJsonObject());
    }

    static Topic parseStory(String category, JsonObject result) throws IOException {
        var items = result.get("questions");
        if (items == null || !items.isJsonArray() || items.getAsJsonArray().size() != 3) {
            throw new IOException("Unexpected Gemini topic shape");
        }
        String openingTitle = validateTitle(result.get("stageTitle"));
        String scenario = validate(result.get("scenario"), 175);
        String mainQuestion = validate(result.get("mainQuestion"), 145);
        var opening = scenario + "\n\nQ. " + mainQuestion;
        if (!TopicRules.eligible(opening)) {
            throw new IOException("Invalid story opening");
        }
        List<TopicStory.Stage> stages = new ArrayList<>(3);
        var questionTexts = new java.util.HashSet<String>();
        questionTexts.add(mainQuestion);
        var titles = new java.util.HashSet<String>();
        titles.add(openingTitle);
        for (var item : items.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                throw new IOException("Invalid story stage");
            }
            var followUp = item.getAsJsonObject();
            String title = validateTitle(followUp.get("title"));
            if (!titles.add(title)) {
                throw new IOException("Repeated story stage title");
            }
            String stageScenario = validate(followUp.get("scenario"), 175);
            String question = validate(followUp.get("question"), 300);
            if (!TopicRules.eligible(stageScenario + "\n\nQ. " + question) || !questionTexts.add(question)) {
                throw new IOException("Invalid or repeated story question");
            }
            stages.add(new TopicStory.Stage(stageScenario, question, title));
        }
        return new Topic(category, new TopicStory(opening, category, "", openingTitle, stages));
    }

    private static String validateTitle(JsonElement element) throws IOException {
        String title = validate(element, 15);
        if (title.length() < 4 || title.lines().count() != 1) {
            throw new IOException("Invalid story stage title");
        }
        return title;
    }

    private static String validate(JsonElement text, int limit) throws IOException {
        if (text == null || !text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString()) {
            throw new IOException("Invalid topic text type");
        }
        String value = text.getAsString().strip();
        if (value.isEmpty() || value.length() > limit) {
            throw new IOException("Invalid topic text");
        }
        return value;
    }

    private static boolean configured(String key) {
        return key != null && !key.isBlank();
    }

    private static JsonObject post(String endpoint, String header, String key, Duration timeout, Object body)
            throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                .header("Content-Type", "application/json").header(header, key)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(body))).build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Provider returned " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    public enum Decision { ALLOWED, BLOCKED, UNAVAILABLE }
    public record Topic(String category, TopicStory story) {}
}
