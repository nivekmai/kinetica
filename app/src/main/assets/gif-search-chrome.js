/* Presentation only: never reads queries, extracts result URLs or calls native code. */
(() => {
    'use strict';
    const allowedPage = () => location.protocol === 'https:' &&
        (location.hostname === 'www.google.com' || location.hostname === 'google.com') &&
        (!location.port || location.port === '443') && location.pathname === '/search';
    if (!allowedPage()) return;
    if (window.__kineticaGifChrome) { window.__kineticaGifChrome(); return; }

    // Mobile Google can put the header INSIDE main. Only actual result regions
    // and previews protect descendants; a main wrapper alone must not protect UI.
    const results = '#search, #rso, #islrg, [data-docid], [data-ri]';
    const protectedArea = results + ', dialog, [role="dialog"], [aria-modal="true"]';
    const challenge = '#captcha, #captcha-form, .g-recaptcha, iframe[src*="/recaptcha/"], ' +
        'form[action*="/sorry/"], form[action*="consent.google."]';
    // IDs cover Google's desktop/mobile header variants; semantic fallbacks handle
    // replacement nodes. Never hide a result, image-preview dialog or its ancestors.
    const controls = '#gb, #gbtop, #hdr, #searchform, #sfcnt, #tophf, #top_nav, #hdtb, #appbar, ' +
        '#logo, #hplogo, #logocont, img[alt="Google"], [aria-label="Google"], ' +
        '[role="tablist"], [role="search"], input[name="q"], textarea[name="q"], ' +
        'form[action="/search"], form[action^="/search?"], #tsf, ' +
        'a[href^="https://accounts.google.com/"], ' +
        '[aria-label="Main menu"], [aria-label="Menu principale"]';
    const containers = 'header, [role="banner"], nav, [role="navigation"], form';
    const queryFields = 'input[name="q"], textarea[name="q"], input[type="search"], ' +
        '[role="combobox"][aria-haspopup="listbox"], [role="searchbox"]';
    const queryBoundary = 'html, body, main, [role="main"], header, [role="banner"], ' +
        protectedArea;
    const nonQueryContent = protectedArea + ', main, [role="main"], nav, [role="navigation"], ' +
        '[role="tablist"], #logo, #hplogo, #logocont, [aria-label="Google"], img, video, canvas, iframe';
    const original = new Map();
    const roundedShells = new WeakSet();
    let updateError = null;
    function roundedQueryShell(node) {
        // Mobile query bars can also contain text/icon spans with no button role.
        // Recognize that painted bar, but never a result, preview or linked card.
        if (node.matches(nonQueryContent) || node.querySelector(nonQueryContent + ', a[href]')) return false;
        const css = getComputedStyle(node);
        if (parseFloat(css.borderTopLeftRadius) < 8 || parseFloat(css.borderTopRightRadius) < 8) return false;
        const box = node.getBoundingClientRect();
        // CSS pixels: a compact query row, not a rounded results/gallery container.
        if (box.width >= 160 && box.height >= 24 && box.height <= 96) roundedShells.add(node);
        return roundedShells.has(node);
    }
    function queryShell(field) {
        // Hiding only the input leaves Google's rounded background, magnifier and
        // clear button behind. Follow its query-only wrappers, including wrappers
        // OUTSIDE the form. Do not rely on Google's generated class names or on
        // dimensions (which change after we hide the input).
        function queryOnly(node) {
            if (node.nodeType === Node.TEXT_NODE) return !node.textContent.trim();
            if (node.nodeType !== Node.ELEMENT_NODE) return true;
            if (node.matches(queryFields)) return true;
            if (node.matches('script, style, input[type="hidden"]')) return true;
            if (node.matches(nonQueryContent) || node.querySelector(nonQueryContent)) return false;
            // Icon controls may contain localized accessible labels. Links and
            // free-standing text are not assumed to be part of the query bar.
            if (node.matches('button, [role="button"], svg, input[type="submit"]')) return true;
            if (node.matches('a, input, textarea, select')) return false;
            return Array.from(node.childNodes).every(queryOnly);
        }
        let shell = field;
        for (let parent = field.parentElement; parent; parent = parent.parentElement) {
            if (parent.matches(queryBoundary) || !(queryOnly(parent) || roundedQueryShell(parent))) break;
            shell = parent;
        }
        return shell;
    }
    function typeNavigation(node) {
        if (!node.matches('nav, [role="navigation"]')) return false;
        const types = new Set();
        Array.from(node.querySelectorAll('a[href]')).forEach(link => {
            let url;
            try { url = new URL(link.href, location.href); } catch (_) { return; }
            if (url.origin === location.origin && url.pathname === '/search') {
                types.add(url.searchParams.get('udm') || url.searchParams.get('tbm') || 'all');
            }
        });
        // Pagination keeps the same search type; do not remove next-page controls.
        return types.size > 1;
    }
    function apply(desired) {
        // Inline CSSOM writes also work on pages whose CSP rejects injected style
        // elements. Preserve and restore each original declaration on transitions.
        original.forEach((props, node) => {
            props.forEach((value, property) => {
                if (desired.has(node) && desired.get(node).has(property)) return;
                if (value[0]) node.style.setProperty(property, value[0], value[1]);
                else node.style.removeProperty(property);
                props.delete(property);
            });
            if (!props.size) original.delete(node);
        });
        desired.forEach((props, node) => {
            if (!original.has(node)) original.set(node, new Map());
            props.forEach((value, property) => {
                const saved = original.get(node);
                if (!saved.has(property)) saved.set(property,
                    [node.style.getPropertyValue(property), node.style.getPropertyPriority(property)]);
                if (node.style.getPropertyValue(property) !== value || node.style.getPropertyPriority(property) !== 'important') {
                    node.style.setProperty(property, value, 'important');
                }
            });
        });
    }
    function update() {
        // Consent/security pages stay usable, including a same-document transition.
        if (!allowedPage() || document.querySelector(challenge)) {
            apply(new Map());
            return;
        }
        const desired = new Map();
        document.querySelectorAll(controls + ', ' + containers).forEach(node => {
            const header = node.matches(controls) || typeNavigation(node) ||
                (node.matches('header, [role="banner"], form') && node.querySelector(controls)) ||
                (node.matches('header, [role="banner"]') && !node.closest('main, [role="main"]'));
            if (header && !node.closest(protectedArea) &&
                !node.querySelector(protectedArea + ', main, [role="main"]')) {
                desired.set(node, new Map([['display', 'none']]));
            }
        });
        document.querySelectorAll(queryFields).forEach(field => {
            // A results wrapper may enclose the search bar too. Only individual
            // results and preview dialogs protect query fields from this pass.
            if (!field.closest('[data-docid], [data-ri], dialog, [role="dialog"], [aria-modal="true"]')) {
                desired.set(queryShell(field), new Map([['display', 'none']]));
            }
        });
        document.querySelectorAll('body, #main, #cnt, #rcnt').forEach(node => {
            desired.set(node, new Map([['padding-top', '0px'], ['margin-top', '0px']]));
        });
        apply(desired);
    }
    let pending = false;
    function safeUpdate() {
        try { update(); updateError = null; }
        catch (error) { updateError = error instanceof TypeError ? 'TypeError' : 'update_failed'; }
    }
    function schedule() {
        if (pending) return;
        pending = true;
        requestAnimationFrame(() => { pending = false; safeUpdate(); });
    }
    window.__kineticaGifChrome = schedule;
    // Explicit diagnostic export only. No values, text, URLs, labels or page HTML.
    window.__kineticaGifChromeReport = () => {
        const describe = node => {
            const box = node.getBoundingClientRect(), css = getComputedStyle(node);
            return {tag: node.tagName, role: node.getAttribute('role'),
                query: node.matches(queryFields), result: node.matches(protectedArea),
                hiddenByUs: original.get(node)?.has('display') || false,
                rect: [box.x, box.y, box.width, box.height].map(Math.round),
                display: css.display, radius: css.borderTopLeftRadius,
                children: Array.from(node.children).slice(0, 16).map(child => ({tag: child.tagName,
                    role: child.getAttribute('role'), query: child.matches(queryFields),
                    result: child.matches(protectedArea)}))};
        };
        const fields = Array.from(document.querySelectorAll(queryFields)).slice(0, 8);
        return {version: 25, allowed: allowedPage(), challenge: !!document.querySelector(challenge), updateError,
            hidden: original.size, fields: fields.map(field => {
                const ancestors = [];
                for (let node = field; node && ancestors.length < 8; node = node.parentElement) ancestors.push(describe(node));
                return ancestors;
            }), top: Array.from(document.querySelectorAll('div, form, input, textarea, header'))
                .filter(node => { const b = node.getBoundingClientRect();
                    return b.y >= 0 && b.y < 260 && b.width >= 160 && b.height >= 24 && b.height <= 96;
                }).slice(0, 24).map(describe)};
    };
    safeUpdate();
    // Writes are idempotent, so watching style changes cannot create a feedback loop.
    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true,
        attributes: true, attributeFilter: ['id', 'class', 'name', 'type', 'role', 'style', 'aria-label', 'action', 'href'] });
    addEventListener('popstate', schedule);
})();
