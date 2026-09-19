# Repository agent instructions

## DeepSeek delegation trigger

If a user prompt begins or ends with the exact sentence `DeepSeek this.`, use the
`deepseek-worker` skill. Treat the remainder of the prompt as the delegated task.
The primary agent must construct the task, run it through the external DeepSeek
V4.1 Flash provider, independently review the resulting commit and acceptance
checks, and report the worker's input, cached-input, output, and reasoning token
counts. Do not claim that an OpenAI subagent is the DeepSeek worker.
