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
    "name": "AGENTS.md Design",
    "status": "active"
  },
  {
    "role": "Manager",
    "scope": "outgoing Manager; bounded handoff/retirement only",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0864a-e865-7447-96f3-6b653a20d3ac",
    "model": "gpt-6-astra",
    "name": "EyeBrowse-Manager",
    "status": "retired"
  },
  {
    "role": "Worker",
    "scope": "issue #1; phone/experiment",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a08757-38e0-73f6-a82b-f38580993719",
    "model": "deepseek-v4.1-flash-expires-on-0910",
    "name": "LockProbe-Worker",
    "status": "retired"
  },
  {
    "role": "Reviewer",
    "scope": "issue #1; phone/experiment; PR #3",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a08824-24ad-77a0-9f14-04ea2d91cbcd",
    "model": "gpt-6-astra",
    "name": "LockProbe-Reviewer",
    "status": "retired"
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
  },
  {
    "role": "Planner",
    "scope": "v0.0.1; local Ticket Planner fallback",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a08b0d-75a8-7529-a345-e1857f506419",
    "model": "gpt-6-astra",
    "name": "v0.0.1 Planner-fallback",
    "status": "paused"
  },
  {
    "role": "Worker",
    "scope": "v0.0.1; issue #4; Phone/shared-core/RG launch",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a08b62-cce3-77c8-91fa-d9d876b907b8",
    "model": "deepseek-flash",
    "name": "Worker-#4",
    "status": "retired"
  },
  {
    "role": "Reviewer",
    "scope": "v0.0.1; issue #4; independent review",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a08c75-7ea4-73f8-b306-814c1488e4bb",
    "model": "gpt-6-astra",
    "name": "Reviewer-#4",
    "status": "retired"
  },
  {
    "role": "Worker",
    "scope": "v0.0.1; issue #5; Phone/shared-core hosting lifecycle",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0a004-df1e-76e1-bd18-9e974724b78a",
    "model": "GLM-5.3-Flash-EXL3",
    "name": "Worker-#5",
    "status": "retired"
  },
  {
    "role": "Reviewer",
    "scope": "v0.0.1; issue #5; independent hosting-lifecycle review",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0a0fc-eaea-77b3-a446-08df61bfc268",
    "model": "gpt-6-astra",
    "name": "Reviewer-#5",
    "status": "retired"
  },
  {
    "role": "Worker",
    "scope": "v0.0.1; issue #5; DeepSeek fallback standby; never activated; retired at #5 closeout",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0a441-925d-7484-81cf-23dd56f1ead4",
    "model": "deepseek-flash",
    "name": "Worker-#5-r2",
    "status": "retired"
  },
  {
    "role": "Worker",
    "scope": "v0.0.1; issue #5; GLM primary, ChatGPT Expert local-verification helper and designated return Worker; I5-T01..T02 executed; retired at #5 closeout",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0a81d-2502-7084-92bd-ef29f9f2e4fd",
    "model": "GLM-5.3-Flash-EXL3",
    "name": "Worker-#5-r3",
    "status": "retired"
  },
  {
    "role": "Expert",
    "scope": "issue #5; I5-T01 escalated corrections and separately released focused verification; superseded by ChatGPT Expert; retired at #5 closeout",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0a81d-2515-762f-9c88-02e751c04cad",
    "model": "gpt-6-astra Max",
    "name": "Expert-#5",
    "status": "retired"
  },
  {
    "role": "Planner",
    "scope": "v0.0.1",
    "host": "chatgpt.com",
    "runtime": "chatgpt",
    "id": "https://chatgpt.com/g/g-p-6a9eb2d5a3a881918c52ebcba6ff7e70-eyebrowse/c/6aaa09e0-af24-83ea-8105-2b56e638f94b",
    "model": "GPT-5.6 Sol / gpt-5-6-thinking / Extra High",
    "name": "v0.0.1 Planner",
    "status": "active"
  },
  {
    "role": "Reviewer",
    "scope": "v0.0.1",
    "host": "chatgpt.com",
    "runtime": "chatgpt",
    "id": "https://chatgpt.com/g/g-p-6a9eb2d5a3a881918c52ebcba6ff7e70-eyebrowse/c/6aaa1b2a-6628-83ea-b1a3-2296c513bd85",
    "model": "GPT-5.6 Sol / gpt-5-6-thinking / Extra High",
    "name": "v0.0.1 Reviewer",
    "status": "active"
  },
  {
    "role": "Expert",
    "scope": "issue #5; I5-T01 replacement Expert; rounds 8-10 corrections, IME-evocation diagnosis and dda3d2cc handback; §14.4 sign-off delivered; retired at #5 closeout",
    "host": "chatgpt.com",
    "runtime": "chatgpt",
    "id": "https://chatgpt.com/g/g-p-6a9eb2d5a3a881918c52ebcba6ff7e70-eyebrowse/c/6aaa3ed7-f170-83e9-b793-000f0e2065f2",
    "model": "GPT-5.6 Sol / gpt-5-6-thinking / Extra High",
    "name": "Expert",
    "status": "retired"
  },

  {
    "role": "Expert",
    "scope": "v0.0.1; RG-chain escalated corrections; completed issue #6 stabilization (handback accepted, verification 36/36 on S20+ at f5bdab3); retired at Owner direction after issue #6 closeout; conversation preserved",
    "host": "chatgpt.com",
    "runtime": "chatgpt",
    "id": "https://chatgpt.com/g/g-p-6a9eb2d5a3a881918c52ebcba6ff7e70-eyebrowse/c/6aae9eb3-2024-83e9-9377-0cb913bab243",
    "model": "gpt-6-pro (ChatGPT '6 Pro')",
    "name": "Expert-#6",
    "status": "retired"
  },

  {
    "role": "Worker",
    "scope": "v0.0.1; issue #6 RG-chain; sole source writer; Paseo worktree wks_65a49d03 (~/.paseo/worktrees/3stq8yqr/work-issue-6-r1, branch work/issue-6-r1 @ main)",
    "host": "u4090",
    "runtime": "pi",
    "id": "204730f4-7032-4135-a2a9-8ea79e96174f",
    "model": "GLM-5.3-Flash-EXL3 / max",
    "name": "Worker-#6-r1",
    "status": "retired"
  },
  {
    "role": "Worker",
    "scope": "v0.0.1; issue #6 RG-chain; replacement for Worker-#6-r1; sole source writer",
    "host": "u4090",
    "runtime": "codex",
    "id": "01a0bfbf-8f30-77d1-8c8f-756c51e5d547",
    "model": "GPT-5.6 Sol / gpt-5.6-sol / Extra High",
    "name": "Worker-#6-r2-Codex",
    "status": "retired"
  },
  {
    "role": "Worker",
    "scope": "Owner-authorized disposable Paseo creation/lifecycle qualification; no product work",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0adea-daa5-76bc-838a-c7eb0083814b",
    "model": "GLM-5.3-Flash-EXL3 / max",
    "name": "Paseo-Create-Setup-1",
    "status": "retired"
  },
  {
    "role": "Manager",
    "scope": "project-wide",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0b008-4721-73da-b70e-2870ec36f8ab",
    "model": "GLM-5.3-Flash-EXL3 / max",
    "name": "EyeBrowse-Manager-GLM",
    "status": "retired"
  },
  {
    "role": "Manager",
    "scope": "project-wide",
    "host": "u4090",
    "runtime": "codex",
    "id": "01a0bfb9-8fc0-7981-a493-14c6894dc70c",
    "model": "gpt-5.6-luna / max",
    "name": "EyeBrowse-Manager-Codex",
    "status": "retired"
  },
  {
    "role": "Manager",
    "scope": "project-wide",
    "host": "u4090",
    "runtime": "codex",
    "id": "01a0bfe8-79d4-73d0-97ce-aac94b7bc438",
    "model": "gpt-5.6-luna / max",
    "name": "EyeBrowse-Manager-Codex-r2",
    "status": "retired"
  },
  {
    "role": "Manager",
    "scope": "project-wide",
    "host": "u4090",
    "runtime": "pi",
    "id": "01a0c021-6d15-740e-bef7-c67e2504252f",
    "model": "GLM-5.3-Flash-EXL3 / max",
    "name": "EyeBrowse-Manager-GLM-r2",
    "status": "active"
  },
  {
    "role": "Worker",
    "scope": "v0.0.1; issue #6 RG-chain (completed: accepted, merged e8eb3cd); continues as qualified local executor for issue #7 (worktree/scratch/harness/device tooling retained)",
    "host": "u4090",
    "runtime": "codex",
    "id": "01a0bfdf-0ba4-78d0-9eb1-ff54e9ddad16",
    "model": "gpt-6-astra / medium",
    "name": "Worker-#6-r3-Codex-#7",
    "status": "active"
  },

  {
    "role": "Expert",
    "scope": "v0.0.1; RG-chain issues #7..#12 escalated corrections; successor to retired Expert-#6; inherits standing directives (physical S20+ testing, TCP5555 priority, T02/T03 seam QR bypass, hybrid owner-scoped viewport architecture per #issuecomment-5757375322); bootstrap ACKed, HOLDING for first assignment",
    "host": "chatgpt.com",
    "runtime": "chatgpt",
    "id": "https://chatgpt.com/g/g-p-6a9eb2d5a3a881918c52ebcba6ff7e70-eyebrowse/c/6ab111f3-423c-83ea-8d94-1a818786289a",
    "model": "gpt-6-pro (ChatGPT '6 Pro')",
    "name": "Expert-#7",
    "status": "active"
  }
]
```
