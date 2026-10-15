// nexplorer update channel — nexplorer-updates.idanck.com
//
// Serves the update manifest and APKs from the R2 bucket bound as BUCKET, and accepts uploads
// from CI on PUT /publish/<key> with a bearer token (secret UPLOAD_TOKEN). The app fetches its
// builds from here, not from GitHub, so it never carries a credential and keeps working
// whatever the repository's visibility.
//
// Same shape as the tivicopy-updates worker, with one fix: a 206 is returned only when the
// request actually asked for a range (an unconditional 206 broke a client that accepts 200 only).

const APK_TYPE = "application/vnd.android.package-archive";
const SAFE = /^[A-Za-z0-9._\/-]{1,120}$/;

function harden(headers) {
  headers.set("x-robots-tag", "noindex, nofollow");
  headers.set("x-content-type-options", "nosniff");
  return headers;
}

function plain(status, text) {
  return new Response(text, { status, headers: harden(new Headers()) });
}

// Constant-time for equal lengths, so the token can't be guessed byte by byte from response times.
function sameSecret(given, expected) {
  const encoder = new TextEncoder();
  const a = encoder.encode(given);
  const b = encoder.encode(expected);
  if (a.byteLength !== b.byteLength) return false;
  return crypto.subtle.timingSafeEqual(a, b);
}

function keyFor(path) {
  if (path === "/latest.json") return "latest.json";
  if (path.startsWith("/apk/")) return path.slice(1);
  return null;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    let path;
    try {
      path = decodeURIComponent(url.pathname);
    } catch {
      // A malformed escape (e.g. "%E0%A4%A") is the client's mistake, not a 500.
      return plain(400, "bad path");
    }

    if (request.method === "PUT" && path.startsWith("/publish/")) {
      const token = env.UPLOAD_TOKEN;
      const auth = request.headers.get("authorization") || "";
      if (!token || !sameSecret(auth, "Bearer " + token)) return plain(401, "unauthorized");
      const key = path.slice("/publish/".length);
      if (!SAFE.test(key) || key.includes("..") || !keyFor("/" + key)) return plain(400, "bad key");
      const contentType = key.endsWith(".apk") ? APK_TYPE : "application/json; charset=utf-8";
      await env.BUCKET.put(key, request.body, { httpMetadata: { contentType } });
      return new Response(JSON.stringify({ ok: true, key }), {
        headers: harden(new Headers({ "content-type": "application/json" })),
      });
    }

    if (request.method !== "GET" && request.method !== "HEAD") return plain(405, "method not allowed");

    const key = keyFor(path);
    if (!key || !SAFE.test(key) || key.includes("..")) return plain(404, "not found");

    const cacheControl = key.endsWith(".json") ? "public, max-age=60" : "public, max-age=31536000, immutable";

    if (request.method === "HEAD") {
      const head = await env.BUCKET.head(key);
      if (!head) return new Response(null, { status: 404, headers: harden(new Headers()) });
      const h = new Headers();
      head.writeHttpMetadata(h);
      h.set("etag", head.httpEtag);
      h.set("content-length", String(head.size));
      h.set("accept-ranges", "bytes");
      h.set("cache-control", cacheControl);
      return new Response(null, { status: 200, headers: harden(h) });
    }

    const wantsRange = request.headers.has("range");
    const object = await env.BUCKET.get(key, {
      range: wantsRange ? request.headers : undefined,
      onlyIf: request.headers,
    });
    if (!object) return plain(404, "not found");

    const h = new Headers();
    object.writeHttpMetadata(h);
    h.set("etag", object.httpEtag);
    h.set("accept-ranges", "bytes");
    h.set("cache-control", cacheControl);

    if (!("body" in object)) return new Response(null, { status: 304, headers: harden(h) });

    let status = 200;
    if (wantsRange && object.range && "offset" in object.range) {
      const start = object.range.offset || 0;
      const length = object.range.length === undefined ? object.size - start : object.range.length;
      h.set("content-range", "bytes " + start + "-" + (start + length - 1) + "/" + object.size);
      h.set("content-length", String(length));
      status = 206;
    } else {
      h.set("content-length", String(object.size));
    }
    return new Response(object.body, { status, headers: harden(h) });
  },
};
