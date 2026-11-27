package com.seecha.dao;

import com.seecha.chat.TopicStory;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TopicDao {
    private final MemberDao database = new MemberDao();

    public List<TopicStory> recent() throws SQLException {
        String sql = """
                SELECT scenario, question_one, question_two, question_three, follow_up_scenario_one, follow_up_scenario_two, follow_up_scenario_three,
                       category, story_title, COALESCE(stage_title_one, opening_title),
                       COALESCE(stage_title_two, follow_up_title_one), COALESCE(stage_title_three, follow_up_title_two),
                       COALESCE(stage_title_four, follow_up_title_three)
                FROM chat_topics ORDER BY topic_id DESC LIMIT 30
                """;
        return load(sql);
    }

    public List<TopicStory> manualTopics() throws SQLException {
        return load("""
                SELECT scenario, question_one, question_two, question_three, follow_up_scenario_one, follow_up_scenario_two, follow_up_scenario_three, category, story_title, COALESCE(stage_title_one, opening_title),
                       COALESCE(stage_title_two, follow_up_title_one), COALESCE(stage_title_three, follow_up_title_two),
                       COALESCE(stage_title_four, follow_up_title_three)
                FROM manual_chat_topics
                """);
    }

    private List<TopicStory> load(String sql) throws SQLException {
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql);
                var rows = statement.executeQuery()) {
            List<TopicStory> topics = new ArrayList<>();
            while (rows.next()) {
                var stages = new ArrayList<TopicStory.Stage>(3);
                for (int index = 0; index < 3; index++) {
                    stages.add(new TopicStory.Stage(java.util.Objects.toString(rows.getString(index + 5), ""),
                            rows.getString(index + 2), java.util.Objects.toString(rows.getString(index + 11), "")));
                }
                topics.add(new TopicStory(rows.getString(1), rows.getString(8),
                        java.util.Objects.toString(rows.getString(9), ""),
                        java.util.Objects.toString(rows.getString(10), ""), stages));
            }
            return List.copyOf(topics);
        }
    }

    public List<Map<String, String>> search(String keyword, String category) throws SQLException {
        String sql = """
                SELECT category, scenario FROM chat_topics
                WHERE (? = '' OR category = ?) AND (? = '' OR LOCATE(?, scenario) > 0)
                ORDER BY topic_id DESC LIMIT 50
                """;
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            statement.setString(2, category);
            statement.setString(3, keyword);
            statement.setString(4, keyword);
            try (var rows = statement.executeQuery()) {
                List<Map<String, String>> results = new ArrayList<>();
                while (rows.next()) {
                    results.add(Map.of("category", rows.getString(1), "scenario", rows.getString(2)));
                }
                return List.copyOf(results);
            }
        }
    }

    public void save(String category, TopicStory topic) throws SQLException {
        String sql = """
                INSERT INTO chat_topics (category, scenario, question_one, question_two, question_three,
                                         follow_up_scenario_one, follow_up_scenario_two, follow_up_scenario_three,
                                         story_title, stage_title_one, stage_title_two, stage_title_three, stage_title_four)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''))
                ON DUPLICATE KEY UPDATE topic_id = topic_id
                """;
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            statement.setString(2, topic.opening());
            statement.setString(9, topic.title());
            statement.setString(10, topic.openingTitle());
            for (int index = 0; index < topic.stages().size(); index++) {
                var stage = topic.stages().get(index);
                statement.setString(index + 3, stage.question());
                statement.setString(index + 6, stage.scenario());
                statement.setString(index + 11, stage.title());
            }
            statement.executeUpdate();
        }
    }
}
