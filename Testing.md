# Testing Overview

This document provides an overview of the current automated + manual test coverage, and describes the scope, structure, and expected behavior of the test suite.

The project now includes **unit tests**, **integration tests**, and a verified **end-to-end execution flow** via compiled runtime + plugin JARs.

---

## Test Scope

| Component        | Test Classes              | Purpose                                       |
| ---------------- | ------------------------- | --------------------------------------------- |
| AES crypto       | `AESFunctionsTest`        | AES-GCM round-trip behavior + malformed input |
| HMAC crypto      | `HMACFunctionsTest`       | HMAC generation + verification logic          |
| HMAC Orchestr.   | `HMACOrchestrationTest`   | Verify JAR HMAC load/verify pipeline          |
| Base64           | `Base64FunctionsTest`     | Standard + URL-safe encoding                  |
| File Provider    | `FileDataProviderTest`    | File I/O correctness                          |
| Runtime + Plugin | *manual integration test* | Full stack plugin pipeline validation         |

---

## Manual Integration Flow

The following end-to-end execution was validated:

1. Build runtime and plugin fat-JARs
2. Sign plugin JAR with `SECRET_KEY`
3. Start runtime with `.env` signature entry
4. Pass structured JSON via STDIN
5. Plugin executed successfully
6. Result returned as Base64 payload

✅ Output observed (decoded to CSV):

```
name,age,city
Alice,30,London
Bob,25,Paris
```

---

## Test Input Fixtures

All test fixtures live under:

```
src/test/resources/
```

---

## Running Tests

```bash
mvn test
```

---

## Test Matrix (in progress)

| Test ID | Type        | Goal                                           | Status    | Notes |
| ------- | ----------- | ---------------------------------------------- | --------- | ----- |
| AES-01  | Unit        | Encrypted data is non-empty                    | ✅ Pass    |       |
| AES-02  | Unit        | AES decrypt returns original plaintext         | ✅ Pass    |       |
| AES-03  | Unit        | Decrypt fails with wrong key                   | ✅ Pass    |       |
| AES-04  | Unit        | `deriveKey()` produces 256-bit key             | ✅ Pass    |       |
| HMAC-01 | Unit        | HMAC signature is generated                    | ✅ Pass    |       |
| HMAC-02 | Unit        | Verification succeeds with correct data        | ✅ Pass    |       |
| HMAC-03 | Unit        | Verification fails on mutated data             | ✅ Pass    |       |
| HMAC-04 | Integration | File read + signature verifies JAR             | ✅ Pass    |       |
| HMAC-05 | Integration | Signature mismatch triggers failure            | ✅ Pass    |       |
| B64-01  | Unit        | Base64 encoding produces expected string       | ✅ Pass    |       |
| B64-02  | Unit        | URL-safe Base64 has no `+` or `/`              | ✅ Pass    |       |
| FP-01   | Unit        | FileDataProvider reads correct byte content    | ✅ Pass    |       |
| RT-01   | Integration | Runtime loads plugin + verifies HMAC           | ✅ Pass    |       |
| RT-02   | Integration | Plugin receives JSON payload                   | ✅ Pass    |       |
| RT-03   | Integration | Plugin returns byte[] → encoded output         | ✅ Pass    |       |
| RT-04   | Integration | Timeout respected                              | ⚠ Pending |       |
| RT-05   | Integration | Encrypted output (AES-GCM)                     | ⚠ Pending |       |
| PL-01   | Integration | JSON→CSV plugin: valid JSON array → CSV output | ✅ Pass    |       |
| PL-02   | Negative    | JSON→CSV plugin: malformed JSON fails          | ⚠ Pending |       |
| PL-03   | Negative    | Non-array input returns structured error       | ⚠ Pending |       |

> ✅ All crypto, encoding, and provider tests pass.
> ✅ Full pipeline (runtime → plugin JAR → output) validated manually.

---

## Notes

* Console logging currently displays SLF4J fallback warning if no backend is configured → does **not** impact pipeline execution
* STDIN remains open if CLI wrapper waits for additional input — normal for pipelines, can be corrected by explicit EOF or input redirection
* Current plugin example demonstrates business workflow (JSON→CSV), suitable for document/data-processing use cases

---

## Next Test Goals

* Timeout enforcement test
* Encrypted input/output integration test
* Plugin negative scenarios
* Additional business plugins (PDF, validation, transformation)

---
