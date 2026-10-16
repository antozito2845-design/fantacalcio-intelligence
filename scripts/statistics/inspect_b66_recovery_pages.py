from __future__ import annotations

import re
import time
import urllib.request
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
    / "b66-recovery-page-structure.txt"
)

AURELIO_URL = (
    "https://www.transfermarkt.com/"
    "giuseppe-aurelio/leistungsdaten/"
    "spieler/457444/saison/2024"
)

AURELIO_FILE = RECOVERY / "1038-457444-2024.html"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/151 Safari/537.36"
    ),
    "Accept-Language": "en-US,en;q=0.9",
}


def clean(value: str) -> str:
    return re.sub(
        r"\s+",
        " ",
        value,
    ).strip()


def retry_aurelio():
    if AURELIO_FILE.exists():
        print(
            "Aurelio 2024/25 gia presente."
        )
        return

    print(
        "Retry Aurelio 2024/2025..."
    )

    for attempt in range(1, 4):
        try:
            request = urllib.request.Request(
                AURELIO_URL,
                headers=HEADERS,
            )

            with urllib.request.urlopen(
                request,
                timeout=60,
            ) as response:
                payload = response.read()

            AURELIO_FILE.write_bytes(
                payload
            )

            print(
                f"  OK {len(payload)} bytes"
            )

            return

        except Exception as exc:
            print(
                f"  tentativo {attempt} fallito: "
                f"{exc}"
            )

            time.sleep(4)

    print(
        "  Aurelio 2024/25 ancora non disponibile."
    )


def inspect_file(path: Path):
    html = path.read_text(
        encoding="utf-8",
        errors="replace",
    )

    soup = BeautifulSoup(
        html,
        "html.parser",
    )

    result = []

    result.append(
        "=" * 80
    )

    result.append(
        f"FILE: {path.name}"
    )

    title = soup.title

    result.append(
        "TITLE: "
        + (
            clean(title.get_text(" "))
            if title
            else ""
        )
    )

    tables = soup.find_all("table")

    result.append(
        f"TABLES: {len(tables)}"
    )

    for index, table in enumerate(
        tables,
        start=1,
    ):
        headers = [
            clean(th.get_text(" "))
            for th in table.find_all("th")
        ]

        rows = table.find_all("tr")

        normalized_rows = []

        for row in rows[:8]:
            cells = [
                clean(cell.get_text(" "))
                for cell in row.find_all(
                    ["td", "th"]
                )
            ]

            if cells:
                normalized_rows.append(
                    " | ".join(cells)
                )

        if not normalized_rows:
            continue

        result.append("")
        result.append(
            f"TABLE {index}"
        )

        if headers:
            result.append(
                "HEADERS: "
                + " | ".join(headers)
            )

        for row in normalized_rows:
            result.append(
                "ROW: " + row
            )

    return result


def main():
    retry_aurelio()

    selected_files = []

    for path in sorted(
        RECOVERY.glob("*.html")
    ):
        # Penev resta volutamente NO_HISTORY.
        if path.name.startswith(
            "1039-"
        ):
            continue

        selected_files.append(
            path
        )

    report = []

    report.append(
        "B66 - RECOVERY PAGE STRUCTURE"
    )

    report.append(
        f"FILES={len(selected_files)}"
    )

    report.append(
        ""
    )

    for path in selected_files:
        report.extend(
            inspect_file(path)
        )

        report.append(
            ""
        )

    REPORT.write_text(
        "\n".join(report),
        encoding="utf-8",
    )

    print()
    print(
        "=== B66 RECOVERY INSPECTION ==="
    )

    print(
        f"html_files_without_penev="
        f"{len(selected_files)}"
    )

    print(
        f"report={REPORT}"
    )


if __name__ == "__main__":
    main()