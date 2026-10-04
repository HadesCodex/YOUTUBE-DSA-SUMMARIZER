# 📘 DSA Lecture Summariser

Turn a long YouTube DSA lecture into clean, exam-ready revision notes: problem statement, algorithm steps, pseudocode, time and space complexity, edge cases and a 5-line recap. Export the notes as a PDF.

Built with **React**, **Spring Boot** and the **Gemini API**.

---

## ✨ Features

- Paste a YouTube link and get structured notes in seconds
- Fetches English captions directly from YouTube (no third-party transcript library)
- **Smart filtering:** long transcripts are chunked, scored and trimmed so only the most informative parts are sent to Gemini, in a **single API call**
- Detects non-DSA videos and says so instead of inventing notes
- **Paste-transcript fallback** for videos where caption fetching fails
- Retry and model fallback when Gemini is overloaded
- Light and dark mode, responsive layout (one column on phones, two on desktop)
- **PDF export** of the generated notes

---

## 🧠 How it works

```
YouTube link
   │
   ▼
VideoIdExtractor ──► YoutubeTranscriptFetcher ──► TranscriptParser ──► clean transcript
                      (InnerTube player call,        (XML -> text)
                       then caption download)
                                                          │
                                                          ▼
                     TranscriptChunker ──► ChunkScorer ──► ChunkSelector
                     (~650-word chunks)    (keyword,        (best chunk per section +
                                            step, problem    best of the rest, within
                                            signals,         a word budget, in order)
                                            filler penalty)
                                                          │
                                                          ▼
                     PromptBuilder ──► GeminiClient ──► SummaryParser ──► JSON response
                     (one call, JSON schema,   (retry on 503,         (validated)
                      faithfulness rules)       model fallback)
                                                          │
                                                          ▼
                                              React UI  ──►  PDF export (jsPDF)
```

### The filtering pipeline

YouTube auto-captions have **no punctuation**, so sentence splitting alone does not work. The pipeline is built around that:

1. **Chunker.** Splits the transcript into chunks of about 650 words. It breaks at a sentence end only if one appears before 800 words, otherwise at 650. Words are never changed, only grouped.
2. **Scorer.** Gives each chunk an explainable score, normalised per 100 words, so chunk size does not matter:
   `score = keywords + steps + problem phrases - filler penalty`
   - strong DSA terms ("time complexity", "base case", "brute force", ...) weigh most; weaker vocabulary ("array", "pointer", ...) weighs less
   - step markers ("first", "then", "finally") and problem-statement phrases ("you are given", "constraints") add points
   - filler ("subscribe", "welcome back", ...) subtracts points
   - the first 150 words of the first chunk and the last 150 words of the last chunk are ignored when scoring, so sign-offs do not hide real teaching
   - all lists and weights live in one class, `ScoringConfig`
3. **Selector.** Splits the lecture into 4 position-based sections, guarantees the best positively-scored chunk of each section, then fills the rest of a 5,200-word budget by global score and restores lecture order. This stops the selection piling up on one topic. Short lectures that fit the budget are never filtered.

Where text was skipped, the kept chunks are joined with `[...]` so the model knows the text jumps.

---

## 🛠️ Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite, Tailwind CSS 4, plain JavaScript |
| PDF export | jsPDF (loaded only when you click Download) |
| Backend | Spring Boot 3.3.5, Java 17, Maven |
| AI | Gemini API (Interactions API), called with Spring's `RestClient`, no SDK |
| Tests | JUnit 5 and MockRestServiceServer (backend), Vitest (frontend) |

---

## 📊 Measured results

Input tokens sent to Gemini, filtered vs the whole transcript (same model, `gemini-3.5-flash-lite`, one run per video):

| Lecture | Transcript | Tokens sent (filtered) | Tokens (whole transcript) | Saved |
|---|---|---|---|---|
| Binary Search (Striver) | 6,325 words | 6,147 | 7,435 | **17.3%** |
| Dynamic Programming (freeCodeCamp) | 20,802 words | 6,594 | 25,509 | **74.2%** |
| **Both together** | | 12,741 | 32,944 | **61.3%** |

- The saving grows with lecture length: the word budget caps what is sent, so a 20k-word lecture costs about the same as a 6k-word one.
- Output tokens are about the same either way, so the saving is all on the input side.
- Only input reduction was measured, not summary quality. In testing, filtering sometimes dropped real content (see limitations).
- Token counts come from the model's own usage metadata, logged on every call.

---

## 🚀 Running it locally

### Prerequisites
- Java 17+ and Maven
- Node.js 20+ (the project was built with Node 22)
- A free Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)

### 1. Add your API key (never commit it)

Create `backend/config/application-local.properties`:

```properties
gemini.api.key=your-key-here
```

No quotes, no spaces around `=`. This file is git-ignored. Alternatively, set the `GEMINI_API_KEY` environment variable. A template is provided as `application-local.properties.example`.

### 2. Start the backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

