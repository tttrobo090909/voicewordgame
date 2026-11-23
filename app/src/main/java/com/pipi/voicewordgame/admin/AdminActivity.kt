package com.pipi.voicewordgame.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pipi.voicewordgame.R
import com.pipi.voicewordgame.game.Hints
import com.pipi.voicewordgame.game.Word
import com.pipi.voicewordgame.game.WordCategory
import com.pipi.voicewordgame.game.WordStore
import com.pipi.voicewordgame.util.ImageStore
import com.pipi.voicewordgame.util.Prefs
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * 숨은 관리자 화면: 게임 설정(문제 수·제한시간)·문제 검색·카테고리 필터·추가·수정(그림/정답/힌트/유사어)·삭제·기본값 복원.
 * 모든 변경은 [WordStore] 가 앱 내부 JSON 파일에 즉시 저장하고, 게임 화면에 바로 반영된다.
 */
class AdminActivity : AppCompatActivity() {

    private companion object {
        /** 시스템 바 인셋 위에 더하는 상·하단 여유. */
        const val EXTRA_VERTICAL_DP = 14

        /** 카테고리 이름 길이 제한(v2.7). */
        const val CATEGORY_NAME_MAX = 8

        /** 한 번에 조절되는 값(문제 수는 5문제씩, 제한시간은 1초씩). */
        const val COUNT_STEP = 5
        const val TIME_STEP = 1

        /** 카테고리 대표 이모지 빠른 선택. */
        val EMOJI_CHOICES = listOf(
            "🙂", "🐶", "🍎", "🚗", "🏠", "🌳", "⭐", "🧸", "🎵", "👨‍👩‍👧", "🍀", "🐟"
        )
    }

    private lateinit var store: WordStore
    private lateinit var prefs: Prefs
    private lateinit var adapter: ProblemAdapter
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var countText: TextView
    private lateinit var search: EditText
    private lateinit var filterRow: LinearLayout

    private val filterButtons = LinkedHashMap<Int, Button>()
    private var query = ""
    private var filterCategory = 0

