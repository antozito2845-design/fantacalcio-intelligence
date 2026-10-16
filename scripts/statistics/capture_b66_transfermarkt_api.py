from __future__ import annotations

import asyncio
import json
from pathlib import Path

from playwright.async_api import async_playwright


ROOT = Path(__file__).resolve().parents[2]

OUT = (
    ROOT
    / "data"
    / "statistics"
    / "international"
    / "b66-no-history-recovery"
)

REPORT = ROOT / "backend" / "b66-transfermarkt-api-capture.txt"

PLAYERS = [
    (1032, "Molina N.", "424042", "nahuel-molina"),
    (1033, "Kevin Carlos", "718201", "kevin-carlos"),
    (1034, "Romero D.", "668512", "david-romero"),
    (1035, "Schmid", "346853", "romano-schmid"),
    (1036, "Grillitsch", "195736", "florian-grillitsch"),
    (1037, "Terzic", "404260", "aleksa-terzic"),
    (1038, "Aurelio", "457444", "giuseppe-aurelio"),
    (1111, "Spence", "483348", "djed-spence"),
]

SEASONS = [
    ("2024/2025", "2024"),
    ("2025/2026", "2025"),
]


def interesting(url: str) -> bool:
    lower = url.lower()

    markers = (
        "api",
        "performance",
        "leistungsdaten",
        "competition",
        "statistics",
        "statistic",
        "spieler",
    )

    return any(
        marker in lower
        for marker in markers
    )


async def main():
    OUT.mkdir(
        parents=True,
        exist_ok=True,
    )

    captured = []

    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(
            headless=True,
        )

        context = await browser.new_context(
            locale="en-US",
            user_agent=(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/151 Safari/537.36"
            ),
        )

        page = await context.new_page()

        async def on_response(response):
            url = response.url

            if not interesting(url):
                return

            content_type = (
                response.headers.get(
                    "content-type",
                    "",
                )
            )

            row = {
                "url": url,
                "status": response.status,
                "content_type": content_type,
            }

            if (
                "json" in content_type.lower()
                or "application" in content_type.lower()
            ):
                try:
                    body = await response.text()

                    row["body_preview"] = body[:3000]

                except Exception as exc:
                    row["body_error"] = str(exc)

            captured.append(row)

        page.on(
            "response",
            on_response,
        )

        # Basta Molina 24/25 per scoprire la struttura API.
        player_id = "424042"

        url = (
            "https://www.transfermarkt.com/"
            "nahuel-molina/leistungsdaten/"
            f"spieler/{player_id}/saison/2024"
        )

        print("Apro pagina Transfermarkt...")
        print(url)

        await page.goto(
            url,
            wait_until="domcontentloaded",
            timeout=90000,
        )

        await page.wait_for_timeout(
            12000
        )

        print(
            f"responses_captured={len(captured)}"
        )

        await browser.close()

    unique = {}

    for row in captured:
        unique[
            (
                row["url"],
                row["status"],
            )
        ] = row

    rows = list(
        unique.values()
    )

    REPORT.write_text(
        "\n\n".join(
            json.dumps(
                row,
                indent=2,
                ensure_ascii=False,
            )
            for row in rows
        ),
        encoding="utf-8",
    )

    print(
        f"unique_responses={len(rows)}"
    )

    print(
        f"report={REPORT}"
    )


if __name__ == "__main__":
    asyncio.run(main())