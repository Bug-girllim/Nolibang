package com.wanted.lmspractice.domain.course.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * CourseLevel ↔ DB 숫자(1·2·3) 변환
 * 담당: 팀원 2 · #08
 * @Enumerated(STRING)은 문자열, ORDINAL은 0부터 시작해서 DB 값(1~3)과 맞지 않으므로 변환기를 쓴다.
 * 사용: 엔티티 필드에 @Convert(converter = CourseLevelConverter.class)
 * 🟡 미학습 기술(AttributeConverter) — 11-06 팀 결정으로 사용
 */
@Converter
public class CourseLevelConverter implements AttributeConverter<CourseLevel, Integer> {

    @Override
    public Integer convertToDatabaseColumn(CourseLevel level) {
        return level == null ? null : level.getCode();
    }

    @Override
    public CourseLevel convertToEntityAttribute(Integer code) {
        return code == null ? null : CourseLevel.fromCode(code);
    }
}
