# 놀이방 LMS · DB 및 정책 변경 정리

2026-11-12. 11월 5일 기획안과 11월 6일 DB 설계서에서 달라진 사항을 기록한다. 구현 상태는 저장소의 SQL과 서비스 코드에서 확인했다. 팀의 공식 정책 개정 여부와 시연 DB 적용 상태는 별도 확인 사항이다. [기준 커밋][commit] · [11월 5일 정책][policy] · [기존 DB 설계서][old-db]

## 변경 요약

| 항목 | 기존 설계 | 현재 구현 |
| --- | --- | --- |
| 약관·만 14세 확인 | 약관 코드와 버전별 동의 기록 | 동의·연령 확인 흐름 및 `terms_agreements` 제외. 개인정보 처리방침 열람과 이메일 인증은 제공 |
| 첫 로그인·세션 | 강사 비밀번호 변경과 약관 동의, Spring Session JDBC | 강사 첫 로그인 시 임시 비밀번호 변경. Spring Security의 메모리 세션 등록부로 계정당 로그인 세션 1개 제한 |
| 회원 탈퇴 | 11월 6일 DB 설계 범위에서 제외 | 활성 학습자 본인 확인, 진행 중 수강 검사, 연관 데이터 정리, 계정 익명화, 동일 이메일 30일 재가입 제한 |
| 일반 작품 삭제 | 작성자 직접 삭제는 후순위. 초기 변경 기록에는 댓글·좋아요 보존으로 기재 | 작품과 댓글·답글은 논리 삭제. 회원 좋아요 행 삭제. 작품 버전·이미지 행 및 실제 파일 보존 |
| 최종 과제 직접 삭제 | 승인 과제 삭제 후속 처리 미정 | 일반 작품 삭제 기능의 대상은 `GENERAL`. 제출·재제출 버전과 공식 평가 이력은 해당 삭제 기능의 대상에서 제외 |
| 관리자 숨김·복구 | 공개 작품의 운영 조치 | `works`에 현재 숨김 상태 기록. `work_moderation_logs`에 조치·사유·관리자·시각 저장 |
| 성장 시스템 | 작품 타임라인·회고, 수료 기반 수준·마스터 배지 | RPG 캐릭터·퀘스트·보스·직업·길드·꾸미기·업적 및 대표 배지 추가 |
| 알림 | HTML 패치 설계 14종 | DB CHECK 기준 13종. `REVIEW_DUE_SOON` 알림 제외 |

[현재 스키마][schema] · [기존 구현 변경 기록][changes] · [세션 설정][security]

## 1. 약관, 연령 확인, 로그인

- 11월 6일 팀 결정이 기록된 현재 스키마는 서비스·개인정보·만 14세·강사 약관 코드별 동의 기록을 저장하는 `terms_agreements`를 제외한다. `/privacy`에서 개인정보 처리방침을 열람할 수 있다.
- 가입 이메일 인증과 계정 복구용 메일 흐름은 제공한다. 수강 자동 확정·평가·수료 등 운영 이벤트는 사이트 알림을 사용한다.
- 강사 임시 비밀번호는 첫 로그인에서 변경한다. 계정별 동시 로그인은 `maximumSessions(1)`로 제한하며, `SPRING_SESSION`과 `SPRING_SESSION_ATTRIBUTES`는 현재 DDL에서 제외됐다.

[현재 스키마][schema] · [인증 컨트롤러][auth] · [보안 설정][security] · [개인정보 처리방침 컨트롤러][main]

## 2. 회원 탈퇴

