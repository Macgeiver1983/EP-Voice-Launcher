package com.openwakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Kotlin/Android port of [openWakeWord](https://github.com/dscripka/openWakeWord).
 *
 * Runs the full 3-stage ONNX pipeline (mel spectrogram → speech embedding → wake word
 * classifier) on-device using ONNX Runtime. No network or API key required.
 *
 * Usage:
 * ```
 * val detector = OpenWakeWord.Builder(context)
 *     .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
 *     .setThreshold(0.5f)
 *     .build()
 *
 * detector.start { score -> Log.d("WW", "Detected! score=$score") }
 * // ...later...
 * detector.stop()
 * detector.release()
 * ```
 */
class OpenWakeWord private constructor(
    private val context: Context,
    private val wakeWordModels: List<WakeWordConfig>,
    private val debounceMs: Long,
) {

    private data class WakeWordConfig(
        val source: ModelSource,
        val label: String,
        val threshold: Float,
        val optional: Boolean,
        val consecutiveFrames: Int = 1,
    )

    /** Pre-trained wake word models bundled with the library. */
    enum class BuiltInModel(internal val assetPath: String, val displayName: String) {
        HEY_JARVIS("openwakeword/hey_jarvis_v0.1.onnx", "Hey Jarvis"),
        ALEXA("openwakeword/alexa_v0.1.onnx", "Alexa"),
        HEY_MYCROFT("openwakeword/hey_mycroft_v0.1.onnx", "Hey Mycroft"),
    }

    /** Callback delivered on the main thread when the wake word is detected. */
    fun interface OnDetectionListener {
        fun onDetected(score: Float)
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    class Builder(private val context: Context) {
        private val models = mutableListOf(
            WakeWordConfig(ModelSource.BuiltIn(BuiltInModel.HEY_JARVIS), "Hey Jarvis", 0.5f, false)
        )
        private var threshold = 0.5f
        private var debounceMs = 2000L

        /** Use one of the bundled pre-trained models. */
        fun setModel(model: BuiltInModel) = apply {
            models.clear()
            models.add(WakeWordConfig(ModelSource.BuiltIn(model), model.displayName, threshold, false))
        }

        /** Use a custom ONNX model from the app's assets folder. */
        fun setModelAsset(assetPath: String) = apply {
            models.clear()
            models.add(WakeWordConfig(ModelSource.Asset(assetPath), assetPath.substringAfterLast("/").substringBeforeLast("."), threshold, false))
        }

        /** Use a custom ONNX model loaded as raw bytes. */
        fun setModelBytes(bytes: ByteArray) = apply {
            models.clear()
            models.add(WakeWordConfig(ModelSource.Raw(bytes), "Custom", threshold, false))
        }

        /** Add an additional model without starting a second microphone capture. */
        fun addModelAsset(
            assetPath: String,
            displayName: String,
            modelThreshold: Float = 0.5f,
            optional: Boolean = true,
            consecutiveFrames: Int = 1,
        ) = apply {
            require(models.none { it.label == displayName }) { "Duplicate wake-word label: $displayName" }
            models.add(WakeWordConfig(
                ModelSource.Asset(assetPath), displayName,
                modelThreshold.coerceIn(0.01f, 0.99f), optional, consecutiveFrames.coerceIn(1, 12)
            ))
        }

        /**
         * Detection threshold (0.0 – 1.0).
         * Lower = more sensitive (more false positives).
         * Higher = stricter (may miss quiet speech).
         * Default: 0.5
         */
        fun setThreshold(threshold: Float) = apply {
            this.threshold = threshold.coerceIn(0.01f, 0.99f)
            // Preserve historical Builder.setThreshold behavior for the primary model.
            if (models.isNotEmpty()) models[0] = models[0].copy(threshold = this.threshold)
        }

        /**
         * Minimum time between consecutive detections, in milliseconds.
         * Default: 2000
         */
        fun setDebounceMs(ms: Long) = apply {
            this.debounceMs = ms.coerceAtLeast(0)
        }

        fun build(): OpenWakeWord = OpenWakeWord(
            context.applicationContext,
            models.toList(),
            debounceMs,
        )
    }

    // ------------------------------------------------------------------
    // Internal types
    // ------------------------------------------------------------------

    internal sealed class ModelSource {
        data class BuiltIn(val model: BuiltInModel) : ModelSource()
        data class Asset(val path: String) : ModelSource()
        data class Raw(val bytes: ByteArray) : ModelSource()
    }

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    companion object {
        private const val TAG = "OpenWakeWord"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SAMPLES = 1280 // 80 ms
        private const val MEL_CONTEXT_SAMPLES = 480 // 160 * 3 overlap
        private const val MEL_BINS = 32
        private const val MEL_WINDOW_FRAMES = 76
        private const val EMBEDDING_DIM = 96
        private const val FEATURE_WINDOW = 16
        private const val FEATURE_BUFFER_MAX = 120
        private const val MEL_BUFFER_MAX = 970
        private const val SKIP_INITIAL_PREDICTIONS = 5
        private const val RAW_BUFFER_SECONDS = 10
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private var ortEnv: OrtEnvironment? = null
    private var melSpecSession: OrtSession? = null
    private var embeddingSession: OrtSession? = null
    private class Classifier(
        val label: String,
        val session: OrtSession,
        val inputName: String,
        val threshold: Float,
        val optional: Boolean = false,
        val consecutiveFramesRequired: Int = 1,
        var consecutiveFramesSeen: Int = 0,
        var lastDetectionTime: Long = 0L,
        var lastScore: Float? = null,
    )
    private val classifiers = mutableListOf<Classifier>()
    @Volatile var activeModels: List<String> = emptyList()
        private set

    private var audioRecord: AudioRecord? = null
    private var processingThread: Thread? = null
    @Volatile private var isRunning = false

    // EP fix: report actual audio progress and asynchronous worker failures.
    var onModelProgress: ((Map<String, Float?>) -> Unit)? = null
    var onOptionalModelError: ((String, String) -> Unit)? = null
    var onAudioProgress: ((Long, Float) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null
    private var listener: OnDetectionListener? = null
    fun interface OnNamedDetectionListener {
        fun onDetected(label: String, score: Float)
    }
    private var namedListener: OnNamedDetectionListener? = null
    private var predictionCount = 0
    private var lastAnyDetectionTime = 0L

    private lateinit var rawBuffer: FloatArray
    private var rawWritePos = 0
    private var rawTotalWritten = 0L

    private val melBuffer = ArrayList<FloatArray>()
    private val featureBuffer = ArrayList<FloatArray>()

    private val mainHandler = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Start listening for the wake word.
     * The [listener] is called on the **main thread** each time the wake word is detected.
     * Requires `RECORD_AUDIO` permission.
     */
    fun start(listener: OnDetectionListener) {
        if (isRunning) return
        this.listener = listener
        isRunning = true

        processingThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            try {
                initModels()
                initBuffers()
                if (!initAudioRecord()) {
                    throw IllegalStateException("AudioRecord konnte nicht initialisiert werden")
                }
                audioLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Error in processing thread", e)
                mainHandler.post { onError?.invoke(e) }
            } finally {
                isRunning = false
                releaseAudioRecord()
            }
        }, "OpenWakeWord")
        processingThread?.start()
    }

    /** Prefer this callback when listening for multiple phrases. */
    fun startNamed(callback: OnNamedDetectionListener) {
        namedListener = callback
        start(OnDetectionListener { /* Named callback handles detections. */ })
    }

    /** Stop listening. Can be restarted with [start]. */
    fun stop() {
        isRunning = false
        processingThread?.join(3000)
        processingThread = null
        releaseAudioRecord()
    }

    /** Release all resources. Instance cannot be reused after this. */
    fun release() {
        stop()
        try {
            classifiers.forEach { it.session.close() }
            classifiers.clear()
            activeModels = emptyList()
            embeddingSession?.close()
            melSpecSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing ONNX sessions", e)
        }
        embeddingSession = null
        melSpecSession = null
        ortEnv = null
        listener = null
        namedListener = null
        onModelProgress = null
        onOptionalModelError = null
        onAudioProgress = null
        onError = null
    }

    // ------------------------------------------------------------------
    // Model loading
    // ------------------------------------------------------------------

    private fun initModels() {
        ortEnv = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
        }

        melSpecSession = ortEnv!!.createSession(
            loadAsset("openwakeword/melspectrogram.onnx"), opts
        )
        embeddingSession = ortEnv!!.createSession(
            loadAsset("openwakeword/embedding_model.onnx"), opts
        )

        classifiers.clear()
        for (config in wakeWordModels) {
            // A missing or corrupt optional model must not disable Jarvis.
            try {
                val modelBytes = when (val src = config.source) {
                    is ModelSource.BuiltIn -> loadAsset(src.model.assetPath)
                    is ModelSource.Asset -> loadAsset(src.path)
                    is ModelSource.Raw -> src.bytes
                }
                val session = ortEnv!!.createSession(modelBytes, opts)
                try {
                    require(session.inputNames.size == 1) { "Expected one classifier input" }
                    val inputName = session.inputNames.first()
                    val inputInfo = session.inputInfo[inputName]?.info as? ai.onnxruntime.TensorInfo
                    require(inputInfo != null && inputInfo.shape.size == 3 &&
                        (inputInfo.shape[0] == 1L || inputInfo.shape[0] == -1L) &&
                        (inputInfo.shape[1] == FEATURE_WINDOW.toLong() || inputInfo.shape[1] == -1L) &&
                        inputInfo.shape[2] == EMBEDDING_DIM.toLong()
                    ) { "Invalid classifier input; expected [1,16,96]" }
                    require(session.outputNames.size == 1) { "Expected one classifier output" }
                    val outputName = session.outputNames.first()
                    val outputInfo = session.outputInfo[outputName]?.info as? ai.onnxruntime.TensorInfo
                    require(outputInfo != null && outputInfo.shape.isNotEmpty() &&
                        outputInfo.shape.size <= 2 && outputInfo.shape.last() == 1L
                    ) { "Invalid classifier output; expected [1,1] score" }
                    classifiers.add(Classifier(config.label, session, inputName, config.threshold, config.optional, config.consecutiveFrames))
                    Log.i(TAG, "Loaded wake-word model: ${config.label}")
                } catch (badShape: Exception) {
                    session.close()
                    throw badShape
                }
            } catch (error: Exception) {
                if (!config.optional) throw error
                reportOptionalModelError(config.label, error)
            }
        }
        require(classifiers.isNotEmpty()) { "No wake-word models available" }
        activeModels = classifiers.map { it.label }

    }

    private fun reportOptionalModelError(label: String, error: Exception) {
        Log.e(TAG, "Optional wake-word model unavailable: $label", error)
        val detail = error.message ?: error.javaClass.simpleName
        mainHandler.post { if (isRunning) onOptionalModelError?.invoke(label, detail) }
    }

    private fun loadAsset(name: String): ByteArray =
        context.assets.open(name).use { it.readBytes() }

    // ------------------------------------------------------------------
    // Buffers
    // ------------------------------------------------------------------

    private fun initBuffers() {
        rawBuffer = FloatArray(SAMPLE_RATE * RAW_BUFFER_SECONDS)
        rawWritePos = 0
        rawTotalWritten = 0L

        melBuffer.clear()
        repeat(MEL_WINDOW_FRAMES) { melBuffer.add(FloatArray(MEL_BINS) { 1.0f }) }

        featureBuffer.clear()
        repeat(FEATURE_WINDOW) { featureBuffer.add(FloatArray(EMBEDDING_DIM)) }

        predictionCount = 0
        classifiers.forEach { it.lastDetectionTime = 0L; it.consecutiveFramesSeen = 0 }
        lastAnyDetectionTime = 0L
    }

    // ------------------------------------------------------------------
    // Audio capture
    // ------------------------------------------------------------------

    private fun initAudioRecord(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return false

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) return false

        return try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize.coerceAtLeast(FRAME_SAMPLES * 4)
            )
            audioRecord?.state == AudioRecord.STATE_INITIALIZED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            false
        }
    }

    private fun releaseAudioRecord() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    // ------------------------------------------------------------------
    // Main processing loop
    // ------------------------------------------------------------------

    private fun audioLoop() {
        audioRecord?.startRecording() ?: return
        val frame = ShortArray(FRAME_SAMPLES)
        var offset = 0
        var frames = 0L
        var lastProgressMs = 0L
        while (isRunning) {
            val read = audioRecord?.read(frame, offset, FRAME_SAMPLES - offset) ?: break
            if (!isRunning) break
            if (read <= 0) throw IllegalStateException("AudioRecord.read: $read")
            offset += read
            if (offset < FRAME_SAMPLES) continue
            offset = 0
            frames++
            val nowMs = android.os.SystemClock.elapsedRealtime()
            if (nowMs - lastProgressMs >= 1000) {
                lastProgressMs = nowMs
                val count = frames
                var peak = 0
                for (s in frame) peak = maxOf(peak, kotlin.math.abs(s.toInt()))
                val level = peak / 32768f
                val scores = classifiers.associate { it.label to it.lastScore }
                mainHandler.post {
                    if (isRunning) {
                        onAudioProgress?.invoke(count, level)
                        onModelProgress?.invoke(scores)
                    }
                }
            }
            addToRawBuffer(frame)
            if (rawTotalWritten < FRAME_SAMPLES + MEL_CONTEXT_SAMPLES) continue

            val audioSlice = getLastNSamples(FRAME_SAMPLES + MEL_CONTEXT_SAMPLES)
            val newMelFrames = computeMelSpectrogram(audioSlice) ?: continue

            for (melFrame in newMelFrames) melBuffer.add(melFrame)
            while (melBuffer.size > MEL_BUFFER_MAX) melBuffer.removeAt(0)

            if (melBuffer.size >= MEL_WINDOW_FRAMES) {
                val start = melBuffer.size - MEL_WINDOW_FRAMES
                val melWindow = ArrayList<FloatArray>(MEL_WINDOW_FRAMES)
                for (i in start until melBuffer.size) melWindow.add(melBuffer[i])

                val embedding = computeEmbedding(melWindow) ?: continue
                featureBuffer.add(embedding)
                while (featureBuffer.size > FEATURE_BUFFER_MAX) featureBuffer.removeAt(0)
            }

            if (featureBuffer.size >= FEATURE_WINDOW) {
                predictionCount++
                if (predictionCount <= SKIP_INITIAL_PREDICTIONS) continue

                val fStart = featureBuffer.size - FEATURE_WINDOW
                val features = ArrayList<FloatArray>(FEATURE_WINDOW)
                for (i in fStart until featureBuffer.size) features.add(featureBuffer[i])

                // Both classifiers consume the SAME speech embeddings.
                val classifierIterator = classifiers.iterator()
                while (classifierIterator.hasNext()) {
                    val classifier = classifierIterator.next()
                    val score = try {
                        runWakeWordModel(features, classifier)
                    } catch (error: Exception) {
                        if (!classifier.optional) throw error
                        // A failing experimental EP classifier must never stop the
                        // working Jarvis detector or interrupt microphone capture.
                        reportOptionalModelError(classifier.label, error)
                        try { classifier.session.close() } catch (_: Exception) {}
                        classifierIterator.remove()
                        activeModels = classifiers.map { it.label }
                        continue
                    }
                    classifier.lastScore = score
                    // Keep historical Jarvis semantics: its required confirmation count is 1.
                    // The experimental EP classifier requires several consecutive positive
                    // 80-ms windows to reduce isolated high-scoring false positives.
                    if (score.isFinite() && score >= classifier.threshold) {
                        classifier.consecutiveFramesSeen =
                            (classifier.consecutiveFramesSeen + 1).coerceAtMost(classifier.consecutiveFramesRequired)
                        if (classifier.consecutiveFramesSeen < classifier.consecutiveFramesRequired) continue
                        val now = android.os.SystemClock.elapsedRealtime()
                        // Global gate prevents one utterance being delivered twice.
                        if (now - lastAnyDetectionTime > debounceMs &&
                            now - classifier.lastDetectionTime > debounceMs) {
                            lastAnyDetectionTime = now
                            classifier.lastDetectionTime = now
                            classifier.consecutiveFramesSeen = 0
                            val label = classifier.label
                            Log.d(TAG, "Wake word detected: $label score=$score")
                            val cb = listener
                            val named = namedListener
                            mainHandler.post {
                                if (isRunning) {
                                    cb?.onDetected(score)
                                    named?.onDetected(label, score)
                                }
                            }
                        }
                    } else {
                        classifier.consecutiveFramesSeen = 0
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Ring buffer helpers
    // ------------------------------------------------------------------

    private fun addToRawBuffer(frame: ShortArray) {
        for (sample in frame) {
            rawBuffer[rawWritePos] = sample.toFloat()
            rawWritePos = (rawWritePos + 1) % rawBuffer.size
        }
        rawTotalWritten += frame.size
    }

    private fun getLastNSamples(n: Int): FloatArray {
        val result = FloatArray(n)
        val available = minOf(n.toLong(), rawBuffer.size.toLong(), rawTotalWritten).toInt()
        var readPos = (rawWritePos - available + rawBuffer.size) % rawBuffer.size
        for (i in 0 until available) {
            result[n - available + i] = rawBuffer[readPos]
            readPos = (readPos + 1) % rawBuffer.size
        }
        return result
    }

    // ------------------------------------------------------------------
    // ONNX inference
    // ------------------------------------------------------------------

    private fun computeMelSpectrogram(audio: FloatArray): List<FloatArray>? {
        val env = ortEnv ?: return null
        val session = melSpecSession ?: return null
        return try {
            OnnxTensor.createTensor(
                env, FloatBuffer.wrap(audio), longArrayOf(1, audio.size.toLong())
            ).use { input ->
                session.run(mapOf("input" to input)).use { result ->
                    val output = result[0] as OnnxTensor
                    val shape = output.info.shape
                    val flat = output.floatBuffer
                    require(shape.last() == MEL_BINS.toLong()) { "Unexpected mel shape: ${shape.contentToString()}" }
                    // ONNX returns [1, 1, time, 32]. Preserve every time slice.
                    val numFrames = flat.remaining() / MEL_BINS

                    ArrayList<FloatArray>(numFrames).also { frames ->
                        for (f in 0 until numFrames) {
                            val melFrame = FloatArray(MEL_BINS)
                            val melStart = f * MEL_BINS
                            for (b in 0 until MEL_BINS) {
                                melFrame[b] = flat.get(melStart + b) / 10.0f + 2.0f
                            }
                            frames.add(melFrame)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Mel spectrogram error", e)
        }
    }

    private fun computeEmbedding(melWindow: List<FloatArray>): FloatArray? {
        val env = ortEnv ?: return null
        val session = embeddingSession ?: return null
        return try {
            val flatData = FloatArray(MEL_WINDOW_FRAMES * MEL_BINS)
            for (i in 0 until MEL_WINDOW_FRAMES) {
                System.arraycopy(melWindow[i], 0, flatData, i * MEL_BINS, MEL_BINS)
            }
            OnnxTensor.createTensor(
                env, FloatBuffer.wrap(flatData),
                longArrayOf(1, MEL_WINDOW_FRAMES.toLong(), MEL_BINS.toLong(), 1)
            ).use { input ->
                session.run(mapOf("input_1" to input)).use { result ->
                    val buf = (result[0] as OnnxTensor).floatBuffer
                    FloatArray(EMBEDDING_DIM) { buf.get(it) }
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Embedding error", e)
        }
    }

    private fun runWakeWordModel(features: List<FloatArray>, classifier: Classifier): Float {
        val env = ortEnv ?: return 0f
        val session = classifier.session
        val inputName = classifier.inputName
        return try {
            val flatData = FloatArray(FEATURE_WINDOW * EMBEDDING_DIM)
            for (i in 0 until FEATURE_WINDOW) {
                System.arraycopy(features[i], 0, flatData, i * EMBEDDING_DIM, EMBEDDING_DIM)
            }
            OnnxTensor.createTensor(
                env, FloatBuffer.wrap(flatData),
                longArrayOf(1, FEATURE_WINDOW.toLong(), EMBEDDING_DIM.toLong())
            ).use { input ->
                session.run(mapOf(inputName to input)).use { result ->
                    (result[0] as OnnxTensor).floatBuffer.get(0)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Wake word model error", e)
        }
    }
}
