#!/usr/bin/env python3
"""Decrypt a nexplorer .enc file without the app.

    pip install cryptography
    python3 nxdecrypt.py photo.jpg.enc            # -> photo.jpg (never overwrites)
    python3 nxdecrypt.py photo.jpg.enc out.jpg

The password is read from the terminal, or from stdin when stdin is not a terminal.
Format: docs/plan/04-phase4.md ("פורמט ההצפנה"). Implementation: data/crypto/EncryptedFiles.kt.
"""
import getpass
import hashlib
import os
import struct
import sys
import unicodedata

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

MAGIC = b"NXENC\x01"
HEADER_SIZE = 43
TAG = 16
INFO = b"nexplorer/enc/v1"


def read_fully(f, size):
    data = b""
    while len(data) < size:
        part = f.read(size - len(data))
        if not part:
            break
        data += part
    return data


def nonce(index, last):
    return index.to_bytes(11, "big") + (b"\x01" if last else b"\x00")


def decrypt(src, dst, password):
    header = read_fully(src, HEADER_SIZE)
    if len(header) < HEADER_SIZE or header[:6] != MAGIC:
        raise SystemExit("not a nexplorer encrypted file")
    iterations, chunk_log2 = struct.unpack(">IB", header[6:11])
    if not 10_000 <= iterations <= 10_000_000 or not 10 <= chunk_log2 <= 20:
        raise SystemExit("damaged header")
    password_salt, file_salt = header[11:27], header[27:43]
    secret = unicodedata.normalize("NFC", password).encode("utf-8")
    master = hashlib.pbkdf2_hmac("sha256", secret, password_salt, iterations, 32)
    key = HKDF(algorithm=hashes.SHA256(), length=32, salt=file_salt, info=INFO).derive(master)
    aead = AESGCM(key)
    sealed_size = (1 << chunk_log2) + TAG
    index = 0
    sealed = read_fully(src, sealed_size)
    while True:
        nxt = src.read(1) if len(sealed) == sealed_size else b""
        last = not nxt
        if len(sealed) < TAG:
            raise SystemExit("truncated file")
        try:
            dst.write(aead.decrypt(nonce(index, last), sealed, header))
        except InvalidTag:
            raise SystemExit("wrong password" if index == 0 else f"damaged file (chunk {index + 1})")
        if last:
            return
        index += 1
        sealed = nxt + read_fully(src, sealed_size - 1)


def main():
    if len(sys.argv) not in (2, 3):
        raise SystemExit(__doc__)
    source = sys.argv[1]
    target = sys.argv[2] if len(sys.argv) == 3 else (source[:-4] if source.lower().endswith(".enc") else source + ".decrypted")
    if os.path.exists(target):
        raise SystemExit(f"{target} already exists")
    password = getpass.getpass("Password: ") if sys.stdin.isatty() else sys.stdin.readline().rstrip("\n")
    part = target + ".part"
    try:
        with open(source, "rb") as src, open(part, "xb") as dst:
            decrypt(src, dst, password)
        os.replace(part, target)
    except BaseException:
        if os.path.exists(part):
            os.remove(part)
        raise
    print(target)


if __name__ == "__main__":
    main()
