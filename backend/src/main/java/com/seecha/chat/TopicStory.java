package com.seecha.chat;

import java.util.List;
import java.util.stream.IntStream;

public record TopicStory(String opening, String category, String title, String openingTitle, List<Stage> stages) {
    public TopicStory {
        stages = List.copyOf(stages);
    }

    public TopicStory(String opening, List<Stage> stages) {
        this(opening, "", "", "", stages);
    }

    public static TopicStory legacy(List<String> text) {
        return new TopicStory(text.get(0), IntStream.range(1, text.size())
                .mapToObj(index -> new Stage("", text.get(index))).toList());
    }

    public record Stage(String scenario, String question, String title) {
        public Stage(String scenario, String question) {
            this(scenario, question, "");
        }
    }
}
