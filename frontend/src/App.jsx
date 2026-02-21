import {useRef, useState} from 'react'
import './index.css'

export default function App() {
    const [currentFolder, setCurrentFolder] = useState('') // relative path from backend ("" means base)
    const [folders, setFolders] = useState([]) // [{name, path}]
    const [songs, setSongs] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [currentPlaying, setCurrentPlaying] = useState(null)

    const [currentIndex, setCurrentIndex] = useState(-1)

    const audioRef = useRef(null)

    const loadFolders = async (dir = '') => {
        setLoading(true)
        setError('')
        try {
            const response = await fetch(`/folders?dir=${encodeURIComponent(dir)}`)
            if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`)
            const data = await response.json()
            setFolders(Array.isArray(data) ? data : [])
        } catch (err) {
            setError(`Error: ${err.message}`)
            setFolders([])
        } finally {
            setLoading(false)
        }
    }

    const loadSongs = async (dir = '') => {
        setLoading(true)
        setError('')
        try {
            const response = await fetch(`/list?dir=${encodeURIComponent(dir)}`)
            if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`)
            const data = await response.json()
            const list = Array.isArray(data) ? data : []
            setSongs(list)

            // Reset playback when the playlist changes
            setCurrentPlaying(null)
            setCurrentIndex(-1)
        } catch (err) {
            setError(`Error: ${err.message}`)
            setSongs([])
            setCurrentPlaying(null)
            setCurrentIndex(-1)
        } finally {
            setLoading(false)
        }
    }

    const browseRoot = async () => {
        setCurrentFolder('')
        setSongs([])
        setCurrentPlaying(null)
        setCurrentIndex(-1)
        await loadFolders('')
    }

    const enterFolder = async (folder) => {
        setCurrentFolder(folder.path)
        await Promise.all([loadFolders(folder.path), loadSongs(folder.path)])
    }

    const goUp = async () => {
        if (!currentFolder) return
        const parts = currentFolder.split(/[\\/]/).filter(Boolean)
        parts.pop()
        const parent = parts.join('/')
        setCurrentFolder(parent)
        await Promise.all([loadFolders(parent), loadSongs(parent)])
    }

    const playSongAtIndex = (idx) => {
        if (!audioRef.current) return
        if (idx < 0 || idx >= songs.length) return

        const song = songs[idx]
        audioRef.current.src = `/stream?file=${encodeURIComponent(song.path)}`
        audioRef.current
            .play()
            .catch((e) => setError(`Playback error: ${e?.message ?? String(e)}`))

        setCurrentPlaying(song.path)
        setCurrentIndex(idx)
    }
    const handleEnded = () => {
        if (!songs.length) return

        const nextIndex = currentIndex >= 0 ? (currentIndex + 1) % songs.length : 0
        playSongAtIndex(nextIndex)
    }

    return (
        <div className="container">
            <h1>🎵 Music Player</h1>

            {error && <div className="error">{error}</div>}

            <div className="input-group">
                <label>Current folder:</label>
                <input type="text" value={currentFolder || '(base folder)'} readOnly/>
                <button onClick={browseRoot} disabled={loading}>
                    Browse
                </button>
                <button onClick={goUp} disabled={loading || !currentFolder}>
                    Up
                </button>
            </div>

            {loading && <div className="loading">Loading...</div>}

            {folders.length > 0 && (
                <div className="playlist">
                    <h2>Folders ({folders.length})</h2>
                    <ul>
                        {folders.map((f, idx) => (
                            <li key={`${f.path}-${idx}`} onClick={() => enterFolder(f)}>
                                {f.name}
                            </li>
                        ))}
                    </ul>
                </div>
            )}

            <audio ref={audioRef} controls onEnded={handleEnded}></audio>

            {songs.length > 0 && (
                <div className="playlist">
                    <h2>Songs ({songs.length})</h2>
                    <ul>
                        {songs.map((song, idx) => (
                            <li
                                key={`${song.path}-${idx}`}
                                className={currentPlaying === song.path ? 'active' : ''}
                                onClick={() => playSongAtIndex(idx)}
                            >
                                {song.name}
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    )
}



