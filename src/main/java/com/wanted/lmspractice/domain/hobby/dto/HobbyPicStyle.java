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
            "cook", "/images/744a198c128b2b5416116117a226db6f.jpg",
            "craft", "/images/0a4a3aa68a387a85f12b842092b1ffa8.webp",   // 공예(준비 중) — 메인·취미 목록 카드용, 세부 취미 pic-craft와 같은 그림
            "photo", "/images/22f7031aebbeb206b167821be79ebced.webp");  // 사진·영상 — 세부 취미 pic-photo와 같은 그림

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

    /** 세부 취미 상세 화면의 대표 이미지 (세부 취미 code → 이미지 경로). 색연필화는 시안 이미지, 나머지는 static/images/hobby 의 변환본 */
    private static final Map<String, String> HERO_IMAGES = Map.of(
            "colored-pencil", "/images/d1e12edec2f86abce9c49eb326397d4d.jpg",
            "digital-drawing", "/images/hobby/digital-drawing-hero.jpg",
            "dessert", "/images/hobby/dessert-hero.jpg");

    /** 세부 취미 상세 화면의 수준별 예시 작품 이미지 (세부 취미 code → 수준 → 이미지 경로) */
    private static final Map<String, Map<CourseLevel, String>> LEVEL_IMAGES = Map.of(
            "colored-pencil", Map.of(
                    CourseLevel.BASIC, "/images/e09f09b947e5652cba60b1aa39b3c64f.webp",
                    CourseLevel.INTERMEDIATE, "/images/cbfd6d2d8616e5aa93b535d0d5f33b84.webp",
                    CourseLevel.ADVANCED, "/images/4bf1934989ed611648e241ac7ec10379.webp"),
            "digital-drawing", Map.of(
                    CourseLevel.BASIC, "/images/hobby/digital-drawing-basic.jpg",
                    CourseLevel.INTERMEDIATE, "/images/hobby/digital-drawing-intermediate.jpg",
                    CourseLevel.ADVANCED, "/images/hobby/digital-drawing-advanced.jpg"),
            "dessert", Map.of(
                    CourseLevel.BASIC, "/images/hobby/dessert-basic.jpg",
                    CourseLevel.INTERMEDIATE, "/images/hobby/dessert-intermediate.jpg",
                    CourseLevel.ADVANCED, "/images/hobby/dessert-advanced.jpg"));

    private HobbyPicStyle() {
    }

    /** 이미지를 정하지 않은 대분류(관리자가 새로 추가한 대분류 등)의 카드 이미지 — 취미 탐색 배너(bn-explore)와 같은 "정원 문" 그림 */
    private static final String DEFAULT_CATEGORY_IMAGE = "/images/3e5e7da65a0dedf758b57596eb180054.webp";

    /** 대분류 카드 이미지 경로. 매핑이 없으면 기본 이미지 */
    public static String categoryImage(String categoryCode) {
        return CATEGORY_IMAGES.getOrDefault(categoryCode, DEFAULT_CATEGORY_IMAGE);
    }

    /** 대분류별 보기 상단 배너 클래스. 없으면 기본 배너 */
    public static String categoryBanner(String categoryCode) {
        return CATEGORY_BANNERS.getOrDefault(categoryCode, "bn-explore");
    }

    /** 이미지를 정하지 않은 세부 취미(관리자가 새로 추가한 취미 등)의 카드 그림 클래스 — atelier-v2.css 의 .pic-default ("정원 문" 그림) */
    private static final String DEFAULT_HOBBY_PIC_CLASS = "pic-default";

    /** 세부 취미 카드의 그림 스타일 클래스. 매핑이 없으면 기본 그림 클래스 */
    public static String hobbyPicClass(String hobbyCode) {
        return HOBBY_PIC_CLASSES.getOrDefault(hobbyCode, DEFAULT_HOBBY_PIC_CLASS);
    }

    /** 세부 취미 상세 화면 대표 이미지 경로. 없으면 null */
    public static String heroImage(String hobbyCode) {
        return HERO_IMAGES.get(hobbyCode);
    }

    /** 세부 취미 상세 화면의 수준별 예시 작품 이미지 경로. 없으면 null */
    public static String levelImage(String hobbyCode, CourseLevel level) {
        Map<CourseLevel, String> images = LEVEL_IMAGES.get(hobbyCode);
        return images == null ? null : images.get(level);
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
