package com.pipi.voicewordgame

import android.util.TypedValue
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pipi.voicewordgame.admin.AdminActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android 15 Edge-to-Edge 에서 상태바·내비게이션 바가 상·하단 버튼을 덮지 않도록
 * 루트 뷰에 시스템 바 여백이 실제로 적용되는지 확인한다.
 * v2.1 부터는 인셋 위에 상·하단 12~16dp 여유를 더 준다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SystemBarInsetsTest {

    private fun dp(resources: android.content.res.Resources, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    /** 인셋 말고 더해진 상·하단 여유가 12~16dp 인지 확인한다. */
    private fun assertExtraBreathingRoom(extraPx: Int, density: Float) {
        val twelve = (12 * density).toInt()
        val sixteen = (16 * density).toInt()
        assertTrue("상·하단 여유 ${extraPx}px 는 12~16dp 범위여야 함", extraPx in twelve..sixteen)
    }

    @Test
    fun systemBarsBecomeRootPaddingWithExtraBreathingRoom() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<View>(R.id.root)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 72, 0, 96))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)

        val density = activity.resources.displayMetrics.density
        assertTrue(root.paddingTop > 72)
        assertExtraBreathingRoom(root.paddingTop - 72, density)
        assertTrue(root.paddingBottom > 96)
        assertExtraBreathingRoom(root.paddingBottom - 96, density)
        assertEquals(dp(activity.resources, 14), root.paddingTop - 72)
    }

    @Test
    fun keyboardInsetsExtendBottomPadding() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<View>(R.id.root)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 60, 0, 40))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 320))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)

        assertEquals(60 + dp(activity.resources, 14), root.paddingTop)
        // 키보드가 떠 있으면 바 높이 대신 키보드 높이 + 여유를 쓴다.
        assertEquals(320 + dp(activity.resources, 14), root.paddingBottom)
    }

    @Test
    fun adminScreenAlsoGetsTopAndBottomBreathingRoom() {
        val activity = Robolectric.buildActivity(AdminActivity::class.java).setup().get()
        val root = activity.findViewById<View>(R.id.adminRoot)
        val extra = dp(activity.resources, 14) // MainActivity 와 같은 규칙(14dp)을 쓴다.

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 72, 0, 96))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)

        assertEquals(72 + extra, root.paddingTop)
        assertEquals(96 + extra, root.paddingBottom)
    }
}
