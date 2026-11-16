package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.IntegratedWavAudioPlayer
import com.example.ui.components.WavFileInspector
import com.example.ui.theme.MohanaAiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WavAudioPlayerRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `wav file inspector parses RIFF metadata and waveform bars`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wavFile = File(context.cacheDir, "inspect_test_${System.currentTimeMillis()}.wav")
        writeTestWav(wavFile, sampleRate = 44100, durationMs = 2000L)

        val meta = WavFileInspector.inspectWav(wavFile, barCount = 40)
        assertNotNull(meta)
        assertEquals(44100, meta!!.sampleRateHz)
        assertEquals(1, meta.channels)
        assertEquals(16, meta.bitsPerSample)
        assertTrue(meta.durationMs >= 1950L)
        assertEquals(40, meta.waveformBars.size)
        assertTrue(meta.waveformBars.all { it in 0.08f..1.0f })

        wavFile.delete()
    }

    @Test
    fun `custom wav audio player displays waveform seek and transport controls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wavFile = File(context.cacheDir, "ui_player_test_${System.currentTimeMillis()}.wav")
        writeTestWav(wavFile, sampleRate = 44100, durationMs = 3000L)

        composeRule.setContent {
            MohanaAiTheme {
                IntegratedWavAudioPlayer(
                    wavFilePath = wavFile.absolutePath,
                    title = "Confesión del Metro"
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wav_audio_player_card").assertIsDisplayed()
        composeRule.onNodeWithText("Confesión del Metro").assertIsDisplayed()
        composeRule.onNodeWithTag("wav_waveform_scrubber").assertIsDisplayed()
        composeRule.onNodeWithTag("wav_seek_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("wav_play_pause_button").assertIsDisplayed()
        composeRule.onNodeWithTag("wav_rewind_10_button").assertIsDisplayed()
        composeRule.onNodeWithTag("wav_forward_10_button").assertIsDisplayed()
        composeRule.onNodeWithTag("wav_speed_button").assertIsDisplayed()

        wavFile.delete()
    }

    private fun writeTestWav(file: File, sampleRate: Int, durationMs: Long) {
        val samples = ((sampleRate * durationMs) / 1000L).toInt()
        val dataSize = samples * 2
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, dataSize + 36)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        header[20] = 1; header[21] = 0
        header[22] = 1; header[23] = 0
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, sampleRate * 2)
        header[32] = 2; header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, dataSize)

        FileOutputStream(file).use { out ->
            out.write(header)
            val pcm = ByteArray(dataSize)
            for (i in 0 until samples) {
                val v = (kotlin.math.sin(2.0 * Math.PI * 330.0 * i / sampleRate) * 18000.0).toInt()
                pcm[i * 2] = (v and 0xFF).toByte()
                pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            out.write(pcm)
        }
    }

    private fun writeIntLE(b: ByteArray, offset: Int, v: Int) {
        b[offset] = (v and 0xFF).toByte()
        b[offset + 1] = ((v shr 8) and 0xFF).toByte()
        b[offset + 2] = ((v shr 16) and 0xFF).toByte()
        b[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }
}
