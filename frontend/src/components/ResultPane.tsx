import React from 'react'
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';


interface PaneProps  {
    className: string,
    title: string,
    results?: string[],
}

const ResultPane = (props: PaneProps) => {
  return (
      <div>
        <p>{props.title}: {props.results?.length}</p>
        <ul>
          {
            props.results?.map((result) => {
              return <li>{result}</li>
            })
          }
        </ul>
      </div>

  )
}

export default ResultPane