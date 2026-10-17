#!/usr/bin/env python3
# שרת מדומה ל-Google Drive (Drive API v3) ול-OneDrive (Microsoft Graph) — לבדיקות שלב 6 (Phase6Test) ולבדיקה
# של DriveFs ו-OneDriveFs מחוץ לאמולטור. רק מה שהאפליקציה משתמשת בו, ובאותם כללים כמו השירות האמיתי:
#   Drive: לפי ID, שמות כפולים מותרים, / בשם, Google Docs (ייצוא בלבד), קיצורי דרך, resumable upload (308 + Range).
#   Graph: לפי נתיב (root:/a/b:/children), שמות ייחודיים בלי רישיות, conflictBehavior=fail, upload session בגושים
#   של כפולות 320KB ובלי Authorization, /content מפנה (302) לכתובת הורדה, OneNote (package) בשורש.
#   הורדה (alt=media, /download) עם `Range: bytes=N-` — 206 מהמקום, 416 מעבר לסוף.
# אסימון: Bearer qa-token (אחר — 401). דפים קטנים (3) כדי שהדפדוף בין דפים ייבדק.
#   python3 cloud-server.py [--port 8090]
# בקרה לבדיקות: POST /qa/reset (חזרה לנתוני ההתחלה), POST /qa/throttle?count=N (N בקשות הבאות — 429).
# וגם נקודת האסימונים של Microsoft (/consumers/oauth2/v2.0/token) ל-MicrosoftAuth, עם client_id ‏qa-client.
import argparse
import json
import re
import struct
import threading
import time
import urllib.parse
import uuid
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = "qa-token"
ACCOUNT = "alex@example.com"
PAGE = 3
QUOTA = 15 * 1024 ** 3
FOLDER = "application/vnd.google-apps.folder"
SHORTCUT = "application/vnd.google-apps.shortcut"
DOC = "application/vnd.google-apps.document"
FORM = "application/vnd.google-apps.form"
EXPORTS = {DOC: "application/vnd.openxmlformats-officedocument.wordprocessingml.document"}
CHUNK_UNIT = 320 * 1024

lock = threading.RLock()
state = {}


def png(r, g, b, size=64):
    raw = b"".join(b"\x00" + bytes([r, g, b]) * size for _ in range(size))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)) + \
        chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def iso(t):
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(t)) + ".000Z"


def parse_iso(value):
    m = re.match(r"(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d):(\d\d)", value)
    if not m:
        return None
    import calendar
    return calendar.timegm(tuple(int(x) for x in m.groups()) + (0, 0, 0))


# ---------- הנתונים ----------

def node(store, name, parent, mime=None, data=None, folder=False, created=None, **extra):
    nid = uuid.uuid4().hex[:12]
    now = created if created is not None else time.time()
    store[nid] = dict(id=nid, name=name, parent=parent, folder=folder, mime=mime or ("" if folder else "application/octet-stream"),
                      data=data if data is not None else b"", created=now, modified=now, trashed=False, **extra)
    return nid


def seed():
    with lock:
        drive = {}
        root = node(drive, "My Drive", None, mime=FOLDER, folder=True)
        qa = node(drive, "nexplorer-qa", root, mime=FOLDER, folder=True)
        node(drive, "photo.png", qa, mime="image/png", data=png(0x1E, 0x63, 0xE9))
        node(drive, "notes.txt", qa, mime="text/plain", data=b"hello from drive\n")
        node(drive, "Report", qa, mime=DOC)
        node(drive, "Survey", qa, mime=FORM)
        node(drive, "dup.txt", qa, mime="text/plain", data=b"first\n", created=1_700_000_000)
        node(drive, "dup.txt", qa, mime="text/plain", data=b"second\n", created=1_700_000_100)
        node(drive, "a/b.txt", qa, mime="text/plain", data=b"slash\n")
        photos = node(drive, "Photos", qa, mime=FOLDER, folder=True)
        node(drive, "inside.txt", photos, mime="text/plain", data=b"inside\n")
        node(drive, "Shortcut to Photos", qa, mime=SHORTCUT, target=photos)

        graph = {}
        groot = node(graph, "root", None, folder=True)
        gqa = node(graph, "nexplorer-qa", groot, folder=True)
        node(graph, "photo.png", gqa, data=png(0xE9, 0x9A, 0x1E))
        node(graph, "notes.txt", gqa, data=b"hello from onedrive\n")
        node(graph, "# 100% עברית.txt", gqa, data=b"encoded\n")
        gphotos = node(graph, "Photos", gqa, folder=True)
        node(graph, "inside.txt", gphotos, data=b"inside\n")
        node(graph, "Notebook", groot, folder=True, package=True)

        state.clear()
        state.update(drive=drive, drive_root=root, graph=graph, graph_root=groot, sessions={}, throttle=0)


