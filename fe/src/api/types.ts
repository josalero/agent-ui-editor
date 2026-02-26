/** One branch of a conditional node. */
export interface ConditionalBranchDto {
  conditionKey: string
  value: string
  agentId: string
}

/** One node in a workflow graph. */
export interface WorkflowNodeDto {
  id: string
  type: string
  baseUrl?: string | null
  modelName?: string | null
  llmId?: string | null
  name?: string | null
  outputKey?: string | null
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
}

/** One available tool from GET /api/v1/tools */
export interface AvailableTool {
  id: string
  description: string
}
