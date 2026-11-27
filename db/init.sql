CREATE DATABASE IF NOT EXISTS seecha
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE seecha;

CREATE TABLE IF NOT EXISTS members (
    member_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(24) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL UNIQUE,
    password_hash VARCHAR(60) CHARACTER SET ascii NOT NULL,
    nickname VARCHAR(16),
    CONSTRAINT uq_members_nickname UNIQUE (nickname),
    adult_confirmed BOOLEAN NOT NULL,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS member_profiles (
    member_id BIGINT UNSIGNED PRIMARY KEY,
    age_group VARCHAR(16) NOT NULL,
    region VARCHAR(16),
    childhood_region VARCHAR(16),
    life_stage VARCHAR(24) NOT NULL,
    mbti VARCHAR(8),
    field VARCHAR(24),
    CONSTRAINT fk_profile_member FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE CASCADE
);

-- Keep legacy residence values separate. Existing members can choose a growth region when editing.
SET @seecha_profile_migration = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'member_profiles'
              AND column_name = 'childhood_region'),
    'DO 0',
    'ALTER TABLE member_profiles MODIFY region VARCHAR(16) NULL, ADD childhood_region VARCHAR(16) NULL'
);
PREPARE seecha_profile_statement FROM @seecha_profile_migration;
EXECUTE seecha_profile_statement;
DEALLOCATE PREPARE seecha_profile_statement;

SET @seecha_nickname_migration = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'nickname'),
    'DO 0', 'ALTER TABLE members ADD nickname VARCHAR(16) NULL'
);
PREPARE seecha_nickname_statement FROM @seecha_nickname_migration;
EXECUTE seecha_nickname_statement;
DEALLOCATE PREPARE seecha_nickname_statement;

SET @seecha_unique_nickname = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'members' AND index_name = 'uq_members_nickname'),
    'DO 0', 'ALTER TABLE members ADD CONSTRAINT uq_members_nickname UNIQUE (nickname)'
);
PREPARE seecha_unique_nickname_statement FROM @seecha_unique_nickname;
EXECUTE seecha_unique_nickname_statement;
DEALLOCATE PREPARE seecha_unique_nickname_statement;

CREATE TABLE IF NOT EXISTS chat_topics (
    topic_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    scenario VARCHAR(500) NOT NULL,
    question_one VARCHAR(300) NOT NULL,
    question_two VARCHAR(300) NOT NULL,
    question_three VARCHAR(300) NOT NULL,
    follow_up_scenario_one VARCHAR(180) NULL,
    follow_up_scenario_two VARCHAR(180) NULL,
    follow_up_scenario_three VARCHAR(180) NULL,
    story_title VARCHAR(100) NULL,
    opening_title VARCHAR(60) NULL,
    follow_up_title_one VARCHAR(60) NULL,
    follow_up_title_two VARCHAR(60) NULL,
    follow_up_title_three VARCHAR(60) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_chat_topic_scenario (scenario),
    KEY ix_chat_topic_category (category)
);

INSERT INTO chat_topics (category, scenario, question_one, question_two, question_three) VALUES
    ('관계', '친구가 중요한 실수를 했다는 걸 알게 됐어요. 당신이라면 친구에게 어떻게 말할 건가요?',
        '다른 사람이 이미 알고 있다면 어떻게 할 건가요?',
        '친구가 먼저 의견을 물으면 어떻게 답할 건가요?',
        '친구가 그 얘기에 상처받을 수 있다면 대화 방식이 달라질까요?'),
    ('학업', '팀 과제 마감이 임박했는데 한 팀원이 맡은 일을 끝내지 못했어요. 당신이라면 어떻게 하겠어요?',
        '그 팀원이 가족 문제를 겪고 있다는 걸 알게 된다면요?',
        '다른 팀원이 기여도를 교수에게 알리자고 한다면요?',
        '다음 과제에서 같은 상황을 피하려면 어떤 약속을 정하고 싶나요?')
ON DUPLICATE KEY UPDATE topic_id = topic_id;

SET @seecha_admin_migration = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'is_admin'),
    'DO 0', 'ALTER TABLE members ADD is_admin BOOLEAN NOT NULL DEFAULT FALSE'
);
PREPARE seecha_admin_statement FROM @seecha_admin_migration;
EXECUTE seecha_admin_statement;
DEALLOCATE PREPARE seecha_admin_statement;
