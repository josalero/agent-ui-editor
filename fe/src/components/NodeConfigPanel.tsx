import { useCallback, useEffect, useMemo, useState } from 'react'
import type { Node, Edge } from 'reactflow'
import { Input, Button, Space, Typography, Tag, Tooltip } from 'antd'
import { FlagOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import type { NodeData } from '../editor/nodeData'
import { getDependencies } from '../editor/dependencies'
import { getAvailableTools } from '../api/client'
import type { AvailableTool } from '../api/types'

interface NodeConfigPanelProps {
  node: Node<NodeData> | null
  allNodes: Node<NodeData>[]
  allEdges: Edge[]
  onUpdate: (nodeId: string, data: Partial<NodeData>) => void
  onSetEntry: (nodeId: string) => void
  onDeleteNode?: (nodeId: string) => void
}

export default function NodeConfigPanel({ node, allNodes, allEdges, onUpdate, onSetEntry, onDeleteNode }: NodeConfigPanelProps) {
  const data = node?.data
  const [availableTools, setAvailableTools] = useState<AvailableTool[]>([])
  useEffect(() => {
    getAvailableTools()
      .then(setAvailableTools)
      .catch(() => setAvailableTools([]))
  }, [])
  const { uses, usedBy } = useMemo(
    () => (node ? getDependencies(node.id, allNodes, allEdges) : { uses: [], usedBy: [] }),
    [node, allNodes, allEdges]
  )
  const update = useCallback(
    (field: keyof NodeData, value: unknown) => {
      if (node) onUpdate(node.id, { [field]: value })
    },
    [node, onUpdate]
  )

  if (!node || !data) {
    return (
      <div className="p-6 flex flex-col items-center justify-center min-h-[200px] text-center text-slate-400">
        <p className="text-sm">Click a node on the canvas to configure it.</p>
        <p className="text-xs mt-1">Or add a new step from the left panel.</p>
      </div>
    )
  }

  const type = data.type ?? 'agent'

  return (
    <div className="p-4 text-sm">
      <Typography.Text strong className="text-slate-600 text-xs uppercase tracking-wide block mb-3">
        Node configuration
      </Typography.Text>
      <Typography.Text strong className="block mb-3 capitalize text-base">
        {type}
      </Typography.Text>
      <Space direction="vertical" className="w-full" size="small">
        <div>
          <label className="block text-slate-600 text-xs mb-1">ID</label>
          <Input
            value={data.id}
            onChange={(e) => update('id', e.target.value)}
            size="small"
          />
        </div>

        {type === 'llm' && (
          <>
            <div>
              <label className="block text-slate-600 text-xs mb-1">Base URL</label>
              <Input
                value={data.baseUrl ?? ''}
                onChange={(e) => update('baseUrl', e.target.value)}
                size="small"
              />
            </div>
            <div>
              <label className="block text-slate-600 text-xs mb-1">Model</label>
              <Input
                value={data.modelName ?? ''}
                onChange={(e) => update('modelName', e.target.value)}
                size="small"
              />
            </div>
          </>
        )}

        {(type === 'agent' || type === 'supervisor') && (
          <>
            <div>
              <label className="block text-slate-600 text-xs mb-1">Name</label>
              <Input
                value={data.name ?? ''}
                onChange={(e) => update('name', e.target.value)}
                size="small"
              />
            </div>
            <div>
              <label className="block text-slate-600 text-xs mb-1">LLM ID</label>
              <Input
                value={data.llmId ?? ''}
                onChange={(e) => update('llmId', e.target.value)}
                size="small"
              />
            </div>
            <div>
              <label className="block text-slate-600 text-xs mb-1">Output key</label>
              <Input
                value={data.outputKey ?? ''}
                onChange={(e) => update('outputKey', e.target.value)}
                size="small"
              />
            </div>
          </>
        )}

        {type === 'supervisor' && (
          <div>
            <label className="block text-slate-600 text-xs mb-1">Response strategy</label>
            <Input
              value={data.responseStrategy ?? ''}
              onChange={(e) => update('responseStrategy', e.target.value)}
              placeholder="e.g. FIRST"
              size="small"
            />
          </div>
        )}

        {type === 'agent' && (
          <>
            {availableTools.length > 0 && (
              <div>
                <label className="block text-slate-600 text-xs mb-1">Available tools</label>
                <div className="flex flex-wrap gap-1 mb-1">
                  {availableTools.map((t) => {
                    const current = Array.isArray(data.toolIds) ? data.toolIds : []
                    const added = current.includes(t.id)
                    return (
                      <Tooltip key={t.id} title={t.description || undefined}>
                        <Tag
                          color={added ? 'blue' : 'default'}
                          className="cursor-pointer"
                          onClick={() => {
                            if (added) {
                              update('toolIds', current.filter((id) => id !== t.id))
                            } else {
                              update('toolIds', [...current, t.id].sort())
                            }
                          }}
                        >
                          {added ? <span>{t.id} ✓</span> : <><PlusOutlined className="mr-0.5" />{t.id}</>}
                        </Tag>
                      </Tooltip>
                    )
                  })}
                </div>
              </div>
            )}
            <div>
              <label className="block text-slate-600 text-xs mb-1">Tool IDs (comma)</label>
              <Input
                value={Array.isArray(data.toolIds) ? data.toolIds.join(', ') : ''}
                onChange={(e) =>
                  update(
                    'toolIds',
                    e.target.value
                      .split(',')
                      .map((s) => s.trim())
                      .filter(Boolean)
                  )
                }
                size="small"
              />
            </div>
          </>
        )}

        {(type === 'sequence' || type === 'parallel') && (
          <>
            <div>
              <label className="block text-slate-600 text-xs mb-1">Output key</label>
              <Input
                value={data.outputKey ?? ''}
                onChange={(e) => update('outputKey', e.target.value)}
                size="small"
              />
            </div>
            {type === 'parallel' && (
              <div>
                <label className="block text-slate-600 text-xs mb-1">Thread pool size</label>
                <Input
                  type="number"
                  value={data.threadPoolSize ?? ''}
                  onChange={(e) =>
                    update('threadPoolSize', e.target.value ? parseInt(e.target.value, 10) : undefined)
                  }
                  size="small"
                />
              </div>
            )}
          </>
        )}

        {type === 'conditional' && (
          <div>
            <label className="block text-slate-600 text-xs mb-1">Router agent ID</label>
            <Input
              value={data.routerAgentId ?? ''}
              onChange={(e) => update('routerAgentId', e.target.value)}
              size="small"
            />
          </div>
        )}

        {/* Dependencies (DSL-like: uses / used by) */}
        {(uses.length > 0 || usedBy.length > 0) && (
          <div className="mt-3 pt-3 border-t border-slate-200">
            <Typography.Text strong className="text-slate-600 text-xs uppercase tracking-wide block mb-2">
              Dependencies
            </Typography.Text>
            {uses.length > 0 && (
              <div className="mb-2">
                <span className="text-slate-500 text-xs block mb-1">Uses (this node depends on)</span>
                <div className="flex flex-wrap gap-1">
                  {uses.map((u) => (
                    <Tag key={u.id} className="text-xs">
                      {u.label}
                    </Tag>
                  ))}
                </div>
              </div>
            )}
            {usedBy.length > 0 && (
              <div>
                <span className="text-slate-500 text-xs block mb-1">Used by</span>
                <div className="flex flex-wrap gap-1">
                  {usedBy.map((u) => (
                    <Tag key={u.id} color="blue" className="text-xs">
                      {u.label}
                    </Tag>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        <Space className="mt-3 w-full" direction="vertical">
          <Button
            type={data.isEntry ? 'primary' : 'default'}
            icon={<FlagOutlined />}
            size="small"
            block
            onClick={() => onSetEntry(node.id)}
          >
            {data.isEntry ? 'Entry node' : 'Set as entry'}
          </Button>
          {onDeleteNode && (
            <Button
              type="text"
              danger
              icon={<DeleteOutlined />}
              size="small"
              block
              onClick={() => {
                if (window.confirm(`Remove node "${data.id}" from the canvas?`)) onDeleteNode(node.id)
              }}
            >
              Delete node
            </Button>
          )}
        </Space>
      </Space>
    </div>
  )
}
