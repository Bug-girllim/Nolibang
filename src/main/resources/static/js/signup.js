/*
 * [역할] 회원가입 화면(C-03)의 아이디·닉네임 "중복 확인" 버튼 동작
 * [화면] templates/user/signup.html   [API] US-03 GET /signup/check-id, US-04 GET /signup/check-nickname
 * [연결] 호출: signup.html 맨 아래 <script> / 부름: SignupController.checkId·checkNickname (@ResponseBody, true = 사용 가능)
 * 🟡 미학습 기술: 브라우저 JavaScript(fetch) — 페이지 이동 없이 사용 가능 여부만 알려 주는 기능이라 서버 렌더링만으로 대신할 수 없음
 *    (작업분배 §5에서 중복 확인용 JS 허용). 최종 판단은 가입(POST /signup)에서 서버가 다시 한다.
 */
(function () {
    // 버튼을 누르면 입력값을 서버에 물어보고, 결과 문구를 지정한 자리에 보여 준다
    function bindCheck(buttonId, inputId, hintId, paramName, emptyMsg, okMsg, usedMsg) {
        var button = document.getElementById(buttonId);
        var input = document.getElementById(inputId);
        var hint = document.getElementById(hintId);
        if (!button || !input || !hint) {
            return;
        }
        button.addEventListener('click', function () {
            var value = input.value.trim();
            if (!value) {
                hint.textContent = emptyMsg;
                return;
            }
            fetch(button.dataset.url + '?' + paramName + '=' + encodeURIComponent(value))
                .then(function (res) { return res.json(); })
                .then(function (available) { hint.textContent = available ? okMsg : usedMsg; })
                .catch(function () { hint.textContent = '확인하지 못했어요. 잠시 후 다시 시도해 주세요.'; });
        });
        // 값이 바뀌면 이전 확인 결과는 더 이상 맞지 않는다
        input.addEventListener('input', function () {
            hint.textContent = '';
        });
    }

    bindCheck('check-id-btn', 'lid', 'id-hint', 'loginId',
        '아이디를 입력해 주세요.', '사용 가능한 아이디예요.', '이미 사용 중인 아이디예요.');
    bindCheck('check-nick-btn', 'nick', 'nick-hint', 'nickname',
        '닉네임을 입력해 주세요. 비워 두면 자동으로 만들어 드려요.', '사용 가능한 닉네임이에요.', '이미 사용 중인 닉네임이에요.');
})();
