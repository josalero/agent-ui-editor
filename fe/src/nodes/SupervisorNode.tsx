import { memo } from 'react'
import type { NodeProps } from 'reactflow'
import BaseNode from './BaseNode'

function SupervisorNode(props: NodeProps) {
  return <BaseNode {...props} />
}

export default memo(SupervisorNode)
