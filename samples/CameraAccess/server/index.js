const http = require("http");
const https = require("https");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { WebSocketServer } = require("ws");

const PORT = process.env.PORT || 8080;
const PUBLIC_DIR = path.join(__dirname, "public");

// TLS from `tailscale cert <hostname>`, run once on whichever machine hosts this server (see
// README). Falls back to plain HTTP/WS if no cert is present -- e.g. local dev, or a machine
// that hasn't been through that one-time setup yet -- rather than refusing to start.
function loadTlsCredentials() {
  if (process.env.TLS_CERT_PATH && process.env.TLS_KEY_PATH) {
    return {
      cert: fs.readFileSync(process.env.TLS_CERT_PATH),
      key: fs.readFileSync(process.env.TLS_KEY_PATH),
    };
  }
  const certFile = fs.readdirSync(__dirname).find((f) => f.endsWith(".crt"));
  const keyFile = fs.readdirSync(__dirname).find((f) => f.endsWith(".key"));
  if (!certFile || !keyFile) return null;
  return {
    cert: fs.readFileSync(path.join(__dirname, certFile)),
    key: fs.readFileSync(path.join(__dirname, keyFile)),
  };
}
const ROOM_CODE_RE = /^[A-Z2-9]{6}$/; // matches generateRoomCode()'s alphabet/length
const rooms = new Map(); // roomCode -> { creator: ws, viewer: ws, creatorToken: string, destroyTimer: timeout|null }

// Grace period (ms) before destroying a room when creator disconnects.
// Allows the iOS user to switch apps (e.g. copy room code, send via WhatsApp) and come back.
const ROOM_GRACE_PERIOD_MS = 60_000;

// TURN: ExpressTURN (1000 GB/month free, reliable)
// Ports 3478 (standard), 80, 443 (firewall bypass)
const EXPRESSTURN_SERVER = process.env.EXPRESSTURN_SERVER || "free.expressturn.com";
const EXPRESSTURN_USER = process.env.EXPRESSTURN_USER || "efPU52K4SLOQ34W2QY";
const EXPRESSTURN_PASS = process.env.EXPRESSTURN_PASS || "1TJPNFxHKXrZfelz";

function getTurnCredentials() {
  return {
    iceServers: [
      {
        urls: [
          `turn:${EXPRESSTURN_SERVER}:3478`,
          `turn:${EXPRESSTURN_SERVER}:3478?transport=tcp`,
          `turn:${EXPRESSTURN_SERVER}:80`,
          `turn:${EXPRESSTURN_SERVER}:80?transport=tcp`,
          `turns:${EXPRESSTURN_SERVER}:443?transport=tcp`,
        ],
        username: EXPRESSTURN_USER,
        credential: EXPRESSTURN_PASS,
      },
    ],
  };
}

// HTTP(S) server for serving the web viewer -- HTTPS (and wss:// for signaling, since the
// WebSocket server below attaches to this same listener) whenever a TLS cert is available,
// otherwise falls back to plain HTTP so local dev keeps working without one.
const tls = loadTlsCredentials();
const requestHandler = (req, res) => {
  // TURN credentials API endpoint
  if (req.url === "/api/turn") {
    const creds = getTurnCredentials();
    res.writeHead(200, {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
    });
    res.end(JSON.stringify(creds));
    return;
  }

  // Resolve against PUBLIC_DIR and verify the result is still inside it -- req.url can contain
  // "../" (or its encoded forms), which without this check lets a request walk out of public/
  // and read arbitrary files under the server directory (e.g. this very source file).
  let requestedPath;
  try {
    requestedPath = decodeURIComponent(req.url.split("?")[0]);
  } catch {
    res.writeHead(400);
    res.end("Bad request");
    return;
  }
  const filePath = path.normalize(
    path.join(PUBLIC_DIR, requestedPath === "/" ? "index.html" : requestedPath)
  );
  if (filePath !== PUBLIC_DIR && !filePath.startsWith(PUBLIC_DIR + path.sep)) {
    res.writeHead(403);
    res.end("Forbidden");
    return;
  }

  const ext = path.extname(filePath);
  const contentTypes = {
    ".html": "text/html",
    ".js": "application/javascript",
    ".css": "text/css",
  };

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404);
      res.end("Not found");
      return;
    }
    res.writeHead(200, {
      "Content-Type": contentTypes[ext] || "text/plain",
    });
    res.end(data);
  });
};

