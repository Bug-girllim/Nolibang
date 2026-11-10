package com.wanted.lmspractice.domain.instructor.service;

import com.wanted.lmspractice.domain.course.service.CourseService;
import com.wanted.lmspractice.domain.hobby.dto.CategoryDTO;
import com.wanted.lmspractice.domain.hobby.service.HobbyService;
import com.wanted.lmspractice.domain.instructor.dto.MyHobbyDTO;
import com.wanted.lmspractice.domain.instructor.dto.MyHobbyPageDTO;
import com.wanted.lmspractice.domain.user.entity.Role;
import com.wanted.lmspractice.domain.user.service.InstructorAccountService;
import com.wanted.lmspractice.domain.user.service.UserService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [역할] 강사가 관리자에게 지정받은 "담당 세부 취미"와 취미별 강의 수를 모아 T-S08 화면 값으로 만든다. (조회 전용)
 * [담당] #T-S08   [화면] T-S08   [기획] 관리자가 세부 취미 담당 강사를 지정(A-S04)
 * [연결]
 *  - 호출받음: InstructorHobbyController.hobbies (GET /instructor/hobbies)
 *  - 호출함:   HobbyService.getCategories(취미·대분류 목록), InstructorAccountService.hasHobbyGrant(계약 C3, 담당 여부),
 *              CourseService.getCourses(취미별 강의 수), UserService.getPublicNames(강사 공개 이름)
 *  - 다른 도메인의 Repository는 쓰지 않고 위 Service 계약으로만 가져온다.
 * [기술] 🟢 @Service, @Transactional(readOnly = true)
 */
@Service
@RequiredArgsConstructor
public class InstructorHobbyService {

    private final HobbyService hobbyService;
    private final InstructorAccountService instructorAccountService;
    private final CourseService courseService;
    private final UserService userService;

    /**
     * 사이드 "취미 탐색" 메뉴에 쓸 대분류·세부 취미 목록 (강사에게 보이는 것만)
     * 호출: InstructorHobbyController.menuCategories — 취미 탐색 화면들과 같은 사이드(hobby/side)를 쓰기 때문
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> getMenuCategories() {
        return hobbyService.getCategories(Role.INSTRUCTOR);
    }

    /**
     * 내 담당 취미 화면 값 — 대분류 순서대로 훑으며 내가 담당(권한 유효)인 세부 취미만 카드로 만든다.
     * 호출: InstructorHobbyController.hobbies
     * 회수된 권한(is_active = false)은 hasHobbyGrant가 걸러 준다.
     */
    @Transactional(readOnly = true)
    public MyHobbyPageDTO getMyHobbyPage(Long instructorId) {
        List<MyHobbyDTO> cards = new ArrayList<>();
        for (CategoryDTO category : hobbyService.getCategories(Role.INSTRUCTOR)) {
            category.getHobbies().stream()
                    .filter(hobby -> instructorAccountService.hasHobbyGrant(instructorId, hobby.getId()))
                    .forEach(hobby -> cards.add(MyHobbyDTO.of(hobby, category.getName(),
                            courseService.getCourses(hobby.getId(), null, Role.INSTRUCTOR).size())));
        }
        String name = userService.getPublicNames(List.of(instructorId)).getOrDefault(instructorId, "");
        return MyHobbyPageDTO.of(name, cards);
    }
}
