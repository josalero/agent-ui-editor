import type { NodeTypes } from 'reactflow'
import LlmNode from './LlmNode'
import AgentNode from './AgentNode'
import SupervisorNode from './SupervisorNode'
import SequenceNode from './SequenceNode'
import ParallelNode from './ParallelNode'
import ConditionalNode from './ConditionalNode'
import ToolNode from './ToolNode'

export const nodeTypes: NodeTypes = {
  llm: LlmNode,
  agent: AgentNode,
  supervisor: SupervisorNode,
  sequence: SequenceNode,
  parallel: ParallelNode,
  conditional: ConditionalNode,
  tool: ToolNode,
}

export const NODE_KINDS = ['llm', 'agent', 'supervisor', 'sequence', 'parallel', 'conditional'] as const
export type NodeKind = (typeof NODE_KINDS)[number]
