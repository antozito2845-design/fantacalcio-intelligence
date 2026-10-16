from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

import requests


BASE_URL = "https://www.fantacalcio.it"
PAGE_URL = f"{BASE_URL}/quotazioni-fantacalcio"
JS_URL = f"{BASE_URL}/js/pages/prices-serie-a.page.js"
EXCEL_URL = f"{BASE_URL}/api/v1/Excel/prices/21/1"

REPORT_PATH = (
    Path(__file__).resolve().parent
    / "logs"
    / "fantacalcio-api-inspection.txt"
)

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/150.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "it-IT,it;q=0.9,en;q=0.8",
}


def unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))


def extract_api_references(
    content: str,
    source_url: str,
) -> list[str]:
    patterns = [
        r'''["']([^"']*/api/v1/[^"']+)["']''',
        r'''["']([^"']*/api/[^"']+)["']''',
        r'''(?:url|endpoint)\s*:\s*["']([^"']+)["']''',
        r'''fetch\s*\(\s*["']([^"']+)["']''',
        r'''axios\.(?:get|post)\s*\(\s*["']([^"']+)["']''',
        r'''\$\.get\s*\(\s*["']([^"']+)["']''',
        r'''\$\.ajax\s*\(\s*\{[^}]*url\s*:\s*["']([^"']+)["']''',
    ]

    references: list[str] = []

    for pattern in patterns:
        matches = re.findall(
            pattern,
            content,
            flags=re.IGNORECASE | re.DOTALL,
        )

        for match in matches:
            value = match.strip()

            if not value:
                continue

            references.append(
                urljoin(source_url, value)
            )

    return unique(references)


def describe_response(
    label: str,
    response: requests.Response,
) -> list[str]:
    lines = [
        label,
        "-" * 70,
        f"URL finale: {response.url}",
        f"HTTP: {response.status_code}",
        f"Content-Type: {response.headers.get('Content-Type')}",
        f"Content-Disposition: "
        f"{response.headers.get('Content-Disposition')}",
        f"Dimensione: {len(response.content)} byte",
    ]

    location = response.headers.get("Location")

    if location:
        lines.append(f"Location: {location}")

    lines.append("")

    return lines


def inspect_candidate(
    session: requests.Session,
    url: str,
) -> list[str]:
    lines = [
        f"CANDIDATO: {url}",
        "-" * 70,
    ]

    try:
        response = session.get(
            url,
            timeout=30,
            allow_redirects=False,
            headers={
                **HEADERS,
                "Accept": (
                    "application/json,text/plain,*/*"
                ),
                "Referer": PAGE_URL,
            },
        )

        lines.extend(
            [
                f"HTTP: {response.status_code}",
                f"Content-Type: "
                f"{response.headers.get('Content-Type')}",
                f"Content-Disposition: "
                f"{response.headers.get('Content-Disposition')}",
                f"Dimensione: {len(response.content)} byte",
            ]
        )

        preview = response.text[:500]

        if preview:
            preview = preview.replace(
                "\r",
                " ",
            ).replace(
                "\n",
                " ",
            )

            lines.append(f"Anteprima: {preview}")

    except requests.RequestException as exception:
        lines.append(f"ERRORE: {exception}")

    lines.append("")

    return lines


def main() -> int:
    session = requests.Session()
    session.headers.update(HEADERS)

    report: list[str] = [
        "FANTACALCIO API INSPECTION",
        "=" * 70,
        "",
    ]

    page_response = session.get(
        PAGE_URL,
        timeout=30,
    )

    report.extend(
        describe_response(
            "PAGINA QUOTAZIONI",
            page_response,
        )
    )

    page_response.raise_for_status()

    js_response = session.get(
        JS_URL,
        timeout=30,
        headers={
            **HEADERS,
            "Referer": PAGE_URL,
        },
    )

    report.extend(
        describe_response(
            "JAVASCRIPT QUOTAZIONI",
            js_response,
        )
    )

    js_response.raise_for_status()

    page_references = extract_api_references(
        page_response.text,
        PAGE_URL,
    )

    js_references = extract_api_references(
        js_response.text,
        JS_URL,
    )

    candidates = unique(
        [
            EXCEL_URL,
            *page_references,
            *js_references,
        ]
    )

    report.extend(
        [
            "RIFERIMENTI API TROVATI",
            "=" * 70,
            f"Totale: {len(candidates)}",
            "",
        ]
    )

    for candidate in candidates:
        report.append(candidate)

    report.append("")

    for candidate in candidates:
        report.extend(
            inspect_candidate(
                session,
                candidate,
            )
        )

    REPORT_PATH.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    REPORT_PATH.write_text(
        "\n".join(report) + "\n",
        encoding="utf-8",
    )

    print(f"Pagina quotazioni: HTTP {page_response.status_code}")
    print(f"JavaScript: HTTP {js_response.status_code}")
    print(f"Endpoint trovati: {len(candidates)}")
    print()

    for candidate in candidates:
        print(candidate)

    print()
    print(f"Report: {REPORT_PATH}")

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