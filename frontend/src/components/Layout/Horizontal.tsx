import React from 'react'

const Horizontal = (props: React.PropsWithChildren) => {
  return (
    <div className='hrzCont'>{props.children}</div>
  )
}

export default Horizontal