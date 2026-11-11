# 놀이방 기획안 대비 개발 변경·진행 현황

> **작성일:** 2026-11-11  
> **기준 기획안:** 2026-11-05 (`docs/기획안_11.05/`)  
> **확인 기준:** `main` 커밋 [`66c5bdf`](https://github.com/Bug-girllim/Nolibang/commit/66c5bdfa3245e06268ff0bbc305b5a33c29ad7c2) (2026-11-11 오전 확인)  
> **문서 위치:** `docs/기획안_11.05/09-update_ing.md`  
> **목적:** 11월 5일 기획 원본은 수정하지 않고, 이후 **구현 사항·기획과 달라진 점·미검증 항목**을 별도로 누적 기록한다.

## 0. 문서 작성 원칙

- 본 파일은 **진행 중인 구현 변경 기록**이다. 기존 `PRD.md`, `03-requirements.md`, `05-policy.md`와 기타 11월 5일 기획 문서를 변경하거나 대체하지 않는다.
- 기획 기준은 [`README.md`](README.md)와 [`05-policy.md`](05-policy.md)의 **D-51~D-59**이다.
- **코드 반영**은 GitHub `main`에 코드가 병합된 상태, **검증 필요**는 실제 DB·브라우저·배포 환경의 동작을 끝까지 확인하지 못한 상태, **팀 결정 필요**는 기존 기획 범위를 넘어 공식 승인이 필요한 상태를 뜻한다.
- PR 병합은 운영 배포 성공이나 실제 공용 DB 적용을 증명하지 않는다.
- DB 변경 및 DB 쓰기 테스트는 DB 담당자 소관이다. 이 문서는 DB 작업을 지시하지 않는다.

## 1. 전체 진행 요약

2026-11-05 기획의 핵심인 **강의별 공통 분반, 정원 1~30, 남은 자리 최대 분반 자동 배정, 단일 FIFO 대기열과 자동 확정, 30일 수강, 실제 영상 재생 60% 완료, 최종 과제 및 강사 평가, 사이트 알림, 강사별 현재 통계**를 담당하는 기능 코드가 `main`에 병합되었다.

이후 **관리자 카테고리·강의 편집, 관리자·강사 영상 업로드, 회원 탈퇴, 일반 작품 논리 삭제, 보안 강화, 속도 최적화**가 추가되었다. 이 추가 기능은 **구현 사실**이며, 11월 5일 정책의 공식 개정이 있었는지는 별도 확인해야 한다.

**전체 기능 통합 테스트 및 외부 접속 가능한 최종 배포는 완료로 판정하지 않는다.** 부분 기능 검증 자료와 단위 테스트가 존재하지만, 최신 `main`의 공용 환경 종단 간 검증에는 미확인 항목이 있다.

## 2. 11월 5일 정책(D-51~D-59) 반영 현황

| 정책 | 11/05 확정 사항 | 현재 구현·변경 | 분류 |
| --- | --- | --- | --- |
| D-51 강사 계정 | 관리자가 임시 비밀번호로 생성, 첫 로그인 비밀번호 변경 | 강사 생성·재발급·취미 권한·강제 변경 코드 [PR #25](https://github.com/Bug-girllim/Nolibang/pull/25) | 코드 반영 |
| D-52 분반 배정 | 분반 정원 1~30, 잔여석 최대인 분반 우선, 강사 합산 30건 제한 폐기 | 신청·자동 배정·분반 관리 [#28](https://github.com/Bug-girllim/Nolibang/pull/28), [#44](https://github.com/Bug-girllim/Nolibang/pull/44) | 코드 반영 |
| D-53 대기열 | 강의별 단일 FIFO, 자리 발생 즉시 1순위 수강 확정·사이트 알림, 48시간 초대 없음 | 대기 신청/취소·자동 확정 및 알림 [#28](https://github.com/Bug-girllim/Nolibang/pull/28), [#36](https://github.com/Bug-girllim/Nolibang/pull/36) | 코드 반영 |
| D-54 수강 취소 | 취소 후 자리 반환 → 대기 1순위 자동 확정 | 취소·빈자리 채움·만료 처리 [#28](https://github.com/Bug-girllim/Nolibang/pull/28), [#44](https://github.com/Bug-girllim/Nolibang/pull/44) | 코드 반영 |
| D-55 재신청 | 수료·취소·만료 강의 모두 재신청 불가 | 재신청 제한 코드 [#28](https://github.com/Bug-girllim/Nolibang/pull/28) | 코드 반영 |
| D-56 평가 | 평가 시작 → 잠금 → 초안 → 승인/반려, 시작 취소 및 기한 경고 | 과제 제출/수정/재제출, 강사 평가 및 결과 [#35](https://github.com/Bug-girllim/Nolibang/pull/35), [#42](https://github.com/Bug-girllim/Nolibang/pull/42), [#33](https://github.com/Bug-girllim/Nolibang/pull/33) | 코드 반영·통합 검증 필요 |
| D-57 알림 | 운영 이벤트는 **사이트 알림만** 사용 | 알림함·읽음·헤더 알림 [#36](https://github.com/Bug-girllim/Nolibang/pull/36). 별도의 가입 인증·계정 복구 메일은 [#68](https://github.com/Bug-girllim/Nolibang/pull/68) | 코드 반영 |
| D-58 강사 통계 | 분반별 수강 중·수료·반려·평가 대기 **현재 숫자** | 강사·관리자 통계 [#27](https://github.com/Bug-girllim/Nolibang/pull/27), 표시 보완 [#92](https://github.com/Bug-girllim/Nolibang/pull/92) | 코드 반영 |
| D-59 관리자 범위 | 관리자 홈·강사·분반·대기열 **조회**, 카테고리·강의는 시드 데이터 | 이후 카테고리 CRUD·강의 편집·영상 업로드가 추가됨 [#100](https://github.com/Bug-girllim/Nolibang/pull/100), [#108](https://github.com/Bug-girllim/Nolibang/pull/108), [#81](https://github.com/Bug-girllim/Nolibang/pull/81) | **팀 정책 재확인 필요** |

## 3. 기능별 개발 진행 상황

| 영역 | `main` 기준 구현된 항목 | 검증·후속 확인 |
| --- | --- | --- |
| 계정·인증 | 학습자 회원가입·이메일 인증·로그인, 계정 복구, 계정 설정, 한 기기 로그인 [#24](https://github.com/Bug-girllim/Nolibang/pull/24) | SMTP 오류 처리, 이메일 인증 링크 주소와 세션 동작 |
| 강사·관리자 | 강사 계정 발급·취미 권한, 분반·대기열 관리, 담당 학습자, 통계, 내 담당 취미 [#25](https://github.com/Bug-girllim/Nolibang/pull/25), [#27](https://github.com/Bug-girllim/Nolibang/pull/27), [#85](https://github.com/Bug-girllim/Nolibang/pull/85) | 담당 변경 후 접근 권한·강사 비활성화 세션 |
| 취미·강의 | 대분류/세부 취미 조회, 수준별 강의 목록·상세, 운영/준비/비공개 노출 규칙 [#23](https://github.com/Bug-girllim/Nolibang/pull/23) | 상태 변경 후 기존 사용자 화면 및 URL 접근 확인 |
| 신청·대기열 | 신청·자동 배정·대기·취소·즉시 자동 확정·30일 만료 [#28](https://github.com/Bug-girllim/Nolibang/pull/28), [#44](https://github.com/Bug-girllim/Nolibang/pull/44) | 동시 신청 시 정원 초과/중복 확정 방지 |
| 영상·학습 | 시청 시간 60% 완료, 영상 접근 제한, MP4 Range 재생 및 관리자/강사 업로드·차시 연결, 27개 기본 영상 [#30](https://github.com/Bug-girllim/Nolibang/pull/30), [#81](https://github.com/Bug-girllim/Nolibang/pull/81) | 저장 PC·실제 파일·DB video_key 연동, 업로드 후 재생 |
| 최종 과제·평가·배지 | 이미지 제출·수정·반려 후 재제출, 강사 평가 시작·잠금·승인/반려, 결과·수준/마스터 배지 [#35](https://github.com/Bug-girllim/Nolibang/pull/35), [#42](https://github.com/Bug-girllim/Nolibang/pull/42), [#33](https://github.com/Bug-girllim/Nolibang/pull/33) | 제출-평가-수료-배지-알림-자리 반환 통합 시나리오 |
| 작품·성장 | 게시판·좋아요·댓글·답글, 일반 작품·성장 타임라인·회고, 관리자 숨김·복구 [#38](https://github.com/Bug-girllim/Nolibang/pull/38), [#40](https://github.com/Bug-girllim/Nolibang/pull/40), [#41](https://github.com/Bug-girllim/Nolibang/pull/41) | 이미지 열람/삭제·공개 범위·타인 접근 차단 |
| 알림·문의 | 사이트 알림·읽음/이동 및 담당 강사 1:1 문의 [#36](https://github.com/Bug-girllim/Nolibang/pull/36), [#39](https://github.com/Bug-girllim/Nolibang/pull/39) | 알림 대상 삭제 시 처리·문의 소유권 |
| 보안 | Spring Security CSRF 활성화, SameSite=Lax 쿠키, 역할별 URL MockMvc 테스트 [#79](https://github.com/Bug-girllim/Nolibang/pull/79) | 운영 HTTPS 쿠키 및 권한별 실제 브라우저 확인 |
| 성능·배포 준비 | 정적 파일 7일 캐시·내용 해시, N+1/공통 조회 축소, 원격 DB 왕복 최적화, prod 프로필 [#83](https://github.com/Bug-girllim/Nolibang/pull/83), [#106](https://github.com/Bug-girllim/Nolibang/pull/106), [#116](https://github.com/Bug-girllim/Nolibang/pull/116) | 실제 로그인 및 외부 접속 환경 성능 검증 |

## 4. 기획안 대비 변경·확장 사항 (중요)

| 관리 ID | 변경 사항 | 근거 | 팀이 최종 확인할 내용 |
| --- | --- | --- | --- |
| CH-01 | **카테고리·강의 관리 확대:** D-59는 시드 데이터 중심이나, 관리자 대분류·세부 취미 추가, 상태 변경, 이름·소개 편집, 일부 대분류 삭제 및 강의명·소개·준비물·공개 상태 편집 기능까지 추가됨 | [#100](https://github.com/Bug-girllim/Nolibang/pull/100), [#103](https://github.com/Bug-girllim/Nolibang/pull/103), [#105](https://github.com/Bug-girllim/Nolibang/pull/105), [#108](https://github.com/Bug-girllim/Nolibang/pull/108) | 이번 MVP에 공식 포함할지, 기록이 연결된 객체 삭제 기준 |
| CH-02 | **대기열 관리자 취소:** 단순 조회 외에 관리자가 대기 중 항목을 취소할 수 있음 | [#108](https://github.com/Bug-girllim/Nolibang/pull/108) | 관리 권한·사유 기록·당사자 알림 |
| CH-03 | **영상 업로드:** 개발자가 파일을 배치하는 방식에서 관리자/강사가 MP4(최대 500MB)를 웹으로 업로드·차시 연결할 수 있게 확장. 새 업로드 영상은 기본적으로 로컬 디스크에 저장 | [#81](https://github.com/Bug-girllim/Nolibang/pull/81) | 운영 저장소·업로드 권한·백업 정책·배포 PC 경로 |
| CH-04 | **회원 탈퇴:** 학습자 계정 익명화와 탈퇴 후 30일 이메일 재가입 제한 추가(기존 후순위 기능 선구현) | [#77](https://github.com/Bug-girllim/Nolibang/pull/77) | MVP 포함·개인정보·기록 보존 정책 |
| CH-05 | **일반 작품 삭제는 논리 삭제:** 본인 일반 작품 삭제 시 `deleted_at` 설정·목록/상세/이미지 URL 차단. 댓글·좋아요·파일 행·실제 파일은 유지. 최종 과제 직접 삭제 불가 | [#110](https://github.com/Bug-girllim/Nolibang/pull/110) | 보존·영구 삭제·최종 과제 정책; 브라우저 삭제 버튼 미검증 |
| CH-06 | **약관·만 14세 확인 제외:** 11/10 체크리스트에 11/06 팀 결정에 따른 미구현으로 기록 | [기능 체크리스트](../기능체크리스트_결과.md) | 기획안과의 차이 승인, 개인정보·연령 정책 검토 |
| CH-07 | **강사 비활성화 기존 세션:** 11/10 체크리스트에 비활성화 후 기존 로그인 세션이 유효하던 문제 기록. 수정 PR 상태는 별도 확인 필요 | [기능 체크리스트](../기능체크리스트_결과.md) | 수정 병합 및 최신 main에서 재현/해결 확인 |
| CH-08 | **PC 레이아웃 고정:** 좁은 창에서도 데스크톱 UI 유지하도록 일부 반응형 미디어쿼리 비활성화 | [#64](https://github.com/Bug-girllim/Nolibang/pull/64) | 가로 스크롤 허용·PC 전용 범위 |
| CH-09 | **성능 개선:** 기능 정책 유지하면서 반복 조회, 캐시, DB 커넥션 왕복과 prod 설정 개선 | [#106](https://github.com/Bug-girllim/Nolibang/pull/106), [#116](https://github.com/Bug-girllim/Nolibang/pull/116) | 실제 시연 환경에서 로그인 성능·회귀 검증 |

> **구분 원칙:** 위 CH 항목은 '코드상 변경된 사실'을 기록한 것이지, D-51~D-59가 공식 폐기되었다는 뜻이 아니다. 공식 정책 변경은 팀의 결정 날짜·내용·담당자 확인 후 별도로 표시한다.

## 5. 테스트·검증 현황

| 항목 | 지금까지 확인된 내용 | 남은 작업 |
| --- | --- | --- |
| 기본 점검 | [`기능체크리스트_결과.md`](../기능체크리스트_결과.md)의 ①~⑥ 부분 점검 기록(2026-11-10, 당시 기준 커밋 `de0c5fc`) | 최신 `main` 재검증 |
| 영상 | [PR #81](https://github.com/Bug-girllim/Nolibang/pull/81)에 27개 영상 재생/Range 200·206/권한 테스트 기록 | 최종 서버 파일 위치와 시연용 계정에서 재확인 |
| 업로드/삭제 | 이미지 업로드 유효성 검사와 일반 작품 논리 삭제 단위 테스트 기록 | 실제 브라우저, 삭제 이후 파일·댓글·좋아요·권한 시나리오 |
| DB 연동 테스트 | [PR #106](https://github.com/Bug-girllim/Nolibang/pull/106)은 DB 변경을 발생시킬 수 있는 `@SpringBootTest` 19개 클래스를 실행하지 않음 | 최신 스키마가 있는 **DB 담당자 측 별도 테스트** 결과 필요 |
| 전체 테스트 | [PR #116](https://github.com/Bug-girllim/Nolibang/pull/116)에는 전체 420개 중 172개 실패가 기존과 같고, 로컬 DB 구형 스키마가 원인이라고 보고됨 | DB 스키마 차이 확인·실패 원인 정리 |
| 성능 | PR #116의 공용 DB **비회원 화면** 실측: 홈 1030→19ms, 강의 상세 예 3286→582ms (해당 테스트 조건) | 로그인·학습자/강사·알림 화면 실측; 전체 환경 성능 보장 아님 |
| 배포 | `application-prod.yaml`과 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `APP_BASE_URL` 등의 환경변수 설정 구조 확인 | 외부 접속 주소·HTTPS·상시 실행·영상/이미지 경로·백업. **최종 배포 완료 확인 없음** |

### 우선 확인할 통합 테스트

- [ ] 회원가입 → 이메일 인증 → 로그인 → 수강 신청 → 자동 분반 배정 또는 단일 FIFO 대기 → 자동 확정 알림
- [ ] 3차시마다 실제 60% 재생 → 최종 과제 1~5장 제출 → 평가 잠금 → 승인/반려 → 배지·정원 반환
- [ ] 취소·만료·수료 시 재신청 차단, 동시 수강 신청·자리 반환 시 중복 확정 방지
- [ ] 관리자/강사 영상 업로드 → 차시 연결 → 학습자 재생, 사용 중 영상 삭제 거부, 저장 위치 확인
- [ ] 작성자·다른 학습자·담당 강사·관리자별 공개/비공개/숨김/삭제 작품의 목록·상세·이미지 URL 접근 차단 확인
- [ ] 일반 작품 논리 삭제 뒤 댓글·좋아요·실제 파일/DB 데이터 보존 방식이 팀 정책과 일치하는지 확인
- [ ] 비활성 강사·탈퇴 회원 기존 세션 차단, 권한 없는 POST와 CSRF 누락 요청 차단
- [ ] 운영 배포 URL에서 메일 인증 링크, 업로드된 영상 및 이미지 파일 확인

## 6. DB·파일 저장·운영 주의사항

- 11/05 문서의 **17개 테이블 설계 추적표**와 현재 `src/main/resources/sql/schema.sql`, 실제 공용 DB는 동일하다고 전제하지 않는다. DB 차이 점검 및 수정은 DB 담당자만 수행한다.
- 기본 업로드 경로는 `./uploads`이다. Git에 있는 기본 27개 영상과 **웹으로 새로 업로드한 영상**의 관리 방식이 다르다. 웹 업로드 파일은 서버 PC 로컬 디스크에 존재하므로, 다른 PC에서 코드를 `pull`했다고 자동으로 생기지 않는다.
- 영상 데이터는 실제 `lessons.video_key`가 가리키는 상대 경로와 서버 디스크의 경로가 일치해야 한다. 영상 연결 변경은 담당자 승인/관리 아래 검증한다.
- 이메일 인증·비밀번호 복구용 발송 메일은 D-57의 **운영 이벤트 알림 이메일 미사용** 정책과 별개이다.
- 외부 URL에서 인증 링크를 열려면 `APP_BASE_URL`이 실제 접속 주소를 가리켜야 한다.

## 7. 앞으로 이 문서 갱신하는 방법

새 기능/정책 변경이 있을 때 다음 정보를 4절에 추가하고, 3절·5절의 진행 상황도 함께 갱신한다.

| 날짜 | 변경 ID | 기존 기획 기준 | 실제 변경 내용 | 근거 PR/커밋 | 코드 상태 | 테스트 상태 | 팀 정책 확정 여부 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| YYYY-MM-DD | CH-10 | D-xx / FR-xxx | 내용 작성 | #번호 | 미반영/병합 | 미확인/부분/통과 | 미정/확정 |

**보존 원칙:** `docs/기획안_11.05/`의 11/05 원본은 수정하지 않는다. 이 문서만 현재 상태를 기록하며, 새 결정이 공식 확정되면 결정일과 합의 내용을 명시한다.

---

**참고 링크:** [기획안 README](README.md) · [요구사항](03-requirements.md) · [운영 정책](05-policy.md) · [11/05 변경 이력](CHANGELOG_20261105.md) · [DB 추적](08-requirements_DB_trace.md) · [GitHub PR 목록](https://github.com/Bug-girllim/Nolibang/pulls)
