import { useEffect, useState } from 'react'
import { Typography } from 'antd'
import type { NodeKind } from '../nodes'
import { getAvailableTools } from '../api/client'
import type { AvailableTool } from '../api/types'

const LABELS: Record<NodeKind, string> = {
  llm: 'LLM',
  agent: 'Agent',
  supervisor: 'Supervisor',
  sequence: 'Sequence',
  parallel: 'Parallel',
  conditional: 'Conditional',
}

const KINDS: NodeKind[] = ['llm', 'agent', 'supervisor', 'sequence', 'parallel', 'conditional']

interface NodePaletteProps {
  onAddNode: (kind: NodeKind) => void
  agents?: { id: string; name: string; type: 'agent' | 'supervisor' }[]
  selectedAgentId?: string
  onSelectAgent?: (id: string) => void
}

export default function NodePalette({
  onAddNode,
  agents = [],
  selectedAgentId,
  onSelectAgent,
}: NodePaletteProps) {
  const [tools, setTools] = useState<AvailableTool[]>([])
  useEffect(() => {
    getAvailableTools()
      .then(setTools)
      .catch(() => setTools([]))
  }, [])

  return (
    <div className="p-3">
      <Typography.Text strong className="text-slate-600 text-xs uppercase tracking-wide block mb-3">
        Add step
      </Typography.Text>
      <ul className="list-none p-0 m-0 space-y-0.5">
        {KINDS.map((kind) => (
          <li key={kind}>
            <button
              type="button"
              onClick={() => onAddNode(kind)}
              className="w-full text-left px-3 py-2 rounded-md text-sm border border-slate-200 bg-white hover:border-blue-400 hover:bg-blue-50/50 transition-colors"
            >
              {LABELS[kind]}
            </button>
          </li>
        ))}
      </ul>
      <div className="mt-4 pt-3 border-t border-slate-200">
        <Typography.Text strong className="text-slate-600 text-xs uppercase tracking-wide block mb-2">
          Agents ({agents.length})
        </Typography.Text>
        {agents.length === 0 ? (
          <Typography.Text type="secondary" className="text-xs">
            No agents yet.
          </Typography.Text>
        ) : (
          <ul className="list-none p-0 m-0 space-y-1">
            {agents.map((a) => {
              const selected = selectedAgentId === a.id
              return (
                <li key={a.id}>
                  <button
                    type="button"
                    onClick={() => onSelectAgent?.(a.id)}
                    className={`w-full text-left px-2 py-1.5 rounded-md text-xs border transition-colors ${
                      selected
                        ? 'border-blue-500 bg-blue-50 text-blue-900'
                        : 'border-slate-200 bg-white hover:border-blue-300 hover:bg-blue-50/40'
                    }`}
                  >
                    <span className="font-medium">{a.name}</span>
                    <span className="block text-[11px] text-slate-500">{a.type}</span>
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </div>
      {tools.length > 0 && (
        <div className="mt-4 pt-3 border-t border-slate-200">
          <Typography.Text strong className="text-slate-600 text-xs uppercase tracking-wide block mb-2">
            Default tools
          </Typography.Text>
          <ul className="list-none p-0 m-0 text-xs text-slate-600 space-y-1">
            {tools.map((t) => (
              <li key={t.id}>
                <span className="font-medium text-slate-700">{t.id}</span>
                {t.description && <span className="block text-slate-500 mt-0.5">{t.description}</span>}
              </li>
            ))}
          </ul>
          <Typography.Text type="secondary" className="text-xs block mt-1">
            Assign to Agent in the config panel.
          </Typography.Text>
        </div>
      )}
    </div>
  )
}
