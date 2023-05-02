import React, { ChangeEvent } from 'react'

interface fileProps {
  setCode: React.Dispatch<React.SetStateAction<string>>
}

const LocalFile = ({setCode}:fileProps) => {

  const readConstraint = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) e.target.files[0].text().then(setCode);
  }
  return (
    <div className="localFile">
      <h4>Upload Constraint</h4>
      Please select a file containing a SHACL-Constraint.
      <input type="file" name="" id="" onChange={readConstraint} />
    </div>
  )
}

export default LocalFile