/*
 * [역할] 내 캐릭터 화면의 장착·해제(대표 배지, 꾸미기)를 "새로고침 없이" 처리한다.
 * [연결] templates/game/character.html 의 <form data-ajax> → GameController 의 JSON 방식 핸들러(헤더 X-Requested-With)
 * [동작]
 *   1) data-ajax 폼이 전송되면 막고, 같은 폼 값(CSRF 토큰 포함)을 fetch 로 보낸다.
 *   2) 서버가 돌려준 안내 문구(success·message)를 화면 아래 안내 창에 띄운다. 실패(예: 대표 배지 칸이 꽉 참)면 주의색으로 띄운다.
 *   3) 성공하면 같은 주소를 다시 읽어 캐릭터 카드·배지·꾸미기 영역만 바꿔 끼운다(스크롤·펼친 곳은 그대로).
 *      "NEW" 표시는 서버가 처음 한 번만 만들기 때문에, 바꿔 끼우기 전에 읽어 두었다가 새 영역에 다시 붙인다.
 * [안전] 서버가 항상 최종 판단을 한다(가진 배지인지·해금됐는지·칸이 남았는지). 이 스크립트는 화면만 갱신한다.
 *        스크립트가 꺼져 있거나 오류가 나면 폼이 그대로 전송되어 기존 방식(redirect)으로 똑같이 처리된다.
 */
(function () {
  'use strict';

  var REFRESH_IDS = ['gm-card', 'gm-badges-box', 'gm-avatar-box', 'gm-classes-box', 'gm-battle'];   // 장착 상태에 따라 바뀌는 영역
  var busy = false;
  var toastTimer = null;

  /** 화면 아래 안내 창 — kind: 'ok' | 'warn' */
  function toast(message, kind) {
    var el = document.getElementById('gm-toast');
    if (!el) { return; }
    el.textContent = message;
    el.className = 'gm-toast ' + (kind === 'warn' ? 'warn' : 'ok');
    el.hidden = false;
    clearTimeout(toastTimer);
    // 주의 문구는 읽을 시간을 더 준다
    toastTimer = setTimeout(function () { el.hidden = true; }, kind === 'warn' ? 6000 : 2500);
  }

  /** 서버가 처음 한 번만 붙이는 NEW 표시를 지금 화면에서 찾아 둔다 (배지 코드 목록) */
  function collectNewCodes() {
    var codes = [];
    document.querySelectorAll('.gm-badge').forEach(function (card) {
      if (card.querySelector('.gm-badge-new') && card.dataset.code) { codes.push(card.dataset.code); }
    });
    return codes;
  }

  function restoreNew(root, codes) {
    codes.forEach(function (code) {
      var card = root.querySelector('.gm-badge[data-code="' + code + '"]');
      if (card && !card.querySelector('.gm-badge-new')) {
        var tag = document.createElement('span');
        tag.className = 'gm-badge-new';
        tag.textContent = 'NEW';
        card.insertBefore(tag, card.firstChild);
      }
    });
  }

  /** 같은 주소를 다시 읽어 장착 상태가 바뀐 영역만 교체한다 */
  function refreshSections() {
    var newCodes = collectNewCodes();
    var oldCard = document.getElementById('gm-card');
    var wasCrowned = !!(oldCard && oldCard.dataset.crowned === 'true');
    var selected = document.querySelector('#gm-battle .bt-tab[aria-selected="true"]');
    var selectedPanel = selected && selected.getAttribute('data-target');
    return fetch(window.location.pathname, { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) { throw new Error('refresh ' + res.status); }
        return res.text();
      })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        REFRESH_IDS.forEach(function (id) {
          var fresh = doc.getElementById(id);
          var old = document.getElementById(id);
          if (fresh && old) { old.replaceWith(fresh); }
        });
        restoreNew(document, newCodes);
        var battle = document.getElementById('gm-battle');
        if (battle && selectedPanel && document.getElementById(selectedPanel)) {
          battle.querySelectorAll('.bt-panel').forEach(function (p) { p.hidden = p.id !== selectedPanel; });
          battle.querySelectorAll('.bt-tab').forEach(function (t) {
            var on = t.getAttribute('data-target') === selectedPanel;
            t.classList.toggle('on', on); t.setAttribute('aria-selected', String(on));
          });
        }
        var currentCard = document.getElementById('gm-card');
        document.dispatchEvent(new CustomEvent('nb:character-refreshed', { detail: {
          crownEquipped: !wasCrowned && !!(currentCard && currentCard.dataset.crowned === 'true')
        } }));
      });
  }

  document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-ajax')) { return; }
    event.preventDefault();
    if (busy) { return; }   // 더블클릭으로 같은 요청이 두 번 가지 않게
    busy = true;

    var key = form.getAttribute('data-key');
    var button = form.querySelector('button');
    if (button) { button.disabled = true; }

    fetch(form.action, {
      method: 'POST',
      body: new FormData(form),                       // hidden _csrf 입력 포함
      headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' },
      credentials: 'same-origin'
    })
      .then(function (res) {
        var type = res.headers.get('content-type') || '';
        if (!res.ok || type.indexOf('application/json') === -1) {
          throw new Error('unexpected ' + res.status);   // 로그인 만료로 로그인 화면(HTML)이 오는 경우 등
        }
        return res.json();
      })
      .then(function (result) {
        toast(result.message, result.success ? 'ok' : 'warn');
        if (!result.success) {
          if (button) { button.disabled = false; }   // 실패(예: 칸이 꽉 참)면 버튼을 다시 쓸 수 있게 되돌린다
          return null;
        }
        return refreshSections();
      })
      .then(function () {
        // 바뀐 영역에서 방금 누른 항목의 새 버튼으로 포커스를 옮긴다(키보드 사용자 배려)
        if (key) {
          var next = document.querySelector('form[data-key="' + key + '"] button');
          if (next) { next.focus({ preventScroll: true }); }
        }
      })
      .catch(function () {
        toast('처리하지 못했어요. 로그인이 만료됐거나 연결이 불안정할 수 있어요. 페이지를 새로고침한 뒤 다시 시도해 주세요.', 'warn');
        if (button) { button.disabled = false; }
      })
      .then(function () { busy = false; });
  });
})();
