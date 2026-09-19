"""Validate documentation contracts and fixture consistency, not Android behavior.

Requires Python 3 and jsonschema >= 4. Run from any working directory.
"""
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return json.loads((ROOT / path).read_text())


def main():
    schemas = sorted((ROOT / "contracts").glob("*.schema.json"))
    for path in schemas:
        Draft202012Validator.check_schema(json.loads(path.read_text()))
    validator = Draft202012Validator(
        read("contracts/v0.1.schema.json"), format_checker=FormatChecker()
    )
    valid = read("contracts/fixtures/v0.1-valid.json")
    invalid = read("contracts/fixtures/v0.1-invalid.json")
    for record in valid:
        validator.validate(record)
    for fixture in invalid:
        if not list(validator.iter_errors(fixture["record"])):
            raise AssertionError(f"Invalid fixture accepted: {fixture['name']}")
    corpus = read("contracts/fixtures/retrieval-v0.1.json")
    ids = {record["id"] for record in corpus["records"]}
    assert len(ids) == len(corpus["records"])
    assert len(corpus["cases"]) >= 50
    assert len({case["id"] for case in corpus["cases"]}) == len(corpus["cases"])
    for case in corpus["cases"]:
        expected = set(case["expectedEvidenceIds"])
        forbidden = set(case["forbiddenEvidenceIds"])
        assert not expected & forbidden, case["id"]
        assert expected | forbidden <= ids, case["id"]
    print(f"Validated {len(schemas)} schemas, {len(valid)} valid and "
          f"{len(invalid)} invalid examples, {len(corpus['cases'])} retrieval cases.")
    print("Android authorization, retrieval quality, migrations and runtime remain unverified.")


if __name__ == "__main__":
    main()
