#!/usr/bin/env python3
# שרת מדומה ל-DSM של Synology (File Station API) — לבדיקות שלב 7 (Phase7Test, ‏SynologyFsTest). רק מה
# שהאפליקציה משתמשת בו, ובאותם כללים כמו DSM 7.4 (נבדקו מול NAS אמיתי):
#   SYNO.API.Auth (login/logout, ‏format=sid), ‏List (list_share/list/getinfo, דפים), ‏Download (`Range` — 206, 416
#   מעבר לסוף), ‏Upload (multipart, גם chunked; בלי overwrite — 414 לקובץ קיים; mtime באלפיות שנייה), ‏CreateFolder
#   (1100 ← 414), ‏Rename (1200 ← 414), ‏Delete, ‏CopyMove ו-DirSize (משימה + status), ‏Search (start/list, התוצאות בהדרגה),
#   ‏Thumb. ה-taskid חייב להיות מחרוזת JSON (במירכאות) — כמו ש-DSM צריך. קובץ שלא נמצא בהורדה — 502 ב-HTML,
#   ובתמונה ממוזערת — 404 ב-HTML (כמו DSM).
#   קודי sid: 119 ל-sid לא מוכר — האפליקציה מתחברת מחדש.
# המשתמש: alex / qa-pass. השיתופים — תיקיות תחת --root (ברירת מחדל /tmp/nexplorer-syno).
#   python3 syno-server.py [--port 8091] [--root DIR]
# בקרה לבדיקות: POST /qa/reset (חזרה לנתוני ההתחלה), POST /qa/expire (כל ה-sid פגים).
import argparse
import json
import os
import shutil
import struct
import threading
import time
import urllib.parse
import uuid
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

USER = "alex"
PASSWORD = "qa-pass"
PAGE_LIMIT_MAX = 2  # דפים קטנים — כדי שהדפדוף בין דפים ייבדק (האפליקציה מבקשת 1000, השרת מחזיר עד 2)

lock = threading.RLock()
sessions = set()
tasks = {}
ROOT = "/tmp/nexplorer-syno"
SHARES = ("nexplorer-qa", "backup")


def png(r, g, b, size=64):
    raw = b"".join(b"\x00" + bytes([r, g, b]) * size for _ in range(size))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)) + \
        chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def seed():
    with lock:
        shutil.rmtree(ROOT, ignore_errors=True)
        qa = os.path.join(ROOT, "nexplorer-qa")
        os.makedirs(os.path.join(qa, "Photos"))
        os.makedirs(os.path.join(ROOT, "backup"))
        with open(os.path.join(qa, "notes.txt"), "wb") as f:
            f.write(b"hello from dsm\n")
        with open(os.path.join(qa, "photo.png"), "wb") as f:
            f.write(png(0x2B, 0x6F, 0xEE))
        with open(os.path.join(qa, "Photos", "inside.png"), "wb") as f:
            f.write(png(0xE9, 0x8A, 0x5D))
        with open(os.path.join(qa, "# 100% עברית.txt"), "wb") as f:
            f.write(b"encoded\n")
        os.utime(os.path.join(qa, "notes.txt"), (1_700_000_000, 1_700_000_000))
        sessions.clear()
        tasks.clear()


def real(path):
    """נתיב של File Station ("/share/a/b") ← נתיב בדיסק. None — מחוץ לשיתופים."""
    parts = [p for p in path.split("/") if p]
    if not parts or parts[0] not in SHARES or any(p in (".", "..") for p in parts):
        return None
    return os.path.join(ROOT, *parts)


def info(path, full):
    st = os.stat(full)
    isdir = os.path.isdir(full)
    return {
        "isdir": isdir,
        "name": os.path.basename(full),
        "path": path,
        "additional": {"size": 0 if isdir else st.st_size, "time": {"mtime": int(st.st_mtime), "atime": int(st.st_atime),
                                                                      "ctime": int(st.st_ctime), "crtime": int(st.st_ctime)}},
    }


