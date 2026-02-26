import type { WorkflowNodeDto } from '../api/types'

/** Data stored in each React Flow node (graph fields + UI). */
export interface NodeData extends Record<string, unknown> {
  id: string
  type: string
  label?: string
  isEntry?: boolean
  baseUrl?: string
  modelName?: string
  llmId?: string
  name?: string
  outputKey?: string
  toolIds?: string[]
  subAgentIds?: string[]
  responseStrategy?: string
  routerAgentId?: string
  branches?: { conditionKey: string; value: string; agentId: string }[]
  threadPoolSize?: number
}

export function nodeDataToDto(data: NodeData): WorkflowNodeDto {
  const dto: WorkflowNodeDto = {
    id: data.id,
    type: data.type,
  }
  if (data.baseUrl != null) dto.baseUrl = data.baseUrl
  if (data.modelName != null) dto.modelName = data.modelName
  if (data.llmId != null) dto.llmId = data.llmId
  if (data.name != null) dto.name = data.name
  if (data.outputKey != null) dto.outputKey = data.outputKey
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
    llmId: dto.llmId ?? undefined,
    name: dto.name ?? undefined,
    outputKey: dto.outputKey ?? undefined,
    toolIds: dto.toolIds ?? undefined,
    subAgentIds: dto.subAgentIds ?? undefined,
    responseStrategy: dto.responseStrategy ?? undefined,
    routerAgentId: dto.routerAgentId ?? undefined,
    branches: dto.branches ?? undefined,
    threadPoolSize: dto.threadPoolSize ?? undefined,
  }
}
