# Local MP3 Streaming Web App

A web application for streaming MP3 files from local folders. Backend built with Scala 3 + http4s, frontend built with React + Vite.

## Project Structure

```
scala-web-player/
├── backend/              # Scala backend (http4s + cats-effect)
│   └── src/main/scala/com/example/backend/
│       └── Main.scala    # HTTP server with /list and /stream endpoints
├── shared/               # Shared models (JVM-only)
│   └── src/main/scala/com/example/models/
│       └── Song.scala    # Song case class with circe codecs
├── frontend/             # React + Vite frontend
│   ├── src/
│   │   ├── App.jsx       # Main React component
│   │   ├── main.jsx      # React entry point
│   │   └── index.css     # Styles
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
├── build.sbt             # SBT build configuration (backend + shared only)
├── run.bat               # Windows startup script
└── README.md
```

## Architecture

- **Backend**: JVM-based HTTP server using http4s
  - `GET /list?dir=path` - List all .mp3 files recursively in a directory
  - `GET /stream?file=path` - Stream an MP3 file with range request support
  - CORS enabled for local development
  - Path traversal protection (restricted to base directory)

- **Frontend**: React app using Vite dev server
  - Fetch folder path from user
  - Display playlist of MP3 files
  - Stream and play audio via HTML5 audio element
  - Vite proxy routes `/list` and `/stream` to backend

## Requirements

- JDK 11+
- Node.js 16+
- sbt 1.12+

## Quick Start

### Windows

```powershell
.\run.bat
```

This starts:
- Backend: http://localhost:8080
- Frontend: http://localhost:5173

### Manual Start

**Terminal 1 - Backend:**
```bash
sbt backend/run
```

**Terminal 2 - Frontend:**
```bash
cd frontend
npm install
npm run dev
```

Then open: http://localhost:5173

## Configuration

### Music Directory

Set base music directory via environment variable (default: `/home/andrii`):

```bash
# Linux/macOS
export MUSIC_BASE="/path/to/music"
sbt backend/run

# Windows PowerShell
$env:MUSIC_BASE="D:\Music"
sbt backend/run
```

## API Examples

### List songs in folder
```bash
curl "http://localhost:8080/list?dir=/path/to/folder"
```

Response:
```json
[
  {"name": "song.mp3", "path": "/full/path/to/song.mp3", "duration": null},
  {"name": "track.mp3", "path": "/full/path/to/track.mp3", "duration": null}
]
```

### Stream an MP3 file
```bash
curl "http://localhost:8080/stream?file=/full/path/to/song.mp3" --output song.mp3
```

## Tech Stack

- **Backend**: Scala 3.8.1, http4s 1.0.0-M45, cats-effect 3.5.0, circe 0.14.8
- **Frontend**: React 18.2.0, Vite 5.0.0
- **Dev**: Node.js, npm, SBT

## Features

✅ Recursive MP3 file discovery  
✅ RESTful API with JSON responses  
✅ HTTP Range request support for seeking  
✅ CORS headers for development  
✅ Path traversal protection  
✅ React UI with real-time feedback  
✅ Audio player with standard controls  

## License

MIT

