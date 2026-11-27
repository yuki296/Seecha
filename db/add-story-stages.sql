USE seecha;

SET @seecha_story_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'chat_topics' AND column_name = 'follow_up_scenario_one'),
    'ALTER TABLE chat_topics ADD COLUMN follow_up_scenario_one VARCHAR(180) NULL',
    'DO 0'
);
PREPARE seecha_story_statement FROM @seecha_story_ddl;
EXECUTE seecha_story_statement;
DEALLOCATE PREPARE seecha_story_statement;

SET @seecha_story_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'chat_topics' AND column_name = 'follow_up_scenario_two'),
    'ALTER TABLE chat_topics ADD COLUMN follow_up_scenario_two VARCHAR(180) NULL',
    'DO 0'
);
PREPARE seecha_story_statement FROM @seecha_story_ddl;
EXECUTE seecha_story_statement;
DEALLOCATE PREPARE seecha_story_statement;

SET @seecha_story_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'chat_topics' AND column_name = 'follow_up_scenario_three'),
    'ALTER TABLE chat_topics ADD COLUMN follow_up_scenario_three VARCHAR(180) NULL',
    'DO 0'
);
PREPARE seecha_story_statement FROM @seecha_story_ddl;
EXECUTE seecha_story_statement;
DEALLOCATE PREPARE seecha_story_statement;

SET @seecha_story_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics' AND column_name = 'follow_up_scenario_one'),
    'ALTER TABLE manual_chat_topics ADD COLUMN follow_up_scenario_one VARCHAR(180) NULL',
    'DO 0'
);
PREPARE seecha_story_statement FROM @seecha_story_ddl;
EXECUTE seecha_story_statement;
DEALLOCATE PREPARE seecha_story_statement;

SET @seecha_story_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics' AND column_name = 'follow_up_scenario_two'),
    'ALTER TABLE manual_chat_topics ADD COLUMN follow_up_scenario_two VARCHAR(180) NULL',
    'DO 0'
);
PREPARE seecha_story_statement FROM @seecha_story_ddl;
EXECUTE seecha_story_statement;
DEALLOCATE PREPARE seecha_story_statement;

SET @seecha_story_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics' AND column_name = 'follow_up_scenario_three'),
    'ALTER TABLE manual_chat_topics ADD COLUMN follow_up_scenario_three VARCHAR(180) NULL',
    'DO 0'
);
PREPARE seecha_story_statement FROM @seecha_story_ddl;
EXECUTE seecha_story_statement;
DEALLOCATE PREPARE seecha_story_statement;
