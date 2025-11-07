# Peregrine‑Engine (Plugin Execution Engine)

A lightweight, secure, and flexible Java runtime for executing user‑defined plugins under controlled conditions.
Designed for data‑driven pipelines where processing logic is delivered as plugins (e.g., PDF generation, transformations, validation).

---

## Key Features

* ✅ Plugin integrity validation via HMAC‑SHA256
* ✅ Optional AES‑GCM encryption of input/output
* ✅ Standard & URL‑safe Base64 encoding
* ✅ Reflection‑based isolated plugin loading
* ✅ Timeout‑protected execution
* ✅ CLI + Embedded JVM usage via `reflectedMain()`
* ✅ Pluggable resource providers (default: local filesystem)

---

## Why Use Peregrine‑Engine?

Peregrine‑Engine enables secure, modular execution of business logic without redeploying core services.
It’s ideal for:

* Processing pipelines where logic changes frequently
* Multi‑tenant PDF/document generation
* Controlled execution of user extensions
* Integrity‑checked, encrypted workflows

It provides strong controls while remaining lightweight and embeddable.

---

## Architecture Overview

```
Main
 └─ Processor
     ├─ verifyPlugin (HMAC)
     ├─ decryptInputIfNeeded (AES‑GCM)
     ├─ PluginProcessor
     │     ├─ load JAR
     │     ├─ reflect execute(meta,input,settings)
     │     └─ enforce timeout
     ├─ encryptOutputIfNeeded (AES‑GCM)
     └─ base64 encode
```

---

## JSON Input Format

A single JSON object containing three sections:

* `meta`
* `input`
* `settings`

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
  "settings": {
    "timeoutMs": 8000
  }
}
```

---

## Metadata Fields

| Field             | Description                   | Required          |
| ----------------- | ----------------------------- | ----------------- |
| `pluginName`      | Logical identifier for plugin | ✅                 |
| `pluginMainClass` | Fully qualified main class    | ✅                 |
| `fileLocation`    | JAR location                  | ✅                 |
| `encryptedInput`  | AES‑GCM encrypted input       | ❌ (default false) |
| `encryptOutput`   | AES‑GCM encrypt output        | ❌ (default false) |
| `urlSafeOutput`   | Enable Base64‑URL output      | ❌ (default false) |

---

## Plugin Contract

Plugins must expose:

```java
public byte[] execute(JsonObject meta, JsonObject input, JsonObject settings);
```

* Must return raw `byte[]`
* `null` or non‑byte result is treated as failure
* Throwing exceptions returns an error

A default constructor is required.

---

## Programmatic Usage

### CLI

```bash
cat input.json | java -jar Peregrine-Engine-runtime.jar
```

### Embedded

```java
String result = Main.reflectedMain(metaJson, inputJson, settingsJson);
```

Returns Base64‑encoded output.

---

## Plugin Verification

Plugins are verified via HMAC‑SHA256, keyed from `SECRET_KEY`.

Environment variables:

```
SECRET_KEY=<master>
PLUGIN_SIG_<PLUGINNAME>=<base64 signature>
```

Signature must be computed over the raw JAR bytes.

---

## Timeout Resolution

Execution timeout resolves in this order:

1. `settings.timeoutMs`
2. `PLUGIN_TIMEOUT_MS` in environment
3. Default (5000 ms)

---

## Output Encoding

Output is:

* raw bytes → (optional AES‑GCM) → Base64 / Base64‑URL

---

## Error Handling

Processor returns **plain strings** on failure.
Timeout returns:

```
"plugin timed out"
```

Plugin failure returns:

```
"plugin execution failed: <reason>"
```

---

## Resource Providers

Default: `LocalFileDataProvider`

* Reads plugins via filesystem

Custom providers may be implemented via the `FileDataProvider` interface.

---

## Modules

| Package                | Responsibility              |
| ---------------------- | --------------------------- |
| `ch.ermfox.crypto`     | AES‑GCM + HMAC utilities    |
| `ch.ermfox.encoding`   | Base64 helpers              |
| `ch.ermfox.providers`  | Env + resource providers    |
| `ch.ermfox.processing` | Pipeline + plugin execution |
| `ch.ermfox.resources`  | Result wrappers             |
| `ch.ermfox`            | Entry points                |

---

## Example Plugin: JSON → CSV

A reference plugin `JsonToCsvPlugin` is included to demonstrate business-oriented plugin development.

### Purpose

Converts `input.data` (an array of JSON objects) into CSV for use in Excel, Google Sheets, etc.

**Input example:**

```json
{
  "data": [
    { "name": "Alice", "age": 30, "city": "London" },
    { "name": "Bob",   "age": 25, "city": "Paris" }
  ]
}
```

**CSV output (Base64-encoded):**

```
bmFtZSxhZ2UsY2l0eQpBbGljZSwzMCxMb25kb24KQm9iLDI1LFBhcmlzCg==
```

Decoded:

```
name,age,city
Alice,30,London
Bob,25,Paris
```

### Plugin Contract

```
public byte[] execute(JsonObject meta,
                      JsonObject input,
                      JsonObject settings);
