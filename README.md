# TheoryBench

---

## Project Members
| Name | Role |
|------|------|
| Andrew Blasko | Project Member |
| Ayush Sharma | Project Member |
| Jahleel Martinez Fleming | Project Member |

---

## Overview
TheoryBench is a real-time MIDI analysis and visualization platform built to help users understand music theory as they perform. The system captures MIDI input, detects notes, intervals, and chords, and provides live visual feedback through an interactive piano interface. Recordings can be stored, managed, and analyzed through a cloud-enabled backend.

TheoryBench is structured as a two-component system:

- Client Application (JavaFX): Real-time piano UI, recording, playback, and database interaction.
- Analyzer Service (Javalin Microservice): Provides difficulty scoring, chord detection, and timeline analysis.

---

## How It Works
1. MIDI input is received from a USB keyboard or the on-screen piano.
2. Notes, chords, and intervals are displayed in real time.
3. Sessions can be recorded and exported as `.mid` files.
4. MIDI files are uploaded to a Neon.tech PostgreSQL database.
5. The Analyzer Service retrieves stored files, analyzes them, and returns difficulty scores and chord timelines.
6. Users can list, search, download, and delete stored files.

---

## Key Features
| Feature | Description |
|---------|-------------|
| Real-Time Visualization | Displays notes, chords, and intervals as they are played. |
| MIDI Recording and Playback | Records performance data into MIDI files. |
| Cloud Storage | Stores MIDI files in a PostgreSQL database hosted on Neon.tech. |
| Difficulty and Chord Analysis | Analyzer Service processes files and returns structured analysis. |
| Web-Based Analyzer UI | Simple HTML interface to browse and analyze stored files. |
| Case-Sensitive File Management | File names must match exactly when saving or retrieving. |
| Two-Service Architecture | Separates client UI from backend analytical processing. |

---

## Technology Stack

### Client Application
- Java  
- JavaFX 23  
- Maven  
- MIDI recording and playback

### Analyzer Service
- Java  
- Javalin framework  
- Custom chord detection engine  
- MIDI difficulty analyzer  
- JSON and HTML output

### Database
- PostgreSQL hosted Neon.tech  
- Stores MIDI data using BYTEA  
- SSL-secured  
- Indexed for fast retrieval  

---

## Purpose
TheoryBench is designed for students, educators, and musicians who want interactive visual feedback on music theory concepts. By combining performance with automated analysis, the system provides an engaging way to reinforce theory through practice.

---

## Acknowledgments
Special thanks to Professor Kapolka for his guidance and support throughout this project.

---

## Project Status
| Area | Status |
|------|--------|
| Overall Development | completed |
| Database Integration | Completed |
| Analyzer Microservice | Completed |
| User Interface Enhancements | In progress |
| Future Features | Additional analytics and visualizations planned |

