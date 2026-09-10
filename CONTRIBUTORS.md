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
  },
  {
    "role": "Worker",
    "scope": "issue #1; phone/experiment",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a08757-38e0-73f6-a82b-f38580993719",
    "model": "deepseek-v4.1-flash-expires-on-0910",
    "name": "LockProbe-Worker",
    "status": "active"
  },
  {
    "role": "Reviewer",
    "scope": "issue #1; phone/experiment; PR #3",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a08824-24ad-77a0-9f14-04ea2d91cbcd",
    "model": "gpt-6-astra",
    "name": "LockProbe-Reviewer",
    "status": "active"
  },
  {
    "role": "Planner",
    "scope": "project-wide",
    "host": "chatgpt.com",
    "runtime": "chatgpt",
    "id": "https://chatgpt.com/g/g-p-6a9eb2d5a3a881918c52ebcba6ff7e70-eyebrowse/c/6aa24adb-d8f0-83ea-9f93-a68dcb21bd01",
    "model": "gpt-6 Pro",
    "name": "SPEC Design",
    "status": "active"
  }
]
```
