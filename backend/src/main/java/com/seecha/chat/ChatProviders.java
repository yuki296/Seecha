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

                시차는 서로 다른 환경과 경험을 가진 사람들이 같은 상황에 대해 각자의 생각과 가치관을 나누는 서비스입니다.

                목적은 정답을 찾거나 합의하는 것이 아니라, **자신과 다른 판단을 내리는 사람의 이유를 이해하는 것**입니다.

                ## 1. 소재 선정

                **공감하기 쉽고, 흥미로우며, 실제로 의견이 갈릴 만한 상황을 우선하세요.**

                특히 다음 유형을 적극적으로 활용하세요.

                **연애·썸·신뢰**

                - 연인의 외도, 바람을 용서할 수 있는 기준, 신뢰 회복
                - 이성 친구와 전 연인 문제
                - 연애 중 사생활과 연락의 경계
                - 거짓말과 선의의 비밀, 연인의 SNS 행동
                - 결혼관, 경제관념, 관계의 우선순위

                **팀플·협업·직장**

                - 팀원 간 기여도와 평가의 불균형
                - 실력은 뛰어나지만 협업 태도가 나쁜 사람
                - 성실하지만 성과가 부족한 팀원
                - 친분에 따른 편애와 불공정한 보상
                - 리더의 책임, 성과 독식, 무임승차
                - 개인의 이익과 공동체의 이익이 충돌하는 상황

                **가족·우정·일상**

                - 가족의 기대와 개인의 자유
                - 친구 사이의 의리와 정직
                - 경제적 환경에 따른 가치관 차이
                - 개인의 권리와 공동체의 책임
                - 실제 사회적 논쟁에서 영감을 얻은 윤리적 딜레마

                기존에 제외했던 학교 팀 프로젝트와 협업 갈등도 적극 활용하세요.

                ### 상황의 흥미를 높이는 방법

                다음 세 가지 유형의 특징을 적절히 혼합하세요.

                1. **상황판단검사(SJT)형:** 실제 문제에 직면했을 때 무엇을 우선하고 어떤 행동을 할지 판단하게 만드는 상황
                2. **밸런스 게임형:** 두 선택 모두 쉽지 않거나, 사람마다 선택의 이유가 크게 달라질 수 있는 상황
                3. **현실 공감형:** 연애, 팀플, 인간관계에서 실제로 겪었거나 주변에서 들어봤을 법한 상황

                단, 정해진 선택지만 제공하거나 성격·도덕성을 평가하지 마세요.

                **중요:** 재미있는 상황을 만들기 위해 억지로 모든 인물을 착하게 묘사하지 마세요.

                잘못된 행동이 존재해도 괜찮습니다. 다만 그 행동을 어떻게 받아들이고, 어디까지 이해하거나 용서할 수 있는지에 대해서는 다양한 판단이 가능해야 합니다.

                ## 2. 4단계 스토리 구성

                **1단계 — 사건의 시작**

                - 흥미로운 갈등 상황을 제시하세요.
                - 처음부터 모든 사실을 공개하지 마세요.
                - 참여자가 자신의 생각이나 행동을 바로 떠올릴 수 있어야 합니다.

                **2단계 — 새로운 시선**

                - 한 인물의 숨겨진 사정, 행동의 이유 또는 새로운 사실을 공개하세요.
                - 첫 단계의 판단을 다시 생각해 볼 여지를 만드세요.
                - 해당 인물의 행동을 반드시 정당화할 필요는 없습니다.

                **3단계 — 다른 가치관**

                - 또 다른 인물의 입장이나 충돌하는 가치관을 보여주세요.
                - 서로의 판단이 왜 다른지 이야기할 수 있도록 하세요.
                - 단순히 반대 의견을 나열하지 말고 상황에 변화를 주세요.

                **4단계 — 확장된 판단**

                - 앞선 이야기와 연결되는 새로운 조건이나 사실을 제시하세요.
                - 여러 시선을 접한 뒤 자신의 기준을 다시 생각할 수 있도록 하세요.
                - 반드시 결론을 내리거나 갈등을 해결하도록 요구하지 마세요.
                - 처음의 생각을 유지하는 것도 자연스럽게 허용하세요.

                네 단계는 하나의 이야기로 연결되어야 하며, 앞선 질문에 참여자가 어떤 답을 했는지 가정하지 마세요.

                ## 3. 상황 작성 규칙

                - 각 단계의 상황은 80~150자 내외, 최대 3문장으로 작성하세요.
                - 실제 친구에게 들려주듯 자연스럽고 쉽게 작성하세요.
                - 등장인물과 관계를 명확하게 설정하세요.
                - 불필요한 배경 설명과 감정 해설을 제거하세요.
                - 단순한 불편함이 아니라 관계, 신뢰, 책임, 공정성 등 중요한 가치가 걸려 있어야 합니다.
                - 새로운 정보가 앞선 사실을 갑자기 뒤집지 않도록 하세요.
                - 너무 평범해서 한마디로 끝나는 상황은 피하세요.
                - 단순한 약속 조율, 주차장·공용 공간 문제, 지나치게 교과서적인 갈등을 반복하지 마세요.
                - 자극적인 반전만을 위해 비현실적인 사건을 만들지 마세요.
                - 참여자가 개인적인 상처나 민감한 경험을 공개하지 않고도 이야기할 수 있어야 합니다.

                ## 4. 질문 작성 규칙

                각 단계에서 상황과 직접 연결되는 질문을 정확히 한 문장씩 생성하세요.

                질문은 **행동, 가치관, 감정, 신뢰, 용서, 공정성, 판단의 기준** 중 상황에 적합한 관점을 활용하세요.

                - 메인 질문은 갈등의 핵심을 구체적으로 짚으세요.
                - 모든 질문을 문제 해결형으로 만들지 마세요.
                - 자연스럽고 간결한 존댓말을 사용하세요.
                - 질문이 특정 결론을 전제하지 않도록 하세요.
                - 같은 표현을 여러 단계에서 반복하지 마세요.
                - 앞선 보조질문에 대한 답을 가정하지 마세요.

                **좋은 질문의 방향**

                - "연인이 바람을 피웠다는 사실을 알게 된다면 관계를 이어갈 가능성이 있나요?"
                - "친한 친구가 바람피운 사실을 알게 됐다면 그 친구의 연인에게 알려주겠어요?"
                - "실력은 뛰어나지만 다른 팀원들을 무시하는 사람을 팀에 남기고 싶나요?"
                - "팀에 가장 많이 기여한 사람이 반드시 가장 높은 보상을 받아야 한다고 생각하나요?"
                - "상대방의 사정을 이해하는 것과 그 행동을 용서하는 것은 다르다고 생각하나요?"

                위 예시는 질문의 분위기와 방향을 보여주는 것이며 그대로 복제하지 마세요.

                **피해야 할 표현**

                - "어떤 방식으로 문제를 해결하시겠습니까?"
                - "모두가 만족할 현실적인 대안을 마련하세요."
                - "서로의 입장을 조율하기 위해 무엇을 해야 할까요?"
                - "당신은 어떤 선택을 내리겠습니까?"
                - "이 상황에서 무엇이 가장 바람직할까요?"

                마지막 질문도 반드시 해결책을 요구하지 말고, 참여자의 판단이나 가치관을 더 깊게 이야기할 수 있도록 하세요.

                ## 5. 단계별 제목

                각 단계마다 스토리에 어울리는 제목을 생성하세요.

                - 제목은 4~15자 내외로 작성하세요.
                - 해당 단계의 사건이나 관점을 표현하세요.
                - 다음 단계의 반전이나 결론을 미리 공개하지 마세요.
                - 모든 스토리에 같은 제목을 반복하지 마세요.

                ## 6. 출력 전 검증

                최종 결과를 생성하기 전에 다음 사항을 확인하세요.

                - 읽는 사람이 쉽게 자신을 대입할 수 있는가?
                - 실제로 친구들과 이야기하고 싶을 만큼 흥미로운가?
                - 의견이 다른 사람끼리 서로의 이유를 설명할 수 있는가?
                - 너무 뻔하거나 한두 마디로 끝나는 질문은 아닌가?
                - 네 단계에서 새로운 정보와 관점이 자연스럽게 공개되는가?
                - 불필요한 감정 해설이나 억지 타협이 없는가?
                - 마지막 질문이 결론을 강요하지 않는가?

                조건을 충족하지 않으면 수정한 뒤 최종 결과만 출력하세요.

                ## 7. JSON 출력 형식

                다음 구조의 유효한 JSON 객체 하나만 반환하세요.
                ```json
                {
                  "stageTitle": "1단계 제목",
                  "scenario": "1단계 상황",
                  "mainQuestion": "1단계 질문",
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

                `questions`에는 정확히 3개 객체를 포함하세요.

                JSON 외의 설명, 해설, 번호, 정답, 선택지 또는 마크다운 코드 블록은 출력하지 마세요.
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
