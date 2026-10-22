/* Presentation only: never reads queries, extracts result URLs or calls native code. */
(() => {
    'use strict';
    const allowedPage = () => location.protocol === 'https:' &&
        (location.hostname === 'www.google.com' || location.hostname === 'google.com') &&
        (!location.port || location.port === '443') && location.pathname === '/search';
    if (!allowedPage() || window.__kineticaGifChrome) return;
    window.__kineticaGifChrome = true;

    const active = 'data-kinetica-gif-results';
    const hidden = 'data-kinetica-gif-chrome';
    const results = '#search, #rso, #islrg, main, [role="main"]';
    const protectedArea = results + ', dialog, [role="dialog"], [aria-modal="true"]';
    const challenge = '#captcha, #captcha-form, .g-recaptcha, iframe[src*="/recaptcha/"], ' +
        'form[action*="/sorry/"], form[action*="consent.google."]';
    // IDs cover Google's desktop/mobile header variants; semantic fallbacks handle
    // replacement nodes. Never hide a result, image-preview dialog or its ancestors.
    const controls = '#gb, #gbtop, #hdr, #searchform, #sfcnt, #tophf, #top_nav, #hdtb, #appbar, ' +
        '#logo, #logocont, img[alt="Google"], [aria-label="Google"], ' +
        'header, [role="banner"], [role="tablist"], ' +
        'form[role="search"], form[action="/search"], form[action^="/search?"], #tsf, ' +
        'a[href^="https://accounts.google.com/"], ' +
        '[aria-label="Main menu"], [aria-label="Menu principale"]';
    const style = document.createElement('style');
    style.textContent = `
        html[${active}] [${hidden}] { display: none !important; }
        html[${active}] body, html[${active}] #main, html[${active}] #cnt,
        html[${active}] #rcnt { padding-top: 0 !important; margin-top: 0 !important; }
    `;
    function update() {
        // Consent/security pages stay usable, including a same-document transition.
        if (!allowedPage() || document.querySelector(challenge)) {
            document.documentElement.removeAttribute(active);
            return;
        }
        if (!style.isConnected) (document.head || document.documentElement).appendChild(style);
        document.documentElement.setAttribute(active, '');
        document.querySelectorAll(controls + ', [' + hidden + ']').forEach(node => {
            if (node.matches(controls) && !node.closest(protectedArea) && !node.querySelector(protectedArea)) {
                node.setAttribute(hidden, '');
            } else {
                node.removeAttribute(hidden);
            }
        });
    }
    let pending = false;
    function schedule() {
        if (pending) return;
        pending = true;
        requestAnimationFrame(() => { pending = false; update(); });
    }
    update();
    // Google replaces headers during navigation and preview transitions. Observe
    // child changes, not our own marker attributes, to avoid an observer loop.
    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
    addEventListener('popstate', schedule);
})();
