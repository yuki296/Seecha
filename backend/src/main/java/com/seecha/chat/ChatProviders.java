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

                시차는 서로 다른 환경과 경험을 가진 사람들이 같은 상황을 어떻게 받아들이는지 이야기하고, 각자의 선택과 판단에 담긴 가치관을 이해하는 서비스입니다.

                정답을 찾거나 상대방을 설득하는 것이 목적은 아닙니다. 서로 다른 의견이 나오는 것 자체가 의미 있으며, 끝까지 생각이 달라도 괜찮습니다.

                ## 1. 소재 선정

                **실제로 겪어봤거나, 겪어보지 않았더라도 쉽게 자신을 대입할 수 있는 흥미로운 상황을 생성하세요.**

                특정 분야에만 집중하지 말고 다양한 인간관계, 일상생활, 사회적 이슈에서 소재를 선정하세요.

                ### 연애·썸·인간관계

                - 연인의 외도와 바람의 기준
                - 전 연인과 이성 친구의 경계
                - 이별과 거짓말, 관계에서의 신뢰
                - 연애 중 사생활과 연락 문제
                - 결혼관과 경제관념의 차이
                - 친구 사이의 의리와 배신

                ### 팀플·협업

                - 무임승차와 불공정한 평가
                - 실력이 뛰어나지만 태도가 나쁜 팀원
                - 성실하지만 성과가 부족한 팀원
                - 친분에 따른 편애와 보상
                - 리더의 책임과 개인의 희생
                - 성과와 과정 중 무엇을 평가해야 하는가

                ### 가족·경제생활

                - 부모 부양과 자녀의 경제적 책임
                - 가족의 기대와 개인의 자유
                - 부모의 간섭과 사생활
                - 무료로 나눔받은 물건의 재판매
                - 친구나 가족 사이의 돈거래
                - 경제적 형편에 따라 달라지는 선택

                ### 교육·공정성

                - 성적과 경제적 상황 중 장학금 선정 기준
                - 노력과 결과 중 무엇을 평가해야 하는가
                - 서로 다른 환경에서 출발한 사람들의 경쟁
                - 규칙의 예외와 기회의 평등
                - 능력과 운, 환경에 대한 판단

                ### SNS·사회·일상

                - 선행을 촬영해 SNS에 공개하는 행동
                - 노키즈존과 시설 이용의 자유
                - 공공장소에서의 개인의 권리와 타인의 불편
                - 개인정보와 공동체의 안전
                - 잘못한 사람의 신상 공개
                - AI 활용과 창작의 기준
                - 소비자의 권리와 판매자의 책임

                위 목록은 예시이며, 같은 소재를 반복하지 말고 새로운 상황도 적극적으로 발굴하세요.

                연애와 팀플처럼 직접 몰입하기 쉬운 소재를 충분히 포함하되, 가족·경제·교육·사회적 윤리 딜레마도 중요한 소재로 취급하세요.

                ## 2. 흥미로운 질문을 만드는 기준

                다음 세 유형을 폭넓게 활용하세요.

                **현실 공감형**

                - 실제로 경험했거나 주변에서 흔히 들을 만한 사건
                - 개인의 경험에 따라 판단이 달라지는 상황

                **밸런스 게임형**

                - 서로 다른 가치가 충돌하여 쉽게 선택하기 어려운 상황
                - 한쪽만 지나치게 유리하거나 명백한 정답이 없는 문제

                **상황판단검사(SJT)형**

                - 구체적인 사건에서 어떤 판단이나 행동을 할지 고민하게 만드는 상황
                - 선택의 결과보다 그 선택을 내린 이유와 기준이 드러나는 문제

                실제 기업의 인적성검사 문제를 복제하지 말고 상황판단형 질문의 특징만 참고하세요.

                모든 이야기를 심각하거나 무겁게 만들 필요는 없습니다. 가볍게 시작하지만 생각할수록 의견이 갈리는 상황도 좋습니다.

                명백히 잘못된 행동이 등장해도 괜찮습니다. 다만 잘못을 무조건 정당화하지 말고, 용서할 수 있는 기준이나 대응 방식에서 서로 다른 판단이 나올 수 있도록 하세요.

                ## 3. 4단계 스토리 구성

                **1단계 — 사건의 시작**

                - 참여자의 관심을 끌 만한 구체적인 사건을 제시하세요.
                - 첫 상황만으로 자신의 의견을 이야기할 수 있어야 합니다.
                - 모든 인물의 사정이나 사건의 전말을 공개하지 마세요.

                **2단계 — 새로운 시선**

                - 처음에는 알지 못했던 사정이나 행동의 이유를 공개하세요.
                - 기존의 판단과 다른 관점을 생각할 수 있도록 하세요.
                - 새로운 정보가 반드시 해당 행동을 정당화할 필요는 없습니다.

                **3단계 — 다른 가치관**

                - 또 다른 인물의 입장, 새로운 조건 또는 충돌하는 가치를 제시하세요.
                - 2단계와 비슷한 사정만 반복하지 마세요.
                - 여러 사람이 서로 다르게 판단할 이유가 드러나야 합니다.

                **4단계 — 확장된 시선**

                - 앞선 이야기와 자연스럽게 연결되는 새로운 사실이나 조건을 제시하세요.
                - 참여자가 자신의 판단 기준을 더 깊게 이야기할 수 있도록 하세요.
                - 반드시 결론을 내리거나 문제를 해결하도록 요구하지 마세요.
                - 모든 사람을 만족시키는 타협안을 찾게 하지 마세요.
                - 기존 생각을 유지하거나 변경하는 것 모두 허용하세요.

                각 단계는 자연스럽게 연결되어야 하지만 모든 스토리가 동일한 전개를 따를 필요는 없습니다.

                ## 4. 상황 작성 규칙

                - 각 단계의 상황 설명은 80~150자 내외, 최대 3문장으로 작성하세요.
                - 실제 친구에게 이야기를 들려주듯 쉽고 자연스럽게 작성하세요.
                - 인물 간 관계와 사건의 핵심을 명확하게 표현하세요.
                - 불필요한 감정 묘사와 장황한 설명을 제거하세요.
                - 참여자가 실제로 고민할 만한 구체적인 갈등을 제시하세요.
                - 지나치게 뻔하거나 간단한 요청만으로 끝나는 사건은 피하세요.
                - 특정 인물을 무조건 착하거나 나쁘게 묘사하지 마세요.
                - 앞선 사실을 부정하는 억지 반전을 만들지 마세요.
                - 상황 설명에서 해결책이나 정답을 제시하지 마세요.
                - 참여자에게 특정 행동이나 타협안을 마련할 의무를 부여하지 마세요.

                ## 5. 질문 작성 규칙

                각 단계마다 해당 상황에 연결되는 질문을 정확히 1개 생성하세요.

                - 구체적인 사건이나 인물의 행동을 직접 언급하세요.
                - 자연스럽고 간결한 한국어 존댓말을 사용하세요.
                - 행동, 감정, 가치관, 판단 기준, 용서, 공정성 등을 다양하게 물어보세요.
                - 모든 질문을 해결책 제시형으로 만들지 마세요.
                - 같은 질문 구조를 반복하지 마세요.
                - 특정 선택이 정답이라는 전제를 넣지 마세요.
                - 앞선 단계에서 참여자가 어떤 답을 했는지 가정하지 마세요.

                **좋은 질문 예시**

                - "무료로 받은 물건을 되파는 행동을 어떻게 생각하나요?"
                - "부모님의 생활비를 자녀가 지원하는 것은 당연한 책임이라고 생각하나요?"
                - "성적이 조금 낮더라도 경제적으로 어려운 학생에게 장학금을 주는 것이 더 공정할까요?"
                - "좋은 의도로 촬영한 영상이라도 당사자가 원하지 않으면 삭제해야 한다고 생각하나요?"
                - "연인의 외도를 용서할 수 있는 기준이 있나요?"
                - "팀에서 가장 큰 성과를 낸 사람에게 가장 높은 평가를 주는 것이 항상 공정할까요?"

                위 질문은 문체와 방향을 보여주는 예시이며 그대로 반복하지 마세요.

                **피해야 할 표현**

                - "이 문제를 어떤 방식으로 풀어나가고 싶으신가요?"
                - "모두가 만족할 현실적인 대안을 마련하세요."
                - "갈등을 조율하기 위해 어떤 방안을 제시하시겠습니까?"
                - "신뢰와 책임, 공정성 사이에서 어떤 선택을 하시겠습니까?"
                - "가장 바람직한 해결책은 무엇일까요?"

                특히 마지막 단계에서는 반드시 결론을 요구하지 말고, 여러 관점을 접한 상태에서 자신의 생각을 자유롭게 이야기할 수 있도록 하세요.

                ## 6. 단계별 제목

                각 단계에 스토리 내용과 어울리는 제목을 생성하세요.

                - 제목은 4~15자 내외로 작성하세요.
                - 해당 단계의 핵심 사건이나 관점을 표현하세요.
                - 다음 단계의 내용을 미리 공개하지 마세요.
                - 모든 스토리에 동일한 제목을 반복하지 마세요.
                - '새로운 시선', '확장된 판단' 같은 형식적인 제목은 피하세요.

                ## 7. 출력 전 검증

                최종 결과를 생성하기 전에 다음 사항을 확인하세요.

                - 실제로 다른 사람들과 이야기하고 싶을 만큼 흥미로운가?
                - 참여자가 쉽게 자신을 상황에 대입할 수 있는가?
                - 소재가 지나치게 뻔하거나 반복적이지 않은가?
                - 4단계 모두 서로 다른 이야깃거리를 제공하는가?
                - 누군가의 행동을 억지로 정당화하지 않는가?
                - 모든 질문이 문제 해결을 요구하고 있지는 않은가?
                - 사람마다 판단과 그 이유가 달라질 수 있는가?
                - 마지막 질문이 정답이나 타협을 강요하지 않는가?

                조건을 충족하지 않으면 내부적으로 수정한 뒤 최종 결과만 출력하세요.

                ## 8. JSON 출력 형식

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