1. 활성 학습자가 현재 비밀번호로 본인을 확인한다. 대기를 취소한 뒤 진행 중 수강이 있으면 탈퇴를 거절하며, 앞서 처리한 대기 취소도 롤백한다.
2. 알림, 1:1 문의, 본인 작품과 최종 과제, 평가·공식 피드백, 진도·대기·수강 및 성장 기능 데이터를 삭제한다. 수강에서 산출하는 수준·마스터 배지도 해당 수강 기록이 삭제되면 사라진다.
3. 타인 작품에 남긴 정상 댓글은 본문에서 탐지한 개인정보를 마스킹한 뒤 `work_comments.author_id`를 NULL로 바꾼다. 기존에 삭제한 댓글의 상태와 다른 이용자의 답글은 유지한다. 타인 작품에 누른 좋아요는 사용자별 행을 제거하고 `works.anonymous_like_count`에 개수만 합산한다.
4. `users` 행은 `WITHDRAWN`과 `withdrawn_at`으로 남긴다. 아이디·닉네임·실명·이메일을 익명 값으로 바꾸고 비밀번호를 사용할 수 없는 값으로 교체한다. 인증 토큰을 삭제하고 세션을 종료한다.
5. 같은 이메일의 재가입은 탈퇴 시각부터 30일 동안 제한한다. `withdrawn_email_holds`에는 정규화된 이메일의 HMAC-SHA256 확인값과 탈퇴·만료 시각을 저장한다. 가입 가능 여부는 만료 시각으로 판단하며, 만료 행은 스케줄러에서 정리한다.

DB 정리는 한 트랜잭션에서 수행한다. 실제 이미지 파일은 커밋 이후 삭제하며, 삭제 실패 파일은 재처리 대상으로 넘어간다. 댓글 마스킹은 계정의 식별값 및 이메일·전화번호·URL·외부 계정 패턴을 사용하므로 임의 문장에 포함된 개인정보를 전부 탐지한다고 단정할 수 없다.

[탈퇴 서비스][withdraw] · [이메일 보관 서비스][hold] · [개인정보 정리 설계][privacy] · [댓글 마스킹][mask]

## 3. 작품 삭제와 운영 조치

| 상황 | DB와 파일 처리 | 화면·학습 기록 |
| --- | --- | --- |
| 작성자가 일반 작품 삭제 | `works.deleted_at` 설정, `display_version_id`·`reference_review_id` 해제. 댓글·답글은 `DELETED` 처리하고 회원 좋아요 행 삭제. 작품·버전·파일 행과 실제 이미지 보존 | 목록·상세·이미지 접근 및 성장 기록에서 제외. 참고했던 원본 평가·최종 과제·수료 기록 보존 |
| 관리자가 공개 작품 숨김·복구 | `works.admin_hidden`과 숨김 관련 열 갱신. `work_moderation_logs`에 `HIDE`·`RESTORE` 및 사유 기록 | 숨김 중 게시판 공개 접근 차단. 작성자 및 담당 강사의 허용 경로와 수료·배지·공식 피드백은 유지 |
| 학습자 탈퇴 | 본인 일반 작품과 최종 과제, 버전·파일 행, 해당 작품의 댓글·좋아요·조치 이력을 물리 삭제. 이미지 파일은 커밋 후 정리 | 탈퇴한 학습자의 수강·수료·평가 및 성장 기록도 정리 |

일반 작품 논리 삭제 이후 버전과 이미지의 영구 정리 시점은 미정이다. 승인된 최종 과제의 작성자 직접 삭제와 그에 따른 수료 처리 정책도 미정이다.

[작품 서비스][work] · [작품 운영 조치 서비스][moderation] · [현재 스키마][schema]

## 4. 성장 시스템

기존 수료 배지는 수강 수료 기록에서 계산한다. RPG 경험치·레벨·직업·보스 진행도는 학습과 활동 기록을 바탕으로 산출한다. 길드는 분반에 대응하며, 순위는 분반 내 기여 경험치를 기준으로 계산한다. DB에는 다음 네 가지 상태를 저장한다.

