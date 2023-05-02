import React from 'react'

interface PropTypes {
    values: {info: string, value: number}[]
}

const InfoBar = (props: PropTypes) => {
  return (
    <div className='ibBar'>
    {
        props.values.map((val) =>{
            return (
                <span> <b>{val.info}</b>: <span className='ibValue'>{val.value}</span></span>
            )
        })
    }
    </div>
  )
}

export default InfoBar