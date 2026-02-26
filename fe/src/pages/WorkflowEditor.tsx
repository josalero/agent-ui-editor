import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import ReactFlow, {
  Background,
  Controls,
  addEdge,
  useNodesState,
  useEdgesState,
  MarkerType,
  type Connection,
  type Node,
  type Edge,
  type ReactFlowInstance,
} from 'reactflow'
import 'reactflow/dist/style.css'
import { Button, Input, Alert, Space, Spin } from 'antd'
import { ArrowLeftOutlined, SaveOutlined, PlayCircleOutlined, DeleteOutlined, ApartmentOutlined } from '@ant-design/icons'
import { getWorkflow, createWorkflow, updateWorkflow, runWorkflow, deleteWorkflow } from '../api/client'
import { nodeTypes, type NodeKind } from '../nodes'
import NodePalette from '../components/NodePalette'
import NodeConfigPanel from '../components/NodeConfigPanel'
import RunDialog from '../components/RunDialog'
import { graphToReactFlow, reactFlowToGraph, deriveEdgesFromNodes } from '../editor/conversion'
import { getEdgeLabel, layoutByDependency, layoutFromEntry, toVisualEdge } from '../editor/dependencies'

function applyLayout(
  nodes: Node<NodeData>[],
  edges: Edge[],
  entryNodeId: string
): Node<NodeData>[] {
  if (entryNodeId && nodes.some((n) => n.id === entryNodeId)) {
    return layoutFromEntry(entryNodeId, nodes, edges, { columnWidth: 360, rowHeight: 170 })
  }
  return layoutByDependency(nodes, edges, { columnWidth: 300, rowHeight: 130 })
}
import type { NodeData } from '../editor/nodeData'

const ENTRY_NODE_TYPES = new Set(['sequence', 'parallel', 'supervisor'])

let nodeIdCounter = 0
function nextId(prefix: string): string {
  nodeIdCounter += 1
  return `${prefix}-${nodeIdCounter}`
}

function toolNodeId(parentAgentId: string, toolId: string): string {
  const normalized = toolId.replace(/[^a-zA-Z0-9_-]/g, '_')
  return `tool-${parentAgentId}-${normalized}`
}

/** One tool ref for layout (id + optional description). */
interface ToolRef {
  id: string
  description?: string
}

function getToolsList(data: NodeData | undefined): ToolRef[] {
  if (!data) return []
  // Prefer full tools array (id + description); support any shape from API
  const rawTools = (data as Record<string, unknown>).tools ?? data.tools
  if (rawTools != null && Array.isArray(rawTools) && rawTools.length > 0) {
    const list: ToolRef[] = []
    for (const t of rawTools) {
      if (t == null || typeof t !== 'object') continue
      const o = t as Record<string, unknown>
      const id = o.id != null ? String(o.id).trim() : ''
      if (id.length > 0) list.push({ id, description: o.description != null ? String(o.description) : undefined })
    }
    if (list.length > 0) return list
  }
  const raw = data.toolIds as string[] | string | undefined
  const ids: string[] = Array.isArray(raw) ? raw.filter(Boolean) : typeof raw === 'string' && raw.length > 0 ? [raw] : []
  return ids.map((id) => ({ id }))
}

