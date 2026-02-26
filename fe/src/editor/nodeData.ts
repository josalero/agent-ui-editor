import type { WorkflowNodeDto, NodeToolDto } from '../api/types'

const TOOLS_DEBUG = true // set to false to disable (must match WorkflowEditor if desired)

/** Normalize tools from API: prefer tools[] (id + description), fallback to toolIds[]. */
function normalizeTools(dto: WorkflowNodeDto): NodeToolDto[] | undefined {
  const dtoId = (dto as { id?: string }).id
  const dtoType = (dto as { type?: string }).type
  const rawTools = (dto as unknown as Record<string, unknown>).tools ?? dto.tools
  if (rawTools != null && Array.isArray(rawTools) && rawTools.length > 0) {
    const list: NodeToolDto[] = []
    for (const t of rawTools) {
      if (t != null && typeof t === 'object') {
        const ref = t as Record<string, unknown>
        const rawId = ref.id
        const idStr = rawId != null ? String(rawId).trim() : ''
        if (idStr.length > 0) {
          list.push({
            id: idStr,
            description: ref.description != null ? String(ref.description) : undefined,
          })
        }
      }
    }
    if (TOOLS_DEBUG && list.length > 0) {
      console.log('[WorkflowEditor:tools] normalizeTools: node', dtoId, dtoType, '→ from tools', list.length, list.map((x) => x.id))
    }
    if (list.length > 0) return list
  }
  const rawIds = (dto as unknown as Record<string, unknown>).toolIds ?? dto.toolIds
  if (rawIds == null) return undefined
  const ids: string[] = Array.isArray(rawIds)
    ? (rawIds as unknown[]).filter((id: unknown): id is string => typeof id === 'string' && (id as string).length > 0)
    : typeof rawIds === 'string' && (rawIds as string).length > 0
      ? [rawIds as string]
      : []
  if (TOOLS_DEBUG && ids.length > 0) {
    console.log('[WorkflowEditor:tools] normalizeTools: node', dtoId, dtoType, '→ from toolIds', ids.length, ids)
  }
  return ids.length > 0 ? ids.map((id) => ({ id, description: undefined })) : undefined
}

/** Data stored in each React Flow node (graph fields + UI). */
export interface NodeData extends Record<string, unknown> {
  id: string
  type: string
  label?: string
  isEntry?: boolean
  baseUrl?: string
  modelName?: string
  temperature?: number
  maxTokens?: number
  llmId?: string
  name?: string
  role?: string
  systemMessage?: string
  promptTemplate?: string
  wasExecuted?: boolean
  outputKey?: string
  /** Full tool definitions (id + optional description) for layout/UI. */
  tools?: NodeToolDto[]
  toolIds?: string[]
  subAgentIds?: string[]
  responseStrategy?: string
  routerAgentId?: string
  branches?: { conditionKey: string; value: string; agentId: string }[]
  threadPoolSize?: number
  toolId?: string
  description?: string
  parentAgentId?: string
  isVisualOnly?: boolean
}

export function nodeDataToDto(data: NodeData): WorkflowNodeDto {
  const dto: WorkflowNodeDto = {
    id: data.id,
    type: data.type,
  }
  if (data.baseUrl != null) dto.baseUrl = data.baseUrl
  if (data.modelName != null) dto.modelName = data.modelName
  if (data.temperature != null) dto.temperature = data.temperature
  if (data.maxTokens != null) dto.maxTokens = data.maxTokens
  if (data.llmId != null) dto.llmId = data.llmId
  if (data.name != null) dto.name = data.name
  if (data.role != null) dto.role = data.role
  if (data.systemMessage != null) dto.systemMessage = data.systemMessage
  if (data.promptTemplate != null) dto.promptTemplate = data.promptTemplate
  if (data.outputKey != null) dto.outputKey = data.outputKey
  if (data.tools != null && data.tools.length > 0) {
    dto.tools = data.tools.map((t) => ({ id: t.id, description: t.description ?? '' }))
  }
  if (data.toolIds != null) dto.toolIds = data.toolIds
  if (data.subAgentIds != null) dto.subAgentIds = data.subAgentIds
  if (data.responseStrategy != null) dto.responseStrategy = data.responseStrategy
  if (data.routerAgentId != null) dto.routerAgentId = data.routerAgentId
  if (data.branches != null) dto.branches = data.branches
  if (data.threadPoolSize != null) dto.threadPoolSize = data.threadPoolSize
  return dto
}

export function dtoToNodeData(dto: WorkflowNodeDto, isEntry: boolean): NodeData {
  return {
    id: dto.id,
    type: dto.type,
    label: dto.name ?? dto.id,
    isEntry,
    baseUrl: dto.baseUrl ?? undefined,
    modelName: dto.modelName ?? undefined,
    temperature: dto.temperature ?? undefined,
    maxTokens: dto.maxTokens ?? undefined,
    llmId: dto.llmId ?? undefined,
    name: dto.name ?? undefined,
    role: dto.role ?? undefined,
    systemMessage: dto.systemMessage ?? undefined,
    promptTemplate: dto.promptTemplate ?? undefined,
    outputKey: dto.outputKey ?? undefined,
    tools: normalizeTools(dto),
    subAgentIds: dto.subAgentIds ?? undefined,
    responseStrategy: dto.responseStrategy ?? undefined,
    routerAgentId: dto.routerAgentId ?? undefined,
    branches: dto.branches ?? undefined,
    threadPoolSize: dto.threadPoolSize ?? undefined,
  }
}
