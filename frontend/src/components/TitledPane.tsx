import React, { CSSProperties } from 'react'

interface PaneProps {
  title: string,
  children: React.ReactNode,
  style?: CSSProperties
}

const TitledPane = ({title, children, style}: PaneProps) => {
  return (
    <div className='tpContainer' style={style}>
      <div className='tpTitle'>{title}</div>
      <div className='tpBody'>{children}</div>
    </div>
  )
}

export default TitledPane