def used():
    return sum(len(n["data"]) for store in (state["drive"], state["graph"]) for n in store.values())


# ---------- Drive ----------

def drive_json(n):
    o = {"id": n["id"], "name": n["name"], "mimeType": n["mime"], "modifiedTime": iso(n["modified"]),
         "createdTime": iso(n["created"]), "parents": [n["parent"]] if n["parent"] else []}
    if not n["mime"].startswith("application/vnd.google-apps."):
        o["size"] = str(len(n["data"]))
    if n["mime"] == SHORTCUT:
        target = state["drive"].get(n["target"])
        o["shortcutDetails"] = {"targetId": n["target"], "targetMimeType": target["mime"] if target else ""}
    return o


def drive_id(value):
    return state["drive_root"] if value == "root" else value


# ---------- Graph ----------

def graph_json(n, base):
    o = {"id": n["id"], "name": n["name"], "lastModifiedDateTime": iso(n["modified"]),
         "fileSystemInfo": {"lastModifiedDateTime": iso(n["modified"])}}
    if n.get("package"):
        o["package"] = {"type": "oneNote"}
    elif n["folder"]:
        o["folder"] = {"childCount": len(graph_children(n["id"]))}
        o["size"] = 0
    else:
        o["file"] = {"mimeType": "application/octet-stream"}
        o["size"] = len(n["data"])
    return o


def graph_children(nid):
    return sorted((n for n in state["graph"].values() if n["parent"] == nid), key=lambda n: n["name"].lower())


def graph_child(parent, name):
    return next((n for n in state["graph"].values() if n["parent"] == parent and n["name"].lower() == name.lower()), None)


