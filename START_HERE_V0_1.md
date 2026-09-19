# Current v0.1.0 entry point

Use `../gemini-recallibration.md` as the approved delivery sequence and Phase 1 Gemini prompt. Read `V0_1_CONTRACT.md` before the original pack: it supersedes conflicting original phase ordering, scope, and authorization policy. Do not send the old numbered prompts as the current execution sequence.

Implementation inputs:

- `V0_1_CONTRACT.md`: identity, access, lifecycle, operations, indexing, migration mapping.
- `contracts/v0.1.schema.json`: versioned record shapes; application authorization remains separate.
- `CLIENT_WORKFLOWS.md`: three-client handoff and session flow.
- `VISUAL_DIRECTION.md`: native Android direction and future graph boundary.
- `checklists/V0_1_ACCEPTANCE.md`: pending integration/runtime gates.
- `contracts/fixtures/`: valid/invalid records and 50 synthetic retrieval scenarios.

Run `python3 scripts/validate_contracts.py` with Python 3 and jsonschema >= 4. Results demonstrate schema/fixture consistency only, not app correctness, security, retrieval quality or migration safety.

The original README and phase documents remain intact because this environment's patch executor failed to open existing files for edits. This entry point and V0_1_CONTRACT.md explicitly resolve precedence. The original SHA256SUMS covers only the original pack; new additions are listed separately in SHA256SUMS.v0.1.
