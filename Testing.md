# Testing Overview

This document provides an overview of the current automated test coverage,
and describes the scope, structure, and expected behavior of the test suite.

## Test Scope

| Component | Test Classes | Purpose |
|-----------|--------------|---------|
| AES crypto | AESFunctionsTest | Ensures AES-256-GCM encrypt/decrypt round-trip |
| HMAC crypto | HMACFunctionsTest | Validates HMAC generation + comparison |
| HMAC Orchestration | HMACOrchestrationTest | Validates signing + file read integration |
| Base64 | Base64FunctionsTest | Standard + URL-safe encoding |
| FileProvider | FileDataProviderTest | Basic file IO |

## Test Inputs

Fixtures are stored under:
src/test/resources/
## Running Tests

```bash
mvn test
```

## Test Matrix (in progress)

| Test ID   | Type          | Goal                                          | Status  | Notes |
|-----------|---------------|-----------------------------------------------|---------|-------|
| AES-01    | Unit Test     | Encrypted data should not be empty            | ✅ Pass |       |
| AES-02    | Unit Test     | AES decrypt should return original value      | ✅ Pass |       |
| AES-03    | Unit Test     | Decryption fails with wrong key               | ✅ Pass |       |
| AES-04    | Unit Test     | deriveKey returns 256-bit key                 | ✅ Pass |       |
| HMAC-01   | Unit Test     | HMAC signature is generated                   | ✅ Pass |       |
| HMAC-02   | Unit Test     | Verification succeeds with correct data       | ✅ Pass |       |
| HMAC-03   | Unit Test     | Verification fails on tampered data           | ✅ Pass |       |
| HMAC-04   | Integration   | File read + signature verifies successfully   | ✅ Pass |       |
| HMAC-05   | Integration   | Signature mismatch fails                      | ✅ Pass |       |
| B64-01    | Unit Test     | Base64 encoding returns expected string       | ✅ Pass |       |
| B64-02    | Unit Test     | URL-safe encoding contains no '+' or '/'      | ✅ Pass |       |
| FP-01     | Unit Test     | FileDataProvider reads correct bytes          | ✅ Pass |       |
