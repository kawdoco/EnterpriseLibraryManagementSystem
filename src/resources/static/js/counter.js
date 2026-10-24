/* RFID counter: ONE guided panel that always says what to do next.
   Live data comes from the Spring Boot backend (SSE, with polling fallback).
   The browser never talks to the Arduino: the Java backend owns the serial port. */
(function () {
    'use strict';

    var app = document.getElementById('counterApp');
    if (!app) { return; }

    var base = app.dataset.base;
    var currency = app.dataset.currency || '';
    var csrfToken = (document.querySelector('meta[name=_csrf]') || {}).content || '';
    var csrfHeader = (document.querySelector('meta[name=_csrf_header]') || {}).content || 'X-CSRF-TOKEN';
    var RESULT_MS = 7000;          // how long a result stays on screen before the panel moves on

    var $ = function (id) { return document.getElementById(id); };
    var state = null;              // latest snapshot from the server
    var clockOffset = 0;           // serverTime - browser time
    var lastSeq = null;
    var expiresAt = null;
    var pollTimer = null;
    var currentMode = null;

    var MODE_HELP = {
        AUTO: 'Tap a member card, then books to borrow. With no member selected, tapping a borrowed book returns it.',
        BORROW: 'Tap the member card first, then each book to lend.',
        RETURN: 'Every book tap is a return. No member card needed.'
    };
    var IDLE_TEXT = {
        AUTO: 'Member card to borrow \u2022 Book alone to return',
        BORROW: 'Tap the member\'s card, then the books to lend',
        RETURN: 'Tap each book to return it'
    };
    var STATE_STYLE = {
        ONLINE: ['text-bg-success', 'ONLINE'], CONNECTING: ['text-bg-warning', 'CONNECTING'],
        NO_DATA: ['text-bg-warning', 'NO DATA'], READER_ERROR: ['text-bg-danger', 'READER ERROR'],
        DISCONNECTED: ['text-bg-danger', 'DISCONNECTED'], DISABLED: ['text-bg-secondary', 'DISABLED']
    };
    var ICON = {
        success: 'bi-check-circle-fill', info: 'bi-person-check-fill', warning: 'bi-exclamation-triangle-fill',
        danger: 'bi-x-octagon-fill', idle: 'bi-broadcast'
    };

    // ------------------------------------------------------------ helpers
    function el(tag, cls, text) {
        var e = document.createElement(tag);
        if (cls) { e.className = cls; }
        if (text !== undefined && text !== null) { e.textContent = text; }
        return e;
    }
    function setText(id, v) { $(id).textContent = (v === null || v === undefined || v === '') ? '-' : v; }
    function show(node, on) { node.classList.toggle('d-none', !on); }
    function money(v) {
        if (v === null || v === undefined) { return '-'; }
        return currency + ' ' + Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    function fmtDate(d) {
        if (!d) { return '-'; }
        var p = String(d).split('-');
        return p.length === 3 ? (parseInt(p[2], 10) + ' ' + MONTHS[parseInt(p[1], 10) - 1] + ' ' + p[0]) : d;
    }
    function fmtTime(d) {
        if (!d) { return '-'; }
        var t = new Date(d);
        return isNaN(t) ? '-' : t.toLocaleTimeString('en-GB');
    }

    function api(path, method, body) {
        var opts = { method: method || 'GET', credentials: 'same-origin', headers: { 'Accept': 'application/json' } };
        if (opts.method !== 'GET') {
            opts.headers['Content-Type'] = 'application/json';
            opts.headers[csrfHeader] = csrfToken;
            opts.body = JSON.stringify(body || {});
        }
        return fetch(base + path, opts).then(function (r) {
            return r.text().then(function (txt) {
                var data = null;
                try { data = txt ? JSON.parse(txt) : null; } catch (e) { /* not JSON */ }
                if (!r.ok) {
                    var err = new Error((data && data.message) || ('Request failed (' + r.status + ')'));
                    err.status = r.status;
                    throw err;
                }
                return data;
            });
        });
    }

    // ------------------------------------------------------------ sound
    var audio = null;
    function beep(ok) {
        if (!$('soundToggle').checked) { return; }
        try {
            audio = audio || new (window.AudioContext || window.webkitAudioContext)();
            var tones = ok ? [[880, 0, 0.12]] : [[330, 0, 0.14], [260, 0.18, 0.2]];
            tones.forEach(function (t) {
                var o = audio.createOscillator(), g = audio.createGain();
                o.frequency.value = t[0]; o.connect(g); g.connect(audio.destination);
                g.gain.setValueAtTime(0.15, audio.currentTime + t[1]);
                o.start(audio.currentTime + t[1]); o.stop(audio.currentTime + t[1] + t[2]);
            });
        } catch (e) { /* audio not available: ignore */ }
    }

    // ------------------------------------------------------------ small widgets
    function renderReader(r) {
        var st = STATE_STYLE[r.state] || ['text-bg-secondary', r.state];
        var badge = $('readerState');
        badge.className = 'badge ' + st[0];
        badge.textContent = st[1];
        setText('readerMessage', r.message);
        setText('readerPort', r.port);
        window.dispatchEvent(new CustomEvent('reader-status', { detail: r }));   // keeps the top-bar badge in sync
    }

    function renderMode(mode) {
        currentMode = mode;
        var radio = $('mode' + mode);
        if (radio && !radio.checked) { radio.checked = true; }
        setText('modeHelp', MODE_HELP[mode]);
    }

    function badgeSpan(text, cls) { return el('span', 'badge ' + cls + ' me-1', text); }

    function renderMemberChip(m, seconds) {
        var chip = $('memberChip');
        show(chip, !!m);
        $('clearMember').disabled = !m;
        if (!m) { expiresAt = null; return; }
        setText('mName', m.fullName);
        setText('mCode', m.memberCode);
        setText('mLoans', m.activeLoans + ' of ' + m.maxLoans);
        setText('mFines', money(m.outstandingFines));
        var badges = $('mBadges');
        badges.textContent = '';
        if (m.blacklisted) { badges.appendChild(badgeSpan('BLACKLISTED', 'text-bg-danger')); }
        if (!m.active) { badges.appendChild(badgeSpan('INACTIVE', 'text-bg-secondary')); }
        expiresAt = (seconds === null || seconds === undefined) ? null : Date.now() + seconds * 1000;
        updateCountdown();
    }

    function updateCountdown() {
        if (expiresAt === null) { return; }
        $('mCountdown').textContent = Math.max(0, Math.round((expiresAt - Date.now()) / 1000)) + ' s';
    }

    function renderRecent(list) {
        var body = $('recentBody');
        body.textContent = '';
        if (!list || !list.length) {
            var tr = el('tr'); var td = el('td', 'text-center text-muted py-3', 'No scans yet.'); td.colSpan = 4;
            tr.appendChild(td); body.appendChild(tr); return;
        }
        list.forEach(function (ev) {
            var tr = el('tr');
            tr.appendChild(el('td', 'small text-nowrap', fmtTime(ev.at)));
            tr.appendChild(el('td', 'uid', ev.uid));
            var res = el('td');
            res.appendChild(el('span', 'badge ' + (ev.success ? 'text-bg-success' : 'text-bg-danger'), ev.title));
            tr.appendChild(res);
            tr.appendChild(el('td', 'small', ev.message));
            body.appendChild(tr);
        });
    }

    // ------------------------------------------------------------ THE panel
    function setStage(tone, title, text, uid) {
        $('stage').className = 'card stage-card stage-' + tone;
        $('stageIcon').className = 'bi stage-icon ' + ICON[tone];
        setText('stageTitle', title);
        setText('stageText', text);
        $('stageUid').textContent = uid ? 'UID ' + uid : '';
    }

    function toggleRows(cls, on) {
        document.querySelectorAll('#resultDetails .' + cls).forEach(function (n) { n.classList.toggle('d-none', !on); });
    }

    function showResult(ev) {
        var tone = ev.success ? 'success' : 'danger';
        if (ev.action === 'NEED_MEMBER') { tone = 'warning'; }
        if (ev.action === 'RETURNED' && ev.fineAmount && Number(ev.fineAmount) > 0) { tone = 'warning'; }
        setStage(tone, ev.title, ev.message, (ev.action === 'BORROWED' || ev.action === 'RETURNED') ? '' : ev.uid);

        var borrowed = ev.action === 'BORROWED', returned = ev.action === 'RETURNED';
        var late = returned && ev.overdueDays && Number(ev.overdueDays) > 0;
        show($('resultDetails'), borrowed || returned);
        toggleRows('row-book', !!ev.book);
        toggleRows('row-member', !!ev.member);
        toggleRows('row-due', borrowed);
        toggleRows('row-late', !!late);
        toggleRows('row-fine', !!late);
        if (ev.book) { setText('rBook', ev.book.title + (ev.book.author ? ' - ' + ev.book.author : '')); }
        if (ev.member) { setText('rMember', ev.member.fullName + ' (' + ev.member.memberCode + ')'); }
        setText('rDue', fmtDate(ev.dueDate));
        setText('rLate', ev.overdueDays);
        setText('rFine', money(ev.fineAmount));
    }

    function showMember(m, mode) {
        var tone = 'info', title = m.fullName, text = 'Now tap the book to borrow.';
        if (mode === 'RETURN') { text = 'Return mode is on: tap books to return them.'; }
        if (!m.active) { tone = 'danger'; title = m.fullName + ' is inactive'; text = 'This member cannot borrow books.'; }
        else if (m.blacklisted) { tone = 'danger'; title = m.fullName + ' is blacklisted'; text = 'Cannot borrow. Returns are still accepted.'; }
        else if (m.activeLoans >= m.maxLoans) { tone = 'warning'; title = m.fullName; text = 'Loan limit reached (' + m.maxLoans + ' books). Return a book first.'; }
        setStage(tone, title, text, '');
        show($('resultDetails'), false);
    }

    function showIdle(s) {
        if (s.reader.state !== 'ONLINE' && s.reader.state !== 'DISABLED') {
            setStage('warning', 'Reader not ready', s.reader.message, '');
        } else if (s.reader.state === 'DISABLED') {
            setStage('warning', 'Reader disabled', s.reader.message, '');
        } else {
            setStage('idle', 'Tap a card or a book', IDLE_TEXT[s.mode] || IDLE_TEXT.AUTO, '');
        }
        show($('resultDetails'), false);
    }

    /** Decides which of the three views to show. Runs on every update and twice a second. */
    function renderStage() {
        var s = state;
        if (!s) { return; }
        var ev = s.lastEvent;
        var age = ev ? (Date.now() + clockOffset) - Date.parse(ev.at) : Infinity;
        var freshResult = ev && ev.action !== 'MEMBER_SELECTED' && age < RESULT_MS;

        renderMemberChip(s.member, s.memberExpiresInSeconds);
        if (freshResult) { showResult(ev); }
        else if (s.member) { showMember(s.member, s.mode); }
        else { showIdle(s); }
    }

    function render(s) {
        show($('loadingBar'), false);
        state = s;
        clockOffset = Date.parse(s.serverTime) - Date.now();
        renderReader(s.reader);
        renderMode(s.mode);
        renderRecent(s.recent);
        renderStage();
        if (lastSeq !== null && s.lastEvent && s.lastEvent.seq > lastSeq) { beep(s.lastEvent.success); }
        lastSeq = s.seq;
    }

    // ------------------------------------------------------------ live connection (SSE + polling fallback)
    function poll() {
        api('/state?role=counter').then(render).catch(function () { /* keep retrying */ });
    }
    function startPolling() { if (!pollTimer) { poll(); pollTimer = setInterval(poll, 1500); } }
    function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } }

    function connect() {
        if (!window.EventSource) { show($('linkLost'), true); startPolling(); return; }
        var source = new EventSource(base + '/stream?role=counter');
        source.addEventListener('state', function (e) {
            try { render(JSON.parse(e.data)); } catch (err) { /* ignore malformed frame */ }
        });
        source.onopen = function () { show($('linkLost'), false); stopPolling(); };
        source.onerror = function () { show($('linkLost'), true); startPolling(); };
    }

    // ------------------------------------------------------------ ports + actions
    function loadPorts() {
        api('/ports').then(function (ports) {
            var sel = $('portSelect');
            sel.textContent = '';
            var auto = el('option', null, 'Auto-detect'); auto.value = ''; sel.appendChild(auto);
            ports.forEach(function (p) {
                var o = el('option', null, p.name + (p.description ? ' - ' + p.description : '')); o.value = p.name;
                if (p.inUse) { o.selected = true; }
                sel.appendChild(o);
            });
        }).catch(function () { /* leave list empty */ });
    }

    function alertError(err) { $('manualError').textContent = err.message; }

    document.querySelectorAll('input[name=mode]').forEach(function (r) {
        r.addEventListener('change', function () {
            if (r.checked && r.value !== currentMode) { api('/mode', 'POST', { mode: r.value }).then(render).catch(alertError); }
        });
    });
    $('clearMember').addEventListener('click', function () { api('/clear-member', 'POST').then(render).catch(alertError); });
    $('portApply').addEventListener('click', function () { api('/port', 'POST', { port: $('portSelect').value }).catch(alertError); });
    $('portRefresh').addEventListener('click', loadPorts);
    $('reconnectBtn').addEventListener('click', function () { api('/reconnect', 'POST').catch(alertError); });

    $('manualForm').addEventListener('submit', function (ev) {
        ev.preventDefault();
        $('manualError').textContent = '';
        api('/manual', 'POST', { uid: $('manualUid').value }).then(function () {
            $('manualUid').value = '';
            $('manualUid').focus();
        }).catch(function (err) { $('manualError').textContent = err.message; })
          .then(function () {
              $('manualForm').querySelectorAll('button[type=submit]').forEach(function (b) { b.disabled = false; });
          });
    });

    setInterval(function () { updateCountdown(); renderStage(); }, 500);
    loadPorts();
    connect();
})();
