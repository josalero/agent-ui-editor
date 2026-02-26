import { memo } from 'react'
import type { NodeProps } from 'reactflow'
import BaseNode from './BaseNode'

function ParallelNode(props: NodeProps) {
  return <BaseNode {...props} />
}

export default memo(ParallelNode)
