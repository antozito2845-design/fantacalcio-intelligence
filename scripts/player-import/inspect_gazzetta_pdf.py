from pathlib import Path
from pypdf import PdfReader

pdf_path = Path(
    "scripts/player-import/input/gazzetta/"
    "listone-gazzetta-2026-2027.pdf"
)

reader = PdfReader(pdf_path)

print(f"PAGINE TOTALI: {len(reader.pages)}")
print(f"DIMENSIONE: {pdf_path.stat().st_size} byte")

for index, page in enumerate(reader.pages[:3], start=1):
    text = page.extract_text() or ""

    print()
    print("=" * 70)
    print(f"PAGINA {index}")
    print("=" * 70)
    print(text[:4000])