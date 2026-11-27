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
                지정 주제는 ‘%s’입니다.

                시차의 목적은 서로 다른 환경과 경험을 가진 사람들이 같은 상황을 어떻게 받아들이는지 이야기하며, 각자의 판단이 어디에서 비롯됐는지 이해하는 것입니다.

                스토리 구성
                - 하나의 윤리적 딜레마 또는 가치관의 차이가 드러나는 상황을 4단계로 구성하세요.
                - 1단계에서는 사건의 시작과 첫 번째 질문을 제시하세요.
                - 2단계에서는 처음에는 알지 못했던 인물의 사정이나 경험을 공개하세요.
                - 3단계에서는 또 다른 관점, 충돌하는 가치 또는 새로운 정보를 제시하세요.
                - 4단계에서는 여러 시선을 접한 뒤 자신의 기준을 다시 생각할 수 있도록 하세요.
                - 새로운 사실이 기존 사실을 부정하거나 특정 인물을 일방적인 악인으로 만들지 마세요.
                - 정답, 합의, 반드시 생각을 바꿔야 한다는 결론을 요구하지 마세요.

                질문 생성 기준
                - 현실에서 발생할 법한 윤리적 갈등과 사회적 이슈를 우선하고, 교과서적인 상황과 뻔한 해결책은 피하세요.
                - 모든 질문을 ‘어떻게 조율하겠어요?’ 형태로 반복하지 마세요.
                - 행동을 묻는 질문과 가치관·판단 기준을 묻는 질문을 자연스럽게 혼합하세요.
                - 각 단계의 상황은 80~150자 내외, 최대 3문장으로 작성하세요.
                - 각 질문은 해당 상황에 직접 연결되는 자연스러운 존댓말 한 문장으로 작성하세요.
                - 회사나 업무는 ‘일’ 주제에서만 다루고, 학교 팀플과 마감 갈등은 제외하세요.
                - 개인적 상처나 민감한 경험을 공개하지 않아도 대화할 수 있게 작성하세요.

                단계별 제목 생성 기준
                - 4단계 모두 약 4~15자의 간결한 제목을 생성하세요.
                - 각 제목은 해당 단계의 핵심 사건이나 관점을 자연스럽게 표현하세요.
                - 제목만 읽어도 해당 단계의 이야기 소재를 짐작할 수 있게 하되, 다음 단계나 결말을 암시하지 마세요.
                - ‘새로운 시선’, ‘또 다른 시선’, ‘확장된 판단’처럼 추상적인 제목을 반복하지 마세요.
                - 각 제목에는 해당 이야기만의 인물, 사건, 입장 중 하나가 드러나야 합니다.

                다음 JSON 객체 하나만 반환하세요. questions에는 정확히 3개의 객체가 필요합니다.
                {
                  "stageTitle": "1단계 제목",
                  "scenario": "1단계 상황",
                  "mainQuestion": "1단계 질문",
                  "questions": [
                    {"title": "2단계 제목", "scenario": "2단계 추가 상황", "question": "2단계 질문"},
                    {"title": "3단계 제목", "scenario": "3단계 추가 상황", "question": "3단계 질문"},
                    {"title": "4단계 제목", "scenario": "4단계 추가 상황", "question": "4단계 질문"}
                  ]
                }
                JSON 외의 내용은 출력하지 마세요. 마지막 단계 이후 추가 질문이나 설문을 생성하지 마세요.
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
