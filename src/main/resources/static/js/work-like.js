/*
 * [역할] 작품 좋아요 버튼 — 누르면 POST /works/{id}/like (JSON)을 보내고 화면의 하트·숫자만 바꾼다 (화면 새로고침 없음)
 * [담당] 팀원 4 · #18   [API] WB-03   [연결] templates/work/detail.html, work/board-instructor.html
 * 🟡 미학습 기술: 브라우저 JavaScript fetch — 좋아요는 화면 이동 없이 토글돼야 하는 작은 데이터 응답이라 사용
 *    (작업분배 §5: 중복 확인·좋아요·재생 보고는 @ResponseBody + 짧은 JS 허용)
 * 버튼 속성: data-work-like(대상 표시), data-like-url(POST 주소), aria-pressed(현재 상태)
 * 라벨 span: .like-label — data-with-count 가 있으면 "♥ 좋아요 12"처럼 숫자까지 같이 바꾼다.
 * 카드의 [data-like-count] 는 같은 카드 안의 숫자 표시. (CSRF 토큰은 페이지 head의 meta _csrf·_csrf_header 에서 읽어 헤더로 보낸다)
 */
document.querySelectorAll('[data-work-like]').forEach(function (button) {
  button.addEventListener('click', function () {
    var headers = { 'Accept': 'application/json' };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.content] = token.content; }
    fetch(button.dataset.likeUrl, { method: 'POST', headers: headers })
      .then(function (response) {
        if (!response.ok) { throw new Error('like failed'); }
        return response.json();
      })
      .then(function (result) {
        button.setAttribute('aria-pressed', String(result.liked));
        var label = button.querySelector('.like-label');
        if (label) {
          label.textContent = (result.liked ? '♥ 좋아요' : '♡ 좋아요') + (label.hasAttribute('data-with-count') ? ' ' + result.likeCount : '');
        }
        var card = button.closest('.art') || document;
        card.querySelectorAll('[data-like-count]').forEach(function (el) { el.textContent = result.likeCount; });
      })
      .catch(function () {
        alert('좋아요를 처리하지 못했어요. 잠시 후 다시 시도해 주세요.');
      });
  });
});
