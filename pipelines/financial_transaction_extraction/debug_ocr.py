# TEST FILE DELETE

from pathlib import Path
import tempfile

import pytesseract
import pypdfium2 as pdfium

from evaluation import synthetic_documents as sd

rows = sd.make_rows(10)

with tempfile.TemporaryDirectory() as tmp:
    source = Path(tmp) / "signed.pdf"
    sd.layout_signed_amount(source, rows)

    pdf = pdfium.PdfDocument(str(source))

    try:
        for index in range(len(pdf)):
            image = pdf[index].render(
                scale=200 / 72
            ).to_pil()

            print(f"\n===== PAGE {index + 1} =====")

            for psm in (3, 4, 6, 11):
                text = pytesseract.image_to_string(
                    image,
                    config=f"--psm {psm} -c preserve_interword_spaces=1"
                )

                print(f"\n--- PSM {psm} ---")
                print(text)

                output = Path(tmp) / f"psm_{psm}.txt"
                output.write_text(text, encoding="utf-8")

    finally:
        pdf.close()

print("\nPDF processing completed successfully.")
