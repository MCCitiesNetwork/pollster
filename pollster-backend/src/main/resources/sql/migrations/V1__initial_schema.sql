CREATE TABLE IF NOT EXISTS pollster_survey_state (
  survey_id     VARCHAR(48) PRIMARY KEY,
  content_hash  CHAR(64)    NOT NULL,
  opened_at     TIMESTAMP   NOT NULL,
  closed_at     TIMESTAMP   NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pollster_responses (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  survey_id     VARCHAR(48) NOT NULL,
  player_uuid   BINARY(16)  NOT NULL,
  player_name   VARCHAR(16) NOT NULL,
  started_at    TIMESTAMP   NOT NULL,
  submitted_at  TIMESTAMP   NOT NULL,
  UNIQUE KEY uq_response (survey_id, player_uuid),
  KEY ix_survey (survey_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pollster_answers (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  response_id   BIGINT       NOT NULL,
  question_key  VARCHAR(48)  NOT NULL,
  option_key    VARCHAR(48)  NULL,
  text_value    TEXT         NULL,
  FOREIGN KEY (response_id) REFERENCES pollster_responses(id) ON DELETE CASCADE,
  KEY ix_question (response_id, question_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pollster_player_state (
  player_uuid       BINARY(16) PRIMARY KEY,
  last_featured_at  TIMESTAMP  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