| 테이블 | 저장 내용과 제약 |
| --- | --- |
| `quest_claims` | 사용자·퀘스트·기간을 복합 PK로 사용. 같은 기간의 중복 수령 방지, 지급 XP·수령 시각 기록 |
| `user_avatars` | 사용자당 1행. 아바타·테두리·닉네임 색·의상·머리 장식 장착 상태 저장 |
| `user_achievements` | 사용자·업적 코드별 1행. 캐릭터 화면에서 새로 달성한 업적을 확인하면 획득 시각 기록 |
| `user_equipped_badges` | 사용자·슬롯별 대표 배지 저장. 슬롯 1~3, 같은 사용자에게 동일 배지 중복 장착 금지 |

대표 배지 장착은 업적 배지와 수료 배지를 대상으로 서버에서 소유 여부를 검사한다. 탈퇴 시 네 테이블의 해당 사용자 기록을 삭제한다.

[RPG 성장 요소 구현 문서][game] · [현재 스키마][schema] · [탈퇴 서비스][withdraw]

## 5. 스키마 기준과 문서 적용

- 현재 신규 설치 기준은 `src/main/resources/sql/schema.sql`과 `seed.sql`이다. 기본 설정은 `ddl-auto: none`이다. 11월 6일 설계서의 `NOLEEBANG_ALL.sql`과 당시 검증 71건은 당시 설계 기준의 기록이다.
- 최신 DDL은 **테이블 26개, 외래 키 50개, 뷰 6개**다. 기존 ERD 대비 조치 이력 1개, 성장 4개, 탈퇴 이메일 보관 1개를 추가하고 약관 동의 테이블을 제외했다. `v_public_works`는 회원 좋아요와 익명 보존 좋아요를 합산한다.
- 기존 설치의 실제 반영 여부는 DB 담당자가 확인한다. 이 문서는 저장소 DDL과 코드의 확인 결과이며, 시연 DB 스키마·브라우저 동작·마이그레이션 실행 결과는 확인 범위에 포함되지 않는다.

[최신 ERD][erd] · [현재 스키마][schema] · [제출용 실행 가이드][setup]

[old-db]: https://chatgpt.com/library?file_id=libfile_1e35191b848481919944c941992926ea&account_id=personal
[erd]: Nolibang_ERD_2026-11-12.md
[commit]: https://github.com/Bug-girllim/Nolibang/commit/8bca4ade831ce36001b9ce7e7335804d293ff63c
[policy]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/%EA%B8%B0%ED%9A%8D%EC%95%88_11.05/05-policy.md
[changes]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/%EA%B8%B0%ED%9A%8D%EC%95%88_11.05/09-update_ing.md
[schema]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/resources/sql/schema.sql
[security]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/config/SecurityConfig.java
[auth]: https://github.com/Bug-girllim/Nolibang/tree/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/auth/controller
[main]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/global/controller/MainController.java
[withdraw]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/service/WithdrawalService.java
[hold]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/user/service/WithdrawnEmailHoldService.java
[privacy]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/DB_%EB%B3%80%EA%B2%BD%EC%9A%94%EC%B2%AD_%ED%9A%8C%EC%9B%90%ED%83%88%ED%87%B4_%EA%B0%9C%EC%9D%B8%EC%A0%95%EB%B3%B4%EC%A0%95%EB%A6%AC.md
[mask]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/policy/CommentMaskPolicy.java
[work]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/service/WorkService.java
[moderation]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/src/main/java/com/wanted/lmspractice/domain/work/service/WorkModerationService.java
[game]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/RPG_%EC%84%B1%EC%9E%A5%EC%9A%94%EC%86%8C.md
[setup]: https://github.com/Bug-girllim/Nolibang/blob/8bca4ade831ce36001b9ce7e7335804d293ff63c/docs/%EB%86%80%EC%9D%B4%EB%B0%A9%20LMS%20%EC%A0%9C%EC%B6%9C%EC%9A%A9%20%EC%8B%A4%ED%96%89%20%EA%B0%80%EC%9D%B4%EB%93%9C.md
