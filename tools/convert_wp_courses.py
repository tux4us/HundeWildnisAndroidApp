#!/usr/bin/env python3
"""
Wandelt den WordPress-Export (WXR-XML, "Werkzeuge -> Daten exportieren -> Seiten") in die Kursdaten der App um.

Aufruf:
  pip install beautifulsoup4
  python3 tools/convert_wp_courses.py export.xml app/src/main/assets/courses/de

Kurs     = veröffentlichte Seite, deren Titel mit zwei Ziffern beginnt (z. B. "01 Grundlagen").
Lektion  = jede <section> der Kursseite (Titel = <h2>); der Abschnitt "Meine Notizen" (notes-section) entfällt.
Blöcke   = h (Überschrift, auch <summary> der Ausklappfelder), p (Absatz), list (Aufzählung).
Nicht übernommen: script, style, button, textarea, form, iframe, svg.
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from bs4 import BeautifulSoup, Comment, NavigableString, Tag

NS = {
    "wp": "http://wordpress.org/export/1.2/",
    "content": "http://purl.org/rss/1.0/modules/content/",
}
SKIP = {"script", "style", "button", "textarea", "form", "input", "iframe", "svg", "noscript", "select"}
INLINE = {"strong", "b", "em", "i", "u", "span", "a", "small", "mark", "sup", "sub", "br", "abbr", "code"}
HEADINGS = {"h1", "h2", "h3", "h4", "h5", "h6", "summary"}


def norm(text):
    return re.sub(r"\s+", " ", text.replace("\xa0", " ")).strip()


def walk(node, blocks, skip_ids=()):
    """Durchläuft node und hängt Blöcke an blocks an."""
    buf = []

    def flush():
        text = norm("".join(buf))
        buf.clear()
        if text:
            blocks.append({"type": "p", "text": text})

    for child in node.children:
        if isinstance(child, Comment):
            continue
        if isinstance(child, NavigableString):
            buf.append(str(child))
            continue
        if not isinstance(child, Tag) or child.name in SKIP:
            continue
        if child.name == "br":
            buf.append(" ")
        elif child.name in INLINE:
            buf.append(child.get_text(" "))
        elif child.name == "p":
            flush()
            text = norm(child.get_text(" "))
            if text:
                blocks.append({"type": "p", "text": text})
        elif child.name in HEADINGS:
            flush()
            text = norm(child.get_text(" "))
            if text and child.name != "h1" and child.get("id") not in skip_ids:
                blocks.append({"type": "h", "text": text})
        elif child.name in ("ul", "ol"):
            flush()
            items = [norm(li.get_text(" ")) for li in child.find_all("li")]
            items = [i for i in items if i]
            if items:
                blocks.append({"type": "list", "items": items})
        else:
            flush()
            walk(child, blocks, skip_ids)
    flush()


def build_course(number, title, html_text):
    soup = BeautifulSoup(html_text, "html.parser")
    wrapper = soup.find("div", class_="wordpress-hundekurs-seite") or soup
    course = {"id": f"{number:02d}", "title": re.sub(r"^\d+\s+", "", title), "blocks": [], "lessons": []}

    header = wrapper.find("header")
    if header:
        walk(header, course["blocks"])

    for idx, section in enumerate(wrapper.find_all("section"), start=1):
        classes = section.get("class", [])
        if "notes-section" in classes:
            continue
        h2 = section.find("h2")
        lesson_title = norm(h2.get_text(" ")) if h2 else f"Abschnitt {idx}"
        blocks = []
        # Titel-h2 nicht doppelt als Block ausgeben
        if h2:
            h2.extract()
        walk(section, blocks)
        course["lessons"].append({
            "id": f"{number:02d}-{len(course['lessons']) + 1:02d}",
            "title": lesson_title,
            "blocks": blocks,
            "lessons": [],
        })
    return course


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("export", help="WordPress-Export (WXR-XML)")
    ap.add_argument("outdir", help="Zielordner, z. B. app/src/main/assets/courses/de")
    args = ap.parse_args()

    root = ET.parse(args.export).getroot()
    courses = []
    for item in root.find("channel").findall("item"):
        if item.findtext("wp:post_type", namespaces=NS) != "page":
            continue
        if item.findtext("wp:status", namespaces=NS) != "publish":
            continue
        title = (item.findtext("title") or "").strip()
        m = re.match(r"^(\d{2})\s+\S", title)
        if not m:
            continue
        html_text = item.findtext("content:encoded", namespaces=NS) or ""
        courses.append(build_course(int(m.group(1)), title, html_text))

    if not courses:
        sys.exit("Keine Kursseiten gefunden (Titel müssen mit zwei Ziffern beginnen).")
    courses.sort(key=lambda c: c["id"])

    out = Path(args.outdir)
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.json"):
        old.unlink()
    index = []
    for course in courses:
        fname = f"{course['id']}.json"
        (out / fname).write_text(json.dumps(course, ensure_ascii=False, indent=1), encoding="utf-8")
        first = next((b["text"] for b in course["blocks"] if b["type"] == "p"), "")
        index.append({"id": course["id"], "title": f"{course['id']} {course['title']}",
                      "summary": first[:160], "file": fname})
    (out / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1), encoding="utf-8")
    lessons = sum(len(c["lessons"]) for c in courses)
    print(f"{len(courses)} Kurse, {lessons} Lektionen nach {out} geschrieben.")


if __name__ == "__main__":
    main()
