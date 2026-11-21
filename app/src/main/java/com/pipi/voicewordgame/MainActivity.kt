package com.pipi.voicewordgame

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pipi.voicewordgame.audio.MicCapture
import com.pipi.voicewordgame.audio.SpeakerMatcher
import com.pipi.voicewordgame.audio.VoiceFeatureExtractor
import com.pipi.voicewordgame.game.AnswerMatcher
import com.pipi.voicewordgame.game.GameSession
import com.pipi.voicewordgame.game.Player
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.game.WordRepository
import com.pipi.voicewordgame.speech.AnswerRecognizer
import com.pipi.voicewordgame.tts.Speaker
import com.pipi.voicewordgame.ui.LevelMeterView
import com.pipi.voicewordgame.ui.ParticleView
import com.pipi.voicewordgame.util.ImageStore
import com.pipi.voicewordgame.util.Prefs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private enum class Mode { PIPE, DIRECT, MANUAL }
    private enum class BannerKind { INFO, GOOD, BAD, WAIT }

    private companion object {
        const val REQ_MIC = 2001
        const val ENROLL_SECONDS = 2.5f
        const val NOISE_SECONDS = 2.0f
        const val NEXT_DELAY_MS = 1800L
        const val MAX_PLAYERS = 6
    }

    private lateinit var prefs: Prefs
    private lateinit var mic: MicCapture
    private lateinit var speaker: Speaker
    private lateinit var recognizer: AnswerRecognizer

    private lateinit var setupScroll: ScrollView
    private lateinit var setupBanner: TextView
    private lateinit var gameScreen: LinearLayout
    private lateinit var playerContainer: LinearLayout
    private lateinit var addPlayerButton: Button
    private lateinit var noiseButton: Button
    private lateinit var noiseStatus: TextView
    private lateinit var noiseMeter: LevelMeterView
    private lateinit var categoryGroup: ChipGroup
    private lateinit var startButton: Button
    private lateinit var scoreRow: LinearLayout
    private lateinit var bannerText: TextView
    private lateinit var wordImage: ImageView
    private lateinit var hintText: TextView
    private lateinit var progressBadge: TextView
    private lateinit var micMeter: LevelMeterView
    private lateinit var micStatus: TextView
    private lateinit var answerReveal: TextView
    private lateinit var particles: ParticleView
    private lateinit var countdownOverlay: LinearLayout
    private lateinit var countdownText: TextView
    private lateinit var countdownHint: TextView
    private lateinit var langKo: TextView
    private lateinit var langEn: TextView
    private lateinit var versionText: TextView
    private lateinit var heroImage: ImageView
    private lateinit var hintButton: Button
    private lateinit var skipButton: Button
    private lateinit var answerButton: Button
    private lateinit var stopButton: Button

    private val players = ArrayList<Player>()
    private var session: GameSession? = null
    private var gameActive = false
    private var awaiting = false
    private var lang = "ko"
    private var categoryId = 0
    private var mode = Mode.PIPE
    private var errorStreak = 0
    private var errorWindowStart = 0L
    private var gateRejects = 0
    private var pipeBlocked = false
    private var pendingAfterPermission: (() -> Unit)? = null
    private var resumeGameOnReturn = false

    private val handler = Handler(Looper.getMainLooper())
    private val nextRunnable = Runnable { nextRound() }
    private val restartRunnable = Runnable { startListening() }

    private val meterTicker = object : Runnable {
        override fun run() {
            val db = mic.levelDb
            micMeter.setLevel(db)
            noiseMeter.setLevel(db)
            if (mode == Mode.DIRECT) {
                micMeter.setBaseline(null)
            }
            handler.postDelayed(this, 110)
        }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        mic = MicCapture(this)
        speaker = Speaker(this)
        recognizer = AnswerRecognizer(this)

        WordRepository.load(this)
        bindViews()

        lang = prefs.lang
        categoryId = prefs.categoryId
        versionText.text = BuildConfig.VERSION_DISPLAY
        heroImage.setImageResource(R.mipmap.ic_launcher_foreground)

        loadPlayers()
        buildCategoryChips()
        updateLangToggle()
        refreshNoiseStatus()

        wireListeners()

        speaker.init { ok ->
            if (!ok) setupBanner(getString(R.string.tts_missing), BannerKind.WAIT)
        }
        recognizer.listener = recognitionListener

        micStatus.text = getString(R.string.mic_waiting)
        micMeter.setBaseline(prefs.noiseBaselineOrNull())
        noiseMeter.setBaseline(prefs.noiseBaselineOrNull())
        handler.post(meterTicker)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(meterTicker)
        handler.post(meterTicker)
        if (gameActive && resumeGameOnReturn) {
            resumeGameOnReturn = false
            prepareAudioMode()
            handler.postDelayed(restartRunnable, 450)
        }
    }

    override fun onPause() {
        super.onPause()
        resumeGameOnReturn = gameActive
        handler.removeCallbacks(nextRunnable)
        handler.removeCallbacks(restartRunnable)
        recognizer.cancel()
        mic.closePipeSource()
        speaker.stop()
        mic.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        recognizer.destroy()
        mic.stop()
        speaker.shutdown()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (gameActive) {
            confirmStop()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ---------------------------------------------------------------- views

    private fun bindViews() {
        setupScroll = findViewById(R.id.setupScroll)
        setupBanner = findViewById(R.id.setupBanner)
        gameScreen = findViewById(R.id.gameScreen)
        playerContainer = findViewById(R.id.playerContainer)
        addPlayerButton = findViewById(R.id.addPlayerButton)
        noiseButton = findViewById(R.id.noiseButton)
        noiseStatus = findViewById(R.id.noiseStatus)
        noiseMeter = findViewById(R.id.noiseMeter)
        categoryGroup = findViewById(R.id.categoryGroup)
        startButton = findViewById(R.id.startButton)
        scoreRow = findViewById(R.id.scoreRow)
        bannerText = findViewById(R.id.bannerText)
        wordImage = findViewById(R.id.wordImage)
        hintText = findViewById(R.id.hintText)
        progressBadge = findViewById(R.id.progressBadge)
        micMeter = findViewById(R.id.micMeter)
        micStatus = findViewById(R.id.micStatus)
        answerReveal = findViewById(R.id.answerReveal)
        particles = findViewById(R.id.particles)
        countdownOverlay = findViewById(R.id.countdownOverlay)
        countdownText = findViewById(R.id.countdownText)
        countdownHint = findViewById(R.id.countdownHint)
        langKo = findViewById(R.id.langKo)
        langEn = findViewById(R.id.langEn)
        versionText = findViewById(R.id.versionText)
        heroImage = findViewById(R.id.heroImage)
        hintButton = findViewById(R.id.hintButton)
        skipButton = findViewById(R.id.skipButton)
        answerButton = findViewById(R.id.answerButton)
        stopButton = findViewById(R.id.stopButton)
    }

    private fun wireListeners() {
        langKo.setOnClickListener { setLang("ko") }
        langEn.setOnClickListener { setLang("en") }
        addPlayerButton.setOnClickListener { addPlayer() }
        noiseButton.setOnClickListener { measureNoise() }
        startButton.setOnClickListener { startGame() }
        hintButton.setOnClickListener { showHint() }
        skipButton.setOnClickListener { skipWord() }
        answerButton.setOnClickListener { manualCorrect() }
        stopButton.setOnClickListener { confirmStop() }
        setupScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            if (setupBanner.visibility == View.VISIBLE) setupBanner.visibility = View.GONE
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun setupBanner(text: String, kind: BannerKind) {
        if (gameActive) {
            gameBanner(text, kind)
            return
        }
        applyBanner(setupBanner, text, kind)
    }

    private fun gameBanner(text: String, kind: BannerKind) {
        applyBanner(bannerText, text, kind)
    }

    private fun applyBanner(view: TextView, text: String, kind: BannerKind) {
        view.text = text
        view.setBackgroundResource(
            when (kind) {
                BannerKind.INFO -> R.drawable.bg_banner_info
                BannerKind.GOOD -> R.drawable.bg_banner_good
                BannerKind.BAD -> R.drawable.bg_banner_bad
                BannerKind.WAIT -> R.drawable.bg_banner_wait
            }
        )
        view.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- 가족 등록

    private fun loadPlayers() {
        players.clear()
        players.addAll(prefs.loadPlayers())
        if (players.isEmpty()) {
            players.add(Player(id = 1L, name = "아빠", avatarId = 0))
            players.add(Player(id = 2L, name = "엄마", avatarId = 1))
            prefs.savePlayers(players)
        }
        rebuildPlayerRows()
        refreshScoreboard()
    }

    private fun nextPlayerId(): Long = (players.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun rebuildPlayerRows() {
        playerContainer.removeAllViews()
        players.forEach { p -> playerContainer.addView(buildPlayerRow(p)) }
        addPlayerButton.isEnabled = players.size < MAX_PLAYERS
        addPlayerButton.alpha = if (players.size < MAX_PLAYERS) 1f else 0.45f
    }

    private fun buildPlayerRow(player: Player): View {
        val row = layoutInflater.inflate(R.layout.item_player_setup, playerContainer, false)
        val avatarButton = row.findViewById<ImageButton>(R.id.avatarButton)
        val nameInput = row.findViewById<EditText>(R.id.nameInput)
        val voiceStatus = row.findViewById<TextView>(R.id.voiceStatus)
        val enrollButton = row.findViewById<Button>(R.id.enrollButton)
        val deleteButton = row.findViewById<ImageButton>(R.id.deleteButton)

        loadAvatarInto(avatarButton, player)
        nameInput.setText(player.name)
        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                player.name = s?.toString()?.trim() ?: ""
                prefs.savePlayers(players)
                if (session != null) refreshScoreboard()
            }
        })

        updateVoiceStatus(player, voiceStatus)
        avatarButton.setOnClickListener { showAvatarPicker(player, avatarButton) }
        enrollButton.setOnClickListener { startEnrollment(player, voiceStatus) }
        deleteButton.setOnClickListener { confirmDelete(player) }
        return row
    }

    private fun loadAvatarInto(view: ImageView, player: Player) {
        val avatar = player.avatar()
        val bmp = avatar?.let { ImageStore.load(this, it.image, 2) }
        if (bmp != null) view.setImageBitmap(bmp) else view.setImageResource(R.mipmap.ic_launcher_foreground)
    }

    private fun updateVoiceStatus(player: Player, view: TextView? = null) {
        val target = view ?: return
        if (player.profile != null) {
            target.text = getString(R.string.voice_registered)
            target.setTextColor(ContextCompat.getColor(this, R.color.good))
        } else {
            target.text = getString(R.string.voice_unregistered)
            target.setTextColor(ContextCompat.getColor(this, R.color.ink_soft))
        }
    }

    private fun addPlayer() {
        if (players.size >= MAX_PLAYERS) {
            setupBanner("가족은 ${MAX_PLAYERS}명까지 함께할 수 있어요", BannerKind.WAIT)
            return
        }
        val used = players.map { it.avatarId }.toSet()
        val avatarId = WordRepository.avatars.firstOrNull { it.id !in used }?.id ?: 0
        players.add(Player(id = nextPlayerId(), name = "가족${players.size + 1}", avatarId = avatarId))
        prefs.savePlayers(players)
        rebuildPlayerRows()
        refreshScoreboard()
    }

    private fun confirmDelete(player: Player) {
        if (players.size <= 1) {
            setupBanner("가족은 한 명 이상 있어야 해요", BannerKind.WAIT)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("${player.name} 지울까요?")
            .setMessage("목소리 등록도 함께 지워져요.")
            .setPositiveButton("지우기") { _, _ ->
                players.removeAll { it.id == player.id }
                prefs.savePlayers(players)
                rebuildPlayerRows()
                refreshScoreboard()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showAvatarPicker(player: Player, target: ImageView) {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(4))
        }
        var rowView: LinearLayout? = null
        var dialogRef: androidx.appcompat.app.AlertDialog? = null
        val perRow = 5
        WordRepository.avatars.forEachIndexed { index, avatar ->
            if (index % perRow == 0) {
                rowView = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    container.addView(this)
                }
            }
            val cell = FrameLayout(this)
            val size = dp(56)
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(dp(2), dp(4), dp(2), dp(4))
            cell.layoutParams = params
            val image = ImageView(this)
            image.layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                gravity = android.view.Gravity.CENTER
            }
            val bmp = ImageStore.load(this, avatar.image, 2)
            if (bmp != null) image.setImageBitmap(bmp)
            image.contentDescription = avatar.name
            cell.addView(image)
            cell.setOnClickListener {
                player.avatarId = avatar.id
                prefs.savePlayers(players)
                loadAvatarInto(target, player)
                rebuildPlayerRows()
                refreshScoreboard()
                dialogRef?.dismiss()
                setupBanner("${player.name} 얼굴을 ${avatar.name}(으)로 골랐어요", BannerKind.INFO)
            }
            rowView?.addView(cell)
        }
        scroll.addView(container)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.avatar_pick_title))
            .setView(scroll)
            .setNegativeButton(getString(R.string.close), null)
            .create()
        dialogRef = dialog
        dialog.show()
        dialog.window?.setLayout(dp(320), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    // ---------------------------------------------------------------- 음성 등록

    private fun startEnrollment(player: Player, status: TextView) {
        if (!mic.hasPermission()) {
            pendingAfterPermission = { startEnrollment(player, status) }
            requestMicPermission()
            return
        }
        if (gameActive) finishGame(false)
        showCountdown(3, "${player.name}님, 이름을 크게 말해 주세요!") {
            captureEnrollment(player, status)
        }
    }

    private fun captureEnrollment(player: Player, status: TextView) {
        countdownText.text = "🎤"
        countdownHint.text = "듣고 있어요… ${player.name}!"
        handler.postDelayed({
            Thread {
                val pcm = mic.recordOnce(ENROLL_SECONDS)
                val profile = pcm?.let { VoiceFeatureExtractor.analyze(it) }
                runOnUiThread {
                    hideCountdown()
                    if (profile == null) {
                        status.text = getString(R.string.enroll_fail)
                        status.setTextColor(ContextCompat.getColor(this, R.color.bad))
                        setupBanner(getString(R.string.enroll_fail), BannerKind.BAD)
                    } else {
                        player.profile = profile
                        prefs.savePlayers(players)
                        updateVoiceStatus(player, status)
                        setupBanner("${player.name} 목소리를 기억했어요! (${profile.seconds.toInt()}초)", BannerKind.GOOD)
                    }
                }
            }.start()
        }, 400)
    }

    private fun showCountdown(from: Int, hint: String, onDone: () -> Unit) {
        countdownOverlay.visibility = View.VISIBLE
        countdownHint.text = hint
        var remaining = from
        fun tick() {
            if (remaining > 0) {
                countdownText.text = remaining.toString()
                countdownText.scaleX = 0.8f
                countdownText.scaleY = 0.8f
                countdownText.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
                remaining -= 1
                handler.postDelayed({ tick() }, 750)
            } else {
                countdownText.text = "말해요!"
                onDone()
            }
        }
        tick()
    }

    private fun hideCountdown() {
        countdownOverlay.visibility = View.GONE
    }

    // ---------------------------------------------------------------- 소음 측정

    private fun measureNoise() {
        if (!mic.hasPermission()) {
            pendingAfterPermission = { measureNoise() }
            requestMicPermission()
            return
        }
        if (gameActive) return
        noiseButton.isEnabled = false
        noiseButton.alpha = 0.5f
        noiseStatus.text = "조용히… 재는 중"
        Thread {
            val pcm = mic.recordOnce(NOISE_SECONDS)
            val db = pcm?.let { medianDb(it) }
            runOnUiThread {
                noiseButton.isEnabled = true
                noiseButton.alpha = 1f
                if (db == null) {
                    noiseStatus.text = getString(R.string.noise_idle)
                    setupBanner("소리를 못 쟀어요. 한 번 더 눌러 주세요", BannerKind.BAD)
                } else {
                    prefs.noiseBaselineDb = db
                    prefs.hasNoiseBaseline = true
                    noiseStatus.text = getString(R.string.noise_measured, db.toInt())
                    noiseMeter.setBaseline(db)
                    micMeter.setBaseline(db)
                    setupBanner("주변 소리 기준(${db.toInt()} dB)을 저장했어요", BannerKind.GOOD)
                }
            }
        }.start()
    }

    private fun medianDb(pcm: ShortArray): Float {
        val win = 800 // 50ms
        if (pcm.size < win) return -90f
        val values = ArrayList<Float>()
        var i = 0
        while (i + win <= pcm.size) {
            var sum = 0.0
            for (j in i until i + win) {
                val v = pcm[j] / 32768.0
                sum += v * v
            }
            values.add((20.0 * log10(max(1e-7, sqrt(sum / win)))).toFloat())
            i += win
        }
        if (values.isEmpty()) return -90f
        values.sort()
        return values[values.size / 2]
    }

    private fun refreshNoiseStatus() {
        val base = prefs.noiseBaselineOrNull()
        if (base != null) {
            noiseStatus.text = getString(R.string.noise_measured, base.toInt())
            noiseMeter.setBaseline(base)
        } else {
            noiseStatus.text = getString(R.string.noise_idle)
        }
    }

    // ---------------------------------------------------------------- 언어/카테고리

    private fun setLang(newLang: String) {
        lang = newLang
        prefs.lang = newLang
        updateLangToggle()
        buildCategoryChips()
        if (gameActive) {
            awaiting = true
            recognizer.cancel()
            mic.resetPeak()
            handler.removeCallbacks(restartRunnable)
            handler.postDelayed(restartRunnable, 250)
            gameBanner("이제 ${if (newLang == "en") "영어" else "한국어"}로 말해요", BannerKind.INFO)
        }
    }

    private fun updateLangToggle() {
        val on = ContextCompat.getDrawable(this, R.drawable.bg_toggle_on)
        val off = ContextCompat.getDrawable(this, R.drawable.bg_toggle_off)
        val inkColor = ContextCompat.getColor(this, R.color.ink)
        val softColor = ContextCompat.getColor(this, R.color.ink_soft)
        if (lang == "en") {
            langEn.background = on
            langKo.background = off
            langEn.setTextColor(inkColor)
            langKo.setTextColor(softColor)
        } else {
            langKo.background = on
            langEn.background = off
            langKo.setTextColor(inkColor)
            langEn.setTextColor(softColor)
        }
    }

    private fun buildCategoryChips() {
        categoryGroup.removeAllViews()
        val all = makeChip(0, "${getString(R.string.category_all)} · ${WordRepository.words.size}")
        categoryGroup.addView(all)
        WordRepository.categories.forEach { c ->
            categoryGroup.addView(makeChip(c.id, c.label(lang)))
        }
        val target = categoryGroup.getChildAt(0) as? Chip
        if (categoryId == 0) {
            target?.isChecked = true
        } else {
            val found = (0 until categoryGroup.childCount)
                .map { categoryGroup.getChildAt(it) as Chip }
                .firstOrNull { (it.tag as? Int) == categoryId }
            (found ?: target)?.isChecked = true
        }
    }

    private fun makeChip(id: Int, label: String): Chip {
        val chip = Chip(this)
        chip.text = label
        chip.tag = id
        chip.isCheckable = true
        chip.isClickable = true
        chip.id = View.generateViewId()
        ContextCompat.getColorStateList(this, R.color.chip_bg)?.let { chip.chipBackgroundColor = it }
        ContextCompat.getColorStateList(this, R.color.chip_text)?.let { chip.setTextColor(it) }
        chip.chipStrokeWidth = 0f
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        chip.setPadding(0, 0, 0, 0)
        chip.setOnCheckedChangeListener { view, checked ->
            if (checked) {
                val value = view.tag as? Int ?: 0
                categoryId = value
                prefs.categoryId = value
                session?.setPool(WordRepository.byCategory(value))
            }
        }
        return chip
    }

    // ---------------------------------------------------------------- 게임

    private fun startGame() {
        val named = players.filter { it.name.isNotBlank() }
        if (named.isEmpty()) {
            setupBanner(getString(R.string.need_player), BannerKind.BAD)
            return
        }
        if (named.size != players.size) {
            players.clear()
            players.addAll(named)
            prefs.savePlayers(players)
            rebuildPlayerRows()
        }
        hideKeyboard()

        val s = GameSession(players)
        s.setPool(WordRepository.byCategory(categoryId))
        session = s
        gameActive = true
        awaiting = false
        gateRejects = 0
        errorStreak = 0

        particles.clear()
        setupScroll.visibility = View.GONE
        gameScreen.visibility = View.VISIBLE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        refreshScoreboard()
        progressBadge.text = ""
        answerReveal.text = ""
        hintText.text = ""
        wordImage.setImageDrawable(null)

        if (!mic.hasPermission()) requestMicPermission()
        prepareAudioMode()
        speaker.init { }

        nextRound()
    }

    private fun prepareAudioMode() {
        if (!mic.hasPermission()) {
            mode = Mode.MANUAL
            updateMicStatus()
            return
        }
        if (pipeBlocked) {
            mode = Mode.DIRECT
            mic.stop()
        } else {
            mode = Mode.PIPE
            if (!mic.start()) {
                mode = Mode.DIRECT
                pipeBlocked = true
                mic.stop()
            }
        }
        updateMicStatus()
    }

    private fun updateMicStatus() {
        micStatus.text = when (mode) {
            Mode.PIPE -> getString(R.string.mic_listening)
            Mode.DIRECT -> "듣는 중 · 화자 구분은 잠시 쉼"
            Mode.MANUAL -> getString(R.string.mic_manual)
        }
    }

    private fun nextRound() {
        if (!gameActive) return
        val s = session ?: return
        val word = s.next()
        if (word == null) {
            gameBanner("준비된 단어가 없어요. 카테고리를 골라 주세요", BannerKind.BAD)
            finishGame(false)
            return
        }
        awaiting = true
        gateRejects = 0
        errorStreak = 0
        hintText.text = ""
        answerReveal.text = ""
        mic.resetPeak()
        progressBadge.text = "${s.servedCount}번째 그림"

        val bmp = ImageStore.load(this, word.image)
        wordImage.setImageBitmap(bmp)
        wordImage.alpha = 0f
        wordImage.animate().alpha(1f).setDuration(220).start()

        gameBanner("🎤 그림을 보고 단어를 말해보세요", BannerKind.INFO)
        startListening()
    }

    private fun startListening() {
        if (!gameActive || !awaiting) return
        handler.removeCallbacks(restartRunnable)
        if (mode == Mode.MANUAL) {
            updateMicStatus()
            return
        }
        val langTag = langTag()
        var pfd: ParcelFileDescriptor? = null
        if (mode == Mode.PIPE) {
            pfd = mic.openPipeSource()
            if (pfd == null) {
                fallbackToDirect("이 기기에서는 마이크를 나눠 쓸 수 없어요. 화자 구분 없이 진행할게요")
                return
            }
        }
        val started = recognizer.start(langTag, pfd)
        if (!started) {
            if (mode == Mode.PIPE) {
                fallbackToDirect("음성인식을 다시 준비할게요")
            } else {
                mode = Mode.MANUAL
                mic.stop()
                updateMicStatus()
                gameBanner("음성인식을 쓸 수 없어요. 정답 버튼을 눌러 주세요", BannerKind.WAIT)
            }
        }
    }

    private fun scheduleListening(delayMs: Long) {
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }

    private fun fallbackToDirect(note: String) {
        pipeBlocked = true
        mode = Mode.DIRECT
        mic.closePipeSource()
        mic.stop()
        micMeter.setBaseline(null)
        updateMicStatus()
        gameBanner(note, BannerKind.WAIT)
        scheduleListening(500)
    }

    private fun langTag(): String = if (lang == "en") "en-US" else "ko-KR"

    private val recognitionListener = object : AnswerRecognizer.Listener {
        override fun onReady() {
            errorStreak = 0
            if (gameActive && awaiting) {
                micStatus.text = getString(R.string.mic_listening)
            }
        }

        override fun onPartial(text: String) {}

        override fun onAnswers(texts: List<String>) {
            handleAnswers(texts)
        }

        override fun onNoMatch() {
            if (!gameActive || !awaiting) return
            gameBanner("잘 못 들었어요. 한 번 더 말해볼까요?", BannerKind.WAIT)
            scheduleListening(400)
        }

        override fun onSpeechEnd() {}

        override fun onError(code: Int) {
            handleRecognitionError(code)
        }
    }

    private fun handleAnswers(texts: List<String>) {
        val word = session?.current ?: return
        if (!gameActive || !awaiting) return
        val accepted = ArrayList<Word>(2)
        accepted.add(word)
        accepted.addAll(WordRepository.siblingsOf(word))
        val matched = AnswerMatcher.firstMatchAny(texts, accepted, lang)
        if (!voiceAboveNoiseFloor()) {
            gateRejects += 1
            if (matched == null || gateRejects < 3) {
                // 주변 소음으로 보고 반응하지 않는다.
                scheduleListening(250)
                return
            }
        }
        gateRejects = 0
        if (matched != null) {
            handleCorrect(word, allowSpeakerId = mode == Mode.PIPE)
        } else {
            val heard = texts.firstOrNull()?.take(14).orEmpty()
            gameBanner("이렇게 들었어요 · $heard", BannerKind.BAD)
            scheduleListening(450)
        }
    }

    private fun voiceAboveNoiseFloor(): Boolean {
        val base = prefs.noiseBaselineOrNull() ?: return true
        if (mode != Mode.PIPE) return true
        return mic.peakDb >= base + 2f
    }

    private fun handleCorrect(word: Word, allowSpeakerId: Boolean) {
        if (!awaiting) return
        awaiting = false
        handler.removeCallbacks(nextRunnable)
        handler.removeCallbacks(restartRunnable)
        recognizer.cancel()

        answerReveal.text = "정답! ${word.text(lang)}"
        particles.celebrate()

        val s = session ?: return
        var awarded = false
        if (allowSpeakerId) {
            val pcm = mic.recentPcm(1.7f)
            val profile = if (pcm.isNotEmpty()) VoiceFeatureExtractor.analyze(pcm) else null
            val match = if (profile != null) SpeakerMatcher.match(profile, players) else null
            if (match != null && match.confident) {
                val p = players.firstOrNull { it.id == match.playerId }
                if (p != null) {
                    s.award(p.id, word.id)
                    gameBanner("${p.name} 목소리 정답! +1점", BannerKind.GOOD)
                    awarded = true
                }
            }
        }
        if (!awarded) {
            s.markPending(word.id)
            gameBanner("정답! 점수 받을 사람을 눌러 주세요", BannerKind.WAIT)
        }

        speaker.speak(word.text(lang), langTag())
        refreshScoreboard()
        handler.postDelayed(nextRunnable, NEXT_DELAY_MS)
    }

    private fun manualCorrect() {
        val word = session?.current ?: return
        if (!gameActive || !awaiting) return
        handleCorrect(word, allowSpeakerId = false)
    }

    private fun handleRecognitionError(code: Int) {
        if (!gameActive || !awaiting) return
        val soft = code == SpeechRecognizer.ERROR_NO_MATCH ||
            code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            code == SpeechRecognizer.ERROR_SERVER ||
            code == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
        if (soft) {
            gameBanner("잘 못 들었어요. 한 번 더 말해볼까요?", BannerKind.WAIT)
            scheduleListening(500)
            return
        }
        val now = System.currentTimeMillis()
        if (now - errorWindowStart > 15000L) {
            errorWindowStart = now
            errorStreak = 0
        }
        errorStreak += 1
        when (mode) {
            Mode.PIPE -> fallbackToDirect("음성인식이 마이크를 나눠 쓸 수 없어 화자 구분 없이 진행할게요")
            Mode.DIRECT -> {
                if (errorStreak >= 3) {
                    mode = Mode.MANUAL
                    mic.stop()
                    updateMicStatus()
                    gameBanner("음성인식을 쓸 수 없어요. 아래 정답 버튼을 눌러 주세요", BannerKind.WAIT)
                } else {
                    scheduleListening(700)
                }
            }
            Mode.MANUAL -> Unit
        }
    }

    private fun showHint() {
        val word = session?.current ?: return
        if (!gameActive) return
        val text = word.text(lang)
        val hint = if (lang == "en") {
            text.replaceRange(1, text.length, "_ ".repeat((text.length - 1).coerceAtLeast(0)))
        } else {
            text.first() + "○".repeat((text.length - 1).coerceAtLeast(0))
        }
        hintText.text = "힌트: $hint  (${text.length}글자)"
    }

    private fun skipWord() {
        if (!gameActive) return
        awaiting = false
        recognizer.cancel()
        speaker.stop()
        handler.removeCallbacks(nextRunnable)
        gameBanner("건너뛰었어요. 다음 그림!", BannerKind.INFO)
        handler.postDelayed(nextRunnable, 350)
    }

    private fun confirmStop() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.stop_confirm_title))
            .setMessage(getString(R.string.stop_confirm_body))
            .setPositiveButton(getString(R.string.stop_game)) { _, _ -> finishGame(true) }
            .setNegativeButton("계속하기", null)
            .show()
    }

    private fun finishGame(showSummary: Boolean) {
        gameActive = false
        awaiting = false
        handler.removeCallbacks(nextRunnable)
        handler.removeCallbacks(restartRunnable)
        recognizer.cancel()
        speaker.stop()
        mic.closePipeSource()
        mic.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameScreen.visibility = View.GONE
        setupScroll.visibility = View.VISIBLE
        hideCountdown()
        handler.removeCallbacks(meterTicker)
        handler.post(meterTicker)
        refreshScoreboard()
        if (showSummary) showSummaryDialog()
    }

    private fun showSummaryDialog() {
        val ranking = players.sortedByDescending { it.score }
        val top = ranking.firstOrNull()
        val body = ranking.joinToString("\n") { p ->
            val crown = if (top != null && p.score == top.score && p.score > 0) " 🏆" else ""
            "${p.name} · ${p.score}점$crown"
        }
        val title = if (top != null && top.score > 0) "${top.name} 우승! 🎉" else getString(R.string.summary_title)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("한 번 더") { _, _ -> startGame() }
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    // ---------------------------------------------------------------- 점수판

    private fun refreshScoreboard() {
        scoreRow.removeAllViews()
        players.forEach { p ->
            val chip = layoutInflater.inflate(R.layout.item_score_chip, scoreRow, false)
            val avatar = chip.findViewById<ImageView>(R.id.chipAvatar)
            val name = chip.findViewById<TextView>(R.id.chipName)
            val score = chip.findViewById<TextView>(R.id.chipScore)
            val bmp = p.avatar()?.let { ImageStore.load(this, it.image, 2) }
            if (bmp != null) avatar.setImageBitmap(bmp) else avatar.setImageResource(R.mipmap.ic_launcher_foreground)
            name.text = p.name.ifBlank { "이름" }
            score.text = "${p.score}"
            chip.setOnClickListener { onScoreChipTapped(p) }
            chip.setOnLongClickListener {
                gameBanner("방금 받은 점수를 옮기려면 그 사람을 눌러 주세요", BannerKind.INFO)
                true
            }
            scoreRow.addView(chip)
        }
    }

    private fun onScoreChipTapped(player: Player) {
        val s = session ?: return
        val pending = s.pendingWordId
        if (pending != null) {
            s.award(player.id, pending)
            gameBanner("${player.name} +1점! 🎉", BannerKind.GOOD)
            refreshScoreboard()
            return
        }
        if (s.moveLastAwardTo(player.id)) {
            gameBanner("방금 점수를 ${player.name}에게 옮겼어요", BannerKind.GOOD)
            refreshScoreboard()
        } else {
            gameBanner("${player.name} · ${player.score}점", BannerKind.INFO)
        }
    }

    // ---------------------------------------------------------------- 권한/유틸

    private fun requestMicPermission() {
        if (mic.hasPermission()) return
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
        val action = pendingAfterPermission
        pendingAfterPermission = null
        if (granted) {
            if (gameActive) {
                prepareAudioMode()
                startListening()
            } else {
                action?.invoke()
            }
        } else {
            mode = Mode.MANUAL
            updateMicStatus()
            setupBanner("마이크를 허용하지 않아도 정답 버튼으로 게임할 수 있어요", BannerKind.WAIT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        currentFocus?.clearFocus()
    }
}
