package com.wanted.lmspractice.domain.hobby.dto;

import com.wanted.lmspractice.domain.course.entity.CourseLevel;
import java.util.Map;

/**
 * [역할] 시안의 대표 이미지·그림 스타일 클래스를 취미 코드(code)로 골라 주는 표시 전용 매핑
 * [담당] 팀원 2 · #08   [화면] U-S01~U-S04
 * [연결]
 *  - 호출받음: CategoryDTO·HobbyDTO·HobbyDetailDTO·CourseDTO의 from(...) 정적 팩토리
 *  - 호출함:   없음
 * [기술] 🟢 정적 메서드, Map
 * 왜 필요한가: schema.sql의 thumbnail_key는 시드 데이터가 모두 NULL이라 DB에서 이미지를 가져올 수 없다. 그래서 시안에서 가져온
 * 이미지(static/images)와 CSS 클래스(static/css의 pic-*·cimg-*)를 취미 코드에 맞춰 연결한다. 이미지가 없는 취미는 null/빈 문자열을
 * 돌려주고 화면은 그 부분을 그리지 않는다. ❓ 썸네일 파일이 준비되면 thumbnail_key 기반으로 바꾼다(#16 파일 저장 이후).
 */
public final class HobbyPicStyle {

    /** 대분류 카드 이미지 (대분류 code → 이미지 경로) */
    private static final Map<String, String> CATEGORY_IMAGES = Map.of(
            "art", "/images/cebe4f846beeaa2226dd7a60ffd22cf0.jpg",
            "cook", "/images/744a198c128b2b5416116117a226db6f.jpg");

    /** 대분류별 보기 상단 배너 CSS 클래스 (대분류 code → 클래스). 없으면 기본 배너(bn-explore) */
    private static final Map<String, String> CATEGORY_BANNERS = Map.of(
            "art", "bn-art",
            "cook", "bn-baking",
            "craft", "bn-craft",
            "photo", "bn-photo");

    /** 세부 취미 카드의 그림 스타일 CSS 클래스 (세부 취미 code → 클래스) */
    private static final Map<String, String> HOBBY_PIC_CLASSES = Map.of(
            "colored-pencil", "pic-pencil",
            "digital-drawing", "pic-digital",
            "dessert", "pic-dessert",
            "woodwork", "pic-craft",
            "leather", "pic-craft",
            "ceramics", "pic-craft",
            "ring", "pic-craft",
            "photo-shooting", "pic-photo",
            "video-editing", "pic-photo");

    /** 강의 이미지 CSS 클래스의 취미 접두사 (cimg-{접두사}-{basic|mid|adv}) */
    private static final Map<String, String> COURSE_PIC_PREFIXES = Map.of(
            "colored-pencil", "cp",
            "digital-drawing", "dd",
            "dessert", "ds");

    /** 색연필화 상세 화면의 대표 이미지·수준별 예시 작품 (시안에 있는 이미지) */
    private static final String PENCIL_HERO = "/images/d1e12edec2f86abce9c49eb326397d4d.jpg";
    private static final Map<CourseLevel, String> PENCIL_LEVEL_IMAGES = Map.of(
            CourseLevel.BASIC, "/images/e09f09b947e5652cba60b1aa39b3c64f.webp",
            CourseLevel.INTERMEDIATE, "/images/cbfd6d2d8616e5aa93b535d0d5f33b84.webp",
            CourseLevel.ADVANCED, "/images/4bf1934989ed611648e241ac7ec10379.webp");

    private HobbyPicStyle() {
    }

    /** 대분류 카드 이미지 경로. 없으면 null */
    public static String categoryImage(String categoryCode) {
        return CATEGORY_IMAGES.get(categoryCode);
    }

    /** 대분류별 보기 상단 배너 클래스. 없으면 기본 배너 */
    public static String categoryBanner(String categoryCode) {
        return CATEGORY_BANNERS.getOrDefault(categoryCode, "bn-explore");
    }

    /** 세부 취미 카드의 그림 스타일 클래스. 없으면 빈 문자열 */
    public static String hobbyPicClass(String hobbyCode) {
        return HOBBY_PIC_CLASSES.getOrDefault(hobbyCode, "");
    }

    /** 세부 취미 상세 화면 대표 이미지 경로. 없으면 null */
    public static String heroImage(String hobbyCode) {
        return "colored-pencil".equals(hobbyCode) ? PENCIL_HERO : null;
    }

    /** 세부 취미 상세 화면의 수준별 예시 작품 이미지 경로. 없으면 null */
    public static String levelImage(String hobbyCode, CourseLevel level) {
        return "colored-pencil".equals(hobbyCode) ? PENCIL_LEVEL_IMAGES.get(level) : null;
    }

    /** 강의 목록 카드의 그림 스타일 클래스 (예: cimg-cp-basic). 없으면 빈 문자열 */
    public static String coursePicClass(String hobbyCode, CourseLevel level) {
        String prefix = COURSE_PIC_PREFIXES.get(hobbyCode);
        if (prefix == null) {
            return "";
        }
        String suffix = switch (level) {
            case BASIC -> "basic";
            case INTERMEDIATE -> "mid";
            case ADVANCED -> "adv";
        };
        return "cimg-" + prefix + "-" + suffix;
    }
}
