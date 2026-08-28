# 🐺 Kairo AI Companion — New Features & System Architecture

**Branch:** `feature/ai-buddy-agent`  
**Total Changes:** 49 files modified/created • 7,052+ lines added  
**Platform:** Android (Kotlin, Jetpack Compose, Room DB, Accessibility Services, Dual LLM)

---

## 🌟 Executive Summary

This branch transforms Kairo from a single-turn voice utility into a **full-fledged multi-agent AI companion and automation system**. It introduces a 5-agent team architecture, an interactive radial overlay, on-device app learning, autonomous UI execution, trigger-based automations, and multi-platform message drafting.

---

## 🧠 1. The 5-Agent Multi-Agent Architecture

Kairo utilizes a specialized multi-agent hierarchy. All incoming user requests are received by the **Coordinator**, which automatically classifies and dispatches the task to the dedicated specialist agent.

```
                     ┌───────────────────────────┐
                     │   🐺 Kairo (Coordinator)  │
                     │   "I see all, I route all"│
                     └─────────────┬─────────────┘
                                   │
         ┌─────────────────┬───────┴─────────┬─────────────────┐
         ▼                 ▼                 ▼                 ▼
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │   🔍 Scout   │  │   ⚡ Runner   │  │  🔧 Builder  │  │   ✍️ Scribe  │
  │  (Research)  │  │ (Execution)  │  │    (Work)    │  │  (Drafting)  │
  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

### Agent Roles & Specifications

| Agent | Symbol | Theme Color | Primary Charter | Handled Domain & Capabilities |
|---|:---:|:---:|---|---|
| **Kairo** | `K` | Cyan (`#00D2FF`) | *"I see all, I route all"* | Central intent router, agent orchestrator, greetings, conversational handoff. |
| **Scout** | 🔍 | Blue (`#4A9EFF`) | *"Knowledge is power!"* | Web searches (Google/Bing), screen reading & explanation, general queries. |
| **Runner** | ⚡ | Green (`#00E676`) | *"No questions, just action!"* | Direct hardware controls, phone calls, SMS, app launching, alarms, volume & media. |
| **Builder** | 🔧 | Orange (`#FF9800`) | *"Let's build something!"* | Complex multi-step tasks, quick commerce ordering, background automation routines. |
| **Scribe** | ✍️ | Purple (`#8B5CF6`) | *"Words have power"* | AI draft generation (WhatsApp, Email, SMS, LinkedIn) with customizable tones. |

**Source Files:**
- `app/src/main/java/com/kairo/assistant/agent/KairoAgent.kt`
- `app/src/main/java/com/kairo/assistant/agent/AgentCharters.kt`
- `app/src/main/java/com/kairo/assistant/agent/AgentPromptBuilder.kt`

---

## 🫧 2. Multi-Agent Radial Floating Overlay

A floating overlay service that renders on top of all applications, providing quick access to agents without opening the full application.

### Visual States & Animations
* **Collapsed Mode (Idle):** Single compact 56dp coordinator bubble with subtle radial breathing glow, draggable to any screen edge.
* **Radial Expansion (Active):** Tap expands 4 satellite agent bubbles in a smooth semi-circular spring arc (`dampingRatio = 0.7f`).
* **Agent Focus State:** When an agent is working, its bubble scales up (`40dp → 52dp`) with an active glow pulse (`alpha 0.4 → 1.0`), while inactive agents gently dim to 30% opacity.
* **Agent Conversation Card:** Expandable bottom card rendering the responding agent's emoji, color identity, user query, and AI response.

**Source Files:**
- `app/src/main/java/com/kairo/assistant/service/FloatingBuddyService.kt`
- `app/src/main/java/com/kairo/assistant/ui/overlay/AgentRadialMenu.kt`
- `app/src/main/java/com/kairo/assistant/ui/overlay/AgentBubble.kt`

---

## ✍️ 3. Smart AI Message Drafting Engine (Scribe)

Enables instantaneous contextual message composition formatted specifically for target platforms and selected communication styles.

### Target Platforms & Optimizations
* **WhatsApp:** Friendly, emoji-aware, concise formatting.
* **Email:** Subject line generation, professional greetings, and structured paragraphs.
* **SMS:** Ultra-compact, 160-character budget optimization.
* **LinkedIn:** Professional networking tone with clear call-to-action.

### Tone Presets
`Professional` • `Casual` • `Urgent` • `Formal` • `Friendly`

### Key Highlights
* Auto-copies generated draft directly to clipboard.
* Dedicated **DraftScreen** UI for manual platform/tone selection, live editing, and native sharing.
* Automatically records draft generation history in Room DB.

**Source Files:**
- `app/src/main/java/com/kairo/assistant/intelligence/drafting/MessageDrafter.kt`
- `app/src/main/java/com/kairo/assistant/nlu/rules/DraftIntentMatcher.kt`
- `app/src/main/java/com/kairo/assistant/actions/DraftMessageExecutor.kt`
- `app/src/main/java/com/kairo/assistant/ui/screens/DraftScreen.kt`

---

## 🛒 4. Autonomous Quick Commerce Ordering (Builder)

Understands shopping requests, identifies the target delivery provider, launches the app, and triggers autonomous navigation to search and add products.

### Supported Apps
`Blinkit` • `Zepto` • `Swiggy Instamart` • `BigBasket` • `JioMart` • `Dunzo` • `Amazon Fresh` • `Flipkart Minutes` • `DMart Ready` • `Milkbasket` • `Country Delight`

### Command Flow
1. User says: *"Order milk from Blinkit"*
2. `OrderIntentMatcher` parses item (`milk`) and store (`Blinkit`).
3. `OrderAgent` launches package `com.grofers.customerapp`.
4. `AgentEngine` monitors the UI tree, executes search and add-to-cart actions.

