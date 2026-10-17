// nexplorer.idanck.com — the two public pages Google requires before the OAuth app can be published:
// a home page and a privacy policy. Static; no storage, no cookies, no logging of our own.

const STYLE = `body{font-family:system-ui,sans-serif;max-width:40rem;margin:2rem auto;padding:0 1rem;line-height:1.6;color:#1b1f24}
h1{color:#1565c0}a{color:#1565c0}`;

const page = (title, body) => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title><style>${STYLE}</style></head>
<body>${body}</body></html>`;

const HOME = page("nexplorer", `<h1>nexplorer</h1>
<p>nexplorer is a personal Android file manager. It browses local storage, network shares (SMB, SFTP, WebDAV)
and cloud storage (Google Drive, OneDrive) from one place.</p>
<p>It is built for the developer's own use and is not distributed in app stores.
Source code: <a href="https://github.com/idanck/nexplorer">github.com/idanck/nexplorer</a>.</p>
<p><a href="/privacy">Privacy policy</a></p>`);

const PRIVACY = page("nexplorer — Privacy policy", `<h1>Privacy policy</h1>
<p>Last updated: 17 October 2026</p>
<h2>Google Drive</h2>
<p>When you connect a Google account, nexplorer asks for the <code>https://www.googleapis.com/auth/drive</code> scope so it can
list, open, upload, rename, move and delete your Drive files — the same operations it offers for files on the phone.</p>
<ul>
<li>Your Drive data goes directly between your device and Google's servers. It is not sent to, stored on, or shared with any other server.</li>
<li>Sign-in and access tokens are handled by Google Play services on the device. nexplorer does not store your Google password or tokens.</li>
<li>Files are copied to the device only when you open or copy them, and temporary copies stay in the app's private storage.</li>
<li>nexplorer has no analytics, advertising or tracking, and does not sell or transfer user data.</li>
</ul>
<p>nexplorer's use and transfer of information received from Google APIs adheres to the
<a href="https://developers.google.com/terms/api-services-user-data-policy">Google API Services User Data Policy</a>,
including the Limited Use requirements.</p>
<h2>Removing access</h2>
<p>Remove the connection in the app, or revoke access at any time at
<a href="https://myaccount.google.com/permissions">myaccount.google.com/permissions</a>.</p>
<h2>Contact</h2>
<p><a href="mailto:idan.choen@gmail.com">idan.choen@gmail.com</a></p>`);

export default {
  async fetch(request) {
    const { pathname } = new URL(request.url);
    const html = pathname === "/" ? HOME : pathname === "/privacy" ? PRIVACY : null;
    if (request.method !== "GET" && request.method !== "HEAD") return new Response("Method not allowed", { status: 405 });
    if (!html) return new Response("Not found", { status: 404 });
    return new Response(html, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=3600" } });
  },
};
