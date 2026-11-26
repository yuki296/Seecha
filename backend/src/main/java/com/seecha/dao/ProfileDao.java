package com.seecha.dao;

import java.sql.Connection;
import java.sql.SQLException;

public final class ProfileDao {
    private final MemberDao database = new MemberDao();

    public Profile find(long memberId) throws SQLException {
        String sql = "SELECT age_group, childhood_region, life_stage, mbti, field FROM member_profiles WHERE member_id = ?";
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? new Profile(rows.getString(1), rows.getString(2), rows.getString(3),
                        rows.getString(4), rows.getString(5)) : null;
            }
        }
    }

    public void save(long memberId, Profile profile) throws SQLException {
        try (var connection = database.connect()) {
            save(connection, memberId, profile);
        }
    }

    static void save(Connection connection, long memberId, Profile profile) throws SQLException {
        String sql = """
                INSERT INTO member_profiles (member_id, age_group, childhood_region, life_stage, mbti, field)
                VALUES (?, ?, ?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE age_group = incoming.age_group, childhood_region = incoming.childhood_region,
                    life_stage = incoming.life_stage, mbti = incoming.mbti, field = incoming.field
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberId);
            statement.setString(2, profile.ageGroup());
            statement.setString(3, profile.childhoodRegion());
            statement.setString(4, profile.lifeStage());
            statement.setString(5, profile.mbti());
            statement.setString(6, profile.field());
            statement.executeUpdate();
        }
    }

    public record Profile(String ageGroup, String childhoodRegion, String lifeStage, String mbti, String field) {}
}