**Source Files:**
- `app/src/main/java/com/kairo/assistant/intelligence/commerce/OrderAgent.kt`
- `app/src/main/java/com/kairo/assistant/nlu/rules/OrderIntentMatcher.kt`
- `app/src/main/java/com/kairo/assistant/actions/OrderItemExecutor.kt`

---

## 📚 5. Self-Learning App Navigation & Instant Replay

Eliminates repetitive LLM latency by caching and replaying successful task flows.

* **First Run:** Agent navigates the app step-by-step using LLM planning, recording every semantic anchor (text, content description, class name).
* **Semantic Step Storage:** Stored using relative element descriptors (`elementText`, `nearText`, `class`) rather than volatile raw pixel coordinates.
* **Instant Replay:** Subsequent runs match task intent via token overlap and execute cached steps instantly with **0 LLM calls**.
* **Reliability Decay:** If an app UI updates and replay fails, the engine falls back to LLM reasoning and updates the flow record.

**Source Files:**
- `app/src/main/java/com/kairo/assistant/intelligence/learning/AppKnowledgeManager.kt`

---

## ⚙️ 6. Android Automation Recipes Engine (n8n-Style)

Enables users to establish persistent background routines using natural language.

### Supported Triggers
* ⏰ **Time Alarms:** Exact scheduled triggers using `AlarmManager`.
* 📶 **Wi-Fi Connectivity:** Network callbacks detecting connection/disconnection to designated SSIDs.
* 🔋 **Battery State:** System broadcast listeners for low battery thresholds.

### Automation Voice Commands
* *"When I connect to office WiFi, silence my phone"*
* *"Every day at 7 AM, play music"*
* *"When battery is low, turn off Bluetooth"*

**Source Files:**
- `app/src/main/java/com/kairo/assistant/automation/TriggerEngine.kt`
- `app/src/main/java/com/kairo/assistant/automation/AutomationIntentMatcher.kt`
- `app/src/main/java/com/kairo/assistant/automation/AutomationExecutor.kt`

---

## 🎯 7. Event-Driven Screen Detection & Reliability

Replaced blind `delay()` timeouts with responsive UI verification.

* **Signature Polling:** Tracks UI hierarchy hash changes (`waitForMeaningfulChange`) with 120ms polling intervals and 300ms stabilization windows.
* **Feedback Loop:** Injects previous step execution verification (`SCREEN_CHANGED` / `NO_CHANGE`) into agent reasoning prompts.
* **Adaptive Recovery:** Automatically escalates to alternative interaction heuristics if an element tap fails twice consecutively.

**Source Files:**
- `app/src/main/java/com/kairo/assistant/screen/KairoAccessibilityService.kt`
- `app/src/main/java/com/kairo/assistant/agent/AgentEngine.kt`

---

## 📱 8. Real-Time Phone Context Intelligence

Provides the AI with live situational awareness to improve decision-making.

| Context Element | Data Source | Output Sample |
|---|---|---|
| **Battery Status** | `BatteryManager` | `82%, Charging (AC)` |
| **Network Type** | `ConnectivityManager` | `Wi-Fi (5 GHz)` |
| **Wi-Fi Network** | `WifiManager` | `Home_Studio_Ext` |
| **Temporal Context** | System Clock | `Night, 11:45 PM` |
| **Hardware Info** | `android.os.Build` | `moto g45 5G (Android 15)` |

**Source Files:**
- `app/src/main/java/com/kairo/assistant/intelligence/context/PhoneContextProvider.kt`

---

## 🔀 9. Dual Inference Engine (Cloud + On-Device)

Hybrid execution providing both cloud-level intelligence and offline capabilities.

* **Cloud Engine:** Google Gemini 3.6 Flash via REST API for deep reasoning, complex planning, and drafting.
* **Local Engine:** On-device LLaMA GGUF engine with asynchronous model downloader and memory-sensitive auto-throttling (< 4.5 GB RAM detection).

**Source Files:**
- `app/src/main/java/com/kairo/assistant/nlu/llm/GeminiClient.kt`
- `app/src/main/java/com/kairo/assistant/nlu/llm/LlamaEngine.kt`

---

## 💾 10. Local Data Persistence (Room Database)

All operational data is persisted locally in SQLite under `/data/data/com.kairo.assistant/databases/kairo_database`.

* **`chat_messages`:** Full dialogue history with sender roles and timestamps.
* **`draft_history`:** Platform, tone, user prompt, and generated draft records.
* **`app_knowledge`:** Cached app navigation recipes and success metrics.
* **`automation_recipes`:** Configured trigger-action automation definitions.

**Source Files:**
- `app/src/main/java/com/kairo/assistant/data/local/KairoDatabase.kt`
- `app/src/main/java/com/kairo/assistant/data/local/Entities.kt`
- `app/src/main/java/com/kairo/assistant/data/local/Daos.kt`

---

## 🗣️ Voice Command Quick Reference

| Feature | Example Spoken Commands |
|---|---|
| **AI Drafting** | *"Draft a WhatsApp message to Alex saying I will arrive in 15 minutes"*<br>*"Write a formal email asking for the project update"* |
| **Commerce** | *"Order milk from Blinkit"*<br>*"Buy eggs on Zepto"*<br>*"Get tomatoes from Swiggy Instamart"* |
| **Screen Analysis** | *"What is on my screen?"*<br>*"Explain this page to me"* |
| **Automations** | *"When I connect to Office WiFi, turn off Bluetooth"*<br>*"Every day at 8 AM, set volume to 80%"* |
| **App & System** | *"Open YouTube"*, *"Turn on Flashlight"*, *"Call Mom"*, *"Set alarm for 7 AM"* |