package ir.mahozad.android

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import androidx.test.uiautomator.UiDevice
import de.mannodermaus.junit5.ActivityScenarioExtension
import de.mannodermaus.junit5.condition.DisabledIfBuildConfigValue
import ir.mahozad.android.PieChart.Slice
import ir.mahozad.android.unit.dp
import ir.mahozad.android.unit.sp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * These tests are used to visually inspect the chart to avoid any regressions.
 * Also, they are used to test whether changing chart properties work as expected.
 * This is a kind of end-to-end testing.
 *
 * Make sure the device screen is on and unlocked for the activity to go to resumed state.
 *
 * NOTE: Because JUnit 5 is built on Java 8 from the ground up, its instrumentation tests
 *  will only run on devices running Android 8.0 (API 26) or newer. Older phones will
 *  skip the execution of these tests completely, marking them as "ignored".
 *
 * NOTE: The screenshot tests do not work the same on the GitHub emulator
 *  (even though the emulator device defined in ci.yml is the same as our main local device).
 *  So, we had to disable them on GitHub. Read on to see how.
 *
 * NOTE: Because the tests are instrumented tests and run in the JVM of the device,
 *  they cannot see the environment variables of the OS the device and the tests run in.
 *  So, annotations like [DisabledOnOs] or [DisabledIfEnvironmentVariable] do not
 *  have access to the OS environment variables and hence do not work as intended.
 *  The [Disabled] annotation works correctly.
 *
 *  We used a custom BuildConfig field to check if we are running on CI.
 *  See the build script -> android -> buildTypes -> debug.
 *
 *  Another solution would be to use `InstrumentationRegistry.getArguments()`.
 *  See [this post](https://stackoverflow.com/a/46183452).
 *
 *  Another solution would be the following.
 *  See [this](https://stackoverflow.com/q/42675547) and [this](https://stackoverflow.com/q/40156906) SO posts.
 *  ```
 *  buildTypes {
 *      create("local") {
 *          initWith(buildTypes["debug"])
 *          buildConfigField("Boolean", "CI", "${System.getenv("CI") == "true"}")
 *          isDebuggable = true
 *      }
 *      testBuildType = "local"
 *  ```
 *  With this solution, do not forget to change the build variant to "local" in the IDE.
 */
@DisabledIfBuildConfigValue(named = "CI", matches = "true")
class ScreenshotTest {

    @JvmField
    @RegisterExtension
    val scenarioExtension = ActivityScenarioExtension.launch<ScreenshotTestActivity>()
    lateinit var scenario: ActivityScenario<ScreenshotTestActivity>
    lateinit var device: UiDevice
    // See https://stackoverflow.com/a/46183452
    val shouldSave = InstrumentationRegistry.getArguments().getString("shouldSave", "false").toBoolean()

    @BeforeEach fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        scenario = scenarioExtension.scenario
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test fun chartShouldBeDisplayed() {
        compareScreenshots("screenshot-1") {}
    }

    @Test fun changeSlices() {
        compareScreenshots("screenshot-2") { chart ->
            chart.slices = listOf(
                Slice(0.3f, Color.CYAN),
                Slice(0.2f, Color.YELLOW),
                Slice(0.5f, Color.GREEN)
            )
        }
    }

    @Test fun changeSlicesSuchThatChartLayoutChanges() {
        compareScreenshots("screenshot-3") { chart ->
            chart.slices = listOf(
                Slice(0.3f, Color.CYAN),
                Slice(0.2f, Color.YELLOW),
                Slice(0.1f, Color.GREEN),
                Slice(0.1f, Color.MAGENTA),
                Slice(0.1f, Color.WHITE),
                Slice(0.1f, Color.GRAY),
                Slice(0.1f, Color.LTGRAY)
            )
        }
    }

    @Test fun changeSlicesTwice() {
        compareScreenshots("screenshot-4") { chart ->
            chart.slices = listOf(
                Slice(0.3f, Color.CYAN),
                Slice(0.2f, Color.YELLOW),
                Slice(0.5f, Color.GREEN)
            )
            chart.slices = listOf(
                Slice(0.2f, Color.YELLOW),
                Slice(0.5f, Color.GREEN),
                Slice(0.3f, Color.CYAN)
            )
        }
    }

    @Test fun changeHoleRatio() {
        compareScreenshots("screenshot-5") { chart ->
            chart.holeRatio = 0.67f
        }
    }

    @Test fun changeStartAngle() {
        compareScreenshots("screenshot-6") { chart ->
            chart.startAngle = 121
        }
    }

    @Test fun changeOverlayRatio() {
        compareScreenshots("screenshot-7") { chart ->
            chart.overlayRatio = 0.591f
        }
    }

    @Test fun changeOverlayAlpha() {
        compareScreenshots("screenshot-8") { chart ->
            chart.overlayAlpha = 0.891f
        }
    }

    @Test fun changeGap() {
        compareScreenshots("screenshot-9") { chart ->
            chart.gap = 15.dp
        }
    }

    @Test fun changeLabelsSize() {
        compareScreenshots("screenshot-10") { chart ->
            chart.labelsSize = 17.sp
        }
    }

    @Test fun changeLabelIconsHeight() {
        compareScreenshots("screenshot-11") { chart ->
            chart.slices = listOf(
                Slice(0.3f, Color.CYAN),
                Slice(0.2f, Color.YELLOW, labelIcon = R.drawable.ic_circle),
                Slice(0.5f, Color.GREEN)
            )
            chart.labelIconsHeight = 27.dp
        }
    }

    /**
     * FIXME: the name of the function is misleading. It also works in a saving
     *  mode in that it just saves the screenshot on device and skips the comparison.
     */
    private fun compareScreenshots(screenshotName: String, chartConfig: (chart: PieChart) -> Unit) {
        scenario.onActivity { activity ->
            activity.configureChart { chart ->
                chartConfig(chart)
                val bitmap = takeScreenshot(chart, screenshotName, shouldSave)
                if (!shouldSave) {
                    val reference = loadReferenceScreenshot(screenshotName)
                    assertThat(bitmap.sameAs(reference)).isTrue()
                }
            }
        }
    }

    /**
     * Saving files on the device both requires WRITE permission in the manifest file and also
     * adb install options -g and -r. See the build script for more information.
     *
     * The [Screenshot.capture] method can accept an [Activity] and take a screenshot of it as well.
     *
     * To manually capture a screenshot of the view and save it on the device, do like this:
     * ```
     * val bitmap = Bitmap.createBitmap(
     *     chart.width, /* chart.layoutParams.width */
     *     chart.height, /* chart.layoutParams.height */
     *     Bitmap.Config.ARGB_8888
     * )
     * val canvas = Canvas(bitmap)
     * chart.layout(chart.left, chart.top, chart.right, chart.bottom)
     * chart.draw(canvas)
     *
     * // Save the bitmap as PNG
     * val context = InstrumentationRegistry.getInstrumentation().context
     * val outputStream = File("${Environment.getExternalStorageDirectory()}/pic.png").outputStream()
     * // OR val outputStream = File("${context.filesDir}/pic.png").outputStream()
     * bitmap.compress(Bitmap.CompressFormat.PNG, 50, outputStream)
     * ```
     */
    private fun takeScreenshot(view: View, name: String, shouldSave: Boolean): Bitmap {
        val screenCapture = Screenshot.capture(view)
        if (shouldSave) {
            screenCapture.name = name
            // Saves the screenshot in the device (in Pictures/Screenshots/).
            // The default processor will also append a UUID to the screenshot name.
            screenCapture.process()
        }
        return screenCapture.bitmap
    }

    /**
     * NOTE: the *assets* directory was specified as an assets directory in the build script.
     *
     * Could also have saved the screenshots in *drawable* directory and loaded them like below.
     * See [this post](https://stackoverflow.com/a/9899056).
     * ```
     * val resourceId = ir.mahozad.android.test.R.drawable.myDrawableName
     * val reference = BitmapFactory.decodeResource(context.resources, resourceId)
     * ```
     */
    private fun loadReferenceScreenshot(name: String): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().context
        val reference = context.resources.assets.open("$name.png").use {
            BitmapFactory.decodeStream(it)
        }
        return reference
    }
}
