import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Card, Button, Select, List, Space, Alert, Spin, Typography } from 'antd'
import { PlusOutlined, PlayCircleOutlined, DeleteOutlined } from '@ant-design/icons'
import { getWorkflows, getSampleWorkflows, runWorkflow, deleteWorkflow } from '../api/client'
import RunDialog from '../components/RunDialog'
import type { WorkflowListItem } from '../api/types'

export default function WorkflowList() {
  const navigate = useNavigate()
  const [workflows, setWorkflows] = useState<WorkflowListItem[]>([])
  const [samples, setSamples] = useState<WorkflowListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [runTarget, setRunTarget] = useState<WorkflowListItem | null>(null)

  const refresh = useCallback(() => {
    getWorkflows()
      .then((res) => setWorkflows(res.workflows))
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    setLoading(true)
    refresh()
  }, [refresh])

  useEffect(() => {
    getSampleWorkflows()
      .then((res) => setSamples(res.workflows))
      .catch(() => setSamples([]))
  }, [])

  const handleDelete = useCallback(
    (w: WorkflowListItem) => {
      if (!window.confirm(`Delete workflow "${w.name}"? This cannot be undone.`)) return
      setError(null)
      deleteWorkflow(w.id)
        .then(refresh)
        .catch((e) => setError(e instanceof Error ? e.message : String(e)))
    },
    [refresh]
  )

  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-[200px]">
        <Spin size="large" tip="Loading workflows…" />
      </div>
    )
  }

  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        message="Error"
        description={error}
        className="mb-4"
        action={
          <Button size="small" onClick={() => setError(null)}>
            Dismiss
          </Button>
        }
      />
    )
  }

  return (
    <div className="max-w-4xl mx-auto">
      <Typography.Title level={3} className="!mb-4">
        Workflows
      </Typography.Title>

      <Space wrap className="mb-6" size="middle">
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/workflows/new')}>
          Create workflow
        </Button>
        {samples.length > 0 && (
          <Space>
            <span className="text-slate-600">Load example:</span>
            <Select
              placeholder="Choose example…"
              allowClear
              style={{ minWidth: 220 }}
              options={samples.map((s) => ({ label: s.name, value: s.id }))}
              onChange={(id) => id && navigate(`/workflows/${id}`)}
            />
          </Space>
        )}
      </Space>

      <Card className="shadow-sm">
        <List
          rowKey="id"
          dataSource={workflows}
          locale={{ emptyText: 'No workflows yet. Create one or load an example.' }}
          renderItem={(w) => (
            <List.Item
              actions={[
                <Button
                  key="run"
                  type="link"
                  size="small"
                  icon={<PlayCircleOutlined />}
                  onClick={() => setRunTarget(w)}
                >
                  Run
                </Button>,
                <Button
                  key="delete"
                  type="link"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => handleDelete(w)}
                >
                  Delete
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={<Link to={`/workflows/${w.id}`} className="font-medium">{w.name}</Link>}
                description={
                  <span className="text-slate-500 text-sm">
                    Updated {new Date(w.updatedAt).toLocaleString()}
                  </span>
                }
              />
            </List.Item>
          )}
        />
      </Card>

      {runTarget && (
        <RunDialog
          workflowId={runTarget.id}
          workflowName={runTarget.name}
          onClose={() => setRunTarget(null)}
          onRun={async (wid, input) => runWorkflow(wid, input)}
        />
      )}
    </div>
  )
}
