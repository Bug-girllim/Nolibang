/* 이미지 기반 전투 연출. 서버 학습 상태·경험치는 변경하지 않는다.
   캐릭터 AJAX 교체 후 재연결하고 이전 타이머를 해제한다. 기록은 계정·수강별로 구분한다. */
(function () {
  'use strict';
  var disposers = [];
  var motionClasses = ['play-attack', 'play-ult', 'play-counter', 'play-defeat'];

  function boot() {
    disposers.forEach(function (dispose) { dispose(); });
    disposers = [];
    var wrap = document.getElementById('gm-battle');
    if (!wrap) { return; }
    var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var controllers = new Map();
    var tabs = Array.from(wrap.querySelectorAll('.bt-tab'));
    var panels = Array.from(wrap.querySelectorAll('.bt-panel'));

    function initPanel(root) {
      var stage = root.querySelector('.bt-stage');
      var bar = root.querySelector('.bt-hp-bar');
      var hpText = root.querySelector('.bt-hp-text');
      var hpArea = root.querySelector('.bt-hp');
      var banner = root.querySelector('.bt-banner');
      var replay = root.querySelector('.bt-replay');
      var chest = root.querySelector('.bt-chest');
      var done = root.querySelector('.bt-done');
      var reward = root.querySelector('.bt-reward');
      var toggle = root.querySelector('.bt-reward-toggle');
      var controls = Array.from(root.querySelectorAll('[data-bt-preview]'));
      if (replay) { controls.push(replay); }
      var d = root.dataset;
      var cur = { phase: d.phase, done: Math.max(0, Number(d.done) || 0), hp: Math.max(0, Math.min(100, Number(d.hp) || 0)) };
      var key = 'nb-boss-art-v2:' + encodeURIComponent(wrap.dataset.owner || 'local') + ':' + d.enrollment;
      var timers = new Set();
      var bannerTimer;
      var running = false;
      var listeners = [];
      function later(fn, ms) {
        var id = setTimeout(function () { timers.delete(id); fn(); }, ms); timers.add(id); return id;
      }
      function listen(el, event, fn) {
        if (!el) { return; } el.addEventListener(event, fn); listeners.push(function () { el.removeEventListener(event, fn); });
      }
      function load() {
        try {
          var value = JSON.parse(localStorage.getItem(key));
          return value && typeof value.phase === 'string' && Number.isFinite(value.hp) && Number.isFinite(value.done) ? value : null;
        } catch (e) { return null; }
      }
      function save(seen) {
        try { localStorage.setItem(key, JSON.stringify({ phase: cur.phase, done: cur.done, hp: cur.hp, seen: !!seen })); } catch (e) { /* 화면 저장 실패는 학습 상태에 영향 없음 */ }
      }
      function setHp(value) {
        value = Math.max(0, Math.min(100, value));
        if (bar) { bar.style.width = value + '%'; }
        if (hpText) { hpText.textContent = value + '%'; }
        if (hpArea) { hpArea.setAttribute('aria-label', '보스 체력 ' + value + '퍼센트'); }
      }
      function say(text) {
        if (!banner) { return; }
        clearTimeout(bannerTimer); timers.delete(bannerTimer);
        banner.textContent = text; banner.hidden = false;
        bannerTimer = later(function () { banner.hidden = true; }, 3000);
      }
      function play(cls, ms, after) {
        if (running || !stage) { return false; }
        if (reduced) { if (after) { after(); } return true; }
        stage.style.setProperty('--bt-travel', Math.round(stage.clientWidth * .42) + 'px');
        running = true; controls.forEach(function (c) { c.disabled = true; });
        motionClasses.forEach(function (c) { stage.classList.remove(c); });
        void stage.offsetWidth;
        stage.classList.add(cls);
        later(function () {
          stage.classList.remove(cls); running = false;
          controls.forEach(function (c) { c.disabled = false; });
          if (after) { after(); }
        }, ms);
        return true;
      }
      function attack(fromHp, preview) {
        if (running) { return; }
        // 미리보기는 현재 표시 체력을 그대로 유지한다.
        if (!preview && fromHp != null) { setHp(fromHp); }
        if (play('play-attack', 1400, function () { setHp(cur.hp); })) {
          if (!preview && !reduced) { later(function () { setHp(cur.hp); }, 850); }
          say(d.attack + (preview ? ' · 미리보기' : '!'));
        }
      }
      function ultimate(preview) {
        if (play('play-ult', 1700)) { say(d.ultimateName + (preview ? ' · 미리보기' : '!')); }
      }
      function counter() {
        if (play('play-counter', 1400)) { say('보스의 반격! 피드백을 확인하고 다시 도전해요'); }
      }
      function defeat(first) {
        if (running) { return; }
        if (first) { if (reward) { reward.hidden = true; } if (done) { done.hidden = true; } }
        play('play-defeat', 1900, function () {
          if (first && chest) { chest.hidden = false; if (!root.hidden) { chest.focus({ preventScroll: true }); } }
        });
        say('보스 격파!');
      }
      function replayCurrent() {
        if (cur.phase === 'WAITING') { ultimate(true); }
        else if (cur.phase === 'REJECTED') { counter(); }
        else if (cur.phase === 'DEFEATED') { defeat(false); }
        else if (cur.phase !== 'EXPIRED') { attack(null, true); }
      }
      if (replay) { replay.hidden = cur.phase === 'EXPIRED'; listen(replay, 'click', replayCurrent); }
      root.querySelectorAll('[data-bt-preview]').forEach(function (button) {
        listen(button, 'click', function () {
          if (button.dataset.btPreview === 'ultimate') { ultimate(true); } else { attack(null, true); }
        });
      });
      var prev = load(), seen = !!(prev && prev.seen);
      listen(chest, 'click', function () {
        chest.hidden = true; if (done) { done.hidden = false; }
        if (reward) { reward.hidden = false; if (!reduced) { reward.classList.add('bt-pop'); } }
        if (toggle) { toggle.hidden = false; toggle.setAttribute('aria-expanded', 'true'); toggle.textContent = '보상 접기'; toggle.focus({ preventScroll: true }); }
        seen = true; save(true);
      });
      listen(toggle, 'click', function () {
        if (!reward) { return; }
        reward.hidden = !reward.hidden; toggle.setAttribute('aria-expanded', String(!reward.hidden));
        toggle.textContent = reward.hidden ? '보상 보기' : '보상 접기';
      });
      if (cur.phase === 'DEFEATED') {
        if (!seen) { defeat(true); }
        else {
          if (reward) { reward.hidden = true; }
          if (toggle) { toggle.hidden = false; toggle.setAttribute('aria-expanded', 'false'); toggle.textContent = '보상 보기'; }
        }
      } else if (prev) {
        var wasChallenge = ['LEARNING', 'ROOM_OPEN', 'REJECTED'].indexOf(prev.phase) !== -1;
        if (cur.phase === 'WAITING' && wasChallenge) { ultimate(false); }
        else if (cur.phase === 'REJECTED' && prev.phase !== 'REJECTED') { counter(); }
        else if ((cur.phase === 'LEARNING' || cur.phase === 'ROOM_OPEN') && cur.done > prev.done) {
          attack(prev.hp, false);
          if (cur.phase === 'ROOM_OPEN' && prev.phase === 'LEARNING') {
            later(function () { say('보스의 방이 열렸어요! 최종 과제로 도전하세요'); }, 1700);
          }
        }
      }
      if (cur.phase !== 'DEFEATED' || seen) { save(seen); }
      return function () {
        timers.forEach(clearTimeout); timers.clear(); listeners.forEach(function (off) { off(); });
        motionClasses.forEach(function (c) { if (stage) { stage.classList.remove(c); } });
        setHp(cur.hp);
      };
    }
    function open(panelId) {
      panels.forEach(function (p) { p.hidden = p.id !== panelId; });
      tabs.forEach(function (t) {
        var on = t.getAttribute('data-target') === panelId;
        t.classList.toggle('on', on); t.setAttribute('aria-selected', String(on)); t.tabIndex = on ? 0 : -1;
      });
      var panel = panels.find(function (p) { return p.id === panelId; });
      if (panel && !controllers.has(panelId)) { controllers.set(panelId, initPanel(panel)); }
    }
    tabs.forEach(function (tab, index) {
      function click() { open(tab.getAttribute('data-target')); }
      function keydown(event) {
        var next;
        if (event.key === 'ArrowRight') { next = (index + 1) % tabs.length; }
        else if (event.key === 'ArrowLeft') { next = (index + tabs.length - 1) % tabs.length; }
        else if (event.key === 'Home') { next = 0; }
        else if (event.key === 'End') { next = tabs.length - 1; }
        else { return; }
        event.preventDefault(); tabs[next].focus(); open(tabs[next].getAttribute('data-target'));
      }
      tab.addEventListener('click', click); tab.addEventListener('keydown', keydown);
      disposers.push(function () { tab.removeEventListener('click', click); tab.removeEventListener('keydown', keydown); });
    });
    disposers.push(function () { controllers.forEach(function (dispose) { dispose(); }); controllers.clear(); });
    var first = panels.find(function (p) { return !p.hidden; });
    if (first) { open(first.id); }
  }
  document.addEventListener('nb:character-refreshed', boot);
  boot();
})();
