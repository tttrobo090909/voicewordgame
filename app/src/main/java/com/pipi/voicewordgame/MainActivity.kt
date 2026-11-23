package com.pipi.voicewordgame

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.animation.ValueAnimator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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
import androidx.core.graphics.ColorUtils
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
import com.pipi.voicewordgame.ui.ChickWalk
import com.pipi.voicewordgame.ui.ParticleView
import com.pipi.voicewordgame.util.ImageStore
import com.pipi.voicewordgame.util.Prefs
import java.util.Random
import kotlin.math.max

/**
 * 말해봐요! v2.3 — 첫 화면(카테고리 고르기) + 게임 화면 + 숨은 관리자 잠금 + 개인정보처리방침 안내.
 *
 * v2.3 (사용자 최종 확정 수정본):
 * - 관리자 설정의 `한 판 문제 수`(10~50, 기본 15)·`문제 제한시간`(3~20초, 기본 9초)을 게임에 반영한다.
 *   한 판에 설정한 문제 수만큼만 내고(남은 단어가 적으면 있는 만큼만), 다 풀면 결과창을 띄운다.
 * - 첫 화면 병아리는 좌우 반전 없이(scaleX = 1) 3.6초에 한 왕복으로 잔잔하게 흔들린다.
 * - 게임 화면: 병아리 뒤 가로선을 없애고, 상태 안내 바를 타이머 배너 하나로 합쳤다.
 *   병아리가 걸어가는 그 바에 상태별 배경색과 안내 문구가 함께 뜬다. 그림 카드는 그대로, 위아래 여백은 넓혔다.
 * - 첫 화면 맨 아래 `개인정보처리방침` 링크(구구단 부모 확인 잠금)와 앱 내 요약 다이얼로그 폴백.
 * - v2.2 기능(ENG 2번 정책, `그만하기` 1회 팝업, 게임 중 제목 3탭 관리자, INTERNET 권한 없음)은 그대로.
 */
class MainActivity : AppCompatActivity() {

    private enum class BannerKind { INFO, GOOD, BAD, WAIT }

