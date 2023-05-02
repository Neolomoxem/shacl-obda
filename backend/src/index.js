const io = require('socket.io');
const childProcess = require('child_process');
const fs = require('fs');

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
    fs.writeFileSync("./tmp/constraint.ttl", message, {flag: "w+"})
    
    sendAsJson({
      type: "code",
      message: codes.startEval
    })
    
    // run the .jar file using the child_process module
    const jarProcess = childProcess.spawn('java', ['-jar', 'build/valid-1.0-FIXED.jar', "-r", "-f", "./tmp/constraint.ttl", "-tbox",  "http://comunica:3000/sparql", "http://ontop-cli:8080/sparql", "./out"]);

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
      fs.readdir('out/', (err, files) => {
        
        if (err) {
          // sendAsJson the error over the socket.io
          sendAsJson({
            type: "log",
            message: String(err)
          });

        } else {
          // iterate over the files and read their contents
          files.forEach(function(file) {
            fs.readFile('out/' + file, (err, data) => {
              
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
                  file: file,
                  message: String(data)
                });
              }
            });
          });
        }
      });
    });
  });
});
