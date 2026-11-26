package com.seecha.dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TopicDao {
    private final MemberDao database = new MemberDao();

    public List<List<String>> recent() throws SQLException {
        String sql = """
                SELECT scenario, question_one, question_two, question_three
                FROM chat_topics ORDER BY topic_id DESC LIMIT 30
                """;
        return load(sql);
    }

    public List<List<String>> installManualTopics() throws IOException, SQLException {
        try (var seed = TopicDao.class.getResourceAsStream("/manual-topics.sql")) {
            if (seed == null) {
                throw new IOException("Manual topic SQL is missing");
            }
            try (var connection = database.connect(); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS manual_chat_topics LIKE chat_topics");
                statement.executeUpdate(new String(seed.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return load("SELECT scenario, question_one, question_two, question_three FROM manual_chat_topics");
    }

    private List<List<String>> load(String sql) throws SQLException {
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql);
                var rows = statement.executeQuery()) {
            List<List<String>> topics = new ArrayList<>();
            while (rows.next()) {
                topics.add(List.of(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)));
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

    public void save(String category, List<String> topic) throws SQLException {
        String sql = """
                INSERT INTO chat_topics (category, scenario, question_one, question_two, question_three)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE topic_id = topic_id
                """;
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            for (int index = 0; index < topic.size(); index++) {
                statement.setString(index + 2, topic.get(index));
            }
            statement.executeUpdate();
        }
    }
}
