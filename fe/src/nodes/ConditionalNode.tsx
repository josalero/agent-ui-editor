import { memo } from 'react'
import type { NodeProps } from 'reactflow'
import BaseNode from './BaseNode'

function ConditionalNode(props: NodeProps) {
  return <BaseNode {...props} />
}

export default memo(ConditionalNode)
