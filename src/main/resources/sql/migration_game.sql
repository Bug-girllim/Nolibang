-- =================================================================
-- RPG 성장 요소(캐릭터·퀘스트·꾸미기·업적 배지) 지원 — schema.sql 맨 끝 "RPG 성장 요소" 블록과 같은 내용
-- 경험치·레벨·직업·길드 순위는 기존 표에서 계산하므로 표를 만들지 않는다. 저장이 필요한 두 가지만 둔다.
--   quest_claims : 퀘스트 보상을 받은 기록 (같은 기간에 같은 퀘스트는 1번만)
--   user_avatars : 캐릭터 꾸미기 장착 상태 (사용자당 1행)
-- =================================================================
CREATE TABLE IF NOT EXISTS quest_claims (
    user_id BIGINT UNSIGNED NOT NULL,
    quest_key VARCHAR(40) NOT NULL,
    period_key VARCHAR(20) NOT NULL,
    reward_xp INT UNSIGNED NOT NULL,
    claimed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, quest_key, period_key),
    KEY idx_quest_claims_claimed (claimed_at),
    CONSTRAINT fk_quest_claims_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_avatars (
    user_id BIGINT UNSIGNED NOT NULL,
    avatar_code VARCHAR(30) NULL,
    frame_code VARCHAR(30) NULL,
    name_color_code VARCHAR(30) NULL,
    outfit_code VARCHAR(30) NULL,
    head_code VARCHAR(30) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_avatars_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 업적 배지(첫 걸음·열공러·소통왕 …) 획득 기록 — 배지 종류와 달성 조건은 코드(AchievementType)에 있고, 받은 기록만 둔다.
-- 같은 배지는 한 번만 받는다(기본키). 행은 지우거나 고치지 않는다(이력).
CREATE TABLE IF NOT EXISTS user_achievements (
    user_id BIGINT UNSIGNED NOT NULL,
    badge_code VARCHAR(40) NOT NULL,
    earned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, badge_code),
    CONSTRAINT fk_user_achievements_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 대표 배지 장착 — 캐릭터 카드에 보여 줄 배지(최대 3개). slot_no 는 1~3, 같은 배지는 한 번만 장착한다.
-- badge_code: 업적 배지는 AchievementType 이름(예: LESSON_1), 수료 배지는 C{취미 id}-{B|I|A|M} (입문·중급·고급·마스터)
-- 가진 배지인지는 장착할 때 서버가 확인한다. 해제하면 행을 지운다.
CREATE TABLE IF NOT EXISTS user_equipped_badges (
    user_id BIGINT UNSIGNED NOT NULL,
    slot_no TINYINT UNSIGNED NOT NULL,
    badge_code VARCHAR(40) NOT NULL,
    equipped_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, slot_no),
    UNIQUE KEY uq_equipped_badge (user_id, badge_code),
    CONSTRAINT fk_equipped_badges_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_equipped_badges_slot CHECK (slot_no BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
