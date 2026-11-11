/*
 * [역할] 이미지 파일 선택 칸 공통 미리보기 — 고른 이미지를 입력 칸 아래에 작은 그림으로 바로 보여 준다
 * [연결] templates/review/assignment.html(#images), templates/work/form.html(#w-img) 등 accept 가 image/* 인 모든 파일 칸
 * [기술] 🟡 미학습: 브라우저 JavaScript — 고른 파일은 서버로 보내기 전에는 브라우저만 볼 수 있어 배운 기술(폼 전송)로 대신할 수 없다.
 *        판단은 하지 않는다 — 장수·형식·크기 검증은 서버(SubmissionPolicy·WorkService·FileStorage)가 한다.
 */
(function () {
    var inputs = document.querySelectorAll('input[type="file"][accept*="image"]');

    inputs.forEach(function (input) {
        var anchor = input.closest('.g5') || input.parentElement;
        var box = document.createElement('div');
        box.className = 'img-preview';
        box.setAttribute('aria-live', 'polite');
        box.style.cssText = 'display:flex;flex-wrap:wrap;gap:8px;margin-top:8px';
        anchor.insertAdjacentElement('afterend', box);

        var urls = [];

        function clear() {
            urls.forEach(function (url) { URL.revokeObjectURL(url); });   // 쓰지 않는 미리보기 주소는 바로 반납한다
            urls = [];
            box.textContent = '';
        }

        input.addEventListener('change', function () {
            clear();
            Array.prototype.forEach.call(input.files || [], function (file) {
                if (!file.type || file.type.indexOf('image/') !== 0) {
                    return;
                }
                var url = URL.createObjectURL(file);
                urls.push(url);
                var fig = document.createElement('figure');
                fig.style.cssText = 'margin:0;width:96px';
                var img = document.createElement('img');
                img.src = url;
                img.alt = file.name + ' 미리보기';
                img.style.cssText = 'width:96px;height:96px;object-fit:cover;border:1px solid #d8ccb5;border-radius:6px;display:block';
                var cap = document.createElement('figcaption');
                cap.textContent = file.name;
                cap.style.cssText = 'font-size:11px;color:#68728D;overflow:hidden;text-overflow:ellipsis;white-space:nowrap';
                fig.appendChild(img);
                fig.appendChild(cap);
                box.appendChild(fig);
            });
        });
    });
})();
