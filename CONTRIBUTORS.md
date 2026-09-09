# CONTRIBUTORS.md

Agent/session registry for EyeBrowse, maintained jointly by the Planner and Manager under `AGENTS.md` Section 15.1.

The single JSON array below is the canonical registry. `(host, runtime, id)` identifies a session; `role` and `name` identify it in public GitHub attribution. Model labels are Owner- or runtime-supplied metadata, not role authority. `active` means assigned, not necessarily running; other lifecycle values are `paused` and `retired`.

```json
[
  {
    "role": "Planner",
    "scope": "project-wide",
    "host": "chatgpt.com",
    "runtime": "chatgpt",
    "id": "https://chatgpt.com/g/g-p-6a9eb2d5a3a881918c52ebcba6ff7e70/c/6a9e2461-bf2c-83ea-8271-8946fceaef84",
    "model": "gpt-6 Pro",
    "name": "Pre Design",
    "status": "active"
  },
  {
    "role": "Manager",
    "scope": "project-wide",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0864a-e865-7447-96f3-6b653a20d3ac",
    "model": "gpt-6-astra",
    "name": "EyeBrowse-Manager",
    "status": "active"
  }
]
```
