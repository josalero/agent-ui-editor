import { Handle, Position, type NodeProps } from 'reactflow'
import {
  ApartmentOutlined,
  BranchesOutlined,
  ClusterOutlined,
  OrderedListOutlined,
  RobotOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import type { ReactNode } from 'react'

const visuals: Record<string, { bg: string; border: string; chipBg: string; chipFg: string; icon: ReactNode; label: string }> = {
  llm: {
    bg: '#eff6ff',
    border: '#1d4ed8',
    chipBg: '#dbeafe',
    chipFg: '#1e3a8a',
    icon: <RobotOutlined />,
    label: 'LLM',
  },
  agent: {
    bg: '#f5f3ff',
    border: '#6d28d9',
    chipBg: '#ede9fe',
    chipFg: '#4c1d95',
    icon: <TeamOutlined />,
    label: 'Agent',
  },
  supervisor: {
    bg: '#fff7ed',
    border: '#c2410c',
    chipBg: '#ffedd5',
    chipFg: '#7c2d12',
    icon: <ClusterOutlined />,
    label: 'Supervisor',
  },
  sequence: {
    bg: '#ecfdf5',
    border: '#047857',
    chipBg: '#d1fae5',
    chipFg: '#065f46',
    icon: <OrderedListOutlined />,
    label: 'Sequence',
  },
  parallel: {
    bg: '#ecfeff',
    border: '#0f766e',
    chipBg: '#ccfbf1',
    chipFg: '#134e4a',
    icon: <ApartmentOutlined />,
    label: 'Parallel',
  },
  conditional: {
    bg: '#fff1f2',
    border: '#be123c',
    chipBg: '#ffe4e6',
    chipFg: '#881337',
    icon: <BranchesOutlined />,
    label: 'Conditional',
  },
  tool: {
    bg: '#fff7ed',
    border: '#c2410c',
    chipBg: '#ffedd5',
    chipFg: '#7c2d12',
    icon: <ToolOutlined />,
    label: 'Tool',
  },
}

export default function BaseNode({ data, type = 'agent', selected }: NodeProps) {
  const style = visuals[type] ?? {
    bg: '#f8fafc',
    border: '#64748b',
    chipBg: '#e2e8f0',
    chipFg: '#334155',
    icon: <TeamOutlined />,
    label: type,
  }
  const isEntry = data.isEntry === true
  const wasExecuted = data.wasExecuted === true
  const isTool = type === 'tool'
  const title = (data.name ?? data.label ?? data.id ?? type) as string
  const subtitle =
    type === 'llm'
      ? ((data.modelName as string | undefined) ?? 'Model')
      : type === 'tool'
        ? ((data.description as string | undefined) ?? (data.parentAgentId as string | undefined) ?? '')
        : ((data.role as string | undefined) ?? (data.outputKey as string | undefined) ?? '')
  const glow = [
    isEntry ? '0 0 0 2px gold' : null,
    wasExecuted ? '0 0 0 2px #22c55e, 0 0 14px rgba(34, 197, 94, 0.45)' : null,
  ]
    .filter(Boolean)
    .join(', ')
  return (
    <div
      style={{
        padding: '10px 12px',
        minWidth: isTool ? 150 : 190,
        borderRadius: 10,
        backgroundColor: style.bg,
        border: `2px solid ${selected ? '#0f172a' : style.border}`,
        boxShadow: glow || undefined,
      }}
    >
      <Handle
        type="target"
        id="main-in"
        position={Position.Left}
        style={{ width: 8, height: 8, background: '#0f172a', border: '1px solid #fff', left: -5, display: isTool ? 'none' : 'block' }}
      />
      <Handle
        type="source"
        id="main-out"
        position={Position.Right}
        style={{ width: 8, height: 8, background: '#0f172a', border: '1px solid #fff', right: -5, display: isTool ? 'none' : 'block' }}
      />
      <Handle
        type="target"
        id="dep-in"
        position={Position.Top}
        style={{ width: 6, height: 6, background: '#64748b', border: '1px solid #fff', top: -4 }}
      />
      <Handle
        type="source"
        id="dep-out"
        position={Position.Bottom}
        style={{ width: 6, height: 6, background: '#64748b', border: '1px solid #fff', bottom: -4, display: isTool ? 'none' : 'block' }}
      />
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
        <span
          style={{
            width: 24,
            height: 24,
            borderRadius: 8,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: style.chipBg,
            color: style.chipFg,
            fontSize: 14,
          }}
        >
          {style.icon}
        </span>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 11, letterSpacing: 0.3, textTransform: 'uppercase', color: style.chipFg }}>
            {style.label}
          </div>
          <div style={{ fontSize: 12, color: '#0f172a', fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 150 }}>
            {title}
          </div>
        </div>
      </div>
      {subtitle && (
        <div style={{ fontSize: 11, color: '#334155', opacity: 0.88, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 160 }}>
          {subtitle}
        </div>
      )}
      {isEntry && <div style={{ fontSize: 10, color: '#b8860b', marginTop: 4 }}>Entry</div>}
      {wasExecuted && <div style={{ fontSize: 10, color: '#15803d', marginTop: 4 }}>Executed</div>}
    </div>
  )
}
