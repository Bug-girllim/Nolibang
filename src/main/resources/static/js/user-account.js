/*
 * [역할] 계정 설정 화면(U-S17) "기본 정보" 탭의 닉네임 "중복 확인" 버튼 동작
 * [화면] templates/user/account.html   [API] US-04 GET /signup/check-nickname (#04 것을 그대로 재사용, true = 사용 가능)
 * [연결] 호출: account.html 맨 아래 <script> / 부름: SignupController.checkNickname (@ResponseBody)
 * 🟡 미학습 기술: 브라우저 JavaScript(fetch) — 페이지 이동 없이 사용 가능 여부만 알려 주는 기능이라 서버 렌더링만으로 대신할 수 없음
 *    (작업분배 §5 중복 확인용 JS 허용, signup.js와 같은 방식). 최종 판단은 저장(POST /my/account/nickname)에서 서버가 다시 한다.
 */
(function () {
    var button = document.getElementById('check-nick-btn');
    var input = document.getElementById('nick');
    var hint = document.getElementById('nick-hint');
    if (!button || !input || !hint) {
        return;
    }
    var initialHint = hint.textContent;

    button.addEventListener('click', function () {
        var value = input.value.trim();
        if (!value) {
            hint.textContent = '닉네임을 입력해 주세요.';
            return;
        }
        // 지금 내 닉네임은 check-nickname이 "사용 중"으로 답하므로 먼저 걸러 준다
        if (value === button.dataset.current) {
            hint.textContent = '지금 사용 중인 닉네임이에요.';
            return;
        }
        fetch(button.dataset.url + '?nickname=' + encodeURIComponent(value))
            .then(function (res) { return res.json(); })
            .then(function (available) {
                hint.textContent = available ? '사용 가능한 닉네임이에요.' : '이미 사용 중인 닉네임이에요.';
            })
            .catch(function () { hint.textContent = '확인하지 못했어요. 잠시 후 다시 시도해 주세요.'; });
    });
    // 값이 바뀌면 이전 확인 결과는 더 이상 맞지 않는다
    input.addEventListener('input', function () {
        hint.textContent = initialHint;
    });
})();
