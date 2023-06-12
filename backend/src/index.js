const io = require('socket.io');
const childProcess = require('child_process');
const fs = require('fs');

const INFILE = "./tmp/constraint.ttl";
const ONTOP_URL = 'http://localhost:8080/sparql';

const server = new io.Server(6777, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
}})

const codes = {
  success: 401,
  startEval: 301,
  error: 501,
}

console.log("Waiting for connections on port 6777");
console.log(process.cwd())

server.on('connection', (socket) => {
  var withError = false
  const sendAsJson = (s) => socket.send(JSON.stringify(s))

  socket.on('message', (message) => {

    console.log(message)
    // TODO Fix parser to read from stdin instead od local file to avoid disk access
    fs.writeFileSync(INFILE, message, {flag: "w+"})
    
    sendAsJson({
      type: "code",
      message: codes.startEval
    })
    
    sendAsJson({
      type: "log",
      message: `Hallo ich verbinde mich mit ${ONTOP_URL}`
    });

    // run the .jar file using the child_process module
    // const jarProcess = childProcess.spawn('java', ['-jar', '--enable-preview', './build/shacl-obda.jar', INFILE, ONTOP_URL]);
    const jarProcess = childProcess.spawn('java', ['--enable-preview', '-cp', './build/jars/:./build/jars/*', 'ifis.App', INFILE, ONTOP_URL]);

    // listen for output from the .jar process
    // and send over socket
    jarProcess.stdout.on('data', (data) => {
      sendAsJson({
        type: "log",
        message: String(data)
      });
    });    

    // listen for any errors from the .jar process
    // sendAsJson the error over the socket.io connection
    jarProcess.stderr.on('data', (data) => {
      sendAsJson({
        type: "error",
        message: String(data)
      });
    });

    // listen for the 'close' event on the .jar process
    jarProcess.on('close', (closeCode) => {
      
      // send a message over the socket.io to indicate that the .jar process has finished
      sendAsJson({
        type: "log",
        message: 'jar process finished with code: ' + closeCode
      })
      
      // send code
      sendAsJson({
        type: "code",
        message: withError ? codes.error : codes.success
      })
      
      // read the files in the out/ directory
      fs.readFile('report.log', (err, data) => {
        if (err) {
          // sendAsJson the error over the socket.io
          sendAsJson({
            type: "error",
            message: String(err)
          });
        } else {
          // sendAsJson the file contents over the socket.io
          sendAsJson({
            type: "result",
            file: "report.log",
            message: String(data)
          });
        }
      });
    });
  });
});
