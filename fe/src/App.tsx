import { Link, useLocation, Routes, Route } from 'react-router-dom'
import { ConfigProvider, Layout, Menu } from 'antd'
import WorkflowList from './pages/WorkflowList'
import WorkflowEditor from './pages/WorkflowEditor'

const { Header, Content } = Layout

function App() {
  const location = useLocation()
  const selectedKey = location.pathname === '/' ? 'list' : 'editor'

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 8,
        },
      }}
    >
      <Layout className="min-h-screen">
        <Header className="flex items-center px-4 bg-slate-900">
          <div className="text-white font-semibold text-lg mr-6">Agent UI Editor</div>
          <Menu
            theme="dark"
            mode="horizontal"
            selectedKeys={[selectedKey]}
            className="min-w-0 flex-1 border-0 bg-transparent"
            items={[
              { key: 'list', label: <Link to="/">Workflows</Link> },
              { key: 'new', label: <Link to="/workflows/new">New workflow</Link> },
            ]}
          />
        </Header>
        <Content className="p-4 bg-slate-50">
          <Routes>
            <Route path="/" element={<WorkflowList />} />
            <Route path="/workflows/new" element={<WorkflowEditor />} />
            <Route path="/workflows/:id" element={<WorkflowEditor />} />
          </Routes>
        </Content>
      </Layout>
    </ConfigProvider>
  )
}

export default App
