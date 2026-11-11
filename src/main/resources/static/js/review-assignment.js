/*
 * [역할] 최종 과제 화면(U-S07)의 입력 보조 — 선택한 이미지 수·미리보기(썸네일)·제목/설명 글자 수 표시, 이미지가 필요한 폼에서 0장이면 제출 버튼 잠금
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
    var imageLabel = document.getElementById('image-label');
    var imageNames = document.getElementById('image-names');
    var submit = document.getElementById('btn-submit');
    var requireImages = form.getAttribute('data-require-images') === 'true';

    function updateImages() {
        var count = images.files ? images.files.length : 0;
        imageCount.textContent = count;
        // 고른 파일을 화면에서 바로 알 수 있게 — 칸 문구와 파일 이름 목록을 바꾼다(고르지 않으면 처음 문구로)
        if (imageLabel) {
            imageLabel.textContent = count === 0 ? '+ 이미지 선택' : count + '장 선택됨 (다시 고르기)';
        }
        if (imageNames) {
            var names = [];
            for (var i = 0; i < count; i++) {
                names.push(images.files[i].name);
            }
            imageNames.textContent = count === 0 ? '' : '선택한 이미지: ' + names.join(', ');
        }
        showPreviews();
        if (requireImages) {
            submit.disabled = count === 0;
        }
    }

    // 고른 이미지 미리보기(썸네일) — 파일은 서버에 보내기 전이라 브라우저 안의 임시 주소(blob:)로만 보여 준다
    var previewBox = null;
    var previewUrls = [];
    function showPreviews() {
        previewUrls.forEach(function (url) { URL.revokeObjectURL(url); });   // 이전 미리보기 주소는 메모리에서 해제
        previewUrls = [];
        if (!previewBox) {
            previewBox = document.createElement('div');
            previewBox.id = 'image-previews';
            previewBox.className = 'row';
            previewBox.style.cssText = 'gap: 8px; flex-wrap: wrap; margin-top: 8px;';
            var anchor = imageNames || images;
            anchor.parentNode.insertBefore(previewBox, anchor.nextSibling);
        }
        previewBox.innerHTML = '';
        var files = images.files || [];
        for (var i = 0; i < files.length; i++) {
            if (!/^image\//.test(files[i].type)) {
                continue;   // 이미지가 아니면 미리보기 없이 이름만(서버가 형식을 다시 검사한다)
            }
            var url = URL.createObjectURL(files[i]);
            previewUrls.push(url);
            var img = document.createElement('img');
            img.src = url;
            img.alt = '선택한 이미지 ' + (i + 1);
            img.style.cssText = 'width: 96px; height: 96px; object-fit: cover; border-radius: 8px; border: 1px solid rgb(232, 225, 208);';
            previewBox.appendChild(img);
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
