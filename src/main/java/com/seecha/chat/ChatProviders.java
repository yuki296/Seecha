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
            return response.getAsJsonArray("results").get(0).getAsJsonObject().get("flagged").getAsBoolean()
                    ? Decision.BLOCKED : Decision.ALLOWED;
        } catch (IOException | RuntimeException exception) {
            return Decision.UNAVAILABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Decision.UNAVAILABLE;
        } finally {
            CHECKS.release();
        }
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
                시차(seecha)의 성인 익명 그룹 채팅에서 사용할 **한국어 상황판단형 대화 주제 1개**를 생성하세요.

                **지정 주제: %s**

                시차는 서로 다른 배경의 사람들이 동일한 상황에서 어떤 행동을 선택하고, 왜 그렇게 판단하는지 자유롭게 이야기하는 서비스입니다.

                상황판단검사(SJT)의 형식을 참고하되, 참여자의 성격이나 판단 능력을 평가하거나 특정 정답과 합의를 요구하지 마세요.

                ## 1. 주제 및 소재 선정

                지정 주제에 어울리는 자연스러운 인물, 관계, 장소를 활용하세요.

                다음은 소재의 방향을 보여주는 예시입니다. 예시를 그대로 복제하지 말고 새로운 장면과 갈등을 만드세요.

                - **우정:** 친구 사이의 신뢰, 솔직함, 배려, 금전 문제, 관계의 거리감
                - **가족:** 가족의 기대와 개인의 자유, 사생활, 책임, 생활 방식의 차이
                - **연애:** 연인 사이의 신뢰, 개인의 시간, 소비 방식, 이성 친구, 사생활
                - **이웃·일상:** 공공장소에서의 배려, 생활 규칙, 이웃 간 갈등, 공동 공간, 취미 모임
                - **일:** 업무 역량, 직장 내 인간관계, 공정한 평가, 책임, 고객 응대, 조직문화

                회사나 업무를 배경으로 한 상황은 '일' 주제에서만 작성하세요.

                학교 팀 프로젝트, 팀플 및 마감 갈등을 모든 주제에서 제외하세요.

                같은 소재나 갈등 구조를 반복하지 말고, 인물의 관계와 사건, 선택의 결과가 기존 질문과 구별되는 새로운 상황을 만드세요.

                ## 2. 상황 작성 규칙 — scenario

                - 공백 포함 **100~160자 내외**, 최대 3문장으로 작성하세요.
                - 첫 문장에서 인물과 배경을 소개하고, 이후 핵심 사건과 갈등을 설명하세요.
                - 한 문장은 가급적 60자를 넘기지 마세요.
                - 주요 인물은 2~3명 이내로 제한하세요.
                - 한 상황에는 하나의 핵심 갈등만 담으세요.
                - 인물 간 관계가 복잡해지지 않도록 누구의 행동인지 명확하게 작성하세요.
                - 현실에서 충분히 발생할 법한 구체적인 상황이어야 합니다.
                - 신뢰, 배려, 책임, 공정성, 개인의 이익 등 서로 충돌하는 가치가 자연스럽게 드러나야 합니다.
                - 여러 행동에 각각 납득할 만한 이유가 존재해야 합니다.
                - 어느 한쪽이 명백히 잘못했거나 특정 행동만 정답으로 보이는 상황은 피하세요.
                - 상황 설명에 불필요한 감정 해설이나 교훈을 넣지 마세요.
                - 마지막 문장에서 해결 방법이나 선택지를 제시하지 마세요.
                - 상황과 메인 질문만 읽어도 바로 대화를 시작할 수 있도록 작성하세요.

                **피해야 할 표현**

                - "어떤 결정을 내려야 할지 고민이 깊어집니다."
                - "두 사람 모두에게 상처를 주지 않는 방법을 찾아야 합니다."
                - "계속 함께할지, 관계를 끊을지 선택해야 합니다."
                - "공정성과 인간관계 사이에서 갈등하고 있습니다."

                감정을 직접 해설하거나 선택을 양자택일로 제한하지 말고, 구체적인 사건을 통해 갈등을 보여주세요.

                ## 3. 메인 질문 작성 규칙 — mainQuestion

                메인 질문은 **상황의 핵심 갈등을 반영한 구체적이고 열린 행동 질문**으로 작성하세요.

                - 참여자가 실제로 어떤 행동을 할지 묻는 한 문장으로 작성하세요.
                - 상황에 등장하는 인물, 관계 또는 갈등의 대상을 자연스럽게 포함하세요.
                - 질문만 읽어도 어떤 상황에 대한 이야기인지 어느 정도 파악할 수 있어야 합니다.
                - 특정 해결 방법을 전제하거나 유도하지 마세요.
                - 두 가지 선택지만 제시하는 양자택일 질문을 피하세요.
                - 참여자가 자신만의 행동이나 해결 방법을 자유롭게 제안할 수 있도록 작성하세요.
                - 단순히 옳고 그름이나 찬반을 묻지 마세요.
                - 구체적이면서도 간결한 존댓말을 사용하세요.
                - 질문이 불필요하게 길어지지 않도록 작성하세요.

                **사용하지 말아야 할 질문**

                - "당신이라면 어떻게 하시겠습니까?"
                - "당신은 어떤 선택을 내리겠습니까?"
                - "어떤 결정이 가장 옳다고 생각하시나요?"
                - "이 상황에서 어떻게 하는 것이 바람직할까요?"
                - "친구를 도울까요, 아니면 거절할까요?"

                **좋은 메인 질문 예시**

                - 일: "당신이 팀장이라면 이 직원의 업무 문제를 어떻게 처리하겠어요?"
                - 우정: "친구가 다른 사람의 비밀을 알려달라고 한다면 어떻게 대응하겠어요?"
                - 가족: "가족이 자신의 사생활에 계속 관여한다면 어떤 방식으로 이야기하겠어요?"
                - 연애: "연인과 소비 방식이 다를 때 데이트 비용을 어떻게 조율하겠어요?"
                - 이웃·일상: "영화관에서 다른 사람이 좌석 교환을 부탁한다면 어떻게 대응하겠어요?"

                위 예시의 문장을 그대로 반복하지 말고, 생성한 상황에 맞는 새로운 질문을 작성하세요.

                ## 4. 보조질문 작성 규칙 — questions

                보조질문은 정확히 **3개** 작성하세요.

                메인 질문 이후 하나씩 공개되며, 참여자가 기존 판단을 다른 관점에서 다시 생각하도록 만드는 역할입니다.

                - 각 보조질문은 새로운 정보, 조건 또는 입장을 하나씩 추가하세요.
                - 세 질문은 서로 다른 방향으로 사고를 확장해야 합니다.
                - 첫 번째 보조질문에 대한 답변이 두 번째 또는 세 번째 질문의 전제가 되어서는 안 됩니다.
                - 메인 질문을 단순히 다른 말로 반복하지 마세요.
                - 각각 한 문장으로 간결하게 작성하세요.
                - 특정 선택을 정답으로 유도하지 마세요.
                - 가정이 추가되었을 때 참여자가 실제로 어떻게 행동할지를 묻는 질문을 우선하세요.

                보조질문은 가능하면 다음 관점을 각각 활용하세요.

                1. **관계의 변화:** 상대방과의 친밀도, 신뢰 또는 관계가 달라지는 경우
                2. **상황의 변화:** 비용, 시간, 책임 또는 새로운 사실이 추가되는 경우
                3. **입장의 변화:** 다른 이해관계자의 입장이나 선택에 따른 영향이 드러나는 경우

                단, 위 순서는 필수가 아니며 상황에 적합한 서로 다른 관점으로 구성하세요.

                ## 5. 문체 및 가독성

                - 친구에게 실제 상황을 설명하듯 자연스럽고 명확한 한국어를 사용하세요.
                - 모든 문장은 존댓말로 작성하세요.
                - 불필요한 수식어와 장황한 배경 설명을 제거하세요.
                - '파트너', '상대방', '그 사람'처럼 지칭 대상이 모호한 표현을 피하세요.
                - 비슷한 표현이나 동일한 사건을 반복해서 설명하지 마세요.
                - 상황과 질문에서 도덕적 판단을 강요하지 마세요.
                - 참여자가 자신의 실제 경험이나 민감한 개인정보를 공개하지 않아도 대화할 수 있도록 작성하세요.
                - 단순 취향 선택, 뻔한 도덕 문제, 극단적인 사건, 과도하게 자극적인 상황은 피하세요.

                ## 6. 출력 전 자체 검증

                최종 결과를 출력하기 전에 다음 사항을 확인하세요.

                - 지정 주제에 맞는 상황인가?
                - 상황이 100~160자 내외이며 최대 3문장인가?
                - 인물 관계와 핵심 갈등을 쉽게 이해할 수 있는가?
                - 한쪽 선택만 명백하게 옳은 상황은 아닌가?
                - 상황 설명이 해결 방법을 미리 제시하지 않는가?
                - 메인 질문이 해당 상황에 구체적으로 연결되는가?
                - 메인 질문이 추상적이거나 양자택일을 강요하지 않는가?
                - 보조질문이 정확히 3개인가?
                - 보조질문 각각이 새로운 조건과 관점을 제시하는가?
                - 질문들이 서로 중복되거나 앞선 답변을 전제하지 않는가?

                조건을 충족하지 않는 경우 내부적으로 수정한 뒤 최종 결과만 출력하세요.

                ## 7. 출력 형식

                반드시 아래 구조의 **유효한 JSON 객체 하나만** 반환하세요.
                ```json
                {
                  "scenario": "100~160자 내외의 구체적인 상황 설명",
                  "mainQuestion": "핵심 갈등과 연결된 구체적인 행동 질문",
                  "questions": [
                    "첫 번째 보조질문",
                    "두 번째 보조질문",
                    "세 번째 보조질문"
                  ]
                }
                ```

                JSON 외의 설명, 제목, 번호, 해설, 정답, 선택지 또는 마크다운 코드 블록은 출력하지 마세요.
                """.formatted(category);
        var string = Map.of("type", "string");
        var schema = Map.of("type", "object", "required", List.of("scenario", "mainQuestion", "questions"),
                "properties", Map.of("scenario", string, "mainQuestion", string, "questions",
                        Map.of("type", "array", "items", string, "minItems", 3, "maxItems", 3)));
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
        List<String> topic = new ArrayList<>();
        // The prompt targets about 100–160 characters, with room for a small overrun.
        String scenario = validate(result.get("scenario"), 180);
        String mainQuestion = validate(result.get("mainQuestion"), 145);
        topic.add(scenario + "\\n\\nQ. " + mainQuestion);
        List<String> allQuestions = new ArrayList<>(4);
        allQuestions.add(mainQuestion);
        for (var question : questions) {
            allQuestions.add(validate(question, 300));
        }
        if (allQuestions.stream().distinct().count() != 4) {
            throw new IOException("Duplicate topic text");
        }
        topic.addAll(allQuestions.subList(1, 4));
        return new Topic(category, List.copyOf(topic));
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
    public record Topic(String category, List<String> text) {}
}
