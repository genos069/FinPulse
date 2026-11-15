import tempfile
from pathlib import Path

from scripts.make_sample_fixtures import make_extended_statement
from transaction_pipeline import TransactionPipeline

SAMPLE = Path(__file__).resolve().parents[1] / "samples" / "extended_statement.pdf"


def test_committed_fixture_is_exactly_what_the_generator_produces():
    with tempfile.TemporaryDirectory() as tmp:
        regenerated = make_extended_statement(Path(tmp) / "x.pdf")
        assert regenerated.read_bytes() == SAMPLE.read_bytes()


def test_fixture_content_is_what_the_tests_assume():
    result = TransactionPipeline(None).process_file(str(SAMPLE))

    assert len(result.transactions) == 9
    assert sorted(d["reason"] for d in result.discarded) == ["balance_alert", "otp", "promo"]
    refs = [t.ref_no for t in result.transactions]
    assert refs[2] == "4324103892" and "XX114XXXXX393" in refs
    assert {t.direction for t in result.transactions} == {"debit", "credit"}
