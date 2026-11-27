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

                시차는 서로 다른 환경과 경험을 가진 사람들이 같은 상황을 바라보며, 각자의 선택과 판단이 어디에서 비롯되는지 이해하는 서비스입니다.

                목적은 정답을 찾거나 합의하는 것이 아니라 **서로 다른 관점을 발견하고 이해하는 것**입니다.

                ### 1. 스토리 구성

                하나의 사건을 총 4단계로 구성하세요.

                **1단계 — 사건의 시작**

                - 현실에서 일어날 법한 구체적인 상황을 제시하세요.
                - 참여자가 처음 느끼는 생각과 행동을 자유롭게 이야기할 수 있어야 합니다.
                - 처음부터 모든 인물의 사정이나 사건의 전말을 공개하지 마세요.

                **2단계 — 새로운 시선**

                - 처음에는 알지 못했던 인물의 환경, 경험 또는 사정을 공개하세요.
                - 기존 상황을 이해하는 데 새로운 관점을 제공해야 합니다.

                **3단계 — 또 다른 시선**

                - 다른 이해관계자의 입장이나 새로운 정보를 공개하세요.
                - 앞서 제시한 시선과는 다른 가치를 생각해 볼 수 있도록 구성하세요.

                **4단계 — 확장된 판단**

                - 지금까지 드러난 여러 입장을 고려할 수 있는 새로운 상황을 제시하세요.
                - 참여자가 자신의 판단을 유지하거나 바꾸거나 발전시킬 수 있어야 합니다.
                - 반드시 타협하거나 하나의 결론에 도달하도록 유도하지 마세요.

                ### 2. 상황 작성 규칙

                - 각 단계의 상황 설명은 80~150자 내외, 최대 3문장으로 작성하세요.
                - 인물 관계를 단순하고 명확하게 유지하세요.
                - 갈등은 신뢰, 공정성, 자유, 책임, 배려 등 서로 다른 가치에서 비롯되어야 합니다.
                - 각 인물의 행동에는 납득할 만한 이유가 있어야 합니다.
                - 새로운 정보가 기존 사실을 뒤집거나 특정 인물을 갑자기 악인으로 만들지 않도록 하세요.
                - 자극적인 반전보다 **미처 생각하지 못했던 타인의 삶과 관점을 발견하는 과정**에 집중하세요.
                - 감정 해설이나 교훈을 길게 설명하지 말고 구체적인 사건으로 표현하세요.
                - 실제 사회적 이슈, 세대 차이, 생활환경의 차이에서 소재를 찾되 특정 집단에 대한 편견을 강화하지 마세요.
                - 회사와 업무를 배경으로 하는 상황은 '일' 주제에서만 사용하세요.
                - 학교 팀플과 마감 갈등은 제외하세요.
                - 뻔한 약속 갈등이나 반복적인 소재보다 새롭고 대화할 거리가 많은 상황을 우선하세요.

                ### 3. 질문 작성 규칙

                - 각 단계마다 질문을 정확히 1개 작성하세요.
                - 질문은 해당 단계의 구체적인 상황과 연결되어야 합니다.
                - '당신이라면 어떻게 하겠어요?'처럼 어디에나 적용되는 추상적인 질문은 피하세요.
                - 참여자가 실제로 어떤 행동을 할지 이야기할 수 있도록 질문하세요.
                - 특정 선택이나 도덕적인 정답을 유도하지 마세요.
                - 질문을 반복하거나 찬반을 강요하지 마세요.
                - 새로운 정보를 접한 후에도 기존 의견을 유지하는 것이 자연스럽도록 구성하세요.
                - 모든 질문은 간결한 한 문장의 자연스러운 존댓말로 작성하세요.

                ### 4. 문체

                친구에게 실제 상황을 설명하듯 쉽고 자연스러운 한국어를 사용하세요.

                불필요한 수식어, 복잡한 인물 관계, 장황한 설명, 과장된 표현을 피하세요.

                참여자가 개인적인 상처나 민감한 경험을 공개하지 않아도 대화할 수 있어야 합니다.

                ### 5. 출력 형식

                반드시 다음 JSON 구조로만 응답하세요.
                ```json
                {
                  "scenario": "1단계 상황",
                  "mainQuestion": "1단계 메인 질문",
                  "questions": [
                    {
                      "scenario": "2단계에서 새롭게 공개되는 상황",
                      "question": "2단계 보조질문"
                    },
                    {
                      "scenario": "3단계에서 새롭게 공개되는 상황",
                      "question": "3단계 보조질문"
                    },
                    {
                      "scenario": "4단계에서 새롭게 공개되는 상황",
                      "question": "4단계 보조질문"
                    }
                  ]
                }
                ```

                JSON 외의 제목, 설명, 해설, 번호, 정답 또는 마크다운 코드 블록을 출력하지 마세요.

                출력 전 전체 스토리가 자연스럽게 연결되는지, 서로 다른 시선이 드러나는지, 특정 결론을 강요하지 않는지 검토하세요.
                """.formatted(category);
        var string = Map.of("type", "string");
        var stage = Map.of("type", "object", "required", List.of("scenario", "question"),
                "properties", Map.of("scenario", string, "question", string));
        var schema = Map.of("type", "object", "required", List.of("scenario", "mainQuestion", "questions"),
                "properties", Map.of("scenario", string, "mainQuestion", string, "questions",
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
        var result = JsonParser.parseString(generated.toString()).getAsJsonObject();
        var questions = result.getAsJsonArray("questions");
        if (questions.size() != 3) {
            throw new IOException("Unexpected Gemini topic shape");
        }
        String scenario = validate(result.get("scenario"), 175);
        String mainQuestion = validate(result.get("mainQuestion"), 145);
        var opening = scenario + "\n\nQ. " + mainQuestion;
        if (!TopicRules.eligible(opening)) {
            throw new IOException("Invalid story opening");
        }
        List<TopicStory.Stage> stages = new ArrayList<>(3);
        var questionTexts = new java.util.HashSet<String>();
        questionTexts.add(mainQuestion);
        for (var item : questions) {
            if (!item.isJsonObject()) {
                throw new IOException("Invalid story stage");
            }
            var followUp = item.getAsJsonObject();
            String stageScenario = validate(followUp.get("scenario"), 175);
            String question = validate(followUp.get("question"), 300);
            if (!TopicRules.eligible(stageScenario + "\n\nQ. " + question) || !questionTexts.add(question)) {
                throw new IOException("Invalid or repeated story question");
            }
            stages.add(new TopicStory.Stage(stageScenario, question));
        }
        return new Topic(category, new TopicStory(opening, stages));
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
