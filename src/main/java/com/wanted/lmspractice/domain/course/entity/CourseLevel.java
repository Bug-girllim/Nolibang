package com.wanted.lmspractice.domain.course.entity;

/**
 * 수준: 입문 / 중급 / 고급
 * 담당: 팀원 2 · #08
 * DB(courses.level, works.level)에는 숫자 1·2·3(TINYINT)으로 저장된다 → CourseLevelConverter로 변환.
 * 강의명은 "○○ 기초/응용/심화"(시드), 수준 표시는 입문/중급/고급.
 */
public enum CourseLevel {
    BASIC(1, "입문"),
    INTERMEDIATE(2, "중급"),
    ADVANCED(3, "고급");

    private final int code;
    private final String label;

    CourseLevel(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CourseLevel fromCode(int code) {
        for (CourseLevel level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        throw new IllegalArgumentException("알 수 없는 수준 값: " + code);
    }
}
