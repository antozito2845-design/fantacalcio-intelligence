from __future__ import annotations

import re
from collections import Counter
from pathlib import Path

from bs4 import BeautifulSoup


ROOT = Path(__file__).resolve().parents[2]

RECOVERY = (
    ROOT
    / "data"
    / "statistics"
    / "international"
    / "b66-no-history-recovery"
)

REPORT = (
    ROOT
    / "backend"
    / "b66-transfermarkt-raw-structure.txt"
)

KEYWORDS = (
    "competition",
    "wettbewerb",
    "appearances",
    "einsätze",
    "einsatze",
    "minutes",
    "minuten",
    "goals",
    "tore",
    "assists",
    "vorlagen",
    "yellow",
    "gelbe",
    "red",
    "rote",
    "leistungsdaten",
    "performance",
    "season",
    "saison",
)


def clean(text: str) -> str:
    return re.sub(
        r"\s+",
        " ",
        text,
    ).strip()


def snippets(html: str, keyword: str):
    results = []

    lower = html.lower()
    target = keyword.lower()

    start = 0

    while len(results) < 5:
        index = lower.find(
            target,
            start,
        )

        if index < 0:
            break

        left = max(
            0,
            index - 250,
        )

        right = min(
            len(html),
            index + 500,
        )

        results.append(
            clean(
                html[left:right]
            )
        )

        start = index + len(target)

    return results


def inspect(path: Path):
    html = path.read_text(
        encoding="utf-8",
        errors="replace",
    )

    soup = BeautifulSoup(
        html,
        "html.parser",
    )

    output = []

    output.append(
        "=" * 100
    )

    output.append(
        f"FILE: {path.name}"
    )

    output.append(
        f"BYTES: {len(html.encode('utf-8'))}"
    )

    title = soup.title

    output.append(
        "TITLE: "
        + (
            clean(
                title.get_text(" ")
            )
            if title
            else ""
        )
    )

    all_classes = []

    for tag in soup.find_all(True):
        classes = tag.get(
            "class",
            [],
        )

        for class_name in classes:
            all_classes.append(
                str(class_name)
            )

    class_counts = Counter(
        all_classes
    )

    interesting_classes = [
        (name, count)
        for name, count
        in class_counts.most_common()
        if any(
            key in name.lower()
            for key in (
                "responsive",
                "table",
                "stat",
                "performance",
                "competition",
                "wettbewerb",
                "box",
                "grid",
                "row",
            )
        )
    ]

    output.append("")
    output.append(
        "=== INTERESTING CLASSES ==="
    )

    for name, count in interesting_classes[:80]:
        output.append(
            f"{count:4d}  {name}"
        )

    output.append("")
    output.append(
        "=== SCRIPT TAGS ==="
    )

    scripts = soup.find_all(
        "script"
    )

    output.append(
        f"SCRIPT_COUNT={len(scripts)}"
    )

    for index, script in enumerate(
        scripts,
        start=1,
    ):
        text = clean(
            script.get_text(
                " ",
                strip=True,
            )
        )

        if not text:
            continue

        if any(
            keyword in text.lower()
            for keyword in KEYWORDS
        ):
            output.append(
                f"SCRIPT {index}: "
                + text[:2500]
            )

    output.append("")
    output.append(
        "=== KEYWORD SNIPPETS ==="
    )

    for keyword in KEYWORDS:
        matches = snippets(
            html,
            keyword,
        )

        if not matches:
            continue

        output.append("")
        output.append(
            f"--- {keyword.upper()} "
            f"({len(matches)} snippets) ---"
        )

        for match in matches:
            output.append(
                match
            )

    output.append("")
    output.append(
        "=== DIV/TAG TEXT CANDIDATES ==="
    )

    candidates = []

    for tag in soup.find_all(
        [
            "div",
            "span",
            "li",
            "a",
            "p",
        ]
    ):
        text = clean(
            tag.get_text(
                " ",
                strip=True,
            )
        )

        if not text:
            continue

        lower = text.lower()

        if any(
            keyword in lower
            for keyword in (
                "appearances",
                "minutes",
                "goals",
                "assists",
                "competition",
                "premier league",
                "bundesliga",
                "la liga",
                "serie b",
                "serie a",
                "championship",
            )
        ):
            classes = " ".join(
                tag.get(
                    "class",
                    [],
                )
            )

            candidates.append(
                (
                    tag.name,
                    classes,
                    text,
                )
            )

    seen = set()

    for tag_name, classes, text in candidates:
        key = (
            tag_name,
            classes,
            text,
        )

        if key in seen:
            continue

        seen.add(key)

        output.append(
            f"<{tag_name} class='{classes}'> "
            f"{text[:1500]}"
        )

        if len(seen) >= 80:
            break

    return output


def main():
    files = [
        path
        for path in sorted(
            RECOVERY.glob("*.html")
        )
        if not path.name.startswith(
            "1039-"
        )
    ]

    # Ci basta un campione rappresentativo:
    # una pagina per giocatore + una seconda pagina di controllo.
    selected = {}

    for path in files:
        player_id = (
            path.name.split(
                "-",
                1,
            )[0]
        )

        selected.setdefault(
            player_id,
            path,
        )

    chosen = list(
        selected.values()
    )

    # aggiungiamo Molina 25/26 come seconda stagione di controllo
    molina_second = (
        RECOVERY
        / "1032-424042-2025.html"
    )

    if molina_second.exists():
        chosen.append(
            molina_second
        )

    report = [
        "B66 - TRANSFERMARKT RAW STRUCTURE",
        f"FILES_ANALYZED={len(chosen)}",
        "",
    ]

    for path in chosen:
        report.extend(
            inspect(path)
        )

        report.append("")

    REPORT.write_text(
        "\n".join(report),
        encoding="utf-8",
    )

    print(
        "=== B66 RAW STRUCTURE PRONTO ==="
    )

    print(
        f"files_analyzed={len(chosen)}"
    )

    print(
        f"report={REPORT}"
    )


if __name__ == "__main__":
    main()