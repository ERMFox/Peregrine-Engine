# Peregrine-Engine (Plugin Execution Engine)

A lightweight, secure, and flexible Java runtime designed to execute
user-defined plugins under controlled conditions.

It provides:

✅ HMAC-based plugin trust verification  
✅ Optional AES-GCM encryption of input + output  
✅ Base64/Base64URL encoding support  
✅ Timeout-controlled reflective execution  
✅ Isolated plugin class loading  
✅ CLI + Java embedding via `reflectedMain()`  
✅ Clean JSON input contract  
✅ Modular provider architecture

This project is designed for data-driven pipelines where processing logic
is distributed as plugins. Plugins may perform tasks such as PDF creation,
data transformation, validation, or other compute steps.

---

## Features

### ✅ Secure plugin execution
- Plugins must be signed (HMAC-SHA256)
- Signatures are verified before execution
- Secrets supplied via environment variables

### ✅ Encrypted data flow
- AES-GCM (256-bit) input decryption
- Optional AES-GCM output encryption
- Base64/Base64URL final encoding

### ✅ Plugin timeout control
- Executed via `ExecutorService`
- Cancelled on timeout
- Plugin exceptions are contained

### ✅ Pluggable data sources
- Abstract `FileDataProvider` for easy testing
- Default local-filesystem implementation

### ✅ Developer-friendly modes
- CLI support
- Embedded usage via `reflectedMain()`

---

## High-level Architecture

```text
Main
 └─ Processor
     ├─ verifyPlugin (HMAC from env)
     ├─ decryptInputIfNeeded
     ├─ PluginProcessor
     │    ├─ load JAR
     │    ├─ reflect execute(meta,input,settings)
     │    └─ timeout + isolation
     ├─ encryptResultIfNeeded
     └─ base64 encode
```


---

## JSON Input Format

Runtime expects three JSON objects:  
`meta`, `input`, `settings`

### Example
```json
{
  "meta": {
    "pluginName": "pdf-basic",
    "pluginMainClass": "example.plugins.BasicPDF",
    "fileLocation": "./plugins/basic-pdf.jar",
    "encryptedInput": false,
    "encryptOutput": false,
    "urlSafeOutput": false
  },
  "input": {
    "data": "Hello world"
  },
  "settings": { }
}
```

## Plugin Contract

Plugins must define a class with a method:
```java

public byte[] execute(
    JsonObject meta,
    JsonObject input,
    JsonObject settings
);

```
The plugin returns raw byte[], which will be:

- Optionally encrypted
- Base64/Base64URL encoded

---

## Programmatic Usage
### CLI
```Bash
echo '{"meta": {...}, "input": {...}, "settings": {...}}' \
  | java -jar runtime.jar

```
### Embedded
```Java
String result = Main.reflectedMain(metaJSON, inputJSON, settingsJSON);
```
---

## Plugin Verification
Each plugin must have a matching signature in .env:
```ENV
SECRET_KEY=yourMasterKey
PLUGIN_SIG_PDF_BASIC=base64HmacValue
```
Signature Generation;
```
HMAC_SHA256(SECRET_KEY, pluginJarBytes) → base64
```
Runtime verifies before execution

---

## Module Overview

| Package | Responsibility |
|---------|----------------|
| `ch.ermfox.crypto` | AES-256-GCM + HMAC-SHA256 functionality |
| `ch.ermfox.encoding` | Base64 + Base64URL helpers |
| `ch.ermfox.providers` | Abstractions for data loading (e.g., filesystem) |
| `ch.ermfox.processing` | Core runtime + plugin execution + orchestration |
| `ch.ermfox` | Entry points (`main` + `reflectedMain`) |

---

## Tests

Unit tests are provided under `src/test/java`.

Coverage includes:

- AES encryption & decryption (`AESFunctionsTest`)
- HMAC generation & verification (`HMACFunctionsTest`, `HMACOrchestrationTest`)
- Base64 encoding (`Base64FunctionsTest`)
- File provider abstraction (`FileDataProviderTest`)

Resources used during tests (e.g., fixtures) are located under `src/test/resources`.
For a complete test matrix and breakdown, see:  
➡️ [Testing Documentation](./Testing.md)

---
## Installation

### Requirements
- Java 21+
- Maven 3+

### Build from source
```bash
mvn package
```
This produces:
./target/Peregrine-Engine-runtime.jar

---
## Quick Start

Create an `input.json`:

```json
{
  "meta": {
    "pluginName": "example",
    "pluginMainClass": "example.Plugin",
    "fileLocation": "./plugins/example.jar"
  },
  "input": {
    "data": "Hello"
  },
  "settings": {}
}
```
### Run
```bash
cat input.json | java -jar Peregrine-Engine-runtime.jar
```

## Using Peregrine-Engine from Java

Peregrine-Engine can be embedded directly into an existing JVM-based service
(REST API, message consumer, job worker, etc.) via the reflectedMain(...)
entry point.

Example
```java
import ch.ermfox.Main;
import com.google.gson.JsonObject;

public class Example {
    public static void main(String[] args) {

        JsonObject meta = new JsonObject();
        meta.addProperty("pluginName", "pdf-basic");
        meta.addProperty("pluginMainClass", "example.plugins.BasicPDF");
        meta.addProperty("fileLocation", "./plugins/basic-pdf.jar");
        meta.addProperty("encryptedInput", false);
        meta.addProperty("encryptOutput", false);
        meta.addProperty("urlSafeOutput", false);

        JsonObject input = new JsonObject();
        input.addProperty("data", "Hello world");

        JsonObject settings = new JsonObject();

        // Execute synchronously; returns Base64 string
        String result = Main.reflectedMain(
            meta.toString(),
            input.toString(),
            settings.toString()
        );

        System.out.println("Plugin result:\n" + result);
    }
}
```
### Notes
- reflectedMain() returns the final encoded String
- Plugins are still isolated and verified the same way as in CLI mode
- Input is expected to be pre-validated/sanitized upstream
- The calling service is responsible for:
- - Loading .env before using Peregrine-Engine
- - Handling plugin result (e.g., storing PDF bytes)

---
## Security Notes

Peregrine-Engine verifies plugin integrity (HMAC-SHA256) and can optionally
encrypt/decrypt data using AES-GCM.

However, **runtime execution is not a sandbox**. Plugins can:
- Access filesystem
- Access network
- Spawn threads
- Use reflection

This runtime is designed to be executed **behind a trusted web/API layer**.
As such, Peregrine-Engine **expects all input to be sanitized and validated
before it reaches the engine**.  
The engine assumes all JSON (`meta`, `input`, `settings`) is structurally
correct and safe to process.

If you intend to execute **untrusted plugins**, or cannot guarantee input
sanitization upstream, running Peregrine-Engine in a **separate process or container**
is strongly recommended.
