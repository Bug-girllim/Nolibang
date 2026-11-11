/* 기존 홈의 헤르메스 아이 + 현재 동물/취미 의상 + 독립 왕관 레이어로 수여식을 조합한다. */
(function () {
  'use strict';
  var dialog = document.getElementById('gm-promotion');
  if (!dialog) { return; }
  var backFocus = null;
  var requestNo = 0;
  function close() {
    requestNo++;
    if (typeof dialog.close === 'function' && dialog.open) { dialog.close(); }
    else { dialog.removeAttribute('open'); }
  }
  function show() {
    var card = document.getElementById('gm-card');
    if (!card || card.dataset.crowned !== 'true') { return; }
    var body = dialog.querySelector('[data-promotion-avatar]');
    var caption = dialog.querySelector('[data-promotion-caption]');
    if (!body || !card.dataset.cardImage) { return; }
    backFocus = document.activeElement;
    var mine = ++requestNo;
    body.src = card.dataset.cardImage;
    body.alt = card.dataset.avatarLabel + ' · ' + card.dataset.outfitLabel + ' · 마스터의 왕관';
    if (caption) { caption.textContent = card.dataset.avatarLabel + ' · ' + card.dataset.outfitLabel; }
    // 선택 캐릭터 그림이 준비되기 전에는 수여식을 시작하지 않는다.
    var ready = typeof body.decode === 'function' ? body.decode().catch(function () {}) : Promise.resolve();
    ready.then(function () {
      if (mine !== requestNo) { return; }
      dialog.classList.remove('is-awarding');
      if (!dialog.open) {
        if (typeof dialog.showModal === 'function') { dialog.showModal(); }
        else { dialog.setAttribute('open', ''); }
      }
      void dialog.offsetWidth;
      dialog.classList.add('is-awarding');
      var closeButton = dialog.querySelector('[data-promotion-close]');
      if (closeButton) { closeButton.focus({ preventScroll: true }); }
    });
  }
  document.addEventListener('click', function (event) {
    if (!(event.target instanceof Element)) { return; }
    if (event.target.closest('[data-promotion-replay]')) { show(); }
    if (event.target.closest('[data-promotion-close]')) { close(); }
  });
  dialog.addEventListener('click', function (event) {
    if (event.target !== dialog) { return; }
    var rect = dialog.getBoundingClientRect();
    if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) { close(); }
  });
  dialog.addEventListener('cancel', function () { requestNo++; });
  dialog.addEventListener('close', function () {
    dialog.classList.remove('is-awarding');
    var target = backFocus && backFocus.isConnected ? backFocus : document.querySelector('[data-promotion-replay]');
    if (target && typeof target.focus === 'function') { target.focus({ preventScroll: true }); }
  });
  document.addEventListener('nb:character-refreshed', function (event) {
    if (event.detail && event.detail.crownEquipped) { show(); }
  });
})();
