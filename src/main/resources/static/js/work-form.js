/*
 * [역할] 일반 작품 등록·수정 화면(U-S11, templates/work/form.html)의 작은 화면 동작
 * [담당] 팀원 4 · #19   [API] MW-01·MW-03 (hobbyId 쿼리로 화면 다시 그리기)
 * 🟡 미학습 기술: 브라우저 JavaScript — 시안의 select·input onChange 동작을 대신한다.
 *   1) 취미를 바꾸면 그 취미의 수준 선택지·참고 피드백이 달라진다 → 서버가 다시 그려야 하므로 ?hobbyId=&title= 로 같은 화면을 다시 요청한다.
 *      (폼 전체를 GET 으로 보내면 긴 설명이 주소에 실려 길이 제한에 걸릴 수 있어 제목만 함께 넘긴다)
 *   2) 제목·설명 글자 수와 고른 사진 수 표시 — 서버 요청 없이 입력 중에 보여 줘야 해서 배운 기술(폼 전송)로는 대신할 수 없다.
 * 검증은 하지 않는다 — 1~5장·형식·용량·수준은 서버(WorkService·FileStorage·WorkLevelPolicy)가 검사한다.
 */
(function () {
  var hobby = document.getElementById('w-h');
  var title = document.getElementById('w-t');

  if (hobby && title) {
    hobby.addEventListener('change', function () {
      location.href = hobby.dataset.url
          + '?hobbyId=' + encodeURIComponent(hobby.value)
          + '&title=' + encodeURIComponent(title.value);
    });
  }

  function showLength(input, output) {
    if (!input || !output) {
      return;
    }
    input.addEventListener('input', function () {
      output.textContent = input.value.length;
    });
  }
  showLength(title, document.getElementById('w-t-count'));
  showLength(document.getElementById('w-d'), document.getElementById('w-d-count'));

  var images = document.getElementById('w-img');
  var imageLabel = document.getElementById('w-img-label');
  if (images && imageLabel) {
    images.addEventListener('change', function () {
      var count = images.files.length;
      imageLabel.textContent = count === 0 ? '+ 이미지 추가' : count + '장 선택됨 (다시 고르기)';
    });
  }
})();
