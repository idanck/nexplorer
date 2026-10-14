#!/usr/bin/env python3
"""עזר קטן ל-UiAutomator מהמארח, לבדיקה על build ‏release (בלי instrumentation).

    ui.py has <text>        — יציאה 0 אם יש על המסך צומת עם הטקסט / התיאור הזה
    ui.py tap <text>        — לוחץ על מרכז הצומת
    ui.py tap-desc <text>   — כמו tap, לפי התיאור בלבד (שדה שהתווית שלו מופיעה גם כטקסט מעליו)
    ui.py focus-desc <text> — לוחץ על השדה (לפי התיאור) עד שהוא מקבל פוקוס; יציאה 0 רק אם קיבל.
                              לחיצה שלא נחתה על השדה השאירה את הפוקוס בקודם, והטקסט נכתב אליו
                              ("qaqa-pass" בשדה המשתמש).
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def dump():
    for _ in range(3):
        subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/qa-ui.xml"], capture_output=True)
        raw = subprocess.run(["adb", "exec-out", "cat", "/sdcard/qa-ui.xml"], capture_output=True).stdout
        try:
            return ET.fromstring(raw)
        except ET.ParseError:
            continue
    return None


def find(root, text, by_desc=False):
    for node in root.iter("node"):
        if node.get("content-desc") == text or (not by_desc and node.get("text") == text):
            return node
    return None


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds"))))


def focus(text):
    # המיקום מה-dump יכול להיות ישן: המקלדת של השדה הקודם עוד נפתחת והטופס גולל אליו. אז הלחיצה
    # לא נוחתת על השדה, והטקסט נכתב לשדה הקודם — לכן בודקים שהפוקוס עבר, ואם לא — dump חדש ושוב.
    for _ in range(5):
        root = dump()
        node = find(root, text, by_desc=True) if root is not None else None
        if node is None:
            return False
        if node.get("focused") == "true":
            return True
        x1, y1, x2, y2 = bounds(node)
        subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)], check=True)
        time.sleep(0.5)
    root = dump()
    node = find(root, text, by_desc=True) if root is not None else None
    return node is not None and node.get("focused") == "true"


def main():
    command, text = sys.argv[1], sys.argv[2]
    if command == "focus-desc":
        sys.exit(0 if focus(text) else 1)
    root = dump()
    node = find(root, text, by_desc=command == "tap-desc") if root is not None else None
    if node is None:
        sys.exit(1)
    if command in ("tap", "tap-desc"):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)], check=True)


if __name__ == "__main__":
    main()