const httpServer = tls
  ? https.createServer(tls, requestHandler)
  : http.createServer(requestHandler);

if (!tls) {
  console.warn(
    "[TLS] No certificate found (looked for TLS_CERT_PATH/TLS_KEY_PATH env vars, or a " +
      "*.crt/*.key pair in this directory) -- serving plain HTTP/WS. Run " +
      "`tailscale cert <this-machine's-name>.<tailnet>.ts.net` in this directory to enable HTTPS/WSS."
  );
}

// WebSocket signaling server
const wss = new WebSocketServer({ server: httpServer });

function generateRoomCode() {
  // No ambiguous chars (0/O, 1/I/L)
  const chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  let code = "";
  for (let i = 0; i < 6; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}

function generateCreatorToken() {
  return crypto.randomBytes(24).toString("hex");
}

// Per-IP sliding-window limiter for room create/join/rejoin -- these are the actions an
// attacker would hammer to enumerate/brute-force room codes. Generous enough for normal
// reconnect churn (grace-period rejoins, retries) but well below what brute-forcing a 6-char
// code (32^6 combinations) would need to be practical.
const RATE_LIMIT_WINDOW_MS = 60_000;
const RATE_LIMIT_MAX = 20;
const rateLimitState = new Map(); // ip -> { count, windowStart }

function isRateLimited(ip) {
  const now = Date.now();
  const entry = rateLimitState.get(ip);
  if (!entry || now - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
    rateLimitState.set(ip, { count: 1, windowStart: now });
    return false;
  }
  entry.count += 1;
  return entry.count > RATE_LIMIT_MAX;
}

// Without this, rateLimitState would grow forever on a long-running server -- one entry per
// distinct IP ever seen.
setInterval(() => {
  const now = Date.now();
  for (const [ip, entry] of rateLimitState) {
    if (now - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
      rateLimitState.delete(ip);
    }
  }
}, RATE_LIMIT_WINDOW_MS).unref();

wss.on("connection", (ws, req) => {
  let currentRoom = null;
  let role = null; // 'creator' or 'viewer'
  const clientIP = req.headers["x-forwarded-for"] || req.socket.remoteAddress;
  console.log(`[WS] New connection from ${clientIP}`);

  ws.on("message", (data) => {
    let msg;
    try {
      msg = JSON.parse(data);
    } catch {
      return;
    }
    if (typeof msg !== "object" || msg === null || typeof msg.type !== "string") {
      return;
    }

    if (["create", "join", "rejoin"].includes(msg.type) && isRateLimited(clientIP)) {
      ws.send(JSON.stringify({ type: "error", message: "Too many attempts, slow down" }));
      console.log(`[RateLimit] Blocked ${msg.type} from ${clientIP}`);
      return;
    }

    switch (msg.type) {
      case "create": {
        const code = generateRoomCode();
        const creatorToken = generateCreatorToken();
        rooms.set(code, { creator: ws, viewer: null, creatorToken, destroyTimer: null });
        currentRoom = code;
        role = "creator";
        ws.send(JSON.stringify({ type: "room_created", room: code, token: creatorToken }));
        console.log(`[Room] Created: ${code}`);
        break;
      }

      case "rejoin": {
        // Creator reconnects to an existing room (after app backgrounding). Requires the
        // token handed out at creation -- without this, anyone who learns/guesses a room
        // code could "rejoin" as creator and hijack the stream.
        if (typeof msg.room !== "string" || !ROOM_CODE_RE.test(msg.room)) {
          ws.send(JSON.stringify({ type: "error", message: "Invalid room code" }));
          return;
        }
        const room = rooms.get(msg.room);
        if (!room) {
          ws.send(
            JSON.stringify({ type: "error", message: "Room not found" })
          );
          return;
        }
        if (typeof msg.token !== "string" || msg.token !== room.creatorToken) {
          ws.send(JSON.stringify({ type: "error", message: "Unauthorized" }));
          console.log(`[Room] Rejected rejoin with invalid token: ${msg.room}`);
          return;
        }
        // Cancel the destroy timer since creator is back
        if (room.destroyTimer) {
          clearTimeout(room.destroyTimer);
          room.destroyTimer = null;
          console.log(`[Room] Creator rejoined, cancelled destroy timer: ${msg.room}`);
        }
        room.creator = ws;
        currentRoom = msg.room;
        role = "creator";
        ws.send(JSON.stringify({ type: "room_rejoined", room: msg.room }));
        // If viewer is already waiting, trigger a new offer
        if (room.viewer && room.viewer.readyState === 1) {
          ws.send(JSON.stringify({ type: "peer_joined" }));
          console.log(`[Room] Viewer already present, notifying rejoined creator: ${msg.room}`);
        }
        console.log(`[Room] Creator rejoined: ${msg.room}`);
        break;
      }

      case "join": {
        if (typeof msg.room !== "string" || !ROOM_CODE_RE.test(msg.room)) {
          ws.send(JSON.stringify({ type: "error", message: "Invalid room code" }));
          return;
        }
        const room = rooms.get(msg.room);
        if (!room) {
          ws.send(
            JSON.stringify({ type: "error", message: "Room not found" })
          );
          return;
        }
        if (room.viewer) {
          ws.send(JSON.stringify({ type: "error", message: "Room is full" }));
          return;
        }
        room.viewer = ws;
        currentRoom = msg.room;
        role = "viewer";
        ws.send(JSON.stringify({ type: "room_joined" }));
        // Notify creator that viewer joined (only if creator is connected)
        if (room.creator && room.creator.readyState === 1) {
          room.creator.send(JSON.stringify({ type: "peer_joined" }));
        }
        console.log(`[Room] Viewer joined: ${msg.room}`);
        break;
      }

      // Relay SDP and ICE candidates to the other peer
      case "offer":
      case "answer":
      case "candidate": {
        if (!role || !currentRoom) {
          return; // haven't create/join/rejoin'd yet -- nothing to relay into
        }
        const sdpField = msg.type === "candidate" ? msg.candidate : msg.sdp;
        if (typeof sdpField !== "string" || sdpField.length === 0) {
          console.log(`[Relay] Dropped malformed ${msg.type} from ${role}`);
          return;
        }
        const room = rooms.get(currentRoom);
        if (!room) {
          console.log(`[Relay] ${msg.type} from ${role} but room ${currentRoom} not found`);
          return;
        }
        const target = role === "creator" ? room.viewer : room.creator;
        if (target && target.readyState === 1) {
          target.send(JSON.stringify(msg));
          console.log(`[Relay] ${msg.type} from ${role} -> ${role === "creator" ? "viewer" : "creator"} (room ${currentRoom})`);
        } else {
          console.log(`[Relay] ${msg.type} from ${role} but target not ready (room ${currentRoom})`);
        }
        break;
      }
    }
  });

  ws.on("error", (err) => {
    console.log(`[WS] Error for ${role} in room ${currentRoom}: ${err.message}`);
  });

  ws.on("close", (code, reason) => {
    console.log(`[WS] Closed: ${role} in room ${currentRoom} (code=${code}, reason=${reason || "none"})`);

    if (currentRoom && rooms.has(currentRoom)) {
      const room = rooms.get(currentRoom);
      const otherPeer = role === "creator" ? room.viewer : room.creator;
      if (otherPeer && otherPeer.readyState === 1) {
        otherPeer.send(JSON.stringify({ type: "peer_left" }));
      }
      if (role === "creator") {
        // Don't destroy immediately -- give the creator a grace period to reconnect
        // (e.g. switching to WhatsApp to share the room code)
        room.creator = null;
        room.destroyTimer = setTimeout(() => {
          if (rooms.has(currentRoom)) {
            const r = rooms.get(currentRoom);
            // Only destroy if creator never came back
            if (!r.creator || r.creator.readyState !== 1) {
              if (r.viewer && r.viewer.readyState === 1) {
                r.viewer.send(JSON.stringify({ type: "error", message: "Stream ended" }));
              }
              rooms.delete(currentRoom);
              console.log(`[Room] Destroyed after grace period: ${currentRoom}`);
            }
          }
        }, ROOM_GRACE_PERIOD_MS);
        console.log(`[Room] Creator disconnected, grace period started (${ROOM_GRACE_PERIOD_MS / 1000}s): ${currentRoom}`);
      } else {
        room.viewer = null;
      }
    }
  });
});

httpServer.listen(PORT, "0.0.0.0", () => {
  const scheme = tls ? "https" : "http";
  console.log(`Signaling server running on ${scheme}://0.0.0.0:${PORT}`);
  console.log(`Web viewer available at ${scheme}://localhost:${PORT}`);
});
