/*
 * [역할] 차시 영상 학습(U-S06) 화면의 재생 컨트롤과 재생 시간 주기 보고
 * [담당] 팀원 3 · #12   [API] LE-02 (POST /lessons/{lessonId}/progress)   [기획] FR-020
 * [연결]
 *  - 쓰는 곳: templates/learning/lesson.html (#lesson-root의 data-report-url·data-enrollment-id)
 *  - 보내는 곳: LessonController.report → ProgressService.report
 * 🟡 미학습 기술: 브라우저 JavaScript 주기 보고 — 재생 시간은 브라우저만 알 수 있어 서버만으로 불가(이슈 #12가 허용)
 *
 * 보고 규칙 (서버 LessonProgress.applyReport와 짝)
 *  - 재생을 시작할 때마다 streamKey를 새로 만들고 seq는 1부터 센다. 서버는 같은 streamKey에서 seq가 커질 때만 적립한다.
 *  - 재생 중에는 REPORT_INTERVAL_MS마다 playing=true로 보고, 일시정지·종료에는 playing=false로 한 번 보고한다.
 *  - 적립되는 시간은 서버가 잰 경과 시간이다. 배속·건너뛰기로는 더 쌓이지 않는다.
 *  - 완료 후에도 화면을 이동시키지 않는다(기획). 비율·완료 표시만 갱신한다.
 */
(function () {
  'use strict';

  var REPORT_INTERVAL_MS = 5000;   // ❓ 임시값: DEV_REVIEW 후보 5초 (#12 확정 필요)
  var SKIP_SECONDS = 10;

  var root = document.getElementById('lesson-root');
  var player = document.getElementById('player');
  if (!root) {
    return;
  }

  // 차시 목록·최종 실습 버튼: 시안의 button을 그대로 두고 data-href로 이동한다
  document.querySelectorAll('[data-href]').forEach(function (el) {
    el.addEventListener('click', function () {
      if (!el.disabled) {
        window.location.href = el.getAttribute('data-href');
      }
    });
  });

  if (!player) {
    return;
  }

  var reportUrl = root.getAttribute('data-report-url');
  var enrollmentId = root.getAttribute('data-enrollment-id');
  var playBtn = document.getElementById('btn-play');
  var skipBtn = document.getElementById('btn-skip');
  var speed = document.getElementById('speed');
  var pctText = document.getElementById('pct-text');
  var bar = document.getElementById('progress-bar');

  var streamKey = null;
  var seq = 0;
  var timer = null;

  function newStreamKey() {
    if (window.crypto && window.crypto.randomUUID) {
      return window.crypto.randomUUID();   // 36자 (서버 상한 64)
    }
    return String(Date.now()) + '-' + Math.random().toString(16).slice(2);
  }

  function formBody(playing) {
    seq += 1;
    return new URLSearchParams({
      enrollmentId: enrollmentId,
      streamKey: streamKey,
      seq: String(seq),
      playing: String(playing),
      positionSec: String(player.currentTime || 0)
    });
  }

  function showProgress(percent) {
    if (pctText) {
      pctText.textContent = percent;
    }
    if (bar) {
      bar.setAttribute('aria-valuenow', percent);
      var fill = bar.querySelector('i');
      if (fill) {
        fill.style.width = percent + '%';
      }
    }
  }

  function report(playing) {
    if (!streamKey) {
      return;
    }
    fetch(reportUrl, {
      method: 'POST',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' },
      body: formBody(playing),
      credentials: 'same-origin'
    }).then(function (res) {
      return res.ok ? res.json() : null;
    }).then(function (data) {
      if (data) {
        showProgress(data.percent);
      }
    }).catch(function () {
      // 네트워크 오류는 다음 보고에서 이어진다. 적립은 서버 기록이 기준이라 화면은 건드리지 않는다.
    });
  }

  function startReporting() {
    streamKey = newStreamKey();   // 재생을 시작할 때마다 새 키
    seq = 0;
    report(true);                 // 첫 보고는 서버가 기준점만 잡는다
    clearInterval(timer);
    timer = setInterval(function () { report(true); }, REPORT_INTERVAL_MS);
  }

  function stopReporting() {
    clearInterval(timer);
    timer = null;
    report(false);
    streamKey = null;
  }

  player.addEventListener('play', startReporting);
  player.addEventListener('pause', function () {
    if (streamKey && !player.ended) {
      stopReporting();
    }
  });
  player.addEventListener('ended', function () {
    if (streamKey) {
      stopReporting();
    }
  });

  // 탭을 닫거나 이동할 때 마지막 일시정지 보고 (응답은 필요 없다)
  window.addEventListener('pagehide', function () {
    if (streamKey && navigator.sendBeacon) {
      navigator.sendBeacon(reportUrl, formBody(false));
    }
  });

  if (playBtn) {
    playBtn.addEventListener('click', function () {
      if (player.paused) {
        player.play();
      } else {
        player.pause();
      }
    });
  }
  if (skipBtn) {
    skipBtn.addEventListener('click', function () {
      // 건너뛴 구간은 서버가 재생 위치 변화로 가려내 적립하지 않는다
      player.currentTime = Math.min(player.currentTime + SKIP_SECONDS, player.duration || player.currentTime);
    });
  }
  if (speed) {
    speed.addEventListener('change', function () {
      player.playbackRate = parseFloat(speed.value);   // 배속은 서버 경과 시간만 인정되므로 화면 편의일 뿐이다
    });
  }
})();
