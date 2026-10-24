/* Smart Library: small progressive-enhancement helpers (no framework). */
(function () {
    'use strict';

    // ---- mobile sidebar toggle ----
    var toggle = document.getElementById('sidebarToggle');
    var sidebar = document.getElementById('appSidebar');
    if (toggle && sidebar) {
        toggle.addEventListener('click', function () { sidebar.classList.toggle('open'); });
    }

    // ---- confirmation dialog for any <form data-confirm="message"> ----
    var modalEl = document.getElementById('confirmModal');
    if (modalEl && window.bootstrap) {
        var modal = new bootstrap.Modal(modalEl);
        var pendingForm = null;
        document.addEventListener('submit', function (ev) {
            var form = ev.target;
            if (form && form.dataset && form.dataset.confirm && !form.dataset.confirmed) {
                ev.preventDefault();
                pendingForm = form;
                document.getElementById('confirmMessage').textContent = form.dataset.confirm;
                modal.show();
            }
        });
        document.getElementById('confirmOk').addEventListener('click', function () {
            if (pendingForm) {
                pendingForm.dataset.confirmed = 'true';
                modal.hide();
                pendingForm.submit();
            }
        });
    }

    // ---- UID inputs: show uppercase, allow pasted separators ----
    document.querySelectorAll('input.uid-input').forEach(function (el) {
        el.addEventListener('input', function () { el.value = el.value.toUpperCase(); });
    });

    // ---- success alerts fade away on their own ----
    document.querySelectorAll('.alert-success').forEach(function (el) {
        setTimeout(function () {
            if (window.bootstrap) { bootstrap.Alert.getOrCreateInstance(el).close(); }
        }, 8000);
    });

    // ---- disable submit buttons after first click to stop double submissions ----
    document.querySelectorAll('form').forEach(function (form) {
        form.addEventListener('submit', function (ev) {
            // Decide AFTER all handlers ran: the confirm dialog may have cancelled this submit.
            setTimeout(function () {
                if (ev.defaultPrevented) { return; }
                form.querySelectorAll('button[type=submit]').forEach(function (b) { b.disabled = true; });
            }, 0);
        });
    });
})();

/* ---- RFID helpers available on every page ---- */
(function () {
    'use strict';
    var apiMeta = document.querySelector('meta[name=api-base]');
    if (!apiMeta) { return; }
    var base = apiMeta.content;

    function getJson(path) {
        return fetch(base + path, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (r) { if (!r.ok) { throw new Error('HTTP ' + r.status); } return r.json(); });
    }

    // ---- reader badge in the top bar ----
    var badge = document.getElementById('readerBadge');
    if (badge) {
        var txt = badge.querySelector('.txt');
        var cls = { ONLINE: 'ok', CONNECTING: 'warn', NO_DATA: 'warn', READER_ERROR: 'bad', DISCONNECTED: 'bad', DISABLED: 'off' };
        var label = { ONLINE: 'online', CONNECTING: 'connecting', NO_DATA: 'no data', READER_ERROR: 'error', DISCONNECTED: 'offline', DISABLED: 'disabled' };
        var apply = function (s) {
            badge.className = 'reader-badge ' + (cls[s.state] || 'off');
            txt.textContent = 'Reader ' + (label[s.state] || s.state) + (s.port && s.state === 'ONLINE' ? ' (' + s.port + ')' : '');
            badge.title = s.message || '';
        };
        window.addEventListener('reader-status', function (e) { apply(e.detail); });
        var refresh = function () {
            getJson('/status').then(apply).catch(function () {
                badge.className = 'reader-badge bad';
                txt.textContent = 'Server unreachable';
            });
        };
        refresh();
        setInterval(refresh, 4000);
    }

    // ---- "Scan card/tag" buttons: <button data-capture-target="#inputId" data-capture-kind="member|book"> ----
    function sleep(ms) { return new Promise(function (res) { setTimeout(res, ms); }); }

    document.querySelectorAll('[data-capture-target]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var input = document.querySelector(btn.dataset.captureTarget);
            var wrap = btn.closest('[data-capture-wrap]') || btn.parentElement;
            var status = wrap.querySelector('[data-capture-status]');
            var kind = btn.dataset.captureKind;   // what this field accepts: 'member' or 'book'
            if (!input || !status) { return; }

            btn.disabled = true;
            status.className = 'small text-primary ms-2';
            status.textContent = 'Hold the card/tag on the reader now (20 s)...';

            getJson('/last-uid').then(function (first) {
                var since = Date.parse(first.serverNow);        // server clock, immune to PC clock differences
                var deadline = Date.now() + 20000;
                var loop = function () {
                    if (Date.now() > deadline) { return Promise.resolve(null); }
                    return getJson('/last-uid?since=' + since).then(function (r) {
                        return r.uid ? r : sleep(600).then(loop);
                    });
                };
                return loop();
            }).then(function (r) {
                if (!r) {
                    status.className = 'small text-danger ms-2';
                    status.textContent = 'No scan received. Check the reader status badge at the top of the page.';
                    return;
                }
                input.value = r.uid;
                input.dispatchEvent(new Event('input', { bubbles: true }));
                var taken = (r.kind === 'MEMBER' || r.kind === 'BOOK');
                var thisKind = kind === 'member' ? 'MEMBER' : 'BOOK';
                if (taken) {
                    status.className = 'small text-warning-emphasis ms-2';
                    status.textContent = 'Captured ' + r.uid + ', but it is already registered to ' + (r.label || 'another record') + '.';
                } else {
                    status.className = 'small text-success ms-2';
                    status.textContent = 'Captured ' + r.uid + '. Save the form to assign it.';
                }
            }).catch(function () {
                status.className = 'small text-danger ms-2';
                status.textContent = 'Could not reach the reader service.';
            }).then(function () { btn.disabled = false; });
        });
    });
})();
