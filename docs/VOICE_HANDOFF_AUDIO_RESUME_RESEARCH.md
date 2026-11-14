# Voice handoff / audio resume — verified implementation notes

Branch: test/voice-handoff-audio-resume
Baseline: test/extend-unlock-keyguard. No changes to the two wakeword models.

## Verified code findings
- WakeService currently calls releaseMicrophone() from the accessibility handoff. This calls detector.release(), stopForeground(), stopSelf(); automatic resume is impossible in this path.
- The diagnostic build removes accessibility. Its wakeword callback only calls MainActivity.tryOpenChatGpt() if MainActivity is RESUMED and has window focus. On a black locked screen it falls back to a tap-required notification. The diagnostic path does not itself release the microphone.
- ChatGptHandoffActivity calls requestDismissKeyguard(), then binds WakeService and stops the microphone service before opening the ChatGPT *launcher*, not its voice component.
- OpenWakeWord has stop(), startNamed(), and release(). stop() joins the audio thread and releases AudioRecord; release() also destroys ONNX sessions. Keeping a foreground service alive while temporarily stopping AudioRecord is supported by this code, but device testing is necessary.
- GPTWake (suddenBook/GPTWake) implements GptLauncher.launchDirect() using com.openai.chatgpt/com.openai.voice.assistant.AssistantActivity, checks exported and permission, then falls back to https://chat.com/?mode=voice. It also monitors communication-mode + non-silenced recording and resumes within the existing foreground service.
- A successful startActivity call does NOT establish that ChatGPT voice is active.

## Proposed isolated experiment (not implemented)
1. Keep the existing WakeService foreground; pause detector via stop() rather than release()/stopSelf() on successful voice handoff.
2. Verify that the ChatGPT voice component is exported, permission-free and accessible on the installed version; fallback to a deep link, logging each outcome.
3. Track AudioManager mode and recording configurations, distinguish voice confirmed, timeout, and end-of-call.
4. Resume via startNamed() after confirmed end or timeout, with bounded retries and user-visible failure reporting.
5. Test screen off/locked separately from trusted unlocked keyguard, capturing Android ActivityTaskManager and audio logs. No security bypass or simulated PIN entry.
6. Do not modify the production baseline, wakeword thresholds, models or signing.

## Acceptance criteria
- Black locked screen, no touch: Hey EP/Hey Jarvis triggers active ChatGPT voice; record independent audio confirmation.
- After call ends, both wakewords automatically resume.
- No false success based only on Intent dispatch.
- No regressions in Play Protect diagnostic install or existing wakeword accuracy.

Status: investigation only; no APK built and no device success claimed.
