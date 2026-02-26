import type { Node, Edge } from 'reactflow'
import type { NodeData } from './nodeData'

const LABELS: Record<string, string> = {
  'llm→agent': 'model',
  'llm→supervisor': 'model',
  'agent→sequence': 'delegates',
  'agent→parallel': 'delegates',
  'agent→supervisor': 'delegates',
  'agent→conditional': 'router',
  'agent→tool': 'tool',
  'supervisor→tool': 'tool',
  'conditional→agent': 'branch',
}

function edgeRole(sourceType: string, targetType: string): string {
  return LABELS[`${sourceType}→${targetType}`] ?? '→'
}

export function getEdgeLabel(nodes: Node<NodeData>[], edge: Edge): string {
  const source = nodes.find((n) => n.id === edge.source)
  const target = nodes.find((n) => n.id === edge.target)
  if (!source?.data?.type || !target?.data?.type) return ''
  return edgeRole(source.data.type, target.data.type)
}

export type VisualEdgeKind = 'llm' | 'sub-agent' | 'router' | 'branch' | 'tool' | 'custom'

/**
 * Convert model/dependency edges into visual orchestration edges:
 * parent/orchestrator on the left, dependencies/sub-agents/LLMs on the right.
 */
export function toVisualEdge(
  nodes: Node<NodeData>[],
  edge: Edge
): { edge: Edge; kind: VisualEdgeKind } {
  const byId = new Map(nodes.map((n) => [n.id, n]))
  const source = byId.get(edge.source)
  const target = byId.get(edge.target)
  if (!source || !target) return { edge, kind: 'custom' }

  const sourceType = source.data?.type
  const targetType = target.data?.type

  // Branch edges are already parent -> branch-agent.
  if (sourceType === 'conditional' && source.data?.branches?.some((b) => b.agentId === target.id)) {
    return { edge: { ...edge, id: `viz-${edge.id}` }, kind: 'branch' }
  }

  // Router dependency: render conditional -> router-agent.
  if (targetType === 'conditional' && target.data?.routerAgentId === source.id) {
    return {
      edge: { ...edge, id: `viz-${edge.id}`, source: target.id, target: source.id },
      kind: 'router',
    }
  }
  if (sourceType === 'conditional' && source.data?.routerAgentId === target.id) {
    return { edge: { ...edge, id: `viz-${edge.id}` }, kind: 'router' }
  }

  // LLM dependency: render agent/supervisor -> llm.
  if (target.data?.llmId === source.id && sourceType === 'llm') {
    return {
      edge: { ...edge, id: `viz-${edge.id}`, source: target.id, target: source.id },
      kind: 'llm',
    }
  }
  if (source.data?.llmId === target.id && targetType === 'llm') {
    return { edge: { ...edge, id: `viz-${edge.id}` }, kind: 'llm' }
  }

  // Sub-agent dependency: render sequence/parallel/supervisor -> sub-agent.
  const isParentType = (t?: string) => t === 'sequence' || t === 'parallel' || t === 'supervisor'
  if (isParentType(targetType) && target.data?.subAgentIds?.includes(source.id)) {
    return {
      edge: { ...edge, id: `viz-${edge.id}`, source: target.id, target: source.id },
      kind: 'sub-agent',
    }
  }
  if (isParentType(sourceType) && source.data?.subAgentIds?.includes(target.id)) {
    return { edge: { ...edge, id: `viz-${edge.id}` }, kind: 'sub-agent' }
  }

  // Tool dependency: render agent/supervisor -> tool.
  if ((sourceType === 'agent' || sourceType === 'supervisor') && targetType === 'tool') {
    return { edge: { ...edge, id: `viz-${edge.id}` }, kind: 'tool' }
  }
  if ((targetType === 'agent' || targetType === 'supervisor') && sourceType === 'tool') {
    return {
      edge: { ...edge, id: `viz-${edge.id}`, source: target.id, target: source.id },
      kind: 'tool',
    }
  }

  return { edge: { ...edge, id: `viz-${edge.id}` }, kind: 'custom' }
}

