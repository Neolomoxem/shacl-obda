import Button from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import CodeEditor from '@uiw/react-textarea-code-editor';
import React, { useState } from 'react';
import { io } from 'socket.io-client';
import './App.css';
import './components/Components.css';
import InfoBar from './components/InfoBar';
import Horizontal from './components/Layout/Horizontal';
import LocalFile from './components/LocalFile';
import ResultPane from './components/ResultPane';
import TitledPane from './components/TitledPane';


const backend = process.env.REACT_APP_BACKEND_URL

function App() {
  const [constraint, setConstraint] = React.useState(
    `@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>.
    @prefix sh:  <http://www.w3.org/ns/shacl#>.
    @prefix xsd: <http://www.w3.org/2001/XMLSchema#>. 
    @prefix : <urn:absolute/prototyp#> . 
    
    # Welche Parameter haben ein Symbol, welche nicht?
    
    :testShape
      a sh:NodeShape ;
      sh:targetClass :Parameter ;
      sh:property [                 # _:b1
        sh:path :hat_Symbol ;
        sh:minCount 1;
        sh:severity sh:Violation ;
        sh:message "Kein Symbol!"@de ;
      ] .`
  );
  const [tab, setTab] = useState(0)
  const [logs, setLogs] = useState<string[]>([""])
  const [valid, setValid] = useState<string[]>([""])
  const [invalid, setInvalid] = useState<string[]>([""])
  const [report, setReport] = useState([""])
  const [status, setStatus] = useState<number>(0)
  const [bowl, setBowl] = useState({
    editorPlaceholder: "Click RUN to start evaluation",
    computations: 0
  })

  const appendLogs = (s:string) => {
    setLogs((before : string[]) => {
      return [...before, s]
    })
  }

  const handleChange = (event: React.SyntheticEvent, newValue: number) => {
    setTab(newValue);
  };

  const startEval = () => {
    // Open Socket to eval server and send constraint
    const socket = io(`ws://${backend}`);
    socket.send(constraint)
    socket.on("connect_error", () => {
      // Dont keep trying to connect, if until the user tries again manually
      socket.disconnect()
      alert("The SHACL-OBDA Runner ist not running. Please try again later.")
    })

    // Reset state
    setValid([""])
    setInvalid([""])
    setLogs([""])

    // handle communication
    socket.on("message", (msg_raw: string) => {
      interface messageType {
        type: string,
        message: string,
        file?: string

      }
      
      let msg: messageType = JSON.parse(msg_raw)

      switch (msg.type) {
        case "error":
        case "log": appendLogs(msg.message);
        break
        case "code": setStatus(parseInt(msg.message));
        break
        case "result": 
          switch (msg.file) {
            case "targets_valid.log": setValid(msg.message.split("\n"))
            break
            case "targets_violated.log": setInvalid(msg.message.split("\n"))
            break
            case "validation.log": setReport(msg.message.split("\n"))
            break
            default: break
          }
        break
      }
    });
  }

  return (
    <div className="App" data-color-mode="dark">
      <header className="App-header">
        <p>🔗 SHACL-OBDA 2</p>
        <p>
        </p>
      </header>
      <div hidden={status !== 301}>
        <LinearProgress hidden={false}/>
      </div>
      <div className="main">
        <Horizontal>
          <TitledPane title='' style={{background: "var(--bright)", maxWidth: ""}}>
            <Tabs value={tab} onChange={handleChange} id="simple-tab-0" aria-label="basic tabs example">
              <Tab label="🔗 Constraint" />
              <Tab label="🟢 Valid" />
              <Tab label="🔴 Invalid"/>
              <Tab label="🗒️ Log"/>
            </Tabs>
            
            <div role="tabpanel" id="simple-tabpanel-0" hidden={tab !== 0} className='scrollContainer'>
              <CodeEditor
                value={constraint}
                language="sparql"
                placeholder="Please enter a SHACL Constraint or load from file"
                onChange={(evn) => setConstraint(evn.target.value)}
                padding={15}
                style={{background: "var(--bright)", fontFamily: "monospace"}}
                className="mainEditor"
              />
            </div>

            <div role="tabpanel" id="simple-tabpanel-0" hidden={tab !== 1}>
                <ResultPane className="validPane" title="Valid" results={valid} />
            </div>
            
            <div role="tabpanel" id="simple-tabpanel-1" hidden={tab !== 2} className="scrollContainer">
              <ResultPane className="invalidPane" title="Invalid" results={invalid} />
            </div>
            
            <div role="tabpanel" id="simple-tabpanel-2" hidden={tab !== 3}>
              <CodeEditor
                    value={
                      logs.reduce((prev, curr) => {
                        return prev + "\n" + curr
                      })
                    }
                    language="log"
                    placeholder="Click RUN to start evaluation"
                    padding={15}
                    style={{background: "var(--bright)", fontFamily: "monospace"}}
                    className="mainEditor"
                    readOnly
                />
            </div>

          </TitledPane>
          <div style={{ maxWidth: "15em" }}>
            <TitledPane title='File Upload' style={{ maxWidth: "15em" }}>
              <LocalFile setCode={setConstraint}/>
            </TitledPane>
            <TitledPane title='Run' style={{ maxWidth: "15em" }}>
            <InfoBar values={[{info: "valid", value: valid.length}, {info: "invalid", value: invalid.length}]}/>
              <Button onClick={startEval}>▶ Run</Button>
            </TitledPane>
          </div>
        </Horizontal>

      </div>
    </div>
  );
}

export default App;
