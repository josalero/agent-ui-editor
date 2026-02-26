import type {
  WorkflowCreateRequest,
  WorkflowListResponse,
  WorkflowResponse,
  WorkflowUpdateRequest,
  WorkflowIdResponse,
  RunWorkflowResponse,
  AvailableTool,
} from './types'

const API_BASE = (import.meta as { env?: { VITE_API_BASE?: string } }).env?.VITE_API_BASE ?? ''

function getHeaders(): HeadersInit {
  return {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`API error ${res.status}: ${text || res.statusText}`)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export async function getWorkflows(): Promise<WorkflowListResponse> {
  const res = await fetch(`${API_BASE}/api/v1/workflows`, { headers: getHeaders() })
  return handleResponse<WorkflowListResponse>(res)
}

export async function getSampleWorkflows(): Promise<WorkflowListResponse> {
  const res = await fetch(`${API_BASE}/api/v1/workflows/samples`, { headers: getHeaders() })
  return handleResponse<WorkflowListResponse>(res)
}

export async function getWorkflow(id: string): Promise<WorkflowResponse> {
  const res = await fetch(`${API_BASE}/api/v1/workflows/${id}`, { headers: getHeaders() })
  return handleResponse<WorkflowResponse>(res)
}

export async function createWorkflow(request: WorkflowCreateRequest): Promise<WorkflowIdResponse> {
  const res = await fetch(`${API_BASE}/api/v1/workflows`, {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify(request),
  })
  return handleResponse<WorkflowIdResponse>(res)
}

export async function updateWorkflow(id: string, request: WorkflowUpdateRequest): Promise<WorkflowResponse> {
  const res = await fetch(`${API_BASE}/api/v1/workflows/${id}`, {
    method: 'PUT',
    headers: getHeaders(),
    body: JSON.stringify(request),
  })
  return handleResponse<WorkflowResponse>(res)
}

export async function deleteWorkflow(id: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/v1/workflows/${id}`, { method: 'DELETE' })
  return handleResponse<void>(res)
}

export async function runWorkflow(id: string, input: Record<string, unknown> = {}): Promise<RunWorkflowResponse> {
  const res = await fetch(`${API_BASE}/api/v1/workflows/${id}/run`, {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify(input),
  })
  return handleResponse<RunWorkflowResponse>(res)
}

export async function getAvailableTools(): Promise<AvailableTool[]> {
  const res = await fetch(`${API_BASE}/api/v1/tools`, { headers: getHeaders() })
  return handleResponse<AvailableTool[]>(res)
}
