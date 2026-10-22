// Local DOM regression check, not a snapshot of Google's current markup or an
// Android UI test. Requires playwright-core and its Chromium (or CHROMIUM_PATH).
// All requests are fulfilled locally; this test never contacts the fixture hosts.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright-core');
const script = fs.readFileSync(process.argv[2] || path.join(__dirname,
    '../app/src/main/assets/gif-search-chrome.js'), 'utf8');
const fixture = `<!doctype html><html><head><meta name="viewport" content="width=device-width"></head><body>
    <div id="gb"><button>Menu</button><a href="https://accounts.google.com/">Sign in</a></div>
    <div id="sfcnt"><a id="logo">Google</a><form action="/search"><input name="q"></form></div>
    <div id="top_nav"><div role="tablist">Images Videos</div></div>
    <div id="main" style="padding-top:120px"><div id="search"><a id="result">Result</a>
        <header id="result-header">Result header</header></div></div>
    <div role="dialog" id="preview"><header id="preview-header"><button>Close preview</button></header>
        <div role="tablist" id="preview-tabs">Preview tabs</div><a id="source">Visit source</a></div>
    </body></html>`;

(async () => {
    const browser = await chromium.launch({ headless: true,
        ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
    let checks = 0;
    try {
        const context = await browser.newContext({ viewport: { width: 412, height: 620 } });
        let content = fixture;
        await context.route('**/*', route => route.fulfill({ contentType: 'text/html', body: content }));
        const page = await context.newPage();
        const check = async (url, html = fixture) => {
            content = html;
            await page.goto(url);
            await page.evaluate(script);
        };
        const visible = async (selector, expected) => {
            await page.waitForFunction(({ selector, expected }) => {
                const node = document.querySelector(selector);
                return !!node && (node.getBoundingClientRect().height > 0) === expected;
            }, { selector, expected }, { timeout: 3000 });
            checks++;
        };
        await check('https://www.google.com/search?q=cats');
        for (const selector of ['#gb', '#sfcnt', '#top_nav']) await visible(selector, false);
        for (const selector of ['#result', '#result-header', '#preview-header', '#preview-tabs', '#source']) {
            await visible(selector, true);
        }
        assert.equal(await page.locator('#main').evaluate(node => getComputedStyle(node).paddingTop), '0px');
        checks++;

        // Late insertion/replacement (Google navigation), and idempotent reinjection.
        await page.evaluate(() => {
            document.querySelector('#gb').remove();
            const node = document.createElement('div');
            node.id = 'gb'; node.textContent = 'Replacement toolbar';
            document.body.prepend(node);
        });
        await visible('#gb', false);
        await page.evaluate(script);
        await visible('#gb', false);

        // A header reused inside a result or preview must stop being hidden.
        await page.evaluate(() => document.querySelector('#search').appendChild(document.querySelector('#gb')));
        await visible('#gb', true);

        // Semantic header variants without stable IDs, including a form and tabs.
        await check('https://google.com/search?udm=2', `<header id="mobile">Google logo / menu</header>
            <form role="search" id="query"><input name="q"></form>
            <nav role="tablist" id="types">Images Videos</nav>
            <a id="signin" href="https://accounts.google.com/ServiceLogin">Sign in</a>
            <main id="content"><header id="kept">A result</header></main>`);
        for (const selector of ['#mobile', '#query', '#types', '#signin']) await visible(selector, false);
        await visible('#kept', true);

        // Mobile layouts wrap both header and results in main. Their type row may
        // be navigation links rather than a tablist, and the logo can be an SVG.
        await check('https://www.google.com/search', `<main id="main" role="main">
            <header id="mobile-header"><svg id="hplogo" aria-label="Google" width="80" height="24"></svg>
                <form action="https://www.google.com/search"><input name="q" value="reaction GIF"></form></header>
            <nav id="mobile-types"><a href="/search?q=cats&udm=2">Images</a><a href="/search?q=cats&udm=7">Videos</a></nav>
            <div id="rso"><header id="mobile-result">Reaction GIFs</header><img width="120" height="120"></div>
            <div role="dialog"><header id="mobile-preview">Close / Visit</header></div>
            <nav id="pages"><a href="/search?udm=2&start=10">2</a><a href="/search?udm=2&start=20">3</a></nav></main>`);
        for (const selector of ['#mobile-header', '#mobile-types']) await visible(selector, false);
        for (const selector of ['#mobile-result', '#mobile-preview', '#pages']) await visible(selector, true);

        // Google's CSP may disallow injected style tags. Direct CSSOM changes must
        // still hide controls, and restore the original display on a challenge.
        await check('https://www.google.com/search', fixture.replace('<head>',
            '<head><meta http-equiv="Content-Security-Policy" content="style-src \'none\'">'));
        await visible('#gb', false); await visible('#sfcnt', false);
        await page.evaluate(() => {
            const challenge = document.createElement('form'); challenge.id = 'captcha-form';
            challenge.textContent = 'Verify'; document.body.appendChild(challenge);
        });
        await visible('#gb', true); await visible('#sfcnt', true);

        // Never hide a container that also contains results.
        await check('https://www.google.com/search', '<header id="gb">Mixed header<main>Results</main></header>');
        await visible('#gb', true);

        for (const url of ['https://www.google.com/sorry/index', 'https://consent.google.com/search',
            'https://accounts.google.com/search', 'https://example.com/search',
            'https://www.google.com.example.com/search', 'http://www.google.com/search',
            'https://www.google.com:444/search']) {
            await check(url);
            await visible('#gb', true);
            await visible('#sfcnt', true);
        }

        // A challenge on /search, both at first load and inserted after results.
        await check('https://www.google.com/search', fixture.replace('</body>',
            '<form id="captcha-form"><button>Verify</button></form></body>'));
        await visible('#gb', true); await visible('#captcha-form', true);
        await check('https://www.google.com/search');
        await visible('#gb', false);
        await page.evaluate(() => {
            const captcha = document.createElement('form');
            captcha.id = 'captcha-form'; captcha.textContent = 'Verify';
            document.body.appendChild(captcha);
        });
        await visible('#gb', true); await visible('#captcha-form', true);
        console.log(`PASS: ${checks} Google-header fixture checks (offline DOM only).`);
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