    private var editingWord: Word? = null
    private var editImagePath = ""
    private var editCategoryId = 1
    private var editPreview: ImageView? = null
    private var editError: TextView? = null
    private val editCategoryButtons = LinkedHashMap<Int, Button>()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) importImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                ContextCompat.getColor(this, R.color.cream_deep),
                ContextCompat.getColor(this, R.color.cream_deep)
            )
        )
        setContentView(R.layout.activity_admin)
        applySystemBarInsets(findViewById(R.id.adminRoot))

        store = WordStore(this)
        store.load()
        prefs = Prefs(this)

        list = findViewById(R.id.problemList)
        empty = findViewById(R.id.adminEmpty)
        countText = findViewById(R.id.adminCount)
        search = findViewById(R.id.adminSearch)
        filterRow = findViewById(R.id.adminFilterRow)

        adapter = ProblemAdapter(
            categoryLabel = { id -> store.book.categories.firstOrNull { it.id == id }?.ko ?: "" },
            onEdit = { word -> openEditor(word) },
            onDelete = { word -> confirmDelete(word) }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        buildFilters()
        refresh()
        setupGameSettings()
        setupResets()

        findViewById<Button>(R.id.closeAdmin).setOnClickListener { finish() }
        findViewById<Button>(R.id.addProblem).setOnClickListener { openEditor(null) }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                refresh()
            }
        })
    }

    // ---------------------------------------------------------------- 게임 설정

    /** 한 판 문제 수(10~50, 기본 15)·문제 제한시간(3~20초, 기본 9초)을 조절해 바로 저장한다. */
    private fun setupGameSettings() {
        val countMinus = findViewById<Button>(R.id.settingCountMinus)
        val countPlus = findViewById<Button>(R.id.settingCountPlus)
        val timeMinus = findViewById<Button>(R.id.settingTimeMinus)
        val timePlus = findViewById<Button>(R.id.settingTimePlus)

        countMinus.setOnClickListener {
            prefs.questionCount = prefs.questionCount - COUNT_STEP
            updateGameSettingLabels()
        }
        countPlus.setOnClickListener {
            prefs.questionCount = prefs.questionCount + COUNT_STEP
            updateGameSettingLabels()
        }
        timeMinus.setOnClickListener {
            prefs.timeLimitSeconds = prefs.timeLimitSeconds - TIME_STEP
            updateGameSettingLabels()
        }
        timePlus.setOnClickListener {
            prefs.timeLimitSeconds = prefs.timeLimitSeconds + TIME_STEP
            updateGameSettingLabels()
        }
        updateGameSettingLabels()
    }

    /** 설정 값을 화면에 보여 주고, 범위 끝에서는 −/＋ 를 흐리게 해 더 못 누르게 한다. */
    private fun updateGameSettingLabels() {
        val count = prefs.questionCount
        val seconds = prefs.timeLimitSeconds
        findViewById<TextView>(R.id.settingCountValue).text =
            getString(R.string.admin_setting_questions_value, count)
        findViewById<TextView>(R.id.settingTimeValue).text =
            getString(R.string.admin_setting_time_value, seconds)
        val enabled = ContextCompat.getColor(this, R.color.ink)
        val disabled = ContextCompat.getColor(this, R.color.ink_soft)
        findViewById<Button>(R.id.settingCountMinus).apply {
            isEnabled = count > Prefs.COUNT_MIN
            setTextColor(if (isEnabled) enabled else disabled)
        }
        findViewById<Button>(R.id.settingCountPlus).apply {
            isEnabled = count < Prefs.COUNT_MAX
            setTextColor(if (isEnabled) enabled else disabled)
        }
        findViewById<Button>(R.id.settingTimeMinus).apply {
            isEnabled = seconds > Prefs.TIME_MIN
            setTextColor(if (isEnabled) enabled else disabled)
        }
        findViewById<Button>(R.id.settingTimePlus).apply {
            isEnabled = seconds < Prefs.TIME_MAX
            setTextColor(if (isEnabled) enabled else disabled)
        }
    }

    // ---------------------------------------------------------------- 목록

    private fun buildFilters() {
        filterRow.removeAllViews()
        filterButtons.clear()
        // v2.7: 필터 줄 맨 왼쪽에 `+ 카테고리` 를 둔다(카테고리 카드 삭제).
        filterRow.addView(buildAddCategoryButton())
        addFilterButton(0, getString(R.string.admin_filter_all), store.book.words.size, null)
        store.book.categories.forEach { category ->
            addFilterButton(category.id, category.ko, store.countIn(category.id), category)
        }
        updateFilterSelection()
    }

    /** 필터 줄 왼쪽의 `+ 카테고리` 버튼. 10개를 다 만들었으면 흐리게 두고 눌렀을 때 안내만 한다. */
    private fun buildAddCategoryButton(): Button {
        val button = Button(this).apply {
            id = R.id.addCategory
            text = getString(R.string.admin_category_add)
            setTextColor(ContextCompat.getColor(this@AdminActivity, R.color.ink))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            minHeight = dp(36)
            minWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundResource(R.drawable.bg_button_soft)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            alpha = if (store.canAddCategory()) 1f else 0.45f
            setOnClickListener {
                if (store.canAddCategory()) openCategoryEditor(null) else showCategoryLimit()
            }
        }
        return button
    }

    /** 더는 카테고리를 만들 수 없을 때(최대 10개) 안내. */
    private fun showCategoryLimit() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.admin_categories_title)
            .setMessage(R.string.admin_category_full)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    /**
     * 필터 버튼 하나(전체 또는 카테고리).
     * 카테고리 버튼은 길게 누르면 수정·삭제 창이 열린다(v2.7).
     */
    private fun addFilterButton(id: Int, label: String, count: Int, category: WordCategory?) {
        val button = Button(this)
        button.tag = id
        setFilterLabel(button, label, count, category)
        button.setTextColor(ContextCompat.getColor(this, R.color.ink))
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        button.typeface = Typeface.DEFAULT_BOLD
        button.isAllCaps = false
        button.minHeight = dp(36)
        button.minWidth = dp(52)
        button.setPadding(dp(12), 0, dp(12), 0)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(36)
        )
        params.setMargins(dp(4), dp(4), dp(4), dp(4))
        button.layoutParams = params
        button.setOnClickListener {
            filterCategory = id
            updateFilterSelection()
            refresh()
        }
        if (category != null) {
            button.setOnLongClickListener {
                openCategoryEditor(category)
                true
            }
        }
        filterRow.addView(button)
        filterButtons[id] = button
    }

    /** `전체 (425)` · `동물 (91)` 처럼 제목 옆에 문제 수를 붙인다. */
    private fun setFilterLabel(button: Button, label: String, count: Int, category: WordCategory?) {
        val emoji = category?.emoji.orEmpty()
        button.text = if (emoji.isBlank()) {
            getString(R.string.admin_category_chip, label, count)
        } else {
            getString(R.string.admin_category_chip_emoji, emoji, label, count)
        }
    }

    /** 문제를 더하거나 지우면 숫자가 바로 바뀌도록 필터 이름을 다시 채운다. */
    private fun updateFilterLabels() {
        filterButtons.forEach { (id, button) ->
            if (id == 0) {
                setFilterLabel(button, getString(R.string.admin_filter_all), store.book.words.size, null)
            } else {
                val category = store.book.categories.firstOrNull { it.id == id } ?: return@forEach
                setFilterLabel(button, category.ko, store.countIn(id), category)
            }
        }
    }

    private fun updateFilterSelection() {
        filterButtons.forEach { (id, button) ->
            button.setBackgroundResource(
                if (id == filterCategory) R.drawable.bg_choice_selected else R.drawable.bg_button_soft
            )
        }
    }

    private fun refresh() {
        val q = query.trim().lowercase()
        val items = store.book.words.filter { word ->
            val inCategory = filterCategory == 0 || word.categoryId == filterCategory
            if (!inCategory) return@filter false
            if (q.isEmpty()) return@filter true
            word.ko.lowercase().contains(q) ||
                word.en.lowercase().contains(q) ||
                word.synKo.any { it.lowercase().contains(q) } ||
                word.synEn.any { it.lowercase().contains(q) }
        }
        adapter.submit(items)
        countText.text = getString(R.string.admin_count, items.size)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        updateFilterLabels()
    }

    // ---------------------------------------------------------------- 기본값 복원

    /**
     * v2.6: 기본값 복원을 둘로 나눴다.
     *  - `설정 리셋`: 한 판 문제 수(15)·문제 제한시간(9초)만 되돌린다.
     *  - `문제 리셋`: 문제 목록·추가한 카테고리만 기본 425문제·기본 카테고리 6개로 되돌린다.
     * 두 버튼 모두 누를 때 최종 확인 경고창(AlertDialog)을 먼저 띄운다.
     */
    private fun setupResets() {
        findViewById<Button>(R.id.resetSettings).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.admin_reset_settings_confirm_title)
                .setMessage(R.string.admin_reset_settings_confirm_body)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    prefs.resetGameSettings()
                    updateGameSettingLabels()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        findViewById<Button>(R.id.resetProblems).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.admin_reset_problems_confirm_title)
                .setMessage(R.string.admin_reset_problems_confirm_body)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    store.restoreDefaults()
                    if (store.book.categories.none { it.id == filterCategory }) filterCategory = 0
                    buildFilters()
                    refresh()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    // ---------------------------------------------------------------- 삭제·복원

    private fun confirmDelete(word: Word) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_body, word.ko))
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(word.id)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- 카테고리

    /** 카테고리 추가·수정 창(한글 이름·영문 이름·대표 이모지). 추가한 카테고리는 여기서 지울 수 있다. */
    private fun openCategoryEditor(category: WordCategory?) {
        val view = layoutInflater.inflate(R.layout.dialog_category_edit, null)
        val ko = view.findViewById<EditText>(R.id.categoryKo)
        val en = view.findViewById<EditText>(R.id.categoryEn)
        val emoji = view.findViewById<EditText>(R.id.categoryEmoji)
        val error = view.findViewById<TextView>(R.id.categoryError)
        val emojiRow = view.findViewById<LinearLayout>(R.id.categoryEmojiRow)

        ko.setText(category?.ko.orEmpty())
        en.setText(category?.en.orEmpty())
        emoji.setText(category?.emoji.orEmpty())
        error.visibility = View.GONE

        EMOJI_CHOICES.forEach { choice ->
            val b = Button(this)
            b.text = choice
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            b.minWidth = 0
            b.minHeight = dp(34)
            b.setPadding(0, 0, 0, 0)
            val params = LinearLayout.LayoutParams(0, dp(34), 1f)
            params.setMargins(dp(1), 0, dp(1), 0)
            b.layoutParams = params
            b.setBackgroundResource(R.drawable.bg_button_soft)
            b.setOnClickListener { emoji.setText(choice) }
            emojiRow.addView(b)
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (category == null) R.string.category_title_new else R.string.category_title_edit)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
        if (category != null && category.isCustom) {
            builder.setNeutralButton(R.string.category_delete, null)
        }
        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // v2.7: 카테고리 이름은 8자까지.
                val koText = ko.text.toString().trim().take(CATEGORY_NAME_MAX)
                if (koText.isEmpty()) {
                    error.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val enText = en.text.toString().trim().ifBlank { koText }
                val emojiText = emoji.text.toString().trim()
                if (category == null) {
                    if (store.addCategory(koText, enText, emojiText) == null) {
                        error.visibility = View.VISIBLE
                        error.text = getString(R.string.admin_category_full)
                        return@setOnClickListener
                    }
                } else {
                    store.updateCategory(category.copy(ko = koText, en = enText, emoji = emojiText))
                }
                dialog.dismiss()
                buildFilters()
                refresh()
            }
            if (category != null && category.isCustom) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    dialog.dismiss()
                    confirmDeleteCategory(category)
                }
            }
        }
        dialog.show()
    }

    /** 추가한 카테고리 삭제 확인 — 그 안의 문제도 함께 지워진다고 알린다. */
    private fun confirmDeleteCategory(category: WordCategory) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.category_delete_confirm_title)
            .setMessage(
                getString(R.string.category_delete_confirm_body, category.ko, store.countIn(category.id))
            )
            .setPositiveButton(R.string.delete) { _, _ ->
                if (store.deleteCategory(category.id)) {
                    if (filterCategory == category.id) filterCategory = 0
                    buildFilters()
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- 추가·수정

    private fun openEditor(word: Word?) {
        editingWord = word
        editImagePath = word?.image ?: store.book.words.firstOrNull()?.image.orEmpty()
        editCategoryId = word?.categoryId ?: store.book.categories.firstOrNull()?.id ?: 1
        editCategoryButtons.clear()

        val view = layoutInflater.inflate(R.layout.dialog_problem_edit, null)
        editPreview = view.findViewById(R.id.editImage)
        editError = view.findViewById(R.id.editError)
        val ko = view.findViewById<EditText>(R.id.editKo)
        val en = view.findViewById<EditText>(R.id.editEn)
        val hint = view.findViewById<EditText>(R.id.editHint)
        val syn = view.findViewById<EditText>(R.id.editSyn)

        ko.setText(word?.ko.orEmpty())
        en.setText(word?.en.orEmpty())
        hint.setText(word?.hint.orEmpty())
        syn.setText(word?.synKo.orEmpty().joinToString(", "))

        buildEditCategories(view.findViewById(R.id.editCategoryRow))
        showPreview()

        view.findViewById<Button>(R.id.pickGallery).setOnClickListener { pickImage.launch("image/*") }
        view.findViewById<Button>(R.id.pickBuiltin).setOnClickListener { showArtPicker() }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (word == null) R.string.edit_title_new else R.string.edit_title_edit)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                saveEditor(ko, en, hint, syn, dialog)
            }
        }
        dialog.setOnDismissListener {
            editPreview = null
            editError = null
            editCategoryButtons.clear()
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun buildEditCategories(container: LinearLayout) {
        container.removeAllViews()
        editCategoryButtons.clear()
        val categories = store.book.categories
        var row: LinearLayout? = null
        categories.forEachIndexed { index, category ->
            if (index % 3 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    container.addView(this)
                }
            }
            val button = Button(this)
            button.text = category.ko
            button.setTextColor(ContextCompat.getColor(this, R.color.ink))
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            button.typeface = Typeface.DEFAULT_BOLD
            button.isAllCaps = false
            button.minHeight = dp(34)
            button.setPadding(dp(2), 0, dp(2), 0)
            val params = LinearLayout.LayoutParams(0, dp(34), 1f)
            params.setMargins(dp(2), dp(2), dp(2), dp(2))
            button.layoutParams = params
            button.setOnClickListener {
                editCategoryId = category.id
                updateEditCategorySelection()
            }
            row?.addView(button)
            editCategoryButtons[category.id] = button
        }
        updateEditCategorySelection()
    }

    private fun updateEditCategorySelection() {
        editCategoryButtons.forEach { (id, button) ->
            button.setBackgroundResource(
                if (id == editCategoryId) R.drawable.bg_choice_selected else R.drawable.bg_button_soft
            )
        }
    }

    private fun showPreview() {
        val target = editPreview ?: return
        val bitmap = ImageStore.load(this, editImagePath, 2)
        if (bitmap != null) target.setImageBitmap(bitmap) else target.setImageResource(R.mipmap.ic_launcher)
    }

    private fun saveEditor(
        ko: EditText,
        en: EditText,
        hint: EditText,
        syn: EditText,
        dialog: AlertDialog
    ) {
        val koText = ko.text.toString().trim()
        if (koText.isEmpty()) {
            editError?.visibility = View.VISIBLE
            editError?.text = getString(R.string.edit_need_answer)
            return
        }
        val enText = en.text.toString().trim()
        val hintText = hint.text.toString().trim()
        val synonyms = syn.text.toString()
            .split(",", "，", "/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val image = editImagePath.ifBlank {
            store.book.words.firstOrNull()?.image.orEmpty()
        }
        val existing = editingWord
        if (existing == null) {
            store.add(
                Word(
                    id = 0,
                    ko = koText,
                    en = enText,
                    categoryId = editCategoryId,
                    image = image,
                    synKo = synonyms,
                    synEn = emptyList(),
                    hint = hintText
                )
            )
        } else {
            store.update(
                existing.copy(
                    ko = koText,
                    en = enText,
                    categoryId = editCategoryId,
                    image = image,
                    synKo = synonyms,
                    hint = hintText
                )
            )
        }
        dialog.dismiss()
        refresh()
    }

    /** 기기 갤러리 사진을 앱 내부 파일(WebP)로 저장하고 그림으로 쓴다. */
    private fun importImage(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = max(1, sample) }
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return

            val bytes = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, 90, bytes)
            val path = store.savePickedImage(bytes.toByteArray(), "webp") ?: return
            editImagePath = path
            showPreview()
        } catch (e: Exception) {
            editError?.visibility = View.VISIBLE
            editError?.text = "사진을 불러오지 못했어요"
        }
    }

    /** 앱에 들어 있는 3D 그림(250장) 중에서 고르기. */
    private fun showArtPicker() {
        val images = store.builtInImages()
        val recycler = RecyclerView(this)
        recycler.layoutManager = GridLayoutManager(this, 4)
        recycler.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(420)
        )
        recycler.setPadding(dp(8), dp(8), dp(8), dp(8))
        var picker: AlertDialog? = null
        recycler.adapter = ArtAdapter(images) { path ->
            editImagePath = path
            showPreview()
            picker?.dismiss()
        }
        picker = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pick_art_title)
            .setView(recycler)
            .setNegativeButton(R.string.close, null)
            .create()
        picker.show()
    }

    // ---------------------------------------------------------------- 유틸

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

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    /** 내장 그림 고르기용 작은 그리드. */
    private class ArtAdapter(
        private val images: List<String>,
        private val onPick: (String) -> Unit
    ) : RecyclerView.Adapter<ArtAdapter.Holder>() {

        class Holder(view: ImageView) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val size = (parent.resources.displayMetrics.density * 68).toInt()
            val view = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(size, size)
                setPadding(6, 6, 6, 6)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundResource(R.drawable.bg_card)
            }
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val path = images[position]
            val bitmap = ImageStore.load(holder.itemView.context, path, 2)
            holder.image.setImageBitmap(bitmap)
            holder.image.setOnClickListener { onPick(path) }
        }

        override fun getItemCount(): Int = images.size
    }
}
