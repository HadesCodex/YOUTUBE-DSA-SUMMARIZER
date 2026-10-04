# DSA Lecture Summariser

Paste the link to an English-captioned **data structures and algorithms lecture on YouTube** and get structured revision notes: the problem, the algorithm steps, pseudocode, time and space complexity, edge cases, and a 5-line revision summary. The notes can be downloaded as a PDF.

Long lectures are too big to send to an LLM cheaply, so the backend **cuts the transcript into sections, scores them with simple explainable heuristics, and sends only the most useful ones** (no embeddings). In measured runs this cut input tokens by **17% to 74%**, depending on lecture length (see [Measured results](#measured-results)).

## How it works

```
 Browser (React + Tailwind, http://localhost:5173)
   |  POST /api/summarise   { url, transcript? }
   v
 Spring Boot backend (http://localhost:8080)
   |
   SummariseController
   |
   SummariseService
   |-- TranscriptService
   |     |-- VideoIdExtractor                     link -> 11-character video id
   |     |-- pasted transcript?  --yes--> use it
   |     |-- else YoutubeTranscriptFetcher ---> YouTube InnerTube API
   |     |        (player call -> caption track -> timedtext XML)   English tracks only
   |     |        TranscriptParser                XML -> one clean string
   |     |-- TranscriptChunker                    ~650-word chunks (captions have no punctuation)
   |     |-- ChunkScorer                          keywords + steps + problem phrases - filler
   |     '-- ChunkSelector                        best chunk of 4 sections, then best overall, <= 5200 words
   |-- PromptBuilder + SummarySchema              instructions + JSON schema
   |-- GeminiClient ----------------------------> Gemini Interactions API (JSON mode)
   |        retry once on 503/timeout, then an optional fallback model
   '-- SummaryParser                              validates the JSON; non-DSA text -> NOT_A_DSA_LECTURE
   |
   v
 { videoId, modelUsed, stats, usage (tokens), summary }
```

**The interface.** One page with light and dark mode (it follows your system setting, with a toggle), a link field with inline validation and a "Try an example" button, a switch between fetching captions and pasting a transcript, a skeleton loading state with a Cancel button, a results page with the video thumbnail, chips showing how much of the transcript was sent, one card per section, and **Download PDF** and **Summarise another** buttons. On a wide screen the results use a two-column layout (the problem, steps and edge cases beside the pseudocode, complexity and revision notes); on smaller screens everything stacks. It works down to phone width and respects the reduced-motion setting.

**The filtering step in one paragraph.** YouTube auto-captions have no punctuation, so the transcript is cut into chunks of about 650 words. Each chunk gets a score: DSA keywords (strong terms such as "time complexity" and "base case" count 3, common words such as "array" count 1), step-by-step words ("first", "then"), and problem-statement phrases ("your task", "given an"), minus a penalty for filler ("subscribe", "welcome back"). The first and last chunk are scored on their body, ignoring the first/last 150 words where plugs and sign-offs sit. Selection then keeps the best chunk of each of 4 position-based sections (so a long multi-topic lecture is not summarised from one topic only), fills the rest of a 5,200-word budget by score, and restores lecture order. All word lists and weights are in one file, `ScoringConfig.java`.

## Stack

| Part | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3.5, Maven, Spring `RestClient` (no SDK, no database, no auth) |
| LLM | Gemini Interactions API, JSON response schema, default model `gemini-3.8-flash` |
| Transcripts | Direct YouTube InnerTube call (ANDROID client) with a paste-transcript fallback |
| Frontend | React 19 (plain JavaScript), Vite 8, Tailwind CSS 4 (official Vite plugin), jsPDF |
| Tests | JUnit 5 + Mockito + `MockRestServiceServer` (195 tests), Vitest (100 tests). **No test calls YouTube or Gemini.** |

## Run it

**Prerequisites:** JDK 17+, Maven 3.9+, Node 22+ and npm, and a Gemini API key from Google AI Studio.

**1. Give the backend your key** (never commit it):

```
cd backend/config
copy application-local.properties.example application-local.properties      # Windows
# cp application-local.properties.example application-local.properties      # macOS / Linux
```

Edit `application-local.properties` and replace `PUT_YOUR_KEY_HERE`. (Alternatively set the `GEMINI_API_KEY` environment variable and skip the file.)

**2. Start the backend** (from the `backend` folder, with the `local` profile so it reads `config/application-local.properties`):

```
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

**3. Start the frontend** in a second terminal:

```
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. (The frontend must use port 5173: the backend only allows requests from that origin.)

**Run the tests:** `cd backend && mvn test`, and `cd frontend && npm test`.

### Useful backend settings (`backend/src/main/resources/application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `gemini.model` | `gemini-3.8-flash` | Main model. `gemini-3.5-flash-lite` is cheaper and faster. |
| `gemini.fallback-enabled` | `true` | After a 503 or timeout and one retry, try the fallback model once. |
| `gemini.fallback-model` | `gemini-3.5-flash-lite` | The fallback model. |
| `gemini.retry-delay-millis` | `2000` | Pause before the single retry. |
| `summariser.filter-enabled` | `true` | `false` sends the whole transcript (for comparing token use). |

Override on the command line, for example: `mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--gemini.model=gemini-3.5-flash-lite"`.

## API

`POST /api/summarise` with `{ "url": "https://www.youtube.com/watch?v=...", "transcript": "optional pasted text" }`.

Success (200):

```json
{
  "videoId": "MHf6awe89xw", "wordCount": 6325, "source": "fetched", "modelUsed": "gemini-3.8-flash",
  "stats": { "totalWords": 6325, "chunksCreated": 10, "chunksKept": 8, "wordsSent": 5025 },
  "usage": { "inputTokens": 6147, "outputTokens": 568, "thoughtTokens": 0, "totalTokens": 6715 },
  "summary": {
    "topic": "...", "problemStatement": "...", "algorithmSteps": ["..."], "pseudocode": "...",
    "timeComplexity": { "value": "O(log n)", "explanation": "..." },
    "spaceComplexity": { "value": "...", "explanation": "..." },
    "edgeCases": ["..."], "revisionSummary": ["...", "...", "...", "...", "..."]
  }
}
```

Errors are always `{ "error": "<CODE>", "message": "..." }`. Transcript problems (HTTP 422): `NO_ENGLISH_CAPTIONS`, `NO_CAPTIONS`, `VIDEO_UNAVAILABLE`, `YOUTUBE_REFUSED`, `YOUTUBE_UNREACHABLE`, `TRANSCRIPT_UNAVAILABLE`. Input problems (400): `INVALID_URL`, `VALIDATION_ERROR`. Summary problems: `NOT_A_DSA_LECTURE` (422), `SUMMARY_BLOCKED` (422), `GEMINI_UNAVAILABLE` (502), `GEMINI_TIMEOUT` (504), `GEMINI_RATE_LIMITED` / `GEMINI_QUOTA_EXCEEDED` (429), `SUMMARY_TRUNCATED` and `INVALID_MODEL_RESPONSE` (502), and server setup errors `SUMMARISER_NOT_CONFIGURED` (503), `GEMINI_AUTH_FAILED`, `GEMINI_MODEL_NOT_FOUND`, `GEMINI_BAD_REQUEST` (502). The frontend branches on the code, never on the message text.

## Measured results

Input tokens sent to Gemini with filtering on versus off (`summariser.filter-enabled=false`), measured on **`gemini-3.5-flash-lite`** on 2026-10-04 with real calls. (`gemini-3.8-flash` was too overloaded at the time to complete the comparison.) The unfiltered baseline is a single run per video.

| Lecture | Transcript words | Input tokens, whole transcript | Filtered, 4,600-word budget | Saved | Filtered, final 5,200-word budget | Saved |
|---|---|---|---|---|---|---|
| Binary Search (Striver, BS-1) | 6,325 | 7,435 | 5,523 | 25.7% | 6,147 | 17.3% |
| Dynamic programming course (freeCodeCamp / AlgoMonster) | 20,802 | 25,509 | 5,697 | 77.7% | 6,594 | 74.2% |
| **Both** | 27,127 | 32,944 | 11,220 | **65.9%** | 12,741 | **61.3%** |

Notes: the budget was raised from 4,600 to 5,200 words after the Binary Search summary lost the overflow fix, which sat in the last chunk; that is why the final saving is smaller. The final-budget runs also use a slightly longer prompt than the baseline run, so the saving is understated by a little. Output tokens were about the same in every run (520 to 610). Only two lectures were measured, so treat the numbers as indicative, not as a benchmark.

## Known limitations

- **English captions only.** Videos whose only captions are in another language are rejected (the scoring word lists are English); paste an English transcript instead.
- **One schema per lecture.** The summary has a single problem, one algorithm and one complexity, so a lecture that teaches ten problems gets a generic template (a 10-problem dynamic-programming lecture produced "O(N)" and a generic list of steps).
- **Keyword-based scoring.** It favours generic DSA vocabulary. A chunk that explains a problem in plain words ("the minimum distance between any two cows") can score low and be dropped. There is no ground truth for summary quality, so the results measure size reduction, not quality.
- **Models can invent content.** Even with a rule that every field must be backed by the text, `gemini-3.5-flash-lite` still wrote a space complexity for a lecture that never discusses one. `gemini-3.8-flash` followed the rule but was often overloaded (HTTP 503).
- **Filtering can remove a lecture's conclusion** (the model then reports it as "not covered in the provided portion"), and auto-captions contain recognition errors ("pivot" heard as "pyot").
- **Undocumented YouTube API.** The InnerTube call can change without notice (the constants to update are at the top of `YoutubeTranscriptFetcher`).
- **YouTube blocks many cloud-provider IPs.** It works from a home connection but may fail once deployed; the paste-transcript fallback covers this.

## Project layout

```
backend/    Spring Boot app (src/main/java/com/dsasummariser/...), config/ (git-ignored local key file + example), PROGRESS.md (detailed build log)
frontend/   Vite + React app (src/App.jsx, api.js, errors.js, pdf.js, components/)
CLAUDE.md   Project decisions and working rules (for Claude Code)
```

`backend/PROGRESS.md` is a detailed log of every design decision, test and measurement.

## Before you push

- [ ] **Do not copy `backend/config/application-local.properties`.** It holds your API key. Only the `.example` file should go to GitHub.
- [ ] Do not copy `node_modules/`, `target/`, `dist/` or any `.env` file (the provided `.gitignore` files already exclude them).
- [ ] After `git init` and `git add`, run `git status` and read every file listed **before the first commit**. Confirm `git check-ignore -v backend/config/application-local.properties` prints a matching ignore rule.
- [ ] Search the whole folder for your key before pushing: use your editor's "Find in Files" (so the key does not end up in your shell history), also searching hidden files and files that `.gitignore` excludes. It should appear only in `backend/config/application-local.properties`.
- [ ] If a key was ever committed or pushed, treat it as leaked: delete it in Google AI Studio and create a new one.
- [ ] Decide whether to keep `CLAUDE.md` and `backend/PROGRESS.md` in the public repo (they contain no secrets, but they are working notes).
