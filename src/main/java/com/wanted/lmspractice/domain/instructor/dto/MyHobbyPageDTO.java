package com.wanted.lmspractice.domain.instructor.dto;

import java.util.List;
import lombok.Getter;

/**
 * [역할] "내 담당 취미" 화면 한 장에 필요한 값 — 강사 이름, 담당 취미 카드 목록, 위쪽 숫자 3개
 * [담당] #T-S08   [화면] T-S08
 * [연결]
 *  - 만든 곳: InstructorHobbyService.getMyHobbyPage
 *  - 쓰는 곳: InstructorHobbyController(모델 "page"), templates/instructor/hobbies.html
 * [기술] 🟢 DTO 정적 팩토리
 */
@Getter
public class MyHobbyPageDTO {

    private final String instructorName;
    private final List<MyHobbyDTO> hobbies;
    private final int courseCount;
    private final int preparingCount;

    private MyHobbyPageDTO(String instructorName, List<MyHobbyDTO> hobbies) {
        this.instructorName = instructorName;
        this.hobbies = hobbies;
        this.courseCount = hobbies.stream().mapToInt(MyHobbyDTO::getCourseCount).sum();
        this.preparingCount = (int) hobbies.stream().filter(MyHobbyDTO::isPreparing).count();
    }

    public static MyHobbyPageDTO of(String instructorName, List<MyHobbyDTO> hobbies) {
        return new MyHobbyPageDTO(instructorName, hobbies);
    }

    /** 담당 세부 취미 수 */
    public int getHobbyCount() {
        return hobbies.size();
    }

    /** 담당 취미가 하나라도 있는 "취미 담당 강사"인지 (없으면 "일반 강사") */
    public boolean isAssigned() {
        return !hobbies.isEmpty();
    }
}
