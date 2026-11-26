package com.seecha.chat;

import java.util.regex.Pattern;

public final class TopicRules {
    private static final Pattern EXCLUDED_SCENARIO = Pattern.compile(
            "팀\\s*(?:플|프로젝트|과제)|조별\\s*(?:과제|활동|프로젝트)|마감.{0,20}(?:갈등|임박|직전)"
                    + "|토론해\\s*봅시다|(?:옳은지|할지).{0,60}(?:아니면|또는)");
    private static final Pattern GENERIC_QUESTION = Pattern.compile(
            "(?:(?:당신(?:이라면|은)|이\\s*상황에서)\\s*){0,2}"
                    + "(?:어떻게\\s*(?:행동하|하|대처하|대응하)|어떤\\s*(?:선택을\\s*하|결정을\\s*내리))"
                    + "(?:겠어요|시겠습니까|겠습니까|실\\s*건가요|고\\s*싶나요|나요|요)[?？]");

    private TopicRules() {}

    public static boolean eligible(String storedScenario) {
        String[] topic = storedScenario.split("\\R\\s*Q\\.\\s*", 2);
        if (topic.length == 1) {
            topic = storedScenario.split("(?<=[.!。])\\s+(?=[^.!?]+[?？]\\s*$)", 2);
        }
        return topic.length == 2 && !EXCLUDED_SCENARIO.matcher(topic[0]).find()
                && !GENERIC_QUESTION.matcher(topic[1].strip()).matches();
    }
}