def task_id(p):
    """taskid כמחרוזת JSON ("\"abc\""), כמו ש-DSM צריך (בלי מירכאות הוא מפרש מזהה כמו 1793E9474 כמספר)."""
    raw = p.get("taskid", "")
    try:
        value = json.loads(raw)
    except ValueError:
        return None
    return value if isinstance(value, str) else None


def jarray(value):
    """פרמטר שהוא רשימה ב-JSON (["/a"]) או מחרוזת אחת ("/a")."""
    try:
        parsed = json.loads(value)
        if isinstance(parsed, list):
            return [str(x) for x in parsed]
        return [str(parsed)]
    except (ValueError, TypeError):
        return [value]


class Fail(Exception):
    def __init__(self, code, errors=None):
        super().__init__(code)
        self.code = code
        self.errors = errors


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("%s %s" % (self.command, self.path.split("?")[0]), flush=True)

    # ---------- תשובות ----------

    def send_json(self, obj, status=200):
        body = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def ok(self, data=None):
        self.send_json({"success": True, **({"data": data} if data is not None else {})})

    def error(self, code, errors=None):
        err = {"code": code}
        if errors:
            err["errors"] = errors
        self.send_json({"success": False, "error": err})

    def no_content(self):
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.end_headers()

    # ---------- גוף הבקשה ----------

    def body(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            out = bytearray()
            while True:
                line = self.rfile.readline().strip()
                size = int(line.split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline()
                    break
                out += self.rfile.read(size)
                self.rfile.readline()
            return bytes(out)
        length = int(self.headers.get("Content-Length", "0") or 0)
        return self.rfile.read(length) if length else b""

    def params(self, raw):
        query = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(self.path).query, keep_blank_values=True))
        ctype = self.headers.get("Content-Type", "")
        if ctype.startswith("application/x-www-form-urlencoded"):
            query.update(urllib.parse.parse_qsl(raw.decode(), keep_blank_values=True))
        return query

    @staticmethod
    def multipart(raw, ctype):
        boundary = ctype.split("boundary=", 1)[1].strip().strip('"').encode()
        fields, files = {}, {}
        for part in raw.split(b"--" + boundary):
            if not part or part in (b"--\r\n", b"--"):
                continue
            part = part[2:] if part.startswith(b"\r\n") else part
            head, _, data = part.partition(b"\r\n\r\n")
            data = data[:-2] if data.endswith(b"\r\n") else data
            disposition = {}
            for line in head.decode("utf-8", "replace").split("\r\n"):
                if line.lower().startswith("content-disposition"):
                    for item in line.split(";")[1:]:
                        k, _, v = item.strip().partition("=")
                        disposition[k] = v.strip('"')
            name = disposition.get("name")
            if name is None:
                continue
            if "filename" in disposition:
                files[name] = (disposition["filename"], data)
            else:
                fields[name] = data.decode()
        return fields, files

    # ---------- ניתוב ----------

    def do_POST(self):
        self.route()

    def do_GET(self):
        self.route()

    def route(self):
        raw = self.body()
        url = urllib.parse.urlsplit(self.path)
        if url.path == "/qa/reset":
            seed()
            return self.no_content()
        if url.path == "/qa/expire":
            with lock:
                sessions.clear()
            return self.no_content()
        if url.path == "/webapi/auth.cgi":
            return self.auth(self.params(raw))
        if url.path != "/webapi/entry.cgi":
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        ctype = self.headers.get("Content-Type", "")
        files = {}
        if ctype.startswith("multipart/form-data"):
            fields, files = self.multipart(raw, ctype)
            params = dict(urllib.parse.parse_qsl(url.query))
            params.update(fields)
        else:
            params = self.params(raw)
        with lock:
            if params.get("_sid") not in sessions:
                return self.error(119)
        api, method = params.get("api"), params.get("method")
        try:
            handler = {
                ("SYNO.FileStation.List", "list_share"): self.list_share,
                ("SYNO.FileStation.List", "list"): self.list,
                ("SYNO.FileStation.List", "getinfo"): self.getinfo,
                ("SYNO.FileStation.Download", "download"): self.download,
                ("SYNO.FileStation.Upload", "upload"): lambda p: self.upload(p, files),
                ("SYNO.FileStation.CreateFolder", "create"): self.create_folder,
                ("SYNO.FileStation.Rename", "rename"): self.rename,
                ("SYNO.FileStation.Delete", "delete"): self.delete,
                ("SYNO.FileStation.CopyMove", "start"): self.copy_move,
                ("SYNO.FileStation.CopyMove", "status"): self.copy_move_status,
                ("SYNO.FileStation.CopyMove", "stop"): lambda p: self.ok(),
                ("SYNO.FileStation.DirSize", "start"): self.dir_size,
                ("SYNO.FileStation.DirSize", "status"): self.task_status,
                ("SYNO.FileStation.DirSize", "stop"): lambda p: self.ok(),
                ("SYNO.FileStation.Search", "start"): self.search_start,
                ("SYNO.FileStation.Search", "list"): self.search_list,
                ("SYNO.FileStation.Search", "stop"): lambda p: self.ok(),
                ("SYNO.FileStation.Search", "clean"): lambda p: self.ok(),
                ("SYNO.FileStation.Thumb", "get"): self.thumb,
            }.get((api, method))
            if handler is None:
                return self.error(103)
            with lock:
                handler(params)
        except Fail as f:
            self.error(f.code, f.errors)

    # ---------- Auth ----------

    def auth(self, p):
        if p.get("method") == "login":
            if p.get("account") != USER or p.get("passwd") != PASSWORD:
                return self.error(400)
            sid = uuid.uuid4().hex
            with lock:
                sessions.add(sid)
            return self.ok({"sid": sid})
        if p.get("method") == "logout":
            with lock:
                sessions.discard(p.get("_sid"))
            return self.ok()
        return self.error(103)

    # ---------- File Station ----------

    def list_share(self, p):
        offset, limit = int(p.get("offset", 0)), min(int(p.get("limit", 1000)), PAGE_LIMIT_MAX)
        shares = []
        for name in SHARES:
            st = os.stat(os.path.join(ROOT, name))
            shares.append({"isdir": True, "name": name, "path": "/" + name, "additional": {
                "time": {"mtime": int(st.st_mtime)},
                "volume_status": {"freespace": 200 * 1024 ** 3, "totalspace": 1000 * 1024 ** 3, "readonly": False}}})
        self.ok({"offset": offset, "total": len(shares), "shares": shares[offset:offset + limit]})

    def list(self, p):
        folder = p.get("folder_path", "")
        full = real(folder)
        if full is None or not os.path.isdir(full):
            raise Fail(408)
        names = sorted(os.listdir(full))
        offset, limit = int(p.get("offset", 0)), min(int(p.get("limit", 1000)), PAGE_LIMIT_MAX)
        files = [info(folder.rstrip("/") + "/" + n, os.path.join(full, n)) for n in names[offset:offset + limit]]
        self.ok({"offset": offset, "total": len(names), "files": files})

    def getinfo(self, p):
        out = []
        for path in jarray(p.get("path", "[]")):
            full = real(path)
            out.append(info(path, full) if full and os.path.exists(full) else {"code": 408, "path": path})
        self.ok({"files": out})

    def html(self, status):
        body = b"<!DOCTYPE html><html><body>error</body></html>"
        self.send_response(status)
        self.send_header("Content-Type", "text/html")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def download(self, p):
        full = real(p.get("path", ""))
        # כמו DSM: קובץ שלא נמצא — 502 בדף HTML, לא JSON.
        if full is None or not os.path.isfile(full):
            return self.html(502)
        size = os.path.getsize(full)
        start = 0
        rng = self.headers.get("Range")
        if rng and rng.startswith("bytes="):
            start = int(rng[6:].split("-")[0])
            if start >= size:
                self.send_response(416)
                self.send_header("Content-Range", "bytes */%d" % size)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
        with open(full, "rb") as f:
            f.seek(start)
            data = f.read()
        self.send_response(206 if start else 200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Accept-Ranges", "bytes")
        if start:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, size - 1, size))
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def upload(self, p, files):
        folder = real(p.get("path", ""))
        if folder is None or not os.path.isdir(folder):
            raise Fail(408)
        if "file" not in files:
            raise Fail(1802)
        name, data = files["file"]
        target = os.path.join(folder, name)
        if os.path.exists(target):
            if p.get("overwrite") == "false":
                return self.ok({"blSkip": True, "file": name})
            if p.get("overwrite") != "true":
                raise Fail(414)
        with open(target, "wb") as f:
            f.write(data)
        if p.get("mtime"):
            t = int(p["mtime"]) / 1000
            os.utime(target, (t, t))
        self.ok({"blSkip": False, "file": name, "progress": 1})

    def create_folder(self, p):
        parents, names = jarray(p.get("folder_path", "[]")), jarray(p.get("name", "[]"))
        out = []
        for parent, name in zip(parents, names):
            full = real(parent)
            if full is None or not os.path.isdir(full):
                raise Fail(1100, [{"code": 408, "path": parent}])
            target = os.path.join(full, name)
            if os.path.exists(target):
                raise Fail(1100, [{"code": 414, "path": parent.rstrip("/") + "/" + name}])
            os.mkdir(target)
            out.append({"isdir": True, "name": name, "path": parent.rstrip("/") + "/" + name})
        self.ok({"folders": out})

    def rename(self, p):
        paths, names = jarray(p.get("path", "[]")), jarray(p.get("name", "[]"))
        out = []
        for path, name in zip(paths, names):
            full = real(path)
            if full is None or not os.path.exists(full):
                raise Fail(1200, [{"code": 408, "path": path}])
            target = os.path.join(os.path.dirname(full), name)
            if os.path.exists(target) and target != full:
                raise Fail(1200, [{"code": 414, "path": os.path.dirname(path) + "/" + name}])
            os.rename(full, target)
            out.append(info(os.path.dirname(path) + "/" + name, target))
        self.ok({"files": out})

    def delete(self, p):
        for path in jarray(p.get("path", "[]")):
            full = real(path)
            if full is None or len([x for x in path.split("/") if x]) < 2:
                raise Fail(900, [{"code": 407, "path": path}])
            if os.path.isdir(full):
                if p.get("recursive", "true") == "false" and os.listdir(full):
                    raise Fail(900, [{"code": 401, "path": path}])
                shutil.rmtree(full)
            elif os.path.exists(full):
                os.remove(full)
        self.ok()

    def copy_move(self, p):
        dest = real(p.get("dest_folder_path", ""))
        if dest is None or not os.path.isdir(dest):
            raise Fail(1000, [{"code": 408, "path": p.get("dest_folder_path")}])
        task = uuid.uuid4().hex[:8]
        error = None
        for path in jarray(p.get("path", "[]")):
            full = real(path)
            if full is None or not os.path.exists(full):
                error = {"code": 408, "path": path}
                break
            target = os.path.join(dest, os.path.basename(full))
            if os.path.exists(target):
                error = {"code": 1002, "path": path}
                break
            if p.get("remove_src") == "true":
                shutil.move(full, target)
            elif os.path.isdir(full):
                shutil.copytree(full, target)
            else:
                shutil.copy2(full, target)
        tasks[task] = {"polls": 0, "error": error}
        self.ok({"taskid": task})

    def copy_move_status(self, p):
        task = tasks.get(task_id(p))
        if task is None:
            raise Fail(599)
        task["polls"] += 1
        # הסיום מגיע אחרי שאלה אחת — כדי שההמתנה תיבדק.
        finished = task["polls"] >= 2
        data = {"finished": finished, "progress": 1 if finished else 0.5}
        if finished and task["error"]:
            data["error"] = task["error"]
        self.ok(data)

    def dir_size(self, p):
        size = files = dirs = 0
        for path in jarray(p.get("path", "[]")):
            full = real(path)
            if full is None or not os.path.exists(full):
                raise Fail(408)
            if os.path.isfile(full):
                files += 1
                size += os.path.getsize(full)
                continue
            for base, subdirs, names in os.walk(full):
                dirs += len(subdirs)
                files += len(names)
                size += sum(os.path.getsize(os.path.join(base, n)) for n in names)
        task = uuid.uuid4().hex[:8]
        tasks[task] = {"polls": 0, "result": {"total_size": size, "num_file": files, "num_dir": dirs}}
        self.ok({"taskid": task})

    def task_status(self, p):
        task = tasks.get(task_id(p))
        if task is None:
            raise Fail(599)
        task["polls"] += 1
        finished = task["polls"] >= 2
        self.ok({"finished": finished, **(task["result"] if finished else {})})

    def search_start(self, p):
        import fnmatch
        folder = p.get("folder_path", "")
        full = real(folder)
        if full is None or not os.path.isdir(full):
            raise Fail(408)
        pattern = p.get("pattern", "*") or "*"
        if "*" not in pattern and "?" not in pattern:
            pattern = "*" + pattern + "*"
        size_from = int(p.get("size_from", 0) or 0)
        mtime_from = int(p.get("mtime_from", 0) or 0)
        found = []
        for base, subdirs, names in os.walk(full):
            for n in sorted(subdirs) + sorted(names):
                f = os.path.join(base, n)
                if not fnmatch.fnmatch(n.lower(), pattern.lower()):
                    continue
                if size_from and (os.path.isdir(f) or os.path.getsize(f) < size_from):
                    continue
                if mtime_from and os.stat(f).st_mtime < mtime_from:
                    continue
                path = "/" + os.path.relpath(f, ROOT)
                found.append(info(path, f))
        task = uuid.uuid4().hex[:8]
        tasks[task] = {"polls": 0, "files": found}
        self.ok({"taskid": task})

    def search_list(self, p):
        task = tasks.get(task_id(p))
        if task is None:
            # כמו DSM: מזהה שלא נמצא — "נגמר", בלי תוצאות.
            return self.ok({"finished": True})
        task["polls"] += 1
        # התוצאות מגיעות בהדרגה: חצי בשאלה הראשונה, והשאר — כשהחיפוש "נגמר".
        files = task["files"]
        ready = files if task["polls"] >= 2 else files[: len(files) // 2]
        offset, limit = int(p.get("offset", 0)), min(int(p.get("limit", 1000)), PAGE_LIMIT_MAX * 5)
        self.ok({"finished": task["polls"] >= 2, "total": len(ready), "offset": offset, "files": ready[offset:offset + limit]})

    def thumb(self, p):
        full = real(p.get("path", ""))
        # כמו DSM: אין תמונה (קובץ שאינו תמונה, או לא נמצא) — 404 בדף HTML.
        if full is None or not os.path.isfile(full):
            return self.html(404)
        with open(full, "rb") as f:
            data = f.read()
        if data.startswith(b"\x89PNG"):
            ctype = "image/png"
        elif data.startswith(b"\xff\xd8"):
            ctype = "image/jpeg"
        else:
            return self.html(404)
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main():
    global ROOT
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8091)
    parser.add_argument("--root", default=ROOT)
    args = parser.parse_args()
    ROOT = args.root
    seed()
    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print("syno-server on :%d (%s)" % (args.port, ROOT), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
