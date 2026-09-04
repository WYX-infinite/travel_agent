-- Fresh installation only. Do not run after upgrade-from-legacy.sql.
-- Review the target database and take a recoverable backup before execution.

CREATE TABLE `user` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(30) NULL,
    `username` VARCHAR(30) NULL,
    `password` VARCHAR(100) NULL,
    `role` VARCHAR(16) NOT NULL DEFAULT 'USER',
    `age` INT NULL,
    `gender` VARCHAR(1) NULL,
    `address` VARCHAR(100) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_role` (`role`),
    CONSTRAINT `chk_user_role` CHECK (`role` IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_recommendations` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `up_id` INT NOT NULL,
    `destination` VARCHAR(100) NOT NULL,
    `recommendation_reason` TEXT NULL,
    `estimated_cost` VARCHAR(100) NULL,
    `recommended_accommodation` TEXT NULL,
    `recommended_transportation` TEXT NULL,
    `special_tips` TEXT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `best_season` VARCHAR(100) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ai_recommendations_user` (`up_id`),
    KEY `idx_ai_recommendations_destination` (`destination`),
    KEY `idx_ai_recommendations_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_travel_preference` (
    `user_id` INT NOT NULL,
    `interests_json` JSON NULL,
    `pace` VARCHAR(16) NULL,
    `travel_mode` VARCHAR(16) NULL,
    `accommodation_preference` VARCHAR(255) NULL,
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`user_id`),
    KEY `idx_user_travel_preference_updated_at` (`updated_at`),
    CONSTRAINT `fk_user_travel_preference_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `chk_user_travel_preference_pace`
        CHECK (`pace` IS NULL OR `pace` IN ('RELAXED', 'MODERATE', 'COMPACT')),
    CONSTRAINT `chk_user_travel_preference_mode`
        CHECK (`travel_mode` IS NULL OR `travel_mode` IN ('WALKING', 'TRANSIT', 'TAXI', 'DRIVING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `knowledge_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL,
    `city` VARCHAR(100) NOT NULL,
    `publisher` VARCHAR(255) NOT NULL,
    `source_url` VARCHAR(1024) NOT NULL,
    `published_at` DATE NOT NULL,
    `document_type` VARCHAR(32) NOT NULL,
    `original_filename` VARCHAR(255) NOT NULL,
    `stored_filename` VARCHAR(255) NOT NULL,
    `content_type` VARCHAR(128) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `sha256` CHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    `failure_reason` TEXT NULL,
    `uploaded_by` INT NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_document_sha256` (`sha256`),
    KEY `idx_knowledge_document_city_status` (`city`, `status`),
    KEY `idx_knowledge_document_uploader` (`uploaded_by`),
    KEY `idx_knowledge_document_created_at` (`created_at`),
    CONSTRAINT `fk_knowledge_document_uploader`
        FOREIGN KEY (`uploaded_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `knowledge_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `document_id` BIGINT NOT NULL,
    `chunk_index` INT NOT NULL,
    `content` LONGTEXT NOT NULL,
    `embedding_id` VARCHAR(128) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_chunk_document_index` (`document_id`, `chunk_index`),
    UNIQUE KEY `uk_knowledge_chunk_embedding` (`embedding_id`),
    KEY `idx_knowledge_chunk_created_at` (`created_at`),
    CONSTRAINT `fk_knowledge_chunk_document`
        FOREIGN KEY (`document_id`) REFERENCES `knowledge_document` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `travel_plan` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `destination_city` VARCHAR(100) NOT NULL,
    `start_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `current_version` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(32) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_travel_plan_user_created` (`user_id`, `created_at`),
    KEY `idx_travel_plan_city` (`destination_city`),
    KEY `idx_travel_plan_status` (`status`),
    CONSTRAINT `fk_travel_plan_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `planning_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `request_json` JSON NOT NULL,
    `resolved_requirement_json` JSON NULL,
    `applied_preferences_json` JSON NULL,
    `current_step` VARCHAR(64) NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `failure_code` VARCHAR(64) NULL,
    `failure_reason` TEXT NULL,
    `plan_id` BIGINT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_planning_task_user_status` (`user_id`, `status`),
    KEY `idx_planning_task_plan` (`plan_id`),
    KEY `idx_planning_task_created_at` (`created_at`),
    CONSTRAINT `fk_planning_task_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_planning_task_plan`
        FOREIGN KEY (`plan_id`) REFERENCES `travel_plan` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `travel_plan_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `plan_id` BIGINT NOT NULL,
    `version_number` INT NOT NULL,
    `itinerary_json` JSON NOT NULL,
    `validation_json` JSON NOT NULL,
    `metrics_json` JSON NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_travel_plan_version` (`plan_id`, `version_number`),
    KEY `idx_travel_plan_version_created_at` (`created_at`),
    CONSTRAINT `fk_travel_plan_version_plan`
        FOREIGN KEY (`plan_id`) REFERENCES `travel_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `planning_trace` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `plan_version_id` BIGINT NULL,
    `step` VARCHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `duration_ms` BIGINT NULL,
    `input_summary` TEXT NULL,
    `output_summary` TEXT NULL,
    `error_code` VARCHAR(64) NULL,
    `error_message` TEXT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_planning_trace_task_created` (`task_id`, `created_at`),
    KEY `idx_planning_trace_status` (`status`),
    KEY `idx_planning_trace_plan_version` (`plan_version_id`),
    CONSTRAINT `fk_planning_trace_task`
        FOREIGN KEY (`task_id`) REFERENCES `planning_task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_planning_trace_plan_version`
        FOREIGN KEY (`plan_version_id`) REFERENCES `travel_plan_version` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