    internal companion object {
        const val REQ_MIC = 2001
        const val NEXT_DELAY_MS = 500L
        const val TAP_WINDOW_MS = 1200L
        const val MAX_START_FAILURES = 3

        /** 시스템 바 인셋 위에 더하는 상·하단 여유. */
        const val EXTRA_VERTICAL_DP = 14

        /** 카테고리 타일 높이(v2.1: 68dp → 78dp, 약 +15%). */
        const val TILE_HEIGHT_DP = 78

        /** 첫 화면 병아리: 좌우 ±10dp, 3.6초에 한 왕복(그 사이 4걸음) — 잔잔하게. */
        const val CHICK_WOBBLE_DP = 10
        const val CHICK_WOBBLE_CYCLE_MS = 3600L

        /** 첫 화면 병아리 바운스(위로 3dp)·기울기(±2.5도) — 과하지 않게. */
        const val CHICK_BOUNCE_DP = 3

        /** 게임 화면 통합 타이머·상태 배너 높이(52~56dp 사이)와 병아리 크기(문구 가독성을 위해 36dp). */
        const val TIMER_BANNER_DP = 54
        const val TIMER_CHICK_DP = 36

        /** 타이머 병아리가 걷는 거리를 잴 수 없을 때(레이아웃 전) 쓰는 폴백. */
        const val TIMER_FALLBACK_TRAVEL_DP = 260

        /** 타이머 병아리 바운스(위로 3dp). */
        const val TIMER_BOUNCE_DP = 3

        /** 제한시간 설정이 없을 때 쓰는 기본값(9초). 실제 값은 관리자 설정에서 온다. */
        const val ROUND_LIMIT_MS = ChickWalk.DEFAULT_LIMIT_MS

        /** 타이머 병아리 걷기 갱신 간격(약 30fps). 남은 시간에서 위치를 계산해 그린다. */
        const val TIMER_TICK_MS = 32L
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
    private lateinit var timerBanner: FrameLayout
    private lateinit var timerChick: ImageView
    private lateinit var privacyLink: TextView
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
    private val roundTimeout = Runnable { handleTimeUp() }

    /**
     * 타이머 병아리는 남은 시간(roundDeadline)에서 위치를 계산해 그린다.
     * 애니메이터 대신 시계 하나만 쓰므로 일시정지·재개·시간 초과가 서로 어긋나지 않는다.
     */
    private val timerTick = object : Runnable {
        override fun run() {
            if (!gameActive || !awaiting || !roundTimerOn) {
                stopTimerWalk()
                return
            }
            val elapsed = roundLimitMs - (roundDeadline - SystemClock.elapsedRealtime())
            applyTimerWalk(ChickWalk.timerProgress(elapsed.coerceAtLeast(0L), roundLimitMs))
            handler.postDelayed(this, TIMER_TICK_MS)
        }
    }

    private var session: GameSession? = null
    private var chickWobble: ValueAnimator? = null
    private var wobbleActive = false
    private var timerWalking = false
    private var roundTimerOn = false
    private var roundRemainingMs = -1L
    private var roundDeadline = 0L
    private var overlayPause = false
    private var adminOpening = false
    private var gameActive = false
    private var awaiting = false
    private var resumeNext = false
    private var lang = "ko"
    private var categoryId = 0
    private var errorStreak = 0
    private var startFailures = 0

    /** 이번 판의 한 문제 제한시간(ms) — 관리자 설정에서 읽는다(기본 9초). */
    private var roundLimitMs = ROUND_LIMIT_MS

    /** 지금 타이머 배너에 입혀진 상태 배경(테스트·캡처용). */
    private var bannerBackgroundRes = R.drawable.bg_banner_info

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
        startChickWobble()

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
            startChickWobble()
        } else {
            // 게임 중에 관리자 화면에 다녀온 경우: 문제 목록을 다시 읽고 타이머·마이크를 잇는다.
            resumeGameAfterOverlay()
        }
    }

    override fun onPause() {
        super.onPause()
        stopChickWobble()
        pauseRoundTimer()
        handler.removeCallbacks(nextRunnable)
        handler.removeCallbacks(restartRunnable)
        resumeNext = gameActive && !awaiting
        recognizer.cancel()
        speaker.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopChickWobble()
        stopTimerWalk()
        handler.removeCallbacksAndMessages(null)
        recognizer.destroy()
        speaker.shutdown()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (gameActive) finishGame(true) else super.onBackPressed()
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
        timerBanner = findViewById(R.id.timerBanner)
        timerChick = findViewById(R.id.timerChick)
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
        privacyLink = findViewById(R.id.privacyLink)
        particles = findViewById(R.id.particles)

        // 제목 3번 탭은 숨은 관리자 입구다 — 리플·포커스·소리 같은 시각·청각 반응을 남기지 않는다.
        titleText.background = null
        titleText.foreground = null
        titleText.isSoundEffectsEnabled = false
        titleText.isHapticFeedbackEnabled = false
    }

    private fun wireListeners() {
        langKo.setOnClickListener { setLang("ko") }
        langEn.setOnClickListener { setLang("en") }
        startButton.setOnClickListener { startGame() }
        randomButton.setOnClickListener { selectCategory(0) }
        hintButton.setOnClickListener { showHint() }
        skipButton.setOnClickListener { skipWord() }
        answerButton.setOnClickListener { handleCorrect() }
        // v2.2: 확인창 없이 바로 끝내고 결과창만 한 번 띄운다.
        stopButton.setOnClickListener { finishGame(true) }
        titleText.setOnClickListener { onTitleTapped() }
        privacyLink.setOnClickListener { onPrivacyTapped() }
        setupScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            handler.removeCallbacks(setupMessageReset)
        }
    }

    /** 시스템 바·컷아웃·키보드 여백을 루트 뷰에 적용한다. 상·하단에는 14dp 여유를 더한다. */
    private fun applySystemBarInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val extra = dp(EXTRA_VERTICAL_DP)
            view.setPadding(bars.left, bars.top + extra, bars.right, max(bars.bottom, ime.bottom) + extra)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * 첫 화면 병아리가 잔잔하게 흔들린다(v2.3): 좌우 ±10dp 이동, 3.6초에 한 왕복.
     * 몸은 항상 정방향(scaleX = 1)이라 명암이 좌우로 뒤집히지 않고, 바운스(3dp)·기울기(±2.5도)도 부드럽다.
     */
    private fun startChickWobble() {
        if (wobbleActive) return
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CHICK_WOBBLE_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { applySwayPose(it.animatedValue as Float) }
        }
        wobbleActive = true
        chickWobble = animator
        animator.start()
        applySwayPose(0f)
    }

    private fun applySwayPose(progress: Float) {
        chickImage.translationX = ChickWalk.swayX(progress, dp(CHICK_WOBBLE_DP).toFloat())
        chickImage.scaleX = ChickWalk.swayScaleX()
        chickImage.translationY =
            ChickWalk.bounce(progress, ChickWalk.SWAY_STEPS, dp(CHICK_BOUNCE_DP).toFloat())
        chickImage.rotation =
            ChickWalk.tilt(progress, ChickWalk.SWAY_STEPS, ChickWalk.SWAY_TILT_MAX_DEG)
    }

    private fun stopChickWobble() {
        wobbleActive = false
        chickWobble?.cancel()
        chickWobble = null
        chickImage.translationX = 0f
        chickImage.translationY = 0f
        chickImage.scaleX = 1f
        chickImage.rotation = 0f
    }

    /** 첫 화면 병아리 애니메이션이 켜져 있는지(화면을 떠나면 꺼진다). */
    internal val isChickWobbling: Boolean
        get() = wobbleActive

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
            layoutParams = LinearLayout.LayoutParams(0, dp(TILE_HEIGHT_DP), 1f).apply {
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
        val tint = categoryTint(categoryId)
        val edge = ContextCompat.getColor(this@MainActivity, R.color.select_edge)
        // 선택되면 배경을 살짝 진하게(코랄 쪽으로 12%) 칠하고 3dp 진한 테두리를 두른다.
        setColor(if (selected) ColorUtils.blendARGB(tint, edge, 0.12f) else tint)
        if (selected) setStroke(dp(3), edge) else setStroke(0, Color.TRANSPARENT)
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

    /**
     * v2.2 ENG 정책(사용자 선택 2번): 문제 단어·카테고리 타일·음성인식·정답 발음만 영어로 바뀌고
     * 화면의 버튼·안내 문구는 한국어 그대로 둔다.
     */
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
                getString(if (newLang == "en") R.string.lang_switched_en else R.string.lang_switched_ko),
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

    /** 게임 중에도 제목 3연속 탭으로 관리자 잠금을 열 수 있다(v2.2). */
    private fun onTitleTapped() {
        if (tapCounter.tap(SystemClock.elapsedRealtime())) {
            showGuguLock {
                adminOpening = true
                startActivity(Intent(this, AdminActivity::class.java))
            }
        }
    }

    /** 첫 화면 맨 아래 `개인정보처리방침`: 부모 확인 잠금을 통과하면 웹 문서를 연다. */
    private fun onPrivacyTapped() {
        showGuguLock { openPrivacyPolicy() }
    }

    /**
     * 부모 확인(구구단) 잠금. 맞히면 [onUnlocked] 를 실행한다.
     * 관리자 진입과 개인정보처리방침 열기가 같은 잠금을 쓴다.
     */
    private fun showGuguLock(onUnlocked: () -> Unit) {
        val question = Gugu.next(Random())
        val view = layoutInflater.inflate(R.layout.dialog_gugu, null)
        val questionText = view.findViewById<TextView>(R.id.guguQuestion)
        val input = view.findViewById<EditText>(R.id.guguInput)
        val error = view.findViewById<TextView>(R.id.guguError)
        questionText.text = question.text
        error.visibility = View.GONE

        // 잠금창이 떠 있는 동안에는 제한시간 타이머·마이크·TTS 를 멈춘다.
        pauseForAdmin()

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
                    onUnlocked()
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
            // 관리자 화면으로 넘어간 경우에는 onResume 에서 잇는다.
            if (!adminOpening) resumeGameAfterOverlay()
            adminOpening = false
        }
        dialog.show()
        input.requestFocus()
    }

    /**
     * 개인정보처리방침 웹 문서를 브라우저로 연다.
     * 브라우저가 없거나 열기 실패하면 앱 안의 요약 다이얼로그로 대신 보여 준다.
     */
    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_url)))
        val opened = try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
        if (!opened) showPrivacyFallback()
    }

    /** 브라우저를 열 수 없을 때 보여 주는 앱 내 개인정보처리방침 요약. */
    internal fun showPrivacyFallback() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.privacy_title)
            .setMessage(R.string.privacy_summary)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    /** 관리자 잠금창·관리자 화면에 들어가는 동안 게임을 멈춰 둔다. */
    private fun pauseForAdmin() {
        if (!gameActive) return
        overlayPause = true
        pauseRoundTimer()
        handler.removeCallbacks(restartRunnable)
        handler.removeCallbacks(nextRunnable)
        // 정답을 읽어 주는 중이었다면 돌아왔을 때 다음 문제로 넘어가게 한다.
        if (!awaiting) resumeNext = true
        recognizer.cancel()
        speaker.stop()
        micStatus.text = ""
    }

    /** 관리자 잠금창을 닫거나 관리자 화면에서 돌아오면 게임을 자연스럽게 잇는다. */
    private fun resumeGameAfterOverlay() {
        if (!gameActive) return
        overlayPause = false
        store.load()
        buildCategoryButtons()
        updateCategorySelection()
        // 관리자 화면에서 설정을 바꿨을 수 있으니 제한시간을 다시 읽는다.
        roundLimitMs = prefs.timeLimitSeconds * 1000L
        if (awaiting) {
            resumeRoundTimer()
            scheduleRestart(300)
        } else if (resumeNext) {
            resumeNext = false
            handler.postDelayed(nextRunnable, 500)
        }
    }

    // ---------------------------------------------------------------- 게임

    private fun startGame() {
        val pool = store.book.byCategory(categoryId)
        if (pool.isEmpty()) {
            setupMessage(getString(R.string.need_questions))
            return
        }
        session = GameSession().apply { setPool(pool, prefs.questionCount) }
        roundLimitMs = prefs.timeLimitSeconds * 1000L
        gameActive = true
        awaiting = false
        resumeNext = false
        overlayPause = false
        errorStreak = 0
        startFailures = 0
        speechLog.clear()

        particles.clear()
        stopChickWobble()
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
            // 설정한 문제 수를 다 풀었다 — 자동으로 끝내고 결과창을 띄운다(v2.3).
            finishGame(true)
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
        startRoundTimer()
        startListening()
    }

    // ---------------------------------------------------------------- 8초 타이머

    /** 문제를 시작할 때: 설정한 제한시간을 걸고 타이머 병아리를 왼쪽 끝에서 걷게 한다. */
    private fun startRoundTimer() {
        handler.removeCallbacks(roundTimeout)
        roundRemainingMs = -1L
        roundTimerOn = true
        roundDeadline = SystemClock.elapsedRealtime() + roundLimitMs
        handler.postDelayed(roundTimeout, roundLimitMs)
        beginTimerWalk(0f)
    }

    /** 남은 시간을 기억해 두고 타이머를 멈춘다(관리자 진입·화면 전환). */
    private fun pauseRoundTimer() {
        if (!roundTimerOn) return
        roundTimerOn = false
        roundRemainingMs = (roundDeadline - SystemClock.elapsedRealtime()).coerceIn(0L, roundLimitMs)
        handler.removeCallbacks(roundTimeout)
        stopTimerWalk()
    }

    /** 멈춰 둔 남은 시간부터 타이머를 다시 잇는다. */
    private fun resumeRoundTimer() {
        if (!gameActive || !awaiting || roundTimerOn) return
        val remaining = if (roundRemainingMs in 1..roundLimitMs) roundRemainingMs else roundLimitMs
        roundRemainingMs = -1L
        roundTimerOn = true
        roundDeadline = SystemClock.elapsedRealtime() + remaining
        handler.removeCallbacks(roundTimeout)
        handler.postDelayed(roundTimeout, remaining)
        beginTimerWalk(ChickWalk.timerProgress(roundLimitMs - remaining, roundLimitMs))
    }

    /** 타이머를 완전히 내린다(정답·건너뛰기·시간 초과·게임 종료). */
    private fun stopRoundTimer() {
        roundTimerOn = false
        roundRemainingMs = -1L
        handler.removeCallbacks(roundTimeout)
        stopTimerWalk()
    }

    private fun timerTravelPx(): Float {
        val track = timerBanner.width - timerBanner.paddingLeft - timerBanner.paddingRight - timerChick.width
        return if (track > 0) track.toFloat() else dp(TIMER_FALLBACK_TRAVEL_DP).toFloat()
    }

    /** 타이머 병아리가 [fromProgress] 부터 오른쪽 끝까지 걷는다. */
    private fun beginTimerWalk(fromProgress: Float) {
        handler.removeCallbacks(timerTick)
        timerWalking = true
        applyTimerWalk(fromProgress)
        handler.postDelayed(timerTick, TIMER_TICK_MS)
    }

    private fun applyTimerWalk(progress: Float) {
        timerChick.translationX = ChickWalk.walkX(progress, timerTravelPx())
        timerChick.scaleX = 1f
        timerChick.translationY =
            ChickWalk.bounce(progress, ChickWalk.TIMER_STEPS, dp(TIMER_BOUNCE_DP).toFloat())
        timerChick.rotation =
            ChickWalk.tilt(progress, ChickWalk.TIMER_STEPS, ChickWalk.TILT_MAX_DEG)
    }

    /**
     * 걷기를 멈춘다. 자세는 그 자리에 그대로 둬서(정답을 읽어 주는 동안)
     * 병아리가 왼쪽 끝으로 되돌아가는 것처럼 보이지 않게 한다.
     * 다음 문제가 시작될 때 beginTimerWalk(0f) 가 다시 왼쪽 끝으로 세운다.
     */
    private fun stopTimerWalk() {
        timerWalking = false
        handler.removeCallbacks(timerTick)
    }

    /**
     * 제한시간이 지났다: 타이머·마이크를 즉시 멈추고 정답을 **한 번만** 읽어 준 뒤 다음 문제로 넘어간다.
     */
    private fun handleTimeUp() {
        if (!gameActive || !awaiting) return
        val word = session?.current ?: return
        awaiting = false
        stopRoundTimer()
        handler.removeCallbacks(restartRunnable)
        recognizer.cancel()
        micStatus.text = ""
        hintBadge.visibility = View.GONE
        answerReveal.text = getString(R.string.time_up_line, word.text(lang))
        answerReveal.setTextColor(ContextCompat.getColor(this, R.color.bad))
        gameStatus(getString(R.string.time_up_banner), BannerKind.BAD)
        speakOnceThenNext(word)
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
        stopRoundTimer()
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
        stopRoundTimer()
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
        speechLog.add(word.text(lang) to 2)
        speaker.speakTwice(word.text(lang), langTag()) {
            if (!gameActive) return@speakTwice
            handler.postDelayed(nextRunnable, NEXT_DELAY_MS)
        }
    }

    /** 시간 초과: 정답을 한 번만 읽고 다음 문제로 넘어간다. */
    private fun speakOnceThenNext(word: Word) {
        handler.removeCallbacks(nextRunnable)
        micStatus.text = ""
        speechLog.add(word.text(lang) to 1)
        speaker.speakOnce(word.text(lang), langTag()) {
            if (!gameActive) return@speakOnce
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

    private fun finishGame(showSummary: Boolean) {
        val correct = session?.correctCount ?: 0
        gameActive = false
        awaiting = false
        resumeNext = false
        overlayPause = false
        adminOpening = false
        stopRoundTimer()
        handler.removeCallbacks(nextRunnable)
        handler.removeCallbacks(restartRunnable)
        recognizer.cancel()
        speaker.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameScreen.visibility = View.GONE
        setupScreen.visibility = View.VISIBLE
        scoreText.visibility = View.GONE
        startChickWobble()
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
        // v2.3: 별도 상태 바 대신 병아리가 걷는 타이머 배너 하나에 문구와 배경색을 입힌다.
        gameStatus.text = text
        val background = when (kind) {
            BannerKind.INFO -> R.drawable.bg_banner_info
            BannerKind.GOOD -> R.drawable.bg_banner_good
            BannerKind.BAD -> R.drawable.bg_banner_bad
            BannerKind.WAIT -> R.drawable.bg_banner_wait
        }
        bannerBackgroundRes = background
        timerBanner.setBackgroundResource(background)
        gameStatus.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- 테스트용 훅

    /** 실제로 요청한 발음 기록(단어, 읽는 횟수). 게임을 시작할 때 비운다. */
    private val speechLog = mutableListOf<Pair<String, Int>>()

    internal val speechLogForTest: List<Pair<String, Int>>
        get() = speechLog.toList()

    /** 첫 화면 병아리 자세를 특정 진행도로 맞춘다(애니메이션 프레임 대신). */
    internal fun applySwayPoseForTest(progress: Float) = applySwayPose(progress)

    /** 지금 판의 한 문제 제한시간(ms). */
    internal val roundLimitForTest: Long
        get() = roundLimitMs

    /** 지금 타이머 배너에 입혀진 상태 배경 리소스. */
    internal val bannerBackgroundForTest: Int
        get() = bannerBackgroundRes

    /** 이번 판에 낼 문제 수(설정값, 남은 단어가 적으면 그 만큼). */
    internal val plannedQuestions: Int
        get() = session?.plannedCount() ?: 0

    /** 지금까지 낸 문제 수. */
    internal val servedQuestions: Int
        get() = session?.servedCount ?: 0

    /** 타이머 병아리 자세를 특정 진행도로 맞춘다. */
    internal fun applyTimerPoseForTest(progress: Float) = applyTimerWalk(progress)

    /** 타이머 병아리가 걷는 거리(px). */
    internal val timerTravel: Float
        get() = timerTravelPx()

    /** 지금 정답을 기다리는 중인지. */
    internal val isAwaiting: Boolean
        get() = awaiting

    /** 8초 제한 타이머가 돌고 있는지. */
    internal val isRoundTimerRunning: Boolean
        get() = roundTimerOn

    /** 멈춰 둔 남은 시간(ms). 돌고 있으면 -1. */
    internal val pausedRoundRemainingMs: Long
        get() = roundRemainingMs

    /** 타이머 병아리 걷기 애니메이션이 켜져 있는지. */
    internal val isTimerWalking: Boolean
        get() = timerWalking

    /** 엔진에 실제로 요청한 발음 속도(1번째·2번째·시간 초과 1회). */
    internal val appliedSpeechRates: List<Float>
        get() = synchronized(speaker.appliedRates) { speaker.appliedRates.toList() }

    /** 현재 문제 단어(테스트가 정답 문자열을 확인할 때). */
    internal val currentWord: Word?
        get() = session?.current

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