function toolLabel(tool: ToolRef): string {
  return tool.id
    .split(/[_-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function createNodeData(kind: NodeKind): NodeData {
  const id = nextId(kind)
  const base: NodeData = { id, type: kind, label: id }
  if (kind === 'llm') {
    return {
      ...base,
      baseUrl: 'https://openrouter.ai/api/v1',
      modelName: 'openai/gpt-4o-mini',
      temperature: 0.7,
      maxTokens: 800,
    }
  }
  if (kind === 'agent' || kind === 'supervisor') return { ...base, name: id }
  if (kind === 'sequence' || kind === 'parallel') return { ...base, outputKey: 'output' }
  if (kind === 'conditional') return { ...base, routerAgentId: '', branches: [] }
  return base
}

export default function WorkflowEditor() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [workflowName, setWorkflowName] = useState('New workflow')
  const [entryNodeId, setEntryNodeId] = useState<string>('')
  const [nodes, setNodes, onNodesChange] = useNodesState<NodeData>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [selectedNode, setSelectedNode] = useState<Node<NodeData> | null>(null)
  const [loading, setLoading] = useState(!!id)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [runDialogOpen, setRunDialogOpen] = useState(false)
  const [lastRunExecutedCount, setLastRunExecutedCount] = useState(0)
  const [deleting, setDeleting] = useState(false)
  const [flow, setFlow] = useState<ReactFlowInstance<NodeData, Edge> | null>(null)

  useEffect(() => {
    if (!id) {
      setNodes([])
      setEdges([])
      setEntryNodeId('')
      setLastRunExecutedCount(0)
      setLoading(false)
      return
    }
    getWorkflow(id)
      .then((w) => {
        setWorkflowName(w.name)
        setEntryNodeId(w.entryNodeId)
        const { nodes: n, edges: e } = graphToReactFlow(w.nodes, w.entryNodeId)
        const layouted = applyLayout(n, e, w.entryNodeId)
        setNodes(layouted)
        setEdges(e)
        setLastRunExecutedCount(0)
      })
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setLoading(false))
  }, [id, setNodes, setEdges])

  const onConnect = useCallback(
    (params: Connection) => setEdges((prev) => addEdge(params, prev)),
    [setEdges]
  )

  const onAddNode = useCallback(
    (kind: NodeKind) => {
      const baseX = 250 + (nodes.length % 4) * 220
      const baseY = 100 + Math.floor(nodes.length / 4) * 140

      // Market-style default: every new agent/supervisor comes with its own dedicated model.
      if (kind === 'agent' || kind === 'supervisor') {
        const llmData = createNodeData('llm')
        const workerData = createNodeData(kind)
        workerData.llmId = llmData.id
        const llmNode: Node<NodeData> = {
          id: llmData.id,
          type: 'llm',
          position: { x: baseX, y: baseY + 170 },
          data: llmData,
        }
        const workerNode: Node<NodeData> = {
          id: workerData.id,
          type: kind,
          position: { x: baseX, y: baseY },
          data: workerData,
        }
        setNodes((prev) => [...prev, llmNode, workerNode])
        return
      }

      const data = createNodeData(kind)
      const newNode: Node<NodeData> = {
        id: data.id,
        type: kind,
        position: { x: baseX, y: baseY },
        data,
      }
      setNodes((prev) => [...prev, newNode])
    },
    [nodes.length, setNodes]
  )

  const onUpdateNodeData = useCallback(
    (nodeId: string, patch: Partial<NodeData>) => {
      setNodes((prev) =>
        prev.map((n) => (n.id === nodeId ? { ...n, data: { ...n.data, ...patch } } : n))
      )
    },
    [setNodes]
  )

  const onSetEntry = useCallback(
    (nodeId: string) => {
      const node = nodes.find((n) => n.id === nodeId)
      if (!node || !ENTRY_NODE_TYPES.has(node.data?.type ?? '')) {
        setError('Entry node must be sequence, parallel, or supervisor.')
        return
      }
      setEntryNodeId(nodeId)
      setNodes((prev) =>
        prev.map((n) => ({ ...n, data: { ...n.data, isEntry: n.id === nodeId } }))
      )
    },
    [nodes, setNodes]
  )

  const onSelectionChange = useCallback(
    ({ nodes: selected }: { nodes: Node[] }) => {
      const one = selected.find((n) => n.type !== undefined) as Node<NodeData> | undefined
      setSelectedNode(one ?? null)
    },
    []
  )

  const agentsInWorkflow = useMemo(
    () =>
      nodes
        .filter((n) => n.data?.type === 'agent' || n.data?.type === 'supervisor')
        .map((n) => ({
          id: n.id,
          name: (n.data?.name ?? n.data?.label ?? n.id) as string,
          type: n.data?.type as 'agent' | 'supervisor',
        }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    [nodes]
  )

  const handleSelectAgentFromPanel = useCallback(
    (nodeId: string) => {
      const target = nodes.find((n) => n.id === nodeId)
      if (!target) return
      setSelectedNode(target)
      setNodes((prev) =>
        prev.map((n) => ({
          ...n,
          selected: n.id === nodeId,
        }))
      )
      flow?.setCenter(target.position.x + 80, target.position.y + 30, { duration: 350, zoom: 1.05 })
    },
    [flow, nodes, setNodes]
  )

  const persistEdges = useMemo(() => {
    const derived = deriveEdgesFromNodes(nodes)
    const derivedKey = new Set(derived.map((e) => `${e.source}-${e.target}`))
    const extra = edges.filter((e) => !derivedKey.has(`${e.source}-${e.target}`))
    return [...derived, ...extra]
  }, [nodes, edges])

  const toolNodes = useMemo<Node<NodeData>[]>(() => {
    const created: Node<NodeData>[] = []
    const xs = nodes.map((n) => n.position.x)
    const minX = xs.length > 0 ? Math.min(...xs) : 0
    const maxX = xs.length > 0 ? Math.max(...xs) : 0
    for (const owner of nodes) {
      const ownerType = (owner.data?.type as string | undefined)?.toLowerCase()
      if (ownerType !== 'agent' && ownerType !== 'supervisor') continue
      const toolsList = getToolsList(owner.data)
      const roomLeft = owner.position.x - minX
      const roomRight = maxX - owner.position.x
      const placeRight = roomRight >= roomLeft
      const dependencyBaseX = placeRight ? owner.position.x + 220 : owner.position.x - 220
      const dependencyBaseY = owner.position.y + 4
      toolsList.forEach((tool, index) => {
        const row = index
        created.push({
          id: toolNodeId(owner.id, tool.id),
          type: 'tool',
          draggable: false,
          selectable: true,
          connectable: false,
          zIndex: 10,
          position: {
            x: dependencyBaseX,
            y: dependencyBaseY + row * 96,
          },
          data: {
            id: toolNodeId(owner.id, tool.id),
            type: 'tool',
            label: toolLabel(tool),
            toolId: tool.id,
            description: tool.description,
            parentAgentId: (owner.data?.name ?? owner.id) as string,
            isVisualOnly: true,
          },
        })
      })
    }
    return created
  }, [nodes])

  const displayNodes = useMemo(() => [...nodes, ...toolNodes], [nodes, toolNodes])

  const toolEdges = useMemo<Edge[]>(
    () =>
      nodes.flatMap((owner) => {
        const ownerType = (owner.data?.type as string | undefined)?.toLowerCase()
        if (ownerType !== 'agent' && ownerType !== 'supervisor') return []
        const toolsList = getToolsList(owner.data)
        return toolsList.map((tool) => ({
          id: `e-tool-${owner.id}-${tool.id}`,
          source: owner.id,
          target: toolNodeId(owner.id, tool.id),
        }))
      }),
    [nodes]
  )

  const displayEdges = useMemo(() => [...persistEdges, ...toolEdges], [persistEdges, toolEdges])

  const edgesWithLabels = useMemo<Edge[]>(
    () =>
      displayEdges.map((e) => {
        const { edge: visualEdge, kind } = toVisualEdge(displayNodes, e)
        const labelByKind: Record<string, string> = {
          'sub-agent': 'delegates',
          llm: 'model',
          router: 'router',
          branch: 'branch',
          tool: 'tool',
        }
        const label = labelByKind[kind] ?? getEdgeLabel(displayNodes, visualEdge)
        const styleByKind: Record<string, { stroke: string; strokeWidth: number; strokeDasharray?: string }> = {
          'sub-agent': { stroke: '#0f766e', strokeWidth: 2.6 },
          llm: { stroke: '#2563eb', strokeWidth: 2.2, strokeDasharray: '7 5' },
          router: { stroke: '#7c3aed', strokeWidth: 2.2, strokeDasharray: '5 5' },
          branch: { stroke: '#0284c7', strokeWidth: 2.4 },
          tool: { stroke: '#c2410c', strokeWidth: 2.1, strokeDasharray: '4 4' },
          custom: { stroke: '#475569', strokeWidth: 2.1 },
        }
        const edgeStyle = styleByKind[kind] ?? styleByKind.custom
        const isDependencyEdge = kind === 'llm' || kind === 'router' || kind === 'tool'
        return {
          ...visualEdge,
          type: isDependencyEdge ? 'smoothstep' : 'bezier',
          animated: kind === 'sub-agent' || kind === 'branch' || kind === 'tool',
          sourceHandle: isDependencyEdge ? 'dep-out' : 'main-out',
          targetHandle: isDependencyEdge ? 'dep-in' : 'main-in',
          ...(label ? { label } : {}),
          style: edgeStyle,
          labelStyle: {
            fill: '#0f172a',
            fontSize: 11,
            fontWeight: 600,
          },
          labelBgStyle: {
            fill: '#f8fafc',
            fillOpacity: 0.92,
          },
          labelBgPadding: [4, 2] as [number, number],
          labelBgBorderRadius: 4,
          markerEnd: { type: MarkerType.ArrowClosed, color: edgeStyle.stroke },
        }
      }),
    [displayNodes, displayEdges]
  )

  const handleSave = useCallback(async () => {
    const entry = entryNodeId
    if (!entry || nodes.length === 0) {
      setError('Add at least one node and set an entry node.')
      return
    }
    const entryNode = nodes.find((n) => n.id === entry)
    if (!entryNode || !ENTRY_NODE_TYPES.has(entryNode.data?.type ?? '')) {
      setError('Entry node must be sequence, parallel, or supervisor.')
      return
    }
    const payload = reactFlowToGraph(workflowName, nodes, persistEdges, entry)
    setSaving(true)
    setError(null)
    try {
      if (id) {
        await updateWorkflow(id, payload)
      } else {
        const res = await createWorkflow(payload)
        navigate(`/workflows/${res.id}`, { replace: true })
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }, [workflowName, entryNodeId, nodes, persistEdges, id, navigate])

  const handleDeleteWorkflow = useCallback(async () => {
    if (!id) return
    if (!window.confirm(`Delete this workflow? This cannot be undone.`)) return
    setDeleting(true)
    setError(null)
    try {
      await deleteWorkflow(id)
      navigate('/')
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setDeleting(false)
    }
  }, [id, navigate])

  const handleDeleteNode = useCallback(
    (nodeId: string) => {
      setNodes((prev) => prev.filter((n) => n.id !== nodeId))
      setEdges((prev) => prev.filter((e) => e.source !== nodeId && e.target !== nodeId))
      if (selectedNode?.id === nodeId) setSelectedNode(null)
      if (entryNodeId === nodeId) setEntryNodeId('')
    },
    [setNodes, setEdges, selectedNode?.id, entryNodeId]
  )

  const handleLayout = useCallback(() => {
    setNodes((prev) => applyLayout(prev, persistEdges, entryNodeId))
    requestAnimationFrame(() => {
      flow?.fitView({ duration: 320, padding: 0.22 })
    })
  }, [entryNodeId, persistEdges, setNodes, flow])

  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-[300px]">
        <Spin size="large" tip="Loading workflow…" />
      </div>
    )
  }

  return (
    <div className="flex flex-col h-[calc(100vh-7rem)] bg-white rounded-lg shadow-sm border border-slate-200 overflow-hidden">
      {/* Top bar: workflow name + actions (n8n-style) */}
      <div className="flex items-center gap-3 px-4 py-2 border-b border-slate-200 bg-white flex-shrink-0">
        <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/')} className="text-slate-600">
          Back
        </Button>
        <div className="h-6 w-px bg-slate-200" />
        <Input
          value={workflowName}
          onChange={(e) => setWorkflowName(e.target.value)}
          placeholder="Workflow name"
          variant="borderless"
          className="max-w-xs font-medium px-0"
        />
        {entryNodeId && (
          <span className="text-slate-400 text-xs truncate max-w-[140px]" title={entryNodeId}>
            Entry: {entryNodeId}
          </span>
        )}
        {lastRunExecutedCount > 0 && (
          <span className="text-emerald-700 text-xs truncate max-w-[180px]" title={`${lastRunExecutedCount} nodes executed in last run`}>
            Last run: {lastRunExecutedCount} nodes executed
          </span>
        )}
        <Space className="ml-auto">
          <Button
            icon={<ApartmentOutlined />}
            onClick={handleLayout}
            title={entryNodeId ? 'Arrange with entry on the left, dependencies to the right (orchestrator-style)' : 'Arrange nodes left-to-right by dependency'}
          >
            Layout
          </Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
            Save
          </Button>
          {id && (
            <>
              <Button icon={<PlayCircleOutlined />} onClick={() => setRunDialogOpen(true)}>
                Run
              </Button>
              <Button danger icon={<DeleteOutlined />} loading={deleting} onClick={handleDeleteWorkflow}>
                Delete
              </Button>
            </>
          )}
        </Space>
      </div>

      {error && (
        <Alert
          type="error"
          showIcon
          message={error}
          className="mx-4 mt-2 flex-shrink-0"
          closable
          onClose={() => setError(null)}
        />
      )}

      {/* Three columns: Left = Add step | Center = Canvas | Right = Node settings (n8n-style) */}
      <div className="flex flex-1 min-h-0">
        {/* Left: Node palette only */}
        <aside className="w-48 border-r border-slate-200 bg-slate-50/50 flex-shrink-0 overflow-auto">
          <NodePalette
            onAddNode={onAddNode}
            agents={agentsInWorkflow}
            selectedAgentId={
              selectedNode && (selectedNode.data?.type === 'agent' || selectedNode.data?.type === 'supervisor')
                ? selectedNode.id
                : undefined
            }
            onSelectAgent={handleSelectAgentFromPanel}
          />
        </aside>

        {/* Center: Canvas */}
        <main className="flex-1 min-w-0 bg-slate-100/80">
          <ReactFlow
            nodes={displayNodes}
            edges={edgesWithLabels}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onSelectionChange={onSelectionChange}
            onInit={setFlow}
            onNodesDelete={(deleted) => {
              const ids = deleted.map((n) => n.id)
              setEdges((prev) =>
                prev.filter((e) => !ids.includes(e.source) && !ids.includes(e.target))
              )
              if (selectedNode && ids.includes(selectedNode.id)) setSelectedNode(null)
              setEntryNodeId((current) => (ids.includes(current) ? '' : current))
            }}
            nodeTypes={nodeTypes}
            defaultEdgeOptions={{
              style: { stroke: '#1890ff', strokeWidth: 2 },
              markerEnd: { type: MarkerType.ArrowClosed, color: '#1890ff' },
            }}
            deleteKeyCode={['Backspace', 'Delete']}
            fitView
          >
            <Background color="#cbd5e1" gap={16} />
            <Controls />
          </ReactFlow>
        </main>

        {/* Right: Node configuration (when a node is selected) */}
        <aside className="w-72 border-l border-slate-200 bg-white flex-shrink-0 flex flex-col overflow-hidden">
          <div className="flex-1 overflow-auto">
            <NodeConfigPanel
              node={selectedNode}
              allNodes={displayNodes}
              allEdges={displayEdges}
              onUpdate={onUpdateNodeData}
              onSetEntry={onSetEntry}
              onDeleteNode={handleDeleteNode}
            />
          </div>
        </aside>
      </div>

      {runDialogOpen && id && (
        <RunDialog
          workflowId={id}
          workflowName={workflowName}
          onClose={() => setRunDialogOpen(false)}
          onRun={async (wid, input) => runWorkflow(wid, input)}
          onTrace={(executedNodeIds) => {
            const executedSet = new Set(executedNodeIds)
            setNodes((prev) =>
              prev.map((n) => ({
                ...n,
                data: {
                  ...n.data,
                  wasExecuted: executedSet.has(n.id),
                },
              }))
            )
            setLastRunExecutedCount(executedSet.size)
          }}
        />
      )}
    </div>
  )
}