On PowerShell, quote the argument: `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`.
The API runs on `http://localhost:8080`. The `local` profile is what loads your key.

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. (The port is fixed because the backend's CORS allows only 5173.)

### Tests

```bash
cd backend && mvn test      # 195 tests, none calls the real API
cd frontend && npm test     # 100 tests
```

### Useful properties

| Property | Default | Purpose |
|---|---|---|
| `gemini.api.key` | from `GEMINI_API_KEY` | API key |
| `gemini.model` | `gemini-3.8-flash` | main model |
| `gemini.fallback-enabled` | `true` | switch to the fallback model after a failed retry |
| `gemini.fallback-model` | `gemini-3.5-flash-lite` | fallback model |
| `gemini.retry-delay-millis` | `2000` | wait before the single retry |
| `summariser.filter-enabled` | `true` | set `false` to send the whole transcript (for comparisons) |

---

## 🔌 API

`POST /api/summarise`

```json
{ "url": "https://www.youtube.com/watch?v=...", "transcript": "optional pasted text" }
```

If `transcript` is supplied, YouTube is not called, and the link is used only for the video ID.

Response (abridged):

```json
{
  "videoId": "...",
  "modelUsed": "gemini-3.8-flash",
  "source": "fetched",
  "wordCount": 6325,
  "stats": { "totalWords": 6325, "chunksCreated": 10, "chunksKept": 8, "wordsSent": 5025 },
  "usage": { "inputTokens": 6147, "outputTokens": 568, "thoughtTokens": 0, "totalTokens": 6715 },
  "summary": {
    "topic": "...",
    "problemStatement": "...",
    "algorithmSteps": ["..."],
    "pseudocode": "...",
    "timeComplexity":  { "value": "O(log n)", "explanation": "..." },
    "spaceComplexity": { "value": "...", "explanation": "..." },
    "edgeCases": ["..."],
    "revisionSummary": ["... exactly 5 lines ..."]
  }
}
```

Errors are always `{ "error": "CODE", "message": "..." }`:

| Group | Codes |
|---|---|
| Input | `INVALID_URL`, `VALIDATION_ERROR` |
| Transcript (HTTP 422) | `NO_ENGLISH_CAPTIONS`, `NO_CAPTIONS`, `VIDEO_UNAVAILABLE`, `YOUTUBE_REFUSED`, `YOUTUBE_UNREACHABLE`, `TRANSCRIPT_UNAVAILABLE` |
| Content | `NOT_A_DSA_LECTURE` (422), `SUMMARY_BLOCKED` (422) |
| Gemini | `GEMINI_AUTH_FAILED`, `GEMINI_RATE_LIMITED`, `GEMINI_QUOTA_EXCEEDED`, `GEMINI_MODEL_NOT_FOUND`, `GEMINI_TIMEOUT`, `GEMINI_UNAVAILABLE`, `GEMINI_BAD_REQUEST`, `SUMMARY_TRUNCATED`, `INVALID_MODEL_RESPONSE` |
| Setup | `SUMMARISER_NOT_CONFIGURED` |

---

## 🧩 Design decisions

- **Direct YouTube call instead of a library.** The Java transcript libraries are unofficial and went stale after YouTube changed its web client (the web route now needs a proof-of-origin token). The backend calls the InnerTube `player` endpoint as the ANDROID client, picks a caption track (manual English, then auto English) and downloads the caption XML. The client version and user agent are constants at the top of the fetcher, the first thing to update if YouTube changes.
- **Safe XML parsing.** The caption XML comes from the internet, so the parser disables DTDs to block XXE attacks.
- **Gemini is isolated in one class** (`GeminiClient`), with a JSON response schema so no fragile parsing is needed, `store: false` so transcripts are not retained, and no sampling parameters (the newer models reject them).
- **Retry, then fallback.** On a 503 or timeout, one retry on the main model, then one call to the fallback model. Auth, quota, rate-limit and blocked-content errors are never retried. The response and logs record `modelUsed` and the number of attempts.
- **Prompt rules.** The model is told the transcript is auto-generated and may contain errors, to use only what the text states, to mark missing information as "Not covered in the provided portion of the lecture", and to flag non-DSA videos. The transcript is wrapped as data, so instructions inside it are not followed.
- **Secrets stay out of the build.** The key file lives in `backend/config/`, outside `src/main/resources`, so it is never packaged into the jar. The key is only sent in a request header and is never logged.
- **Frontend renders model text as plain text**, never as HTML.

---

## ⚠️ Known limitations

- **English captions only.** Videos without an English track return a clear error, and you can paste a transcript instead.
- **One schema per lecture.** A lecture covering many problems (for example a 10-problem DP lecture) gets one generic template rather than a summary per problem.
- **Keyword-based scoring.** It favours chunks that name concepts over chunks that build understanding, and it can drop real content. In testing, the selector once dropped the chunk explaining the integer-overflow fix in binary search.
- **Models can invent content.** `gemini-3.5-flash-lite` sometimes writes a space complexity the lecture never states. `gemini-3.8-flash` follows the "only what the text says" rule more closely but is often overloaded, which is why the fallback exists. The UI shows which model produced each summary.
- **Undocumented YouTube API.** It can change without notice, and YouTube blocks many cloud-provider IPs, so expect refusals if you deploy to a cloud host. The paste fallback covers this.
- **Not verified:** other browsers than Chromium, real phones, screen readers, and Gemini failure modes other than 503 (those rest on unit tests).

---

## 📂 Project structure

```
.
├── backend/
│   ├── config/                     # local key file (git-ignored)
│   └── src/main/java/.../
│       ├── controller/             # REST endpoint
│       ├── service/                # fetcher, parser, chunker, scorer, selector, Gemini client
│       ├── model/                  # request/response records
│       ├── exception/              # error codes and global handler
│       └── config/                 # CORS
└── frontend/
    └── src/
        ├── components/             # form, loading, error, summary views
        ├── api.js                  # the single fetch call
        ├── errors.js               # error-code to message table
        └── pdf.js                  # PDF export and ASCII sanitiser
```

---

## 🔒 Before you push

- Do not commit `backend/config/application-local.properties`, `node_modules/` or `target/`.
- Run `git status` before the first commit and search the project for your key (Gemini keys start with `AIza`).
- If a key ever leaks, delete it in AI Studio and create a new one.
