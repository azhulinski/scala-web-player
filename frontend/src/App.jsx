import {useRef, useState} from 'react'
import './index.css'

export default function App() {
    const [currentFolder, setCurrentFolder] = useState('') // relative path from backend ("" means base)
    const [folders, setFolders] = useState([]) // [{name, path}]
    const [songs, setSongs] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [currentPlaying, setCurrentPlaying] = useState(null)

    const [isPlaying, setIsPlaying] = useState(false)
    const [currentTime, setCurrentTime] = useState(0)
    const [duration, setDuration] = useState(0)
    const [volume, setVolume] = useState(1)

    const [currentIndex, setCurrentIndex] = useState(-1)
    const [shuffleMode, setShuffleMode] = useState(false)
    const [shuffledIndices, setShuffledIndices] = useState([])

    const audioRef = useRef(null)

    const togglePlay = () => {
        if (!audioRef.current) return
        if (isPlaying) {
            audioRef.current.pause()
        } else {
            audioRef.current.play().catch(e => setError(`Playback error: ${e?.message}`))
        }
    }

    const handleTimeUpdate = () => {
        if (audioRef.current) {
            setCurrentTime(audioRef.current.currentTime)
            setDuration(audioRef.current.duration)
        }
    }

    const handlePlayPause = () => {
        setIsPlaying(!audioRef.current.paused)
    }

    const handleVolumeChange = (e) => {
        const vol = parseFloat(e.target.value)
        setVolume(vol)
        if (audioRef.current) audioRef.current.volume = vol
    }

    const handleProgressChange = (e) => {
        const newTime = parseFloat(e.target.value)
        if (audioRef.current) {
            audioRef.current.currentTime = newTime
            setCurrentTime(newTime)
        }
    }

    const formatTime = (seconds) => {
        if (!seconds || isNaN(seconds)) return '0:00'
        const mins = Math.floor(seconds / 60)
        const secs = Math.floor(seconds % 60)
        return `${mins}:${secs.toString().padStart(2, '0')}`
    }

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
            setShuffleMode(false)
            setShuffledIndices([])
        } catch (err) {
            setError(`Error: ${err.message}`)
            setSongs([])
            setCurrentPlaying(null)
            setCurrentIndex(-1)
            setShuffleMode(false)
            setShuffledIndices([])
        } finally {
            setLoading(false)
        }
    }

    // Generate shuffled playlist
    const initializeShuffle = (list) => {
        const indices = Array.from({length: list.length}, (_, i) => i)
        // Fisher-Yates shuffle
        for (let i = indices.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [indices[i], indices[j]] = [indices[j], indices[i]]
        }
        return indices
    }

    // Toggle shuffle mode
    const toggleShuffle = () => {
        if (!shuffleMode && songs.length > 0) {
            // Turning ON shuffle
            setShuffledIndices(initializeShuffle(songs))
        }
        setShuffleMode(!shuffleMode)
    }

    // Get next song index
    const getNextIndex = () => {
        if (songs.length === 0) return -1

        if (shuffleMode) {
            // In shuffle mode
            if (shuffledIndices.length === 0) return -1
            const currentPosInShuffled = shuffledIndices.indexOf(currentIndex)
            const nextPos = (currentPosInShuffled + 1) % shuffledIndices.length
            return shuffledIndices[nextPos]
        } else {
            // Normal mode
            return (currentIndex + 1) % songs.length
        }
    }

    // Get previous song index
    const getPreviousIndex = () => {
        if (songs.length === 0) return -1

        if (shuffleMode) {
            // In shuffle mode
            if (shuffledIndices.length === 0) return -1
            const currentPosInShuffled = shuffledIndices.indexOf(currentIndex)
            const prevPos = currentPosInShuffled === 0 ? shuffledIndices.length - 1 : currentPosInShuffled - 1
            return shuffledIndices[prevPos]
        } else {
            // Normal mode
            return currentIndex <= 0 ? songs.length - 1 : currentIndex - 1
        }
    }

    // Play next song
    const playNext = () => {
        const nextIdx = getNextIndex()
        if (nextIdx >= 0) playSongAtIndex(nextIdx)
    }

    // Play previous song
    const playPrevious = () => {
        const prevIdx = getPreviousIndex()
        if (prevIdx >= 0) playSongAtIndex(prevIdx)
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
        const nextIndex = getNextIndex()
        if (nextIndex >= 0) playSongAtIndex(nextIndex)
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

            <audio
                ref={audioRef}
                onEnded={handleEnded}
                onTimeUpdate={handleTimeUpdate}
                onPlay={handlePlayPause}
                onPause={handlePlayPause}
            ></audio>

            <div className="player">
                <div className="player-progress">
                    <input
                        type="range"
                        min="0"
                        max={duration || 0}
                        value={currentTime}
                        onChange={handleProgressChange}
                        className="progress-bar"
                    />
                </div>

                <div className="player-controls">
                    <div className="player-buttons">
                        <button onClick={playPrevious} disabled={songs.length === 0} title="Previous">⏮️</button>
                        <button onClick={togglePlay} disabled={songs.length === 0} className="play-btn"
                                title={isPlaying ? 'Pause' : 'Play'}>
                            {isPlaying ? '⏸️' : '▶️'}
                        </button>
                        <button onClick={playNext} disabled={songs.length === 0} title="Next">⏭️</button>
                        <button onClick={toggleShuffle} disabled={songs.length === 0}
                                className={shuffleMode ? 'active' : ''} title="Shuffle">🔀
                        </button>
                    </div>

                    <div className="player-time">
                        <span>{formatTime(currentTime)}</span>
                        <span>/</span>
                        <span>{formatTime(duration)}</span>
                    </div>

                    <div className="player-volume">
                        <input
                            type="range"
                            min="0"
                            max="1"
                            step="0.1"
                            value={volume}
                            onChange={handleVolumeChange}
                            className="volume-slider"
                        />
                    </div>
                </div>
            </div>

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



