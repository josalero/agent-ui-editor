import { Handle, Position, type NodeProps } from 'reactflow'

const colors: Record<string, { bg: string; border: string }> = {
  llm: { bg: '#e3f2fd', border: '#1976d2' },
  agent: { bg: '#f3e5f5', border: '#7b1fa2' },
  supervisor: { bg: '#fff3e0', border: '#e65100' },
  sequence: { bg: '#e8f5e9', border: '#388e3c' },
  parallel: { bg: '#e0f7fa', border: '#0097a7' },
  conditional: { bg: '#fce4ec', border: '#c2185b' },
}

export default function BaseNode({ data, type = 'agent', selected }: NodeProps) {
  const style = colors[type] ?? { bg: '#f5f5f5', border: '#9e9e9e' }
  const isEntry = data.isEntry === true
  return (
    <div
      style={{
        padding: '8px 12px',
        minWidth: 120,
        borderRadius: 8,
        backgroundColor: style.bg,
        border: `2px solid ${selected ? '#111' : style.border}`,
        boxShadow: isEntry ? '0 0 0 2px gold' : undefined,
      }}
    >
      <Handle
        type="target"
        position={Position.Left}
        style={{ width: 8, height: 8, background: '#0f172a', border: '1px solid #fff' }}
      />
      <Handle
        type="source"
        position={Position.Right}
        style={{ width: 8, height: 8, background: '#0f172a', border: '1px solid #fff' }}
      />
      <div style={{ fontWeight: 600, textTransform: 'capitalize', marginBottom: 4 }}>{type}</div>
      <div style={{ fontSize: 12, color: '#555' }}>{data.label ?? data.name ?? data.id}</div>
      {isEntry && <div style={{ fontSize: 10, color: '#b8860b', marginTop: 4 }}>Entry</div>}
    </div>
  )
}