def graph_resolve(segments):
    current = state["graph"][state["graph_root"]]
    for seg in segments:
        child = graph_child(current["id"], seg)
        if child is None:
            return None
        current = child
    return current


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("%s %s" % (self.command, self.path), flush=True)

    # ---------- תשובות ----------

    def send(self, code, body=b"", content_type="application/json", headers=None):
        if isinstance(body, (dict, list)):
            body = json.dumps(body).encode()
        self.send_response(code)
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        if body or code not in (204, 304):
            self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body and self.command != "HEAD":
            self.wfile.write(body)

    def send_file(self, data, content_type):
        """תוכן קובץ, כמו בשירות האמיתי: `Range: bytes=N-` — 206 מהמקום; מעבר לסוף — 416."""
        m = re.fullmatch(r"bytes=(\d+)-", self.headers.get("Range", ""))
        if not m:
            return self.send(200, data, content_type)
        start = int(m.group(1))
        if start >= len(data):
            return self.send(416, b"", content_type, {"Content-Range": "bytes */%d" % len(data)})
        return self.send(206, data[start:], content_type, {"Content-Range": "bytes %d-%d/%d" % (start, len(data) - 1, len(data))})

    def drive_error(self, code, reason, message=""):
        self.send(code, {"error": {"code": code, "message": message or reason, "errors": [{"reason": reason, "message": message or reason}]}})

    def graph_error(self, code, kind, message=""):
        self.send(code, {"error": {"code": kind, "message": message or kind}})

    def body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            data = b""
            while True:
                size = int(self.rfile.readline().strip(), 16)
                if size == 0:
                    self.rfile.readline()
                    return data
                data += self.rfile.read(size)
                self.rfile.readline()
        return self.rfile.read(length) if length else b""

    def authorized(self):
        if self.headers.get("Authorization") == "Bearer " + TOKEN:
            return True
        self.send(401, {"error": {"code": 401, "message": "Invalid Credentials", "errors": [{"reason": "authError"}]}})
        return False

    def throttled(self):
        with lock:
            if state["throttle"] > 0:
                state["throttle"] -= 1
                self.send(429, {"error": {"code": 429, "message": "Too Many Requests", "errors": [{"reason": "rateLimitExceeded"}]}},
                          headers={"Retry-After": "0"})
                return True
        return False

    def host(self):
        return "http://" + (self.headers.get("Host") or "127.0.0.1")

    # ---------- ניתוב ----------

    def route(self):
        url = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(url.query, keep_blank_values=True)
        q = {k: v[0] for k, v in query.items()}
        path = url.path
        data = self.body() if self.command in ("POST", "PUT", "PATCH") else b""
        if path == "/qa/reset":
            seed()
            return self.send(204)
        if path == "/qa/throttle":
            with lock:
                state["throttle"] = int(q.get("count", "1"))
            return self.send(204)
        if path.startswith("/download/"):
            with lock:
                n = state["graph"].get(path.rsplit("/", 1)[1])
            return self.send_file(n["data"], "application/octet-stream") if n else self.graph_error(404, "itemNotFound")
        if path.startswith("/upload-session/"):
            return self.graph_session(path.rsplit("/", 1)[1], data)
        if path == "/consumers/oauth2/v2.0/token" and self.command == "POST":
            return self.token(urllib.parse.parse_qs(data.decode()))
        if not self.authorized() or self.throttled():
            return
        with lock:
            if path.startswith("/drive/v3/") or path.startswith("/upload/drive/v3/"):
                return self.drive(path, q, data)
            if path.startswith("/v1.0/"):
                return self.graph(path, q, data)
        self.send(404, {"error": "no route"})

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = lambda self: self.route()

    # ---------- Drive ----------

    def drive(self, path, q, data):
        files = state["drive"]
        if path == "/drive/v3/about":
            return self.send(200, {"storageQuota": {"limit": str(QUOTA), "usage": str(used())}, "user": {"emailAddress": ACCOUNT}})
        if path == "/drive/v3/files" and self.command == "GET":
            m = re.fullmatch(r"'([^']+)' in parents and trashed = false", q.get("q", ""))
            if not m:
                return self.drive_error(400, "invalidQuery")
            parent = drive_id(m.group(1))
            children = sorted((n for n in files.values() if n["parent"] == parent and not n["trashed"]), key=lambda n: n["name"])
            start = int(q.get("pageToken") or 0)
            size = min(int(q.get("pageSize") or 100), PAGE)
            page = children[start:start + size]
            out = {"files": [drive_json(n) for n in page]}
            if start + size < len(children):
                out["nextPageToken"] = str(start + size)
            return self.send(200, out)
        if path == "/drive/v3/files" and self.command == "POST":
            meta = json.loads(data or b"{}")
            parent = drive_id(meta["parents"][0])
            if parent not in files or not files[parent]["folder"]:
                return self.drive_error(404, "notFound")
            folder = meta.get("mimeType") == FOLDER
            nid = node(files, meta["name"], parent, mime=meta.get("mimeType"), folder=folder)
            return self.send(200, drive_json(files[nid]))
        m = re.fullmatch(r"/drive/v3/files/([^/]+)(/export)?", path)
        if m:
            nid = drive_id(m.group(1))
            n = files.get(nid)
            if n is None or (n["trashed"] and self.command != "DELETE"):
                return self.drive_error(404, "notFound", "File not found: " + m.group(1))
            if m.group(2):
                if EXPORTS.get(n["mime"]) != q.get("mimeType"):
                    return self.drive_error(400, "badRequest")
                return self.send(200, ("exported " + n["name"]).encode(), q["mimeType"])
            if self.command == "GET":
                if q.get("alt") == "media":
                    if n["folder"] or n["mime"].startswith("application/vnd.google-apps."):
                        return self.drive_error(403, "fileNotDownloadable")
                    return self.send_file(n["data"], n["mime"])
                return self.send(200, drive_json(n))
            if self.command == "DELETE":
                del files[nid]
                return self.send(204)
            if self.command == "PATCH":
                meta = json.loads(data or b"{}")
                if q.get("addParents"):
                    if q.get("removeParents") != n["parent"]:
                        return self.drive_error(400, "badRequest", "removeParents is not the current parent")
                    target = drive_id(q["addParents"])
                    if target not in files or not files[target]["folder"]:
                        return self.drive_error(404, "notFound")
                    n["parent"] = target
                if "name" in meta:
                    n["name"] = meta["name"]
                if meta.get("trashed"):
                    n["trashed"] = True
                if "modifiedTime" in meta:
                    n["modified"] = parse_iso(meta["modifiedTime"]) or n["modified"]
                return self.send(200, drive_json(n))
        if path == "/upload/drive/v3/files":
            return self.drive_upload(q, data)
        self.drive_error(404, "notFound", "no route " + path)

    def drive_upload(self, q, data):
        files = state["drive"]
        kind = q.get("uploadType")
        if kind == "multipart" and self.command == "POST":
            boundary = re.search(r'boundary="?([^";]+)"?', self.headers.get("Content-Type", ""))
            if not self.headers.get("Content-Type", "").startswith("multipart/related") or not boundary:
                return self.drive_error(400, "badContent")
            parts = [p for p in data.split(b"--" + boundary.group(1).encode()) if p.strip() not in (b"", b"--")]
            if len(parts) != 2:
                return self.drive_error(400, "badContent", "expected 2 parts")

            def content(part):
                return part.split(b"\r\n\r\n", 1)[1][:-2]

            meta = json.loads(content(parts[0]))
            parent = drive_id(meta["parents"][0])
            nid = node(files, meta["name"], parent, data=content(parts[1]))
            return self.send(200, {"id": nid})
        if kind == "resumable" and self.command == "POST" and "upload_id" not in q:
            meta = json.loads(data or b"{}")
            sid = uuid.uuid4().hex
            state["sessions"][sid] = dict(kind="drive", meta=meta, data=b"")
            return self.send(200, headers={"Location": self.host() + "/upload/drive/v3/files?uploadType=resumable&upload_id=" + sid})
        if kind == "resumable" and self.command == "PUT":
            session = state["sessions"].get(q.get("upload_id"))
            if session is None:
                return self.drive_error(404, "notFound")
            m = re.fullmatch(r"bytes (?:(\d+)-(\d+)|\*)/(\d+|\*)", self.headers.get("Content-Range", ""))
            if not m:
                return self.drive_error(400, "badContentRange")
            if m.group(1) is not None:
                start, end = int(m.group(1)), int(m.group(2))
                if start != len(session["data"]) or end - start + 1 != len(data):
                    return self.drive_error(400, "badContentRange", "range does not match")
                total_known = m.group(3) != "*"
                if not total_known and len(data) % (256 * 1024) != 0:
                    return self.drive_error(400, "badContentRange", "chunk is not a multiple of 256KB")
                session["data"] += data
            total = m.group(3)
            if total != "*" and int(total) == len(session["data"]):
                meta = session["meta"]
                del state["sessions"][q["upload_id"]]
                nid = node(files, meta["name"], drive_id(meta["parents"][0]), data=session["data"])
                return self.send(200, {"id": nid})
            headers = {"Range": "bytes=0-%d" % (len(session["data"]) - 1)} if session["data"] else {}
            return self.send(308, headers=headers)
        self.drive_error(400, "badRequest")

    # ---------- Graph ----------

    def graph(self, path, q, data):
        g = state["graph"]
        if path == "/v1.0/me":
            return self.send(200, {"userPrincipalName": ACCOUNT, "displayName": "Alex"})
        if path == "/v1.0/me/drive":
            u = used()
            return self.send(200, {"quota": {"total": QUOTA, "used": u, "remaining": QUOTA - u, "state": "normal"}})
        prefix = "/v1.0/me/drive/"
        if not path.startswith(prefix):
            return self.graph_error(404, "itemNotFound")
        rest = path[len(prefix):]
        if rest == "root":
            segments, suffix = [], None
        elif rest.startswith("root/"):
            segments, suffix = [], rest[5:]
        elif rest.startswith("root:/"):
            inner = rest[6:]
            if ":/" in inner:
                inner, suffix = inner.split(":/", 1)
            else:
                inner, suffix = inner.rstrip(":"), None
            segments = [urllib.parse.unquote(s) for s in inner.split("/") if s]
        else:
            return self.graph_error(400, "invalidRequest")
        conflict_fail = q.get("@microsoft.graph.conflictBehavior") == "fail"
        if suffix == "content" and self.command == "PUT":
            parent = graph_resolve(segments[:-1])
            if parent is None:
                return self.graph_error(404, "itemNotFound")
            if graph_child(parent["id"], segments[-1]):
                return self.graph_error(409, "nameAlreadyExists") if conflict_fail else self.graph_error(400, "test expects fail")
            nid = node(g, segments[-1], parent["id"], data=data)
            return self.send(201, graph_json(g[nid], self.host()))
        if suffix == "createUploadSession" and self.command == "POST":
            parent = graph_resolve(segments[:-1])
            if parent is None:
                return self.graph_error(404, "itemNotFound")
            meta = json.loads(data or b"{}")
            if meta.get("item", {}).get("@microsoft.graph.conflictBehavior") != "fail":
                return self.graph_error(400, "invalidRequest", "test expects conflictBehavior=fail")
            if graph_child(parent["id"], segments[-1]):
                return self.graph_error(409, "nameAlreadyExists")
            sid = uuid.uuid4().hex
            state["sessions"][sid] = dict(kind="graph", parent=parent["id"], name=segments[-1], data=b"")
            return self.send(200, {"uploadUrl": self.host() + "/upload-session/" + sid})
        n = graph_resolve(segments)
        if n is None:
            return self.graph_error(404, "itemNotFound")
        if suffix == "children" and self.command == "GET":
            children = graph_children(n["id"])
            start = int(q.get("$skiptoken") or 0)
            size = min(int(q.get("$top") or 200), PAGE)
            out = {"value": [graph_json(c, self.host()) for c in children[start:start + size]]}
            if start + size < len(children):
                link = urllib.parse.urlsplit(self.path)
                params = dict(q, **{"$skiptoken": str(start + size)})
                out["@odata.nextLink"] = self.host() + link.path + "?" + urllib.parse.urlencode(params)
            return self.send(200, out)
        if suffix == "children" and self.command == "POST":
            meta = json.loads(data or b"{}")
            if meta.get("@microsoft.graph.conflictBehavior") != "fail":
                return self.graph_error(400, "invalidRequest", "test expects conflictBehavior=fail")
            if graph_child(n["id"], meta["name"]):
                return self.graph_error(409, "nameAlreadyExists")
            nid = node(g, meta["name"], n["id"], folder=True)
            return self.send(201, graph_json(g[nid], self.host()))
        if suffix == "content" and self.command == "GET":
            if n["folder"]:
                return self.graph_error(400, "notAFile")
            return self.send(302, headers={"Location": self.host() + "/download/" + n["id"]})
        if suffix is not None:
            return self.graph_error(400, "invalidRequest", "unknown " + suffix)
        if self.command == "GET":
            return self.send(200, graph_json(n, self.host()))
        if self.command == "DELETE":
            if n["id"] == state["graph_root"]:
                return self.graph_error(403, "accessDenied")
            ids = {n["id"]}
            changed = True
            while changed:
                more = {x["id"] for x in g.values() if x["parent"] in ids} - ids
                changed = bool(more)
                ids |= more
            for i in ids:
                del g[i]
            return self.send(204)
        if self.command == "PATCH":
            meta = json.loads(data or b"{}")
            parent = n["parent"]
            if "parentReference" in meta:
                parent = meta["parentReference"]["id"]
                if parent not in g or not g[parent]["folder"]:
                    return self.graph_error(404, "itemNotFound")
            name = meta.get("name", n["name"])
            other = graph_child(parent, name)
            if other is not None and other["id"] != n["id"]:
                return self.graph_error(409, "nameAlreadyExists")
            n["parent"], n["name"] = parent, name
            when = meta.get("fileSystemInfo", {}).get("lastModifiedDateTime")
            if when:
                n["modified"] = parse_iso(when) or n["modified"]
            return self.send(200, graph_json(n, self.host()))
        self.graph_error(405, "invalidRequest")

    def token(self, form):
        """נקודת האסימונים של Microsoft: הקוד qa-code, ו-refresh token שמתחלף בכל רענון (qa-refresh-N)."""
        f = {k: v[0] for k, v in form.items()}
        if f.get("client_id") != "qa-client":
            return self.send(400, {"error": "invalid_client"})
        if f.get("grant_type") == "authorization_code":
            if f.get("code") != "qa-code" or not f.get("code_verifier") or not f.get("redirect_uri"):
                return self.send(400, {"error": "invalid_grant"})
            return self.send(200, {"access_token": TOKEN, "refresh_token": "qa-refresh-1", "expires_in": 3600, "token_type": "Bearer"})
        if f.get("grant_type") == "refresh_token":
            m = re.fullmatch(r"qa-refresh-(\d+)", f.get("refresh_token", ""))
            if not m:
                return self.send(400, {"error": "invalid_grant", "error_description": "AADSTS70000: expired"})
            return self.send(200, {"access_token": TOKEN, "refresh_token": "qa-refresh-%d" % (int(m.group(1)) + 1), "expires_in": 3600})
        self.send(400, {"error": "unsupported_grant_type"})

    def graph_session(self, sid, data):
        with lock:
            session = state["sessions"].get(sid)
            if session is None:
                return self.graph_error(404, "itemNotFound")
            if self.command == "DELETE":
                del state["sessions"][sid]
                return self.send(204)
            # השירות האמיתי: אסימון בבקשה לכתובת ה-upload session עלול להחזיר 401.
            if self.headers.get("Authorization"):
                return self.graph_error(401, "unauthenticated", "do not send Authorization to the upload URL")
            m = re.fullmatch(r"bytes (\d+)-(\d+)/(\d+)", self.headers.get("Content-Range", ""))
            if not m:
                return self.graph_error(400, "invalidRange")
            start, end, total = int(m.group(1)), int(m.group(2)), int(m.group(3))
            if session.setdefault("total", total) != total:
                return self.graph_error(400, "invalidRange", "total size changed")
            if start != len(session["data"]) or end - start + 1 != len(data):
                return self.graph_error(416, "invalidRange")
            last = end + 1 == total
            if not last and len(data) % CHUNK_UNIT != 0:
                return self.graph_error(400, "invalidRange", "chunk is not a multiple of 320KB")
            session["data"] += data
            if not last:
                return self.send(202, {"nextExpectedRanges": ["%d-" % len(session["data"])]})
            del state["sessions"][sid]
            if graph_child(session["parent"], session["name"]):
                return self.graph_error(409, "nameAlreadyExists")
            nid = node(state["graph"], session["name"], session["parent"], data=session["data"])
            return self.send(201, graph_json(state["graph"][nid], self.host()))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8090)
    args = parser.parse_args()
    seed()
    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print("cloud-server listening on %d" % args.port, flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
