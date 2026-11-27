USE seecha;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'chat_topics' AND column_name = 'story_title'),
    'ALTER TABLE chat_topics ADD COLUMN story_title VARCHAR(100) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'chat_topics' AND column_name = 'opening_title'),
    'ALTER TABLE chat_topics ADD COLUMN opening_title VARCHAR(60) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'chat_topics' AND column_name = 'follow_up_title_one'),
    'ALTER TABLE chat_topics ADD COLUMN follow_up_title_one VARCHAR(60) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'chat_topics' AND column_name = 'follow_up_title_two'),
    'ALTER TABLE chat_topics ADD COLUMN follow_up_title_two VARCHAR(60) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'chat_topics' AND column_name = 'follow_up_title_three'),
    'ALTER TABLE chat_topics ADD COLUMN follow_up_title_three VARCHAR(60) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'manual_chat_topics' AND column_name = 'story_title'),
    'ALTER TABLE manual_chat_topics ADD COLUMN story_title VARCHAR(100) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'manual_chat_topics' AND column_name = 'opening_title'),
    'ALTER TABLE manual_chat_topics ADD COLUMN opening_title VARCHAR(60) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'manual_chat_topics' AND column_name = 'follow_up_title_one'),
    'ALTER TABLE manual_chat_topics ADD COLUMN follow_up_title_one VARCHAR(60) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'manual_chat_topics' AND column_name = 'follow_up_title_two'),
    'ALTER TABLE manual_chat_topics ADD COLUMN follow_up_title_two VARCHAR(60) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;

SET @seecha_title_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'manual_chat_topics')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'manual_chat_topics' AND column_name = 'follow_up_title_three'),
    'ALTER TABLE manual_chat_topics ADD COLUMN follow_up_title_three VARCHAR(60) NULL', 'DO 0');
PREPARE seecha_title_statement FROM @seecha_title_ddl;
EXECUTE seecha_title_statement;
DEALLOCATE PREPARE seecha_title_statement;
