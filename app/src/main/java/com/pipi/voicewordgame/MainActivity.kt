package com.pipi.voicewordgame

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pipi.voicewordgame.admin.AdminActivity
import com.pipi.voicewordgame.admin.Gugu
import com.pipi.voicewordgame.admin.TapCounter
import com.pipi.voicewordgame.game.AnswerMatcher
import com.pipi.voicewordgame.game.GameSession
import com.pipi.voicewordgame.game.Hints
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.game.WordStore
import com.pipi.voicewordgame.speech.AnswerRecognizer
import com.pipi.voicewordgame.tts.Speaker
import com.pipi.voicewordgame.ui.ParticleView
import com.pipi.voicewordgame.util.ImageStore
import com.pipi.voicewordgame.util.Prefs
import java.util.Random
import kotlin.math.max

/**
 * 말해봐요! v2.0 — 첫 화면(카테고리 고르기) + 게임 화면 + 숨은 관리자 잠금.
 *
 * - 가족 등록·목소리 등록·소음 측정·화자 판별은 v2.0에서 모두 빠졌다(아이와 함께 쓰는 단순한 게임).
 * - 음성인식은 실기기 검증(dictationtest v1.2) 방식: 구글 인식기 우선, DIRECT 마이크,
 *   RESULTS_RECOGNITION 1순위 읽기.
 * - 정답·건너뛰기 모두 정답 단어를 두 번 읽어 준 뒤 다음 문제로 넘어간다.
 */
class MainActivity : AppCompatActivity() {

    private enum class BannerKind { INFO, GOOD, BAD, WAIT }

    private companion object {
        const val REQ_MIC = 2001
        const val NEXT_DELAY_MS = 500L
        const val TAP_WINDOW_MS = 1200L
        const val MAX_START_FAILURES = 3
    }

    private lateinit var prefs: Prefs
    private lateinit var store: WordStore
    private lateinit var speaker: Speaker
    private lateinit var recognizer: AnswerRecognizer

    private lateinit var setupScreen: LinearLayout
    private lateinit var setupScroll: ScrollView
    private lateinit var gameScreen: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var scoreText: TextView
    private lateinit var langKo: TextView
    private lateinit var langEn: TextView
    private lateinit var versionText: TextView
    private lateinit var bannerText: TextView
    private lateinit var chickImage: ImageView
    private lateinit var randomButton: Button
    private lateinit var categoryGrid: LinearLayout
    private lateinit var startButton: Button
    private lateinit var gameStatus: TextView
    private lateinit var wordImage: ImageView
    private lateinit var hintBadge: TextView
    private lateinit var answerReveal: TextView
    private lateinit var hintButton: Button
    private lateinit var skipButton: Button
    private lateinit var answerButton: Button
    private lateinit var stopButton: Button
    private lateinit var micStatus: TextView
    private lateinit var particles: ParticleView

    private val categoryButtons = LinkedHashMap<Int, View>()
    private val tapCounter = TapCounter(TAP_WINDOW_MS)
    private val handler = Handler(Looper.getMainLooper())
    private val nextRunnable = Runnable { nextRound() }
    private val restartRunnable = Runnable { startListening() }
    private val setupMessageReset = Runnable { showBannerLine() }

