/*
 * [역할] 메인 화면 대분류 카드 줄(.car)의 좌우 넘기기 — 카드가 화면보다 많아 잘릴 때 화살표 버튼·방향키·밀어서 넘기기를 쓴다.
 * [연결] templates/main/home.html 의 .car > .car-vp > (카드 줄) / static/css/home-base.css 의 .car-btn
 * [규칙] 카드가 다 보이면 아무것도 바꾸지 않는다(가운데 정렬 그대로, 화살표 없음). 넘칠 때만 왼쪽 정렬로 바꾸고 화살표를 붙인다.
 *        화면 크기가 바뀌면 다시 계산한다. 스크립트가 꺼져 있어도 카드 링크는 그대로 동작한다.
 */
(function () {
  'use strict';

  var car = document.querySelector('.car');
  var vp = car && car.querySelector('.car-vp');
  var track = vp && vp.firstElementChild;
  if (!track) { return; }

  var offset = 0;            // 현재 이동한 거리(px, 0 이상)
  var prev = makeButton('l', '이전 취미 카테고리', '‹');
  var next = makeButton('r', '다음 취미 카테고리', '›');
  var dragging = null;

  function makeButton(side, label, glyph) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'car-btn ' + side;
    b.setAttribute('aria-label', label);
    b.textContent = glyph;
    b.hidden = true;
    car.appendChild(b);
    return b;
  }

  function maxOffset() { return Math.max(0, track.scrollWidth - vp.clientWidth); }

  function step() {
    var card = track.querySelector('.cat');
    var gap = parseFloat(getComputedStyle(track).columnGap || getComputedStyle(track).gap) || 0;
    return card ? card.getBoundingClientRect().width + gap : vp.clientWidth * 0.8;
  }

  function apply(animate) {
    offset = Math.min(Math.max(offset, 0), maxOffset());
    track.style.transition = animate ? 'transform .35s ease' : 'none';
    track.style.transform = 'translate3d(' + (-offset) + 'px, 0, 0)';
    prev.disabled = offset <= 0;
    next.disabled = offset >= maxOffset() - 1;
  }

  function layout() {
    track.style.justifyContent = 'flex-start';   // 가운데 정렬 상태에서는 scrollWidth 가 넘친 폭을 정확히 주지 않아 왼쪽 정렬로 먼저 잰다
    var overflow = track.scrollWidth > vp.clientWidth + 1;
    track.style.justifyContent = overflow ? 'flex-start' : 'center';   // 넘칠 때는 왼쪽부터 보이게
    prev.hidden = next.hidden = !overflow;
    if (!overflow) { offset = 0; }
    apply(false);
  }

  function go(direction) {
    // 카드 2장씩 넘긴다
    offset += direction * step() * 2;
    apply(true);
  }

  prev.addEventListener('click', function () { go(-1); });
  next.addEventListener('click', function () { go(1); });

  vp.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowLeft') { go(-1); e.preventDefault(); }
    else if (e.key === 'ArrowRight') { go(1); e.preventDefault(); }
  });

  // 밀어서 넘기기 (마우스·터치). 조금 움직였으면 끝에서 링크 클릭을 막는다.
  vp.addEventListener('pointerdown', function (e) {
    if (e.button !== 0 || maxOffset() === 0) { return; }
    dragging = { x: e.clientX, start: offset, moved: false };
  });
  window.addEventListener('pointermove', function (e) {
    if (!dragging) { return; }
    var dx = e.clientX - dragging.x;
    if (Math.abs(dx) > 5) { dragging.moved = true; }
    if (dragging.moved) {
      offset = dragging.start - dx;
      apply(false);
    }
  });
  window.addEventListener('pointerup', function () {
    if (!dragging) { return; }
    var moved = dragging.moved;
    dragging = null;
    if (moved) { track.dataset.justDragged = '1'; setTimeout(function () { delete track.dataset.justDragged; }, 0); }
  });
  vp.addEventListener('click', function (e) {
    if (track.dataset.justDragged) { e.preventDefault(); e.stopPropagation(); }
  }, true);

  var resizeTimer = null;
  window.addEventListener('resize', function () { clearTimeout(resizeTimer); resizeTimer = setTimeout(layout, 100); });
  window.addEventListener('load', layout);   // 이미지가 다 불러와진 뒤 폭을 다시 잰다
  layout();
})();
