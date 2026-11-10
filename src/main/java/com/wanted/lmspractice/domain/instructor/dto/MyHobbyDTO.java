package com.wanted.lmspractice.domain.instructor.dto;

import com.wanted.lmspractice.domain.hobby.dto.HobbyDTO;
import com.wanted.lmspractice.domain.hobby.entity.ExposureState;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * [역할] 강사 "내 담당 취미" 카드 한 장의 표시값 (세부 취미 이름·대분류·노출 상태·공개 강의 수)
 * [담당] #T-S08   [화면] T-S08(templates/instructor/hobbies.html)
 * [연결]
 *  - 만든 곳: InstructorHobbyService.getMyHobbies (HobbyDTO + 강의 수를 합쳐 만든다)
 *  - 쓰는 곳: MyHobbyPageDTO(목록·집계), templates/instructor/hobbies.html
 * [기술] 🟢 DTO 정적 팩토리
 */
@Getter
@AllArgsConstructor
public class MyHobbyDTO {

    private final Long id;
    private final String name;
    private final String categoryName;
    private final ExposureState state;
    /** 시안 그림 스타일 CSS 클래스 (예: pic-pencil) */
    private final String picClass;
    /** 이 취미에서 강사에게 보이는 강의 수 */
    private final int courseCount;

    /** 세부 취미 + 소속 대분류 이름 + 강의 수 → 카드 값 */
    public static MyHobbyDTO of(HobbyDTO hobby, String categoryName, int courseCount) {
        return new MyHobbyDTO(hobby.getId(), hobby.getName(), categoryName, hobby.getState(), hobby.getPicClass(), courseCount);
    }

    /** 준비 중인 취미인지 (카드에 "준비 중" 표시) */
    public boolean isPreparing() {
        return state == ExposureState.PREPARING;
    }

    /** 노출 상태 이름 (운영 중·준비 중·비공개) */
    public String getStateLabel() {
        return state.getLabel();
    }
}
