/*
 * [역할] 관리자 대분류·세부 취미 폼의 대표 사진 칸 — 고른 사진을 저장 전에 미리 보여 주고, "삭제" 체크와 새 사진 선택이 서로 부딪히지 않게 한다.
 * [연결] templates/admin/hobby-manage.html 의 [data-image-field] (input[data-image-input], img[data-image-preview], input[data-image-remove])
 * [규칙] 화면 표시만 바꾼다. 형식·크기 검증은 서버(FileStorage)가 한다. 스크립트가 꺼져도 폼은 그대로 전송된다.
 */
(function () {
  'use strict';
  document.querySelectorAll('[data-image-field]').forEach(function (field) {
    var input = field.querySelector('[data-image-input]');
    var remove = field.querySelector('[data-image-remove]');
    var img = field.querySelector('[data-image-preview]');
    if (!input) { return; }
    var original = img ? img.getAttribute('src') : null;

    input.addEventListener('change', function () {
      var file = input.files && input.files[0];
      if (file && file.size > 50 * 1024 * 1024) {
        alert('사진 한 장은 50MB 이하여야 해요.');
        input.value = '';
        return;
      }
      if (file) {
        if (remove) { remove.checked = false; }          // 새 사진을 고르면 "삭제" 선택은 풀린다(교체가 우선)
        if (!img) {
          img = document.createElement('img');
          img.setAttribute('data-image-preview', '');
          img.alt = '선택한 사진 미리보기';
          img.style.cssText = 'width:140px;height:90px;object-fit:cover;border-radius:8px;border:1px solid #D8C28A';
          input.parentNode.insertBefore(img, input);
        }
        img.src = URL.createObjectURL(file);
      } else if (img && original) {
        img.src = original;
      }
    });

    if (remove) {
      remove.addEventListener('change', function () {
        if (remove.checked) { input.value = ''; if (img && original) { img.src = original; } }
      });
    }
  });
})();
