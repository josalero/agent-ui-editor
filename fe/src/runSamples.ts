/**
 * Default run input (JSON) for the seeded example workflows.
 * Keys must match workflow `name` from the API (see backend examples/*.json).
 * Each sample uses a `metadata` payload so the backend can build the user prompt from scope state.
 */
const STORY_INPUT = JSON.stringify(
  {
    metadata: {
      prompt: 'Write a short story (2–3 paragraphs) about a robot in Paris in noir style.',
      topic: 'a robot in Paris',
      style: 'noir',
    },
  },
  null,
  2
)

const EVENING_PLAN_INPUT = JSON.stringify(
  {
    metadata: {
      prompt: 'Suggest an evening plan for a cozy mood: one movie and one dinner idea.',
      mood: 'cozy',
    },
  },
  null,
  2
)

const EXPERT_ROUTER_INPUT = JSON.stringify(
  { metadata: { prompt: 'What time is it right now? Tell me the current time.' } },
  null,
  2
)

const SUPERVISOR_INPUT = JSON.stringify(
  { metadata: { prompt: 'Suggest one cozy movie and one dinner idea for tonight.' } },
  null,
  2
)

const SAMPLE_INPUTS: Record<string, string> = {
  'Story workflow': STORY_INPUT,
  'Story': STORY_INPUT,
  'Evening plan workflow': EVENING_PLAN_INPUT,
  'Evening plan': EVENING_PLAN_INPUT,
  'Expert router workflow': EXPERT_ROUTER_INPUT,
  'Expert router': EXPERT_ROUTER_INPUT,
  'Supervisor workflow': SUPERVISOR_INPUT,
  'Supervisor': SUPERVISOR_INPUT,
}

/** Fallback when workflow name does not match any sample. Always includes clear `metadata`. */
const DEFAULT_INPUT = JSON.stringify(
  { metadata: { prompt: 'Reply with a single short sentence to confirm you understood.' } },
  null,
  2
)

export function getDefaultRunInput(workflowName: string | undefined): string {
  if (workflowName == null || workflowName.trim() === '') {
    return DEFAULT_INPUT
  }
  const exact = SAMPLE_INPUTS[workflowName]
  if (exact) return exact
  const normalized = workflowName.trim().toLowerCase()
  const byNormalized = Object.keys(SAMPLE_INPUTS).find((k) => k.toLowerCase() === normalized)
  if (byNormalized) return SAMPLE_INPUTS[byNormalized]
  const byContains = Object.keys(SAMPLE_INPUTS).find(
    (k) => k.toLowerCase().includes(normalized) || normalized.includes(k.toLowerCase())
  )
  if (byContains) return SAMPLE_INPUTS[byContains]
  return DEFAULT_INPUT
}
