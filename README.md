# AuraMind 🤖⚡

**AuraMind** is an AI-powered smart automation workspace designed to instantly transform unstructured operational chaos into organized, actionable workflows. Built for hackers, developers, and fast-moving teams, it parses messy inputs—like unstructured text streams, meeting transcripts, or chaotic emails—and neatly extracts tasks, calculates urgency levels, and drafts professional communication pieces in a single click.

---

## ✨ Features
* **Multimodal Intent Parsing:** Converts chaotic thoughts into clean data points seamlessly.
* **Native JSON Schema Enforcement:** Utilizes Gemini's advanced structural capabilities to guarantee a clean payload with zero reliance on messy external parsing strings.
* **Instant Action Loops:** Not only organizes data into structured task lists but automatically generates ready-to-distribute follow-up messaging contextually.

---

## 🛠️ Tech Stack
* **Framework:** Next.js (React Lifecycle Orchestration) / Python Flask
* **AI Orchestration Platform:** Google AI Studio
* **Core Foundation Model:** Gemini 2.5 Flash API
* **API SDK:** Google GenAI SDK (`google-genai`)
* **Styling & Presentation:** HTML5, CSS3, Tailwind CSS

---

## 🚀 Quick Start Guide

### 1. Clone the Repository
```bash
git clone https://github.com
cd AuraMind
```

### 2. Set Up Your API Credentials
Obtain an API Key from your Google AI Studio workspace and securely bind it to your environment variables:

**On Linux/macOS:**
```bash
export GEMINI_API_KEY="your_actual_api_key_here"
```

**On Windows (Command Prompt):**
```cmd
set GEMINI_API_KEY="your_actual_api_key_here"
```

### 3. Run the Development Server
Install dependencies and initiate the local loop:

```bash
# If using Node.js / Next.js
npm install
npm run dev

# If using Python Flask Backend
pip install google-genai flask pillow
python app.py
```

Open your local host address in your web browser to interact with the task automation dashboard.

---

## 🧠 System Architecture & Prompts
AuraMind enforces strict boundary logic inside Google AI Studio via custom configuration scripts, processing inputs directly into structured arrays matching the following signature:

```json
{
  "summary": "Concise overview of the operational task context.",
  "tasks": [
    {
      "title": "Action-oriented title for execution.",
      "priority": "High / Medium / Low urgency indicator.",
      "deadline": "Extracted target dates or 'None'."
    }
  ],
  "draft_reply": "A professionally contextualized email or status update draft."
}
```

---

## 🏆 Hackathon Tracks & Evaluation
Developed during the **Hack Devengers 2.0 Open Innovation Hackathon**, matching core parameters for:
* **Innovation & Uniqueness:** Redefining traditional static note-taking applications with automated, immediate system execution scripts.
* **Technical Complexity:** Direct integration with cutting edge multimodal GenAI interfaces without third-party abstraction overhead.
