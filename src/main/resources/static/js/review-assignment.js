/*
 * [역할] 최종 과제 화면(U-S07)의 입력 보조 — 선택한 이미지 수·제목/설명 글자 수 표시, 이미지가 필요한 폼에서 0장이면 제출 버튼 잠금
 * [담당] 팀원 3 · #13   [연결] templates/review/assignment.html (#assignment-form, #images, #t-title, #t-desc, #btn-submit)
 * [기술] 🟡 미학습: 브라우저 JavaScript — 고른 파일 수와 입력 글자 수는 서버 요청 없이 화면에서만 알 수 있어 배운 기술(폼·redirect)로 대신할 수 없다.
 *        판단은 하지 않는다 — 이미지 수(1~5장)·형식·크기는 서버(SubmissionPolicy·FileStorage)가 다시 검사한다.
 */
(function () {
    var form = document.getElementById('assignment-form');
    if (!form) {
        return;  // 폼이 없는 상태(평가 중·수료·기간 종료 등)
    }
    var images = document.getElementById('images');
    var imageCount = document.getElementById('image-count');
    var submit = document.getElementById('btn-submit');
    var requireImages = form.getAttribute('data-require-images') === 'true';

    function updateImages() {
        var count = images.files ? images.files.length : 0;
        imageCount.textContent = count;
        if (requireImages) {
            submit.disabled = count === 0;
        }
    }

    function bindLength(inputId, outputId) {
        var input = document.getElementById(inputId);
        var output = document.getElementById(outputId);
        if (!input || !output) {
            return;
        }
        input.addEventListener('input', function () {
            output.textContent = input.value.length;
        });
    }

    images.addEventListener('change', updateImages);
    bindLength('t-title', 'title-length');
    bindLength('t-desc', 'desc-length');
    updateImages();

    // 제출 버튼 연타 방지 — 서버도 수강 건 잠금으로 평가 대기 1건만 남기지만, 같은 파일을 여러 번 올리지 않게 한 번만 보낸다
    form.addEventListener('submit', function () {
        submit.disabled = true;
        submit.setAttribute('aria-busy', 'true');
    });
})();