```

### Packaging

The plugin is distributed as a **fat JAR** (including its dependencies) to ensure isolated loading.

---

## Working Version

✅ A functional integration between:

* The Peregrine-Engine runtime (`runtime.jar`), and
* The `JsonToCsvPlugin` plugin JAR

…has been validated.

The execution pipeline successfully performs:

1. Loading + signature verification of plugin JAR
2. Execution via isolated classloader
3. Timeout enforcement
4. Output encoding

This demonstrates working modular execution and plugin integrity controls.

---

## Build

Requirements:

* Java 21+
* Maven 3+

Build:

```bash
mvn package
```

Produces:

```
target/Peregrine-Engine-runtime.jar
```

---

## Security Notes

* Plugins are **not sandboxed**

    * Can access filesystem + network
    * Can start threads + reflection
* Use engine only behind trusted boundaries
* Input JSON must be validated upstream
* `deriveKey()` is SHA‑256 hash; adequate for internal workflows but consider PBKDF2/HKDF/Argon2 if using user secrets
* For untrusted plugins → isolate via separate process or container

---

## Roadmap

* Configurable thread pools
* Central logging
* Additional plugin examples
* Optional structured error responses
* Improved KDF options

---

## Logging

Peregrine‑Engine uses **SLF4J** for logging. No backend is bundled.
This allows integrators to choose their preferred logging provider:

* Logback
* Log4j2
* java.util.logging bridge
* File‑based or network logging targets

### Logging Scope

Only orchestration layers produce logs:

* `Processor` — pipeline + lifecycle events
* `PluginProcessor` — plugin load, execution, timeout

Crypto + encoding utilities intentionally do **not** log.

### Configure Logging

Add a backend to your application:

```xml
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <version>1.5.6</version>
</dependency>
```

### Log Levels

* `INFO` — high‑level lifecycle
* `DEBUG` — component steps
* `TRACE` — very fine execution details
* `WARN` — invalid plugin behavior
* `ERROR` — plugin crashes, unexpected failures

### Notes

* Logging is optional; engine works even with no backend.
* Returned result is never affected by logging.

## Quick Start

### 1) Build the engine

```bash
mvn package
```

Produces:

```
target/Peregrine-Engine-runtime.jar
```

### 2) Build the example plugin

```bash
cd Plugin/JsonToCsvPlugin
mvn package
```

Produces a fat jar:

```
JsonToCsvPlugin.jar
```

### 3) Sign the plugin

```bash
openssl dgst -sha256 -mac HMAC -macopt key:$SECRET_KEY -binary JsonToCsvPlugin.jar | base64
```

Copy output → `.env`:

```
SECRET_KEY=yourKey
PLUGIN_SIG_JSON_TO_CSV=<base64>
```

### 4) Run

```bash
echo '{"meta": {"pluginName":"json-to-csv","pluginMainClass":"ch.ermfox.JsonToCsvPlugin.JsonToCsvPlugin","fileLocation":"./JsonToCsvPlugin.jar","encryptedInput":false,"encryptOutput":false,"urlSafeOutput":false},"input": {"data":[{"name":"Alice","age":30},{"name":"Bob","age":25}]},"settings":{}}' | java -jar runtime.jar
```

---

## Plugin Development Template

```java
public class MyPlugin {
    public byte[] execute(JsonObject meta, JsonObject input, JsonObject settings) {
        // logic
        String output = "hello";
        return output.getBytes(StandardCharsets.UTF_8);
    }
}
```

---

## Signing Instructions

1. Choose `SECRET_KEY` shared by runtime + signing environment
2. Compute HMAC of raw plugin JAR
3. Base64-encode
4. Place in `.env`

---

## Design Rationale

Peregrine-Engine aims to provide a controlled execution environment:

* Modularity → Extend without redeploy
* Security → HMAC integrity + optional encryption
* Safety → Timeout + classloader isolation
* Flexibility → CLI + embedded

---

## Recruiter Summary

This project demonstrates:

* Secure plugin execution pipeline
* HMAC + AES-GCM crypto integration
* Reflection + dynamic loading
* Timeout + thread control
* SLF4J integration
* Maven multi-module architecture
* Real-world JSON-to-CSV business plugin
