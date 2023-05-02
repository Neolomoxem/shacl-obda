import React, { CSSProperties } from 'react'

interface PropTypes {
  children?: React.ReactNode
  style?: CSSProperties
}

const Vertical = (props: PropTypes) => {
  return (
    <div className='vertCont' style={props.style}>{props.children}</div>
  )
}

export default Vertical