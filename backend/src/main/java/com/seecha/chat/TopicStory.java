package com.seecha.chat;

import java.util.List;
import java.util.stream.IntStream;

public record TopicStory(String opening, List<Stage> stages) {
    public TopicStory {
        stages = List.copyOf(stages);
    }

    public static TopicStory legacy(List<String> text) {
        return new TopicStory(text.get(0), IntStream.range(1, text.size())
                .mapToObj(index -> new Stage("", text.get(index))).toList());
    }

    public record Stage(String scenario, String question) {}
}