export function getDependencies(
  nodeId: string,
  nodes: Node<NodeData>[],
  edges: Edge[]
): { uses: { id: string; label: string }[]; usedBy: { id: string; label: string }[] } {
  const nodeMap = new Map(nodes.map((n) => [n.id, n]))
  const node = nodeMap.get(nodeId)
  const uses: { id: string; label: string }[] = []
  const usedBy: { id: string; label: string }[] = []

  const usedIds = new Set<string>()
  if (node?.data) {
    const d = node.data
    if (d.llmId && nodeMap.has(d.llmId)) {
      const n = nodeMap.get(d.llmId)!
      uses.push({ id: d.llmId, label: `LLM: ${n.data?.name ?? d.llmId}` })
      usedIds.add(d.llmId)
    }
    if (d.routerAgentId && nodeMap.has(d.routerAgentId)) {
      const n = nodeMap.get(d.routerAgentId)!
      uses.push({ id: d.routerAgentId, label: `Router: ${n.data?.name ?? d.routerAgentId}` })
      usedIds.add(d.routerAgentId)
    }
    if (d.subAgentIds?.length) {
      for (const id of d.subAgentIds) {
        if (nodeMap.has(id) && !usedIds.has(id)) {
          usedIds.add(id)
          const n = nodeMap.get(id)!
          uses.push({ id, label: `Sub: ${n.data?.name ?? n.data?.id ?? id}` })
        }
      }
    }
    const hasToolVisualEdges = edges.some(
      (e) => e.source === nodeId && nodeMap.get(e.target)?.data?.type === 'tool'
    )
    const toolRefs = (d.tools?.length ? d.tools : (d.toolIds ?? []).map((id: string) => ({ id }))) as { id: string; description?: string }[]
    if (toolRefs.length > 0 && !hasToolVisualEdges) {
      for (const t of toolRefs) {
        const label = t.description ? `Tool: ${t.id} — ${t.description}` : `Tool: ${t.id}`
        uses.push({ id: `${nodeId}:tool:${t.id}`, label })
      }
    }
    if (d.branches?.length) {
      for (const b of d.branches) {
        if (nodeMap.has(b.agentId) && !usedIds.has(b.agentId)) {
          usedIds.add(b.agentId)
          const n = nodeMap.get(b.agentId)!
          uses.push({ id: b.agentId, label: `Branch: ${n.data?.name ?? n.data?.id ?? b.agentId}` })
        }
      }
    }
  }
  for (const e of edges) {
    if (e.source === nodeId && !usedIds.has(e.target)) {
      const target = nodeMap.get(e.target)
      if (target) {
        usedIds.add(e.target)
        uses.push({ id: e.target, label: `→ ${target.data?.name ?? target.data?.id ?? e.target}` })
      }
    }
  }

  // Used by: incoming edges
  for (const e of edges) {
    if (e.target === nodeId) {
      const source = nodeMap.get(e.source)
      if (source) {
        const role = getEdgeLabel(nodes, e)
        usedBy.push({ id: e.source, label: role ? `${source.data?.name ?? source.data?.id ?? e.source} (${role})` : source.data?.name ?? source.data?.id ?? e.source })
      }
    }
  }

  return { uses, usedBy }
}

/** Compute depth of each node (0 = roots, 1 = uses only roots, etc.) for DSL-style layout. */
export function getNodeDepths(nodes: Node<NodeData>[], edges: Edge[]): Map<string, number> {
  const depths = new Map<string, number>()
  const inDegree = new Map<string, number>()
  for (const n of nodes) inDegree.set(n.id, 0)
  for (const e of edges) inDegree.set(e.target, (inDegree.get(e.target) ?? 0) + 1)
  let queue = nodes.filter((n) => inDegree.get(n.id) === 0).map((n) => n.id)
  let level = 0
  while (queue.length > 0) {
    const nextLevel: string[] = []
    for (const id of queue) {
      depths.set(id, level)
      for (const e of edges) {
        if (e.source === id) {
          const d = inDegree.get(e.target)! - 1
          inDegree.set(e.target, d)
          if (d === 0) nextLevel.push(e.target)
        }
      }
    }
    queue = nextLevel
    level += 1
  }
  const maxLevel = level
  for (const n of nodes) if (!depths.has(n.id)) depths.set(n.id, maxLevel)
  return depths
}

