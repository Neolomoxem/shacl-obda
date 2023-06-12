import Button from '@mui/material/Button';
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
import LinearProgress from '@mui/material/LinearProgress';




function App() {
	const [constraint, setConstraint] = React.useState(
		`@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>.
		@prefix sh:  <http://www.w3.org/ns/shacl#>.
		@prefix xsd: <http://www.w3.org/2001/XMLSchema#>. 
		@prefix : <urn:absolute/prototyp#> . 
		
		# Welche Parameter haben ein Symbol, welche nicht?
		
		:testShape
			a sh:NodeShape ;
			sh:targetClass :Material ;
			sh:property [                 # _:b1
				sh:path :hat_Symbol ;
				sh:minCount 1;
				sh:severity sh:Violation ;
				sh:message "Kein Symbol!"@de ;
			] .`
	);
	const [tab, setTab] = useState(0)
	const [logs, setLogs] = useState([""])
	const [report, setReport] = useState([""])
	const [status, setStatus] = useState(0)

	const appendLogs = (s: string) => {
		setLogs((before: string[]) => {
			return [...before, s]
		})
	}

	const handleChange = (event: React.SyntheticEvent, newValue: number) => {
		setTab(newValue);
	};

	const startEval = () => {
		// Open Socket to eval server and send constraint
		const socket = io("ws://localhost:6777");
		socket.send(constraint)
		socket.on("connect_error", () => {
			// Dont keep trying to connect, until the user tries again manually
			socket.disconnect()
			alert("⚠️ The SHACL-OBDA Runner ist not running. Please try again later.")
		})

		// Reset state
		setLogs([""])
		setReport([""]);

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
					console.log(msg.message)
					setReport(msg.message.split(/\n/g));
					break
			}
		});
	}

	return (
		<div className="flex flex-col h-full" data-color-mode="dark">

			{/* HEADER */}
			{Header(startEval)}

			{/* STATUSBAR */}
			<div className="w-full h-1 bg-zinc-900">
				<div hidden={status !== 301} className='h-1'>
					<LinearProgress hidden={false} className='h-1'/>
				</div>
			</div>

			{/* TAB RIBBON */}
			<Tabs value={tab} onChange={handleChange} id="simple-tab-0" aria-label="basic tabs example" className='px-8'>
					<Tab label="🔗 Constraint" />
					<Tab label={status===401 ? "✅ Report" : "🕑 Report"}/>
					<Tab label="🗒️ Log" />
				</Tabs>

			{/* Main */}
			<div className="flex flex-col flex-1 bg-zinc-900 overflow-scroll">

				
				<div className="overflow-y-scroll">
					
					{/* EDITOR TAB */}
					<div role="tabpanel" id="simple-tabpanel-0" hidden={tab !== 0} >
					
						{/* EDITOR RIBBON */}
						<div className='bg-zinc-900 px-8 py-4 text-xs flex items-center'>
							<div className="flex items-center gap-4">
								<div>
									Upload Constraint:
								</div>
								<LocalFile setCode={setConstraint} />
							</div>
						</div>
						{/* CODE EDITOR */}
						<div className="px-6 py-4 bg-zinc-800">
							<CodeEditor
								value={constraint}
								language="sparql"
								padding={0}
								placeholder="Please enter a SHACL Constraint or load from file"
								onChange={(evn) => setConstraint(evn.target.value)}
								className="bg-zinc-800 font-consolas"
							/>
						</div>
					</div>
					{/* VALIDATION REPORT */}
					<div role="tabpanel" id="simple-tabpanel-0" hidden={tab !== 1}>
						<div className='px-8 py-8 text-xs bg-zinc-800'>
							{report.map((entry) => {
								return entry ? <p>{entry}</p> : <br />
							})}
							The Validation report will be displayed here.
						</div>
					</div>
					{/* EXECUTION LOGS */}
					<div role="tabpanel" id="simple-tabpanel-2" hidden={tab !== 2} className='px-8 bg-zinc-800 py-4'>
						<CodeEditor
							value={
								logs.reduce((prev, curr) => {
									return prev + "\n" + curr
								})
							}
							language="log"
							placeholder="Click RUN to start evaluation."
							style={{fontFamily: "consolas" }}
							className='bg-zinc-800'
							readOnly
						/>
					</div>
				</div>
			</div>
			<div className='px-8 py-4 text-zinc-500 text-xs text-right'>
				Institut für Informationssysteme, Universität zu Lübeck
			</div>
		</div>
	);
}

export default App;

export function Header(startEval: () => void) {
	return <header className=" px-8 py-6 flex justify-between items-center mb-2">
		<p className='font-bold text-xl'>SHACL-OBDA Validator ↗</p>
		<Button onClick={startEval}>▶ Run</Button>

		

	</header>;
}