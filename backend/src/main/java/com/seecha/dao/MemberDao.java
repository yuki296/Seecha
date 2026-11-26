package com.seecha.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import com.seecha.dao.ProfileDao.Profile;

public final class MemberDao {
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final String url = System.getenv().getOrDefault(
            "SEECHA_DB_URL", "jdbc:mysql://127.0.0.1:3306/seecha");
    private final String user = System.getenv().getOrDefault("SEECHA_DB_USER", "seecha_app");
    private final String password = System.getenv("SEECHA_DB_PASSWORD");

    Connection connect() throws SQLException {
        if (password == null || password.isBlank()) {
            throw new SQLException("SEECHA_DB_PASSWORD is missing");
        }
        return DriverManager.getConnection(url, user, password);
    }

    public RegistrationResult create(String username, String passwordHash, String nickname, Profile profile) throws SQLException {
        String sql = "INSERT INTO members (username, password_hash, nickname, adult_confirmed) VALUES (?, ?, ?, TRUE)";
        try (var connection = connect()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, username);
                statement.setString(2, passwordHash);
                statement.setString(3, nickname);
                try {
                    statement.executeUpdate();
                } catch (SQLIntegrityConstraintViolationException exception) {
                    if (exception.getErrorCode() == 1062) {
                        connection.rollback();
                        return duplicateNickname(exception) ? RegistrationResult.NICKNAME_TAKEN : RegistrationResult.USERNAME_TAKEN;
                    }
                    throw exception;
                }
                try (var keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Missing member ID after insert");
                    }
                    ProfileDao.save(connection, keys.getLong(1), profile);
                }
                connection.commit();
                return RegistrationResult.CREATED;
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        }
    }

    public Account findByUsername(String username) throws SQLException {
        String sql = "SELECT member_id, username, password_hash FROM members WHERE username = ?";
        try (var connection = connect(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? new Account(rows.getLong(1), rows.getString(2), rows.getString(3)) : null;
            }
        }
    }

    public String nickname(long memberId) throws SQLException {
        try (var connection = connect();
                var statement = connection.prepareStatement("SELECT nickname FROM members WHERE member_id = ?")) {
            statement.setLong(1, memberId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    public boolean updateNickname(long memberId, String nickname) throws SQLException {
        try (var connection = connect();
                var statement = connection.prepareStatement("UPDATE members SET nickname = ? WHERE member_id = ?")) {
            statement.setString(1, nickname);
            statement.setLong(2, memberId);
            statement.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException exception) {
            if (duplicateNickname(exception)) {
                return false;
            }
            throw exception;
        }
    }

    private static boolean duplicateNickname(SQLException exception) {
        return exception.getErrorCode() == 1062 && exception.getMessage().contains("uq_members_nickname");
    }

    public enum RegistrationResult { CREATED, USERNAME_TAKEN, NICKNAME_TAKEN }

    public record Account(long id, String username, String passwordHash) {}
}
