import { useState, useEffect } from 'react'
import { Modal, Input, Button, Space, Alert, Tag } from 'antd'
import { getDefaultRunInput } from '../runSamples'
import type { RunWorkflowResponse } from '../api/types'

const { TextArea } = Input

interface RunDialogProps {
  workflowId: string
  workflowName: string
  onClose: () => void
  onRun: (workflowId: string, input: Record<string, unknown>) => Promise<RunWorkflowResponse>
  onTrace?: (executedNodeIds: string[]) => void
}

export default function RunDialog({ workflowId, workflowName, onClose, onRun, onTrace }: RunDialogProps) {
  const [inputJson, setInputJson] = useState(() => getDefaultRunInput(workflowName ?? ''))

  useEffect(() => {
    setInputJson(getDefaultRunInput(workflowName ?? ''))
  }, [workflowId, workflowName])
  const [result, setResult] = useState<string | null>(null)
  const [executedNodes, setExecutedNodes] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)

  const handleRun = async () => {
    setError(null)
    setResult(null)
    setExecutedNodes([])
    let input: Record<string, unknown>
    try {
      input = JSON.parse(inputJson) as Record<string, unknown>
    } catch {
      setError('Invalid JSON')
      return
    }
    setRunning(true)
    try {
      const res = await onRun(workflowId, input)
      setResult(res.result)
      const names = Array.isArray(res.executedNodeNames) ? res.executedNodeNames.filter(Boolean) : []
      const ids = Array.isArray(res.executedNodeIds) ? res.executedNodeIds.filter(Boolean) : []
      setExecutedNodes(names.length > 0 ? names : ids)
      if (onTrace) onTrace(ids)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setRunning(false)
    }
  }

  return (
    <Modal
      title={`Run: ${workflowName}`}
      open
      onCancel={onClose}
      footer={null}
      width={560}
      destroyOnClose
      afterClose={() => {
        setResult(null)
        setExecutedNodes([])
        setError(null)
      }}
    >
      <Space direction="vertical" className="w-full" size="middle">
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">Input (JSON)</label>
          <TextArea
            value={inputJson}
            onChange={(e) => setInputJson(e.target.value)}
            rows={8}
            className="font-mono text-sm"
          />
        </div>
        {error && <Alert type="error" showIcon message={error} />}
        {result != null && (
          <div>
            <span className="text-sm font-medium text-slate-700">Result</span>
            <pre className="mt-1 p-3 bg-slate-100 rounded text-sm overflow-auto max-h-48">
              {result}
            </pre>
          </div>
        )}
        {executedNodes.length > 0 && (
          <div>
            <span className="text-sm font-medium text-slate-700">Executed nodes</span>
            <div className="mt-1 flex flex-wrap gap-1">
              {executedNodes.map((name) => (
                <Tag key={name} color="green">
                  {name}
                </Tag>
              ))}
            </div>
          </div>
        )}
        <Space>
          <Button type="primary" loading={running} onClick={handleRun}>
            {running ? 'Running…' : 'Run'}
          </Button>
          <Button onClick={onClose}>Close</Button>
        </Space>
      </Space>
    </Modal>
  )
}
