/** One branch of a conditional node. */
export interface ConditionalBranchDto {
  conditionKey: string
  value: string
  agentId: string
}

/** Tool reference on a node (id + optional description for layout/UI). */
export interface NodeToolDto {
  id: string
  description?: string
}

/** One node in a workflow graph. */
export interface WorkflowNodeDto {
  id: string
  type: string
  baseUrl?: string | null
  modelName?: string | null
  temperature?: number | null
  maxTokens?: number | null
  llmId?: string | null
  name?: string | null
  role?: string | null
  systemMessage?: string | null
  promptTemplate?: string | null
  outputKey?: string | null
  tools?: NodeToolDto[] | null
  toolIds?: string[] | null
  subAgentIds?: string[] | null
  responseStrategy?: string | null
  routerAgentId?: string | null
  branches?: ConditionalBranchDto[] | null
  threadPoolSize?: number | null
}

export interface WorkflowCreateRequest {
  name: string
  entryNodeId: string
  nodes: WorkflowNodeDto[]
}

export interface WorkflowUpdateRequest {
  name: string
  entryNodeId: string
  nodes: WorkflowNodeDto[]
}

export interface WorkflowListItem {
  id: string
  name: string
  updatedAt: string
}

export interface WorkflowListResponse {
  workflows: WorkflowListItem[]
}

export interface WorkflowResponse {
  id: string
  name: string
  entryNodeId: string
  nodes: WorkflowNodeDto[]
  createdAt: string
  updatedAt: string
}

export interface WorkflowIdResponse {
  id: string
}

export interface RunWorkflowResponse {
  result: string
  executedNodeIds?: string[]
  executedNodeNames?: string[]
}

/** One available tool from GET /api/v1/tools */
export interface AvailableTool {
  id: string
  description: string
}
