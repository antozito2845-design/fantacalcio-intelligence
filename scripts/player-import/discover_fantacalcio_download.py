from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import urljoin

import requests


PAGE_URL = "https://www.fantacalcio.it/quotazioni-fantacalcio"

OUTPUT_PATH = (
    Path(__file__).resolve().parent
    / "logs"
    / "fantacalcio-download-discovery.txt"
)

DOWNLOAD_EXTENSIONS = (
    ".xlsx",
    ".xls",
    ".csv",
    ".zip",
)

KEYWORDS = (
    "download",
    "scarica",
    "quotazioni",
    "listone",
    "excel",
    "classic",
)


def normalize_url(value: str) -> str:
    return urljoin(PAGE_URL, value.strip())


def main() -> int:
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/150.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "it-IT,it;q=0.9,en;q=0.8",
    }

    response = requests.get(
        PAGE_URL,
        headers=headers,
        timeout=30,
    )

    print(f"HTTP: {response.status_code}")
    print(f"Content-Type: {response.headers.get('Content-Type')}")
    print(f"Dimensione pagina: {len(response.content)} byte")

    response.raise_for_status()

    html = response.text

    season_markers = {
        "2026/27": "2026/27" in html,
        "2026/2027": "2026/2027" in html,
        "2025/26": "2025/26" in html,
        "2025/2026": "2025/2026" in html,
    }

    hrefs = re.findall(
        r'''(?:href|src)\s*=\s*["']([^"']+)["']''',
        html,
        flags=re.IGNORECASE,
    )

    absolute_urls = sorted(
        {
            normalize_url(value)
            for value in hrefs
            if value.strip()
        }
    )

    candidates: list[str] = []

    for url in absolute_urls:
        lowered = url.casefold()

        if (
            any(extension in lowered for extension in DOWNLOAD_EXTENSIONS)
            or any(keyword in lowered for keyword in KEYWORDS)
        ):
            candidates.append(url)

    script_urls = [
        url
        for url in absolute_urls
        if ".js" in url.casefold()
    ]

    lines = [
        "FANTACALCIO DOWNLOAD DISCOVERY",
        "=" * 70,
        f"Pagina: {PAGE_URL}",
        f"HTTP: {response.status_code}",
        f"Dimensione: {len(response.content)} byte",
        "",
        "INDICATORI STAGIONE",
    ]

    for marker, found in season_markers.items():
        lines.append(f"{marker}: {'SI' if found else 'NO'}")

    lines.extend(
        [
            "",
            f"LINK CANDIDATI: {len(candidates)}",
        ]
    )

    if candidates:
        lines.extend(candidates)
    else:
        lines.append("Nessun link diretto trovato nel codice HTML")

    lines.extend(
        [
            "",
            f"FILE JAVASCRIPT: {len(script_urls)}",
        ]
    )

    lines.extend(script_urls)

    OUTPUT_PATH.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    OUTPUT_PATH.write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
    )

    print()
    print("INDICATORI STAGIONE")

    for marker, found in season_markers.items():
        print(f"{marker}: {'SI' if found else 'NO'}")

    print()
    print(f"Link candidati trovati: {len(candidates)}")

    for candidate in candidates:
        print(candidate)

    print()
    print(f"Report: {OUTPUT_PATH}")

    if not candidates:
        print(
            "Nessun download diretto individuato: "
            "potrebbe essere caricato tramite JavaScript/API."
        )

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except requests.RequestException as exception:
        print(
            f"ERRORE HTTP: {exception}",
            file=sys.stderr,
        )
        raise SystemExit(1)