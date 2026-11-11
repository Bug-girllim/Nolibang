/*
 * [역할] 메인 화면 "취미 탐색하기" 버튼을 누르면 천상의 문이 열리는 전환 애니메이션을 보여준 뒤 취미 탐색으로 이동한다.
 * [연결] templates/main/home.html 의 <a class="cta" data-door-transition> / static/css/home-base.css 의 .dt-* 스타일
 * [규칙] 이 버튼에서만 재생한다(카테고리 카드 등 다른 링크는 바로 이동).
 *        애니메이션 중 화면을 누르거나 키를 누르면 바로 이동한다. 모션 줄이기 설정이면 짧은 페이드만 쓴다.
 *        스크립트가 꺼져 있어도 링크는 그대로 동작한다.
 */
(function () {
  'use strict';

  var OPEN_MS = 5000;   // 문이 열리고 확대되는 시간 — 이 뒤에 이동한다
  var started = false;

  function layer(cls, parent) {
    var el = document.createElement('div');
    el.className = cls;
    if (parent) { parent.appendChild(el); }
    return el;
  }

  function buildOverlay(reduced) {
    var ov = layer('dt-ov');
    ov.setAttribute('aria-hidden', 'true');
    if (reduced) { ov.classList.add('dt-reduced'); return ov; }

    var shake = layer('dt-shake', ov);
    var stage = layer('dt-stage', shake);
    layer('dt-l dt-garden', stage);
    layer('dt-l dt-glow', stage);
    for (var i = 0; i < 6; i++) { layer('dt-sp', stage); }
    layer('dt-l dt-frame', stage);

    var doors = layer('dt-l dt-doors', stage);
    ['l', 'r'].forEach(function (side) {
      var leaf = layer('dt-leaf ' + side, doors);
      layer('dt-face', leaf);
      var back = layer('dt-back', leaf);
      back.appendChild(document.createElement('i'));
      layer('dt-edge', leaf);
    });

    layer('dt-l dt-bloom', stage);
    return ov;
  }

  document.addEventListener('click', function (event) {
    var link = event.target.closest && event.target.closest('a.cta[data-door-transition]');
    if (!link) { return; }
    // 새 탭·키 조합 클릭은 브라우저 기본 동작에 맡긴다
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) { return; }
    event.preventDefault();
    if (started) { return; }
    started = true;

    var href = link.href;
    var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var ov = buildOverlay(reduced);
    document.body.appendChild(ov);

    var done = false;
    function go() {
      if (done) { return; }
      done = true;
      window.location.href = href;
    }
    ov.addEventListener('click', go);                                  // 누르면 건너뛰기
    document.addEventListener('keydown', go, { once: true });
    window.addEventListener('pageshow', function (e) {                 // 뒤로 가기로 돌아오면 오버레이를 치운다
      if (e.persisted) { ov.remove(); started = false; done = false; }
    });
    setTimeout(go, reduced ? 150 : OPEN_MS);
  });
})();
