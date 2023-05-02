import React, { useState, useEffect } from "react";
import io from "socket.io-client";

const LogViewer = (props) => {
  const [logData, setLogData] = useState("");

  useEffect(() => {
    const socket = io(props.websocketUrl);
    socket.send("aiaiai")
    socket.on("message", (data) => {
      setLogData((prevLogData) => prevLogData + data + "\n");
    });
    return () => {
      socket.close();
    };
  }, [props.websocketUrl]);

  return (
    <textarea
      readOnly
      value={logData}
    />
  );
};

export default LogViewer;
