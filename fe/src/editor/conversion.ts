import type { Node, Edge } from 'reactflow'
import type { WorkflowNodeDto, ConditionalBranchDto } from '../api/types'
import type { NodeData } from './nodeData'
import { nodeDataToDto, dtoToNodeData } from './nodeData'

/** Derive dependency edges from current node data (llmId, subAgentIds, routerAgentId, branches). */
export function deriveEdgesFromNodes(nodes: Node<NodeData>[]): Edge[] {
  const nodeIds = new Set(nodes.map((n) => n.id))
  const edges: Edge[] = []
  for (const n of nodes) {
    const d = n.data
    if (!d) continue
    if (d.llmId && nodeIds.has(d.llmId)) {
      edges.push({ id: `e-${d.llmId}-${n.id}`, source: d.llmId, target: n.id })
    }
    if (d.subAgentIds) {
      for (const subId of d.subAgentIds) {
        if (nodeIds.has(subId)) {
          edges.push({ id: `e-${subId}-${n.id}`, source: subId, target: n.id })
        }
      }
    }
    if (d.routerAgentId && nodeIds.has(d.routerAgentId)) {
      edges.push({ id: `e-${d.routerAgentId}-${n.id}`, source: d.routerAgentId, target: n.id })
    }
    if (d.branches) {
      for (const b of d.branches) {
        if (nodeIds.has(b.agentId)) {
          edges.push({ id: `e-${n.id}-${b.agentId}-${b.value}`, source: n.id, target: b.agentId })
        }
      }
    }
  }
  return edges
}

export function graphToReactFlow(
  nodes: WorkflowNodeDto[],
  entryNodeId: string
): { nodes: Node<NodeData>[]; edges: Edge[] } {
  const nodeMap = new Map(nodes.map((n) => [n.id, n]))
  const edges: Edge[] = []
  const rfNodes: Node<NodeData>[] = nodes.map((n, i) => {
    const data = dtoToNodeData(n, n.id === entryNodeId)
    return {
      id: n.id,
      type: n.type,
      position: { x: 80 + (i % 3) * 220, y: 60 + Math.floor(i / 3) * 120 },
      data,
    }
  })

  for (const n of nodes) {
    if (n.llmId && nodeMap.has(n.llmId)) {
      edges.push({ id: `e-${n.llmId}-${n.id}`, source: n.llmId, target: n.id })
    }
    if (n.subAgentIds) {
      for (const subId of n.subAgentIds) {
        if (nodeMap.has(subId)) {
          edges.push({ id: `e-${subId}-${n.id}`, source: subId, target: n.id })
        }
      }
    }
    if (n.routerAgentId && nodeMap.has(n.routerAgentId)) {
      edges.push({ id: `e-${n.routerAgentId}-${n.id}`, source: n.routerAgentId, target: n.id })
    }
    if (n.branches) {
      for (const b of n.branches) {
        if (nodeMap.has(b.agentId)) {
          edges.push({ id: `e-${n.id}-${b.agentId}-${b.value}`, source: n.id, target: b.agentId })
        }
      }
    }
  }

  return { nodes: rfNodes, edges }
}

export function reactFlowToGraph(
  name: string,
  rfNodes: Node<NodeData>[],
  rfEdges: Edge[],
  entryNodeId: string
): { name: string; entryNodeId: string; nodes: WorkflowNodeDto[] } {
  const nodeMap = new Map(rfNodes.map((n) => [n.id, n]))
  const outEdges = new Map<string, string[]>()
  for (const e of rfEdges) {
    const list = outEdges.get(e.source) ?? []
    list.push(e.target)
    outEdges.set(e.source, list)
  }

  const nodes: WorkflowNodeDto[] = rfNodes.map((rf) => {
    const data = { ...rf.data, id: rf.id, type: rf.data.type }
    const dto = nodeDataToDto(data as NodeData)

    const targets = outEdges.get(rf.id)
    if (dto.type === 'agent' || dto.type === 'supervisor') {
      const fromLlm = rfEdges.filter((e) => e.target === rf.id).map((e) => e.source)
      const llmSource = fromLlm.find((sid) => nodeMap.get(sid)?.data?.type === 'llm')
      if (llmSource) dto.llmId = llmSource
    }
    if (dto.type === 'sequence' || dto.type === 'parallel' || dto.type === 'supervisor') {
      if (targets?.length) dto.subAgentIds = [...targets]
    }
    if (dto.type === 'conditional') {
      const routerSource = rfEdges.filter((e) => e.target === rf.id).map((e) => e.source)
      const router = routerSource.find((sid) => nodeMap.get(sid)?.data?.type === 'agent')
      if (router) dto.routerAgentId = router
      const branchTargets = targets ?? []
      dto.branches = branchTargets.map(
        (agentId, i): ConditionalBranchDto => ({
          conditionKey: 'category',
          value: `branch-${i}`,
          agentId,
        })
      )
    }
    return dto
  })

  return {
    name,
    entryNodeId: entryNodeId || (rfNodes[0]?.id ?? ''),
    nodes,
  }
}