/** Position nodes left-to-right by dependency depth (DSL-style: roots left, entry/sinks right). */
export function layoutByDependency(
  nodes: Node<NodeData>[],
  edges: Edge[],
  options: { columnWidth?: number; rowHeight?: number } = {}
): Node<NodeData>[] {
  const { columnWidth = 280, rowHeight = 100 } = options
  const depths = getNodeDepths(nodes, edges)
  const byLevel = new Map<number, Node<NodeData>[]>()
  for (const n of nodes) {
    const d = depths.get(n.id) ?? 0
    const list = byLevel.get(d) ?? []
    list.push(n)
    byLevel.set(d, list)
  }
  const levels = [...byLevel.keys()].sort((a, b) => a - b)
  return nodes.map((n) => {
    const d = depths.get(n.id) ?? 0
    const levelIndex = levels.indexOf(d)
    const rowIndex = byLevel.get(d)!.indexOf(n)
    return {
      ...n,
      position: {
        x: levelIndex * columnWidth,
        y: rowIndex * rowHeight,
      },
    }
  })
}

/**
 * Layout with entry node on the LEFT and primary flow to the RIGHT (orchestrator-style).
 * Dependency nodes (LLM/router) are attached below their owner nodes to mimic "tool/model docks".
 */
export function layoutFromEntry(
  entryNodeId: string,
  nodes: Node<NodeData>[],
  edges: Edge[],
  options: { columnWidth?: number; rowHeight?: number } = {}
): Node<NodeData>[] {
  const { columnWidth = 320, rowHeight = 150 } = options
  const branchGapUnits = 0.45
  const dependencyXGap = Math.max(140, Math.floor(columnWidth * 0.55))
  const dependencyYGap = Math.max(120, Math.floor(rowHeight * 0.9))
  const dependencyPerRow = 3
  const nodeById = new Map(nodes.map((n) => [n.id, n]))
  const outgoingBySource = new Map<string, Set<string>>()
  const dependencyBySource = new Map<string, Set<string>>()
  const typeRank: Record<string, number> = {
    supervisor: 0,
    sequence: 1,
    parallel: 1,
    conditional: 1,
    agent: 2,
    llm: 3,
  }

  const compareNodeIds = (aId: string, bId: string): number => {
    const a = nodeById.get(aId)
    const b = nodeById.get(bId)
    const aType = a?.data?.type ?? ''
    const bType = b?.data?.type ?? ''
    const rankA = typeRank[aType] ?? 99
    const rankB = typeRank[bType] ?? 99
    if (rankA !== rankB) return rankA - rankB
    const aLabel = (a?.data?.name ?? a?.data?.label ?? aId).toLowerCase()
    const bLabel = (b?.data?.name ?? b?.data?.label ?? bId).toLowerCase()
    return aLabel.localeCompare(bLabel)
  }

  const primaryKinds = new Set<VisualEdgeKind>(['sub-agent', 'branch', 'custom'])
  const dependencyKinds = new Set<VisualEdgeKind>(['llm', 'router'])

  for (const e of edges) {
    if (!nodeById.has(e.source) || !nodeById.has(e.target)) continue
    const { edge: visualEdge, kind } = toVisualEdge(nodes, e)
    if (!nodeById.has(visualEdge.source) || !nodeById.has(visualEdge.target)) continue
    if (primaryKinds.has(kind)) {
      const targets = outgoingBySource.get(visualEdge.source) ?? new Set<string>()
      targets.add(visualEdge.target)
      outgoingBySource.set(visualEdge.source, targets)
      continue
    }
    if (dependencyKinds.has(kind)) {
      const targets = dependencyBySource.get(visualEdge.source) ?? new Set<string>()
      targets.add(visualEdge.target)
      dependencyBySource.set(visualEdge.source, targets)
    }
  }

  const levelByNode = new Map<string, number>([[entryNodeId, 0]])
  const primaryParent = new Map<string, string>()
  const connected = new Set<string>([entryNodeId])

  let frontier: string[] = [entryNodeId]
  let level = 0
  while (frontier.length > 0) {
    const next: string[] = []
    const orderedFrontier = [...frontier].sort(compareNodeIds)
    for (const sourceId of orderedFrontier) {
      const targets = [...(outgoingBySource.get(sourceId) ?? new Set<string>())].sort(compareNodeIds)
      for (const targetId of targets) {
        const nextLevel = level + 1
        const previousLevel = levelByNode.get(targetId)
        if (previousLevel == null || nextLevel < previousLevel) {
          levelByNode.set(targetId, nextLevel)
        }
        if (!primaryParent.has(targetId)) {
          primaryParent.set(targetId, sourceId)
        }
        if (!connected.has(targetId)) {
          connected.add(targetId)
          next.push(targetId)
        }
      }
    }
    frontier = next
    level += 1
  }

  const childrenByParent = new Map<string, string[]>()
  for (const [childId, parentId] of primaryParent.entries()) {
    const children = childrenByParent.get(parentId) ?? []
    children.push(childId)
    childrenByParent.set(parentId, children)
  }
  for (const [parentId, children] of childrenByParent.entries()) {
    childrenByParent.set(parentId, children.sort(compareNodeIds))
  }

  const spanMemo = new Map<string, number>()
  const spanOf = (nodeId: string): number => {
    const memo = spanMemo.get(nodeId)
    if (memo != null) return memo
    const children = childrenByParent.get(nodeId) ?? []
    if (children.length === 0) {
      spanMemo.set(nodeId, 1)
      return 1
    }
    let span = 0
    children.forEach((childId, index) => {
      if (index > 0) span += branchGapUnits
      span += spanOf(childId)
    })
    spanMemo.set(nodeId, span)
    return span
  }

  const positioned = new Map<string, { x: number; y: number }>()
  const placeSubtree = (nodeId: string, startUnits: number): void => {
    const span = spanOf(nodeId)
    const centerUnits = startUnits + span / 2
    const x = (levelByNode.get(nodeId) ?? 0) * columnWidth
    const y = centerUnits * rowHeight
    positioned.set(nodeId, { x, y })

    const children = childrenByParent.get(nodeId) ?? []
    let cursor = startUnits
    children.forEach((childId, index) => {
      placeSubtree(childId, cursor)
      cursor += spanOf(childId)
      if (index < children.length - 1) cursor += branchGapUnits
    })
  }
  if (nodeById.has(entryNodeId)) {
    placeSubtree(entryNodeId, 0)
  }

  const maxConnectedLevel = Math.max(0, ...levelByNode.values())
  const maxConnectedY = Math.max(0, ...[...positioned.values()].map((p) => p.y))
  const placeDependencies = (ownerId: string) => {
    const ownerPos = positioned.get(ownerId)
    if (!ownerPos) return
    const deps = [...(dependencyBySource.get(ownerId) ?? new Set<string>())]
      .filter((id) => nodeById.has(id) && !positioned.has(id))
      .sort(compareNodeIds)
    deps.forEach((depId, index) => {
      const row = Math.floor(index / dependencyPerRow)
      const col = index % dependencyPerRow
      const rowCount = Math.min(dependencyPerRow, deps.length - row * dependencyPerRow)
      const startX = ownerPos.x - ((rowCount - 1) * dependencyXGap) / 2
      positioned.set(depId, {
        x: startX + col * dependencyXGap,
        y: ownerPos.y + dependencyYGap + row * dependencyYGap,
      })
    })
  }

  const positionedPrimary = [...positioned.keys()]
  for (const ownerId of positionedPrimary) {
    placeDependencies(ownerId)
  }

  const disconnectedPrimary = nodes
    .filter((n) => !connected.has(n.id) && !positioned.has(n.id))
    .map((n) => n.id)
    .sort(compareNodeIds)
  let disconnectedY = maxConnectedY + rowHeight * 1.45
  for (const nodeId of disconnectedPrimary) {
    positioned.set(nodeId, {
      x: (maxConnectedLevel + 1) * columnWidth,
      y: disconnectedY,
    })
    placeDependencies(nodeId)
    disconnectedY += rowHeight
  }

  let detachedY = disconnectedY + rowHeight * 0.8
  const detached = nodes
    .filter((n) => !positioned.has(n.id))
    .map((n) => n.id)
    .sort(compareNodeIds)
  for (const nodeId of detached) {
    positioned.set(nodeId, { x: (maxConnectedLevel + 1.6) * columnWidth, y: detachedY })
    detachedY += rowHeight
  }

  return nodes.map((n) => ({
    ...n,
    position: positioned.get(n.id) ?? n.position,
  }))
}
