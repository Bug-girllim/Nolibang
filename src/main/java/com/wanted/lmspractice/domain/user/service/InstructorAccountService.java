package com.wanted.lmspractice.domain.user.service;

import org.springframework.stereotype.Service;

/**
 * 강사 계정 생성·임시 비밀번호 재발급·활성/비활성·취미 권한
 * 담당: 팀원 1 · #05
 * (💡 제안 구조 — 상세 구현은 해당 이슈에서)
 */
@Service
public class InstructorAccountService {

    /** 계약 C3: 강사가 해당 세부 취미 활동 권한을 가졌는지 (사용: 팀원 2 #10, 팀원 4 #18) */
    public boolean hasHobbyGrant(Long instructorId, Long hobbyId) {
        // TODO #05: 구현 (현재는 스텁)
        return false;
    }
}
