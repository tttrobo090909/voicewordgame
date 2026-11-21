package com.pipi.voicewordgame

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android 15 Edge-to-Edge 에서 상태바·내비게이션 바가 상·하단 버튼을 덮지 않도록
 * 루트 뷰에 시스템 바 여백이 실제로 적용되는지 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SystemBarInsetsTest {

    @Test
    fun systemBarsBecomeRootPadding() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<View>(R.id.root)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 72, 0, 96))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)

        assertEquals(72, root.paddingTop)
        assertEquals(96, root.paddingBottom)
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

        assertEquals(60, root.paddingTop)
        assertEquals(320, root.paddingBottom)
    }
}
