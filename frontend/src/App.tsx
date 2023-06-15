import Button from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import CodeEditor from '@uiw/react-textarea-code-editor';
import React, { useState } from 'react';
import { io } from 'socket.io-client';
import './App.css';
import './components/Components.css';
import LocalFile from './components/LocalFile';




function App() {
	function extractValidationCounts(reportString:string) {
		const invalidRegex = / INVALID: (\d+)/;
		const validRegex = / VALID: (\d+)/;
	
		const invalidMatch = reportString.match(invalidRegex);
		const validMatch = reportString.match(validRegex);
	
		const invalid = invalidMatch ? parseInt(invalidMatch[1]) : 0;
		const valid = validMatch ? parseInt(validMatch[1]) : 0;
		
		console.log(invalid, valid);
		return { invalid, valid };
	}
	
	const [constraint, setConstraint] = React.useState(
		`@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>.
@prefix sh:  <http://www.w3.org/ns/shacl#>.
@prefix xsd: <http://www.w3.org/2001/XMLSchema#>. 
@prefix : <urn:absolute/prototyp#> . 

# Jeder Parameter braucht einen Namen

:ParameterName
	a sh:NodeShape ;
	sh:targetClass 	:Parameter ;
	sh:property [                 
		sh:path :hat_Name;           
		sh:minCount 1;
	] .`
	);
	const [tab, setTab] = useState(0)
	const [logs, setLogs] = useState([""])
	const [report, setReport] = useState("")
	const [status, setStatus] = useState(0)
	const [results, setResults] = useState({valid: 0, invalid: 0})

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
		setReport("");
		setResults({valid: 0, invalid: 0});

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
					setReport(msg.message);
					
					setResults(extractValidationCounts(msg.message));

					break
			}
		});
	}

	return (
		<div className="flex flex-col h-full bg-zinc-100  dark:text-zinc-50 text-zinc-950 dark:bg-zinc-950">

			{/* HEADER */}
			{Header(startEval)}

			{/* STATUSBAR */}
			<div className={"w-full h-1 " + ((status:number) => {
				switch (status) {
					case 0:
						return "dark:bg-zinc-900"
					case 501:
						return "bg-yellow-500"
				
					default:
						break;
				}
			})(status)}>
				{status === 401 && (
					<div className='w-full h-1 bg-green-500'>
						<div style={{
							width:(results.invalid / (results.invalid + results.valid) * 100) + "%"
						}} className='dark:bg-red-500 bg-red-600 h-full'></div>
					</div>
				)}
				<div hidden={status !== 301} className='h-1'>
					<LinearProgress hidden={false} className='h-1'/>
				</div>
			</div>

			{/* TAB RIBBON */}
			<Tabs value={tab} onChange={handleChange} id="simple-tab-0" aria-label="basic tabs example" className='px-10 dark:bg-zinc-950 bg-zinc-800 text-zinc-900'>
					<Tab label="🔗 Constraint" />
					<Tab label={status===401 ? "✅ Report" : (status === 501 ? "⚠️ Report":"🕑 Report")}/>
					<Tab label="🗒️ Log" />
				</Tabs>

			{/* Main */}
			<div className="flex flex-col flex-1 dark:bg-zinc-950 overflow-scroll">

				
				<div className="overflow-y-scroll">
					
					{/* EDITOR TAB */}
					<div role="tabpanel" id="simple-tabpanel-0" hidden={tab !== 0} >
					
						{/* EDITOR RIBBON */}
						<div className='dark:bg-zinc-900 px-10 py-4 text-xs flex items-center'>
							<div className="flex items-center gap-4">
								<div className='flex flex-row gap-4'>
								📁 Upload Constraint:
								<LocalFile setCode={setConstraint} />
								</div>
							</div>
						</div>

						{/* CODE EDITOR */}
						<div className="px-10 py-4 dark:bg-zinc-900 bg-white">
							<CodeEditor
								value={constraint}
								language="sparql"
								padding={0}
								placeholder="Please enter a SHACL Constraint or load from file"
								onChange={(evn) => setConstraint(evn.target.value)}
								className="dark:bg-zinc-900 font-consolas p-0 m-0 bg-white text-sm"
							/>
						</div>
					</div>

					{/* VALIDATION REPORT */}
					<div role="tabpanel" id="simple-tabpanel-0" hidden={tab !== 1}>
						<div className='px-10 py-8 text-xs dark:bg-zinc-900'>
							{status === 501 && (
								<p className='text-red-600 dark:text-red-500'>There was an error validating your constraint. Please check the log.</p >
								
								)}
							<p></p>
							{status === 401 && (
								<pre>
									{report}
								</pre>
							)}
							{status < 401 && "The Validation Report will be displayed here."}
						</div>
					</div>
					
					{/* EXECUTION LOGS */}
					<div role="tabpanel" id="simple-tabpanel-2" hidden={tab !== 2} className='px-10 dark:bg-zinc-900 py-4 bg-white'>
						<CodeEditor
							value={
								logs.reduce((prev, curr) => {
									return prev + "\n" + curr
								})
							}
							language="log"
							placeholder="Click RUN to start evaluation."
							style={{fontFamily: "consolas" }}
							className='dark:bg-zinc-900 bg-white'
							readOnly
						/>
					</div>

				</div>
			</div>
			<div className='px-10 py-4 text-zinc-500 text-xs text-right bg-white dark:bg-zinc-950'>
				Institut für Informationssysteme, Universität zu Lübeck
			</div>
		</div>
	);
}

export default App;

export function Header(startEval: () => void) {
	return <header className=" px-10 py-6 flex justify-between items-center mb-2 dark:bg-zinc-950">
		<p className='font-bold text-xl'>SHACL-OBDA Validator ↗</p>
		<Button onClick={startEval} color='inherit'>▶ Run</Button>

		

	</header>;
}