    private var session: GameSession? = null
    private var gameActive = false
    private var awaiting = false
    private var resumeNext = false
    private var lang = "ko"
    private var categoryId = 0
    private var errorStreak = 0
    private var startFailures = 0

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15(targetSdk 35)는 화면 꽉 채우기(Edge-to-Edge)가 강제된다.
        // 루트 뷰에 시스템 바·키보드 여백을 줘서 버튼이 상태바·내비바에 가려지지 않게 한다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                ContextCompat.getColor(this, R.color.cream_deep),
                ContextCompat.getColor(this, R.color.cream_deep)
            )
        )
        setContentView(R.layout.activity_main)
        applySystemBarInsets(findViewById(R.id.root))

        prefs = Prefs(this)
        store = WordStore(this)
        store.load()
        speaker = Speaker(this)
        recognizer = AnswerRecognizer(this)

        bindViews()

        lang = if (prefs.lang == "en") "en" else "ko"
        categoryId = prefs.categoryId
        if (categoryId < 0 || categoryId > 6) categoryId = 0
        versionText.text = BuildConfig.VERSION_DISPLAY

        buildCategoryButtons()
        updateLangToggle()
        updateCategorySelection()
        showBannerLine()

        wireListeners()
        recognizer.listener = recognitionListener

        speaker.init { ok ->
            if (!ok) {
                bannerText.text = getString(R.string.tts_missing)
                bannerText.setTextColor(ContextCompat.getColor(this, R.color.bad))
            }
        }
        micStatus.text = ""
    }

    override fun onResume() {
        super.onResume()
        if (!gameActive) {
            // 관리자 화면에서 문제를 고치고 돌아왔을 수 있으니 다시 읽는다.
            store.load()
            buildCategoryButtons()
            updateCategorySelection()
        } else if (awaiting) {
            handler.postDelayed(restartRunnable, 350)
        } else if (resumeNext) {
            resumeNext = false
            handler.postDelayed(nextRunnable, 500)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(nextRunnable)
        handler.removeCallbacks(restartRunnable)
        resumeNext = gameActive && !awaiting
        recognizer.cancel()
        speaker.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        recognizer.destroy()
        speaker.shutdown()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (gameActive) confirmStop() else super.onBackPressed()
    }

    // ---------------------------------------------------------------- views

    private fun bindViews() {
        setupScreen = findViewById(R.id.setupScreen)
        setupScroll = findViewById(R.id.setupScroll)
        gameScreen = findViewById(R.id.gameScreen)
        titleText = findViewById(R.id.titleText)
        scoreText = findViewById(R.id.scoreText)
        langKo = findViewById(R.id.langKo)
        langEn = findViewById(R.id.langEn)
        versionText = findViewById(R.id.versionText)
        bannerText = findViewById(R.id.bannerText)
        chickImage = findViewById(R.id.chickImage)
        randomButton = findViewById(R.id.randomButton)
        categoryGrid = findViewById(R.id.categoryGrid)
        startButton = findViewById(R.id.startButton)
        gameStatus = findViewById(R.id.gameStatus)
        wordImage = findViewById(R.id.wordImage)
        hintBadge = findViewById(R.id.hintBadge)
        answerReveal = findViewById(R.id.answerReveal)
        hintButton = findViewById(R.id.hintButton)
        skipButton = findViewById(R.id.skipButton)
        answerButton = findViewById(R.id.answerButton)
        stopButton = findViewById(R.id.stopButton)
        micStatus = findViewById(R.id.micStatus)
        particles = findViewById(R.id.particles)
    }

    private fun wireListeners() {
        langKo.setOnClickListener { setLang("ko") }
        langEn.setOnClickListener { setLang("en") }
        startButton.setOnClickListener { startGame() }
        randomButton.setOnClickListener { selectCategory(0) }
        hintButton.setOnClickListener { showHint() }
        skipButton.setOnClickListener { skipWord() }
        answerButton.setOnClickListener { handleCorrect() }
        stopButton.setOnClickListener { confirmStop() }
        titleText.setOnClickListener { onTitleTapped() }
        setupScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            handler.removeCallbacks(setupMessageReset)
        }
    }

    /** 시스템 바·컷아웃·키보드 여백을 루트 뷰에 적용한다. */
    private fun applySystemBarInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    // ---------------------------------------------------------------- 첫 화면

    private fun buildCategoryButtons() {
        categoryGrid.removeAllViews()
        categoryButtons.clear()
        randomButton.text = getString(R.string.category_all_format, store.book.words.size)

        var row: LinearLayout? = null
        store.book.categories.forEachIndexed { index, category ->
            if (index % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    categoryGrid.addView(this)
                }
            }
            val cell = buildCategoryTile(category.id, category.label(lang), store.book.byCategory(category.id).size)
            row?.addView(cell)
            categoryButtons[category.id] = cell
        }
    }

    /** 그림 + 이름이 같이 있는 카테고리 타일(색은 카테고리마다 다르게). */
    private fun buildCategoryTile(categoryId: Int, label: String, count: Int): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(8), 0)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, dp(68), 1f).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            setOnClickListener { selectCategory(categoryId) }
        }

        val image = ImageView(this)
        image.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.contentDescription = label
        val picture = representativeImage(categoryId)?.let { ImageStore.load(this, it, 2) }
        if (picture != null) image.setImageBitmap(picture) else image.setImageResource(R.mipmap.ic_launcher)

        val text = TextView(this)
        text.id = R.id.tileLabel
        text.text = getString(R.string.category_format, label, count)
        text.setTextColor(ContextCompat.getColor(this, R.color.ink))
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        text.typeface = Typeface.DEFAULT_BOLD
        text.maxLines = 2
        text.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        }

        cell.addView(image)
        cell.addView(text)
        return cell
    }

    /** 카테고리를 대표하는 내장 그림(없으면 그 카테고리 첫 문제). */
    private fun representativeImage(categoryId: Int): String? {
        val preferred = when (categoryId) {
            1 -> "강아지"
            2 -> "사과"
            3 -> "자동차"
            4 -> "집"
            5 -> "무지개"
            6 -> "손"
            else -> null
        }
        val words = store.book.byCategory(categoryId)
        val word = words.firstOrNull { it.ko == preferred } ?: words.firstOrNull()
        return word?.image
    }

    private fun categoryTint(categoryId: Int): Int = ContextCompat.getColor(
        this,
        when (categoryId) {
            1 -> R.color.tint_animal
            2 -> R.color.tint_food
            3 -> R.color.tint_vehicle
            4 -> R.color.tint_home
            5 -> R.color.tint_nature
            else -> R.color.tint_person
        }
    )

    private fun selectCategory(id: Int) {
        categoryId = id
        prefs.categoryId = id
        updateCategorySelection()
    }

    private fun updateCategorySelection() {
        randomButton.setBackgroundResource(
            if (categoryId == 0) R.drawable.bg_choice_selected else R.drawable.bg_choice_random
        )
        categoryButtons.forEach { (id, view) ->
            view.background = categoryTileBackground(id, id == categoryId)
        }
    }

    private fun categoryTileBackground(categoryId: Int, selected: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(if (selected) ContextCompat.getColor(this@MainActivity, R.color.lemon) else categoryTint(categoryId))
        setStroke(dp(2), if (selected) ContextCompat.getColor(this@MainActivity, R.color.lemon_deep) else android.graphics.Color.TRANSPARENT)
    }

    private fun showBannerLine() {
        bannerText.text = getString(R.string.banner_line)
        bannerText.setTextColor(ContextCompat.getColor(this, R.color.ink))
    }

    private fun setupMessage(text: String) {
        bannerText.text = text
        bannerText.setTextColor(ContextCompat.getColor(this, R.color.bad))
        handler.removeCallbacks(setupMessageReset)
        handler.postDelayed(setupMessageReset, 2600)
    }

    private fun setLang(newLang: String) {
        if (lang == newLang) return
        lang = newLang
        prefs.lang = newLang
        updateLangToggle()
        buildCategoryButtons()
        updateCategorySelection()
        if (gameActive) {
            recognizer.cancel()
            scheduleRestart(250)
            gameStatus(
                if (newLang == "en") "Now say it in English" else "이제 한국어로 말해요",
                BannerKind.INFO
            )
        }
    }

    private fun updateLangToggle() {
        val on = ContextCompat.getDrawable(this, R.drawable.bg_toggle_on)
        val off = ContextCompat.getDrawable(this, R.drawable.bg_toggle_off)
        val ink = ContextCompat.getColor(this, R.color.ink)
        val soft = ContextCompat.getColor(this, R.color.ink_soft)
        if (lang == "en") {
            langEn.background = on
            langKo.background = off
            langEn.setTextColor(ink)
            langKo.setTextColor(soft)
        } else {
            langKo.background = on
            langEn.background = off
            langKo.setTextColor(ink)
            langEn.setTextColor(soft)
        }
    }

    // ---------------------------------------------------------------- 숨은 관리자

    private fun onTitleTapped() {
        if (gameActive) return
        if (tapCounter.tap(SystemClock.elapsedRealtime())) showGuguLock()
    }

    private fun showGuguLock() {
        val question = Gugu.next(Random())
        val view = layoutInflater.inflate(R.layout.dialog_gugu, null)
        val questionText = view.findViewById<TextView>(R.id.guguQuestion)
        val input = view.findViewById<EditText>(R.id.guguInput)
        val error = view.findViewById<TextView>(R.id.guguError)
        questionText.text = question.text
        error.visibility = View.GONE

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.gugu_title)
            .setMessage(R.string.gugu_body)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (Gugu.isCorrect(question, input.text.toString())) {
                    dialog.dismiss()
                    startActivity(Intent(this, AdminActivity::class.java))
                } else {
                    error.visibility = View.VISIBLE
                    error.text = getString(R.string.gugu_wrong)
                    handler.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, 900)
                }
            }
        }
        dialog.setOnDismissListener {
            input.clearFocus()
            tapCounter.reset()
        }
        dialog.show()
        input.requestFocus()
    }

    // ---------------------------------------------------------------- 게임

    private fun startGame() {
        val pool = store.book.byCategory(categoryId)
        if (pool.isEmpty()) {
            setupMessage(getString(R.string.need_questions))
            return
        }
        session = GameSession().apply { setPool(pool) }
        gameActive = true
        awaiting = false
        resumeNext = false
        errorStreak = 0
        startFailures = 0

        particles.clear()
        handler.removeCallbacks(setupMessageReset)
        setupScreen.visibility = View.GONE
        gameScreen.visibility = View.VISIBLE
        scoreText.visibility = View.VISIBLE
        updateScore()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!hasMicPermission()) requestMicPermission()
        speaker.init()
        nextRound()
    }

    private fun nextRound() {
        if (!gameActive) return
        val active = session ?: return
        val word = active.next()
        if (word == null) {
            finishGame(false)
            return
        }
        awaiting = true
        resumeNext = false
        hintBadge.visibility = View.GONE
        answerReveal.text = getString(R.string.what_is_it)
        answerReveal.setTextColor(ContextCompat.getColor(this, R.color.ink_soft))

        val bitmap = ImageStore.load(this, word.image)
        if (bitmap != null) {
            wordImage.setImageBitmap(bitmap)
        } else {
            wordImage.setImageResource(R.mipmap.ic_launcher)
        }
        wordImage.alpha = 0f
        wordImage.animate().alpha(1f).setDuration(200).start()

        gameStatus(getString(R.string.game_hint_line), BannerKind.INFO)
        updateScore()
        startListening()
    }

    private fun startListening() {
        handler.removeCallbacks(restartRunnable)
        if (!gameActive || !awaiting) return
        if (!hasMicPermission()) {
            micStatus.text = getString(R.string.mic_manual)
            return
        }
        if (recognizer.start(langTag())) {
            startFailures = 0
            micStatus.text = getString(R.string.mic_listening)
        } else {
            startFailures += 1
            micStatus.text = getString(R.string.mic_manual)
            if (startFailures <= MAX_START_FAILURES) {
                handler.postDelayed(restartRunnable, 1500)
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }

    private fun langTag(): String = if (lang == "en") "en-US" else "ko-KR"

    private val recognitionListener = object : AnswerRecognizer.Listener {
        override fun onReady() {
            errorStreak = 0
            startFailures = 0
            if (gameActive && awaiting) micStatus.text = getString(R.string.mic_listening)
        }

        override fun onPartial(text: String) {}

        override fun onAnswers(texts: List<String>) {
            if (!gameActive || !awaiting) return
            val word = session?.current ?: return
            val matched = AnswerMatcher.firstMatchAny(texts, listOf(word), lang)
            if (matched != null) {
                handleCorrect()
            } else {
                val heard = texts.firstOrNull()?.take(14).orEmpty()
                gameStatus(getString(R.string.heard_line, heard), BannerKind.BAD)
                scheduleRestart(450)
            }
        }

        override fun onNoMatch() {
            if (!gameActive || !awaiting) return
            gameStatus(getString(R.string.not_heard), BannerKind.WAIT)
            scheduleRestart(400)
        }

        override fun onSpeechEnd() {}

        override fun onError(code: Int) {
            handleRecognitionError(code)
        }
    }

    private fun handleRecognitionError(code: Int) {
        if (!gameActive || !awaiting) return
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                gameStatus(getString(R.string.not_heard), BannerKind.WAIT)
                scheduleRestart(450)
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                micStatus.text = getString(R.string.mic_manual)
                requestMicPermission()
            }

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> {
                recognizer.recreate()
                scheduleRestart(800)
            }

            else -> {
                errorStreak += 1
                if (errorStreak >= 3) {
                    recognizer.recreate()
                    errorStreak = 0
                }
                scheduleRestart(800)
            }
        }
    }

    /** 정답이거나 '정답 처리'를 눌렀을 때. */
    private fun handleCorrect() {
        if (!gameActive || !awaiting) return
        val word = session?.current ?: return
        awaiting = false
        handler.removeCallbacks(restartRunnable)
        recognizer.cancel()

        session?.markCorrect()
        updateScore()
        answerReveal.text = getString(R.string.correct_line, word.text(lang))
        answerReveal.setTextColor(ContextCompat.getColor(this, R.color.ink))
        particles.celebrate()
        gameStatus(getString(R.string.correct_banner), BannerKind.GOOD)
        speakThenNext(word)
    }

    private fun skipWord() {
        if (!gameActive || !awaiting) return
        val word = session?.current ?: return
        awaiting = false
        handler.removeCallbacks(restartRunnable)
        recognizer.cancel()

        answerReveal.text = getString(R.string.skipped_line, word.text(lang))
        answerReveal.setTextColor(ContextCompat.getColor(this, R.color.ink))
        gameStatus(getString(R.string.skipped_banner), BannerKind.INFO)
        speakThenNext(word)
    }

    /** 정답 단어를 두 번 읽고, 두 번째 발음이 끝나고 500ms 뒤 다음 문제로 넘어간다. */
    private fun speakThenNext(word: Word) {
        handler.removeCallbacks(nextRunnable)
        micStatus.text = ""
        speaker.speakTwice(word.text(lang), langTag()) {
            if (!gameActive) return@speakTwice
            handler.postDelayed(nextRunnable, NEXT_DELAY_MS)
        }
    }

    private fun showHint() {
        if (!gameActive) return
        val word = session?.current ?: return
        hintBadge.text = Hints.badge(word, lang)
        hintBadge.visibility = View.VISIBLE
        hintBadge.scaleX = 0.85f
        hintBadge.scaleY = 0.85f
        hintBadge.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    private fun confirmStop() {
        if (!gameActive) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.stop_confirm_title)
            .setMessage(R.string.stop_confirm_body)
            .setPositiveButton(R.string.stop_game) { _, _ -> finishGame(true) }
            .setNegativeButton(R.string.continue_game, null)
            .show()
    }

    private fun finishGame(showSummary: Boolean) {
        val correct = session?.correctCount ?: 0
        gameActive = false
        awaiting = false
        resumeNext = false
        handler.removeCallbacks(nextRunnable)
        handler.removeCallbacks(restartRunnable)
        recognizer.cancel()
        speaker.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameScreen.visibility = View.GONE
        setupScreen.visibility = View.VISIBLE
        scoreText.visibility = View.GONE
        hintBadge.visibility = View.GONE
        micStatus.text = ""
        particles.clear()
        if (showSummary) showSummary(correct)
    }

    private fun showSummary(correct: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.summary_title)
            .setMessage(getString(R.string.summary_body, correct))
            .setPositiveButton(R.string.play_again) { _, _ -> startGame() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun updateScore() {
        scoreText.text = getString(R.string.score_format, session?.correctCount ?: 0)
    }

    private fun gameStatus(text: String, kind: BannerKind) {
        gameStatus.text = text
        gameStatus.setBackgroundResource(
            when (kind) {
                BannerKind.INFO -> R.drawable.bg_banner_info
                BannerKind.GOOD -> R.drawable.bg_banner_good
                BannerKind.BAD -> R.drawable.bg_banner_bad
                BannerKind.WAIT -> R.drawable.bg_banner_wait
            }
        )
        gameStatus.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- 권한

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        if (hasMicPermission()) return
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (gameActive && awaiting) startListening()
        } else {
            micStatus.text = getString(R.string.mic_manual)
        }
    }
}
