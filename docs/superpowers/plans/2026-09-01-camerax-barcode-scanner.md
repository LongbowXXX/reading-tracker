# CameraX + ML Kit バーコード読み取り 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 書籍バーコード下段（`192` 始まりの日本図書コード）を読んでもエラーにせず、カメラを開いたまま上段の ISBN が読めるまで読み取りを継続する。

**Architecture:** Google Code Scanner（1回1件で終了する）を捨て、CameraX のプレビューと ML Kit の連続解析による自前実装へ差し替える。採用条件は `Isbn.parse(raw).isSuccess`（接頭辞とチェックディジットを検証）で、満たさない値は無言で破棄して解析を続ける。スキャン画面は専用 Activity とし、`BarcodeScanner` 契約・`ScanResult`・`RecordViewModel`・`:domain` は一切変更しない。

**Tech Stack:** Kotlin / Jetpack Compose / CameraX 1.6.2 / ML Kit barcode-scanning 17.3.0（バンドル版）/ Hilt / Gradle バージョンカタログ

**Spec:** [docs/superpowers/specs/2026-09-01-camerax-barcode-scanner-design.md](../specs/2026-09-01-camerax-barcode-scanner-design.md)

**Issue:** [#1](https://github.com/LongbowXXX/reading-tracker/issues/1)

## Global Constraints

これらは全タスクの要件に暗黙に含まれる。

- **開発環境**: Windows + PowerShell。ラッパは `.\gradlew.bat`（`./gradlew` ではない）。CI 待ちを避けるため全 Gradle 実行に `--no-daemon --console=plain` を付ける。
- **ビルドゲート（憲法）**: 各タスクの完了時に `.\gradlew.bat assembleDebug --no-daemon --console=plain` が通ること。通らないままコミットしない。
- **整形**: コミット前に `.\gradlew.bat spotlessApply --no-daemon --console=plain` を実行し、`spotlessCheck` が通ること。ktlint 1.8.0。
- **言語（憲法 原則VII）**: コード内の識別子は英語。コメント・KDoc・コミットメッセージ・ドキュメントは日本語。
- **モジュール境界（憲法 原則III / NON-NEGOTIABLE）**: `:domain` に `android` / `androidx` / `com.google.android` のいずれの依存も追加しない。本計画では `:domain` を変更しない。
- **契約の不変性**: `domain/src/main/kotlin/io/github/longbowxxx/readingtracker/domain/port/BarcodeScanner.kt`（`BarcodeScanner` と `ScanResult`）と `app/src/main/kotlin/io/github/longbowxxx/readingtracker/ui/record/RecordViewModel.kt` を変更しない。変更が必要に見えたら設計の読み違いなので、進める前に報告すること。
- **依存の版**: CameraX = `1.6.2`、ML Kit barcode-scanning = `17.3.0`。版はバージョンカタログ `gradle/libs.versions.toml` にのみ書き、モジュールの `build.gradle.kts` には書かない。
- **パッケージ**: 本計画で追加するファイルはすべて `io.github.longbowxxx.readingtracker.scanner`（`app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/`）に置く。
- **実機確認（憲法 原則IV）**: カメラの実挙動は自動テストで検証できない。完了報告では「実機確認が必要」を明示し、未確認のまま完了としない。

## ファイル構成

| ファイル | 区分 | 責務 |
| --- | --- | --- |
| `gradle/libs.versions.toml` | 変更 | CameraX / ML Kit の版と座標。`play-services-code-scanner` を削除 |
| `app/build.gradle.kts` | 変更 | 依存の差し替え |
| `app/src/main/kotlin/.../scanner/IsbnSelection.kt` | 新規 | 読み取った生値から採用する ISBN を選ぶ純粋関数。**本 Issue の中核**で、唯一ユニットテストできる部分 |
| `app/src/test/kotlin/.../scanner/IsbnSelectionTest.kt` | 新規 | 上記のテスト。下段を採用しないことを固定する |
| `app/src/main/kotlin/.../scanner/IsbnBarcodeAnalyzer.kt` | 新規 | `ImageAnalysis.Analyzer`。1フレームずつ ML Kit に渡し、採用値が出たときだけ通知する |
| `app/src/main/kotlin/.../scanner/ScanScreen.kt` | 新規 | CameraX のプレビュー、ライフサイクル束縛、トーチ、ガイド表示 |
| `app/src/main/kotlin/.../scanner/ScanActivity.kt` | 新規 | スキャン専用 Activity。結果を ActivityResult として返す |
| `app/src/main/kotlin/.../scanner/BarcodeScannerFactory.kt` | 新規 | `ScanContract` / `CameraXMlKitBarcodeScanner` / `rememberBarcodeScanner()` |
| `app/src/main/AndroidManifest.xml` | 変更 | `ScanActivity` の登録 |
| `app/src/main/kotlin/.../ui/record/RecordScreen.kt` | 変更 | スキャナ生成の1行を差し替え |
| `app/src/main/kotlin/.../scanner/GoogleCodeScannerBarcodeScanner.kt` | 削除 | 差し替えにより未使用 |
| `specs/001-reading-shelf-record/contracts/barcode-scanner.md` | 変更 | 実装セクションの更新 |
| `specs/001-reading-shelf-record/research.md` | 変更 | R-003 の Decision 更新 |

設計ドキュメントからの差分が2点ある。

1. 設計では3ファイルとしていたが、`ScanScreen`（Compose UI）・`IsbnBarcodeAnalyzer`（ML Kit 連携）・`IsbnSelection`（純粋な判定）を分けた。判定を単独の関数に切り出すことで、Issue の受け入れ条件をユニットテストで固定できるようになる。
2. 設計が `RememberBarcodeScanner.kt` としていたファイルを `BarcodeScannerFactory.kt` とした。`ScanContract` と `CameraXMlKitBarcodeScanner` も同居するため、関数名をファイル名にすると中身と合わない。

---

### Task 1: 依存関係の追加

CameraX と ML Kit を追加する。この段階では `play-services-code-scanner` と `GoogleCodeScannerBarcodeScanner` を残し、ビルドが通る状態を保つ。削除は Task 6 で行う。

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: なし
- Produces: `libs.androidx.camera.core` / `libs.androidx.camera.camera2` / `libs.androidx.camera.lifecycle` / `libs.androidx.camera.view` / `libs.mlkit.barcode.scanning`

- [ ] **Step 1: バージョンカタログに版を追加**

`gradle/libs.versions.toml` の `[versions]` にある以下の行

```toml
# バーコード読み取り
playServicesCodeScanner = "16.1.0"
```

を、次で置き換える。

```toml
# バーコード読み取り（R-003 の差し替え先。CameraX のプレビューを維持したまま ML Kit で連続解析する）
playServicesCodeScanner = "16.1.0"
camerax = "1.6.2"
# バンドル版を使う。Play 開発者サービス版はモデルの初回ダウンロードが要るため、
# 圏外の個室で初回スキャンが失敗する（research.md R-003）
mlkitBarcodeScanning = "17.3.0"
```

- [ ] **Step 2: バージョンカタログに座標を追加**

`gradle/libs.versions.toml` の `[libraries]` にある以下の行

```toml
# バーコード読み取り（初期実装。将来 CameraX + ML Kit へ差し替えうる — research.md R-003）
play-services-code-scanner = { group = "com.google.android.gms", name = "play-services-code-scanner", version.ref = "playServicesCodeScanner" }
```

の直後に、次を追加する。

```toml
androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
mlkit-barcode-scanning = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkitBarcodeScanning" }
```

- [ ] **Step 3: app モジュールに依存を追加**

`app/build.gradle.kts` の以下の2行

```kotlin
    // バーコード読み取り（初期実装。差し替え可能なようにインターフェースで抽象化する — research.md R-003）
    implementation(libs.play.services.code.scanner)
```

を、次で置き換える。

```kotlin
    // バーコード読み取り（初期実装。差し替え可能なようにインターフェースで抽象化する — research.md R-003）
    implementation(libs.play.services.code.scanner)

    // バーコード読み取りの差し替え先。プレビューを維持したまま連続解析する（Issue #1）
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
```

- [ ] **Step 4: 依存が解決できることを確認**

Run: `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath --no-daemon --console=plain`
Expected: 成功して終了する。出力に `androidx.camera:camera-view:1.6.2` と `com.google.mlkit:barcode-scanning:17.3.0` が現れる。

- [ ] **Step 5: ビルドゲート**

Run: `.\gradlew.bat spotlessApply assembleDebug --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: コミット**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: CameraX と ML Kit（バンドル版）の依存を追加（Issue #1）"
```

---

### Task 2: 採用する ISBN を選ぶ純粋関数（TDD）

**本 Issue の中核。** 読み取った生値のうち採用できるものを選び、下段や誤読は捨てる。
判定条件を新設せず `Isbn.parse` に委ねる。接頭辞（978/979）に加えてチェックディジットも
検証されるため、下段の読み捨てと誤読の排除が同時に効く。

`app` モジュールにはまだテストソースセットが無いので、ここで新設する。
`app/build.gradle.kts` には `testImplementation(libs.junit4)` が既にあるため、依存の追加は要らない。

**Files:**
- Create: `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/IsbnSelection.kt`
- Test: `app/src/test/kotlin/io/github/longbowxxx/readingtracker/scanner/IsbnSelectionTest.kt`

**Interfaces:**
- Consumes: `io.github.longbowxxx.readingtracker.domain.model.Isbn`（`:domain`、変更しない）
- Produces: `internal fun selectIsbn(rawValues: List<String?>): Isbn?`

- [ ] **Step 1: 失敗するテストを書く**

Create `app/src/test/kotlin/io/github/longbowxxx/readingtracker/scanner/IsbnSelectionTest.kt`:

```kotlin
package io.github.longbowxxx.readingtracker.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 読み取った値のうち何を採用するか（Issue #1）。
 *
 * 書籍バーコードは上段が ISBN、下段が日本図書コード（192 始まりの分類・価格コード）である。
 * 下段を読んでも**採用しない**ことが本 Issue の受け入れ条件であり、ここで固定する。
 */
class IsbnSelectionTest {
    @Test
    fun `上段の ISBN を採用する`() {
        assertEquals("9784088807232", selectIsbn(listOf("9784088807232"))?.value)
    }

    @Test
    fun `下段の日本図書コード（192 始まり）は採用しない`() {
        // 1920979018006 は書籍バーコード下段の分類・価格コード。EAN-13 としては成立する
        assertNull(selectIsbn(listOf("1920979018006")))
    }

    @Test
    fun `上段と下段が同時に読めた場合は上段を採用する`() {
        assertEquals("9784088807232", selectIsbn(listOf("1920979018006", "9784088807232"))?.value)
    }

    @Test
    fun `チェックディジットが一致しない誤読は採用しない`() {
        assertNull(selectIsbn(listOf("9784088807233")))
    }

    @Test
    fun `979 で始まる ISBN も採用する`() {
        assertEquals("9791234567896", selectIsbn(listOf("9791234567896"))?.value)
    }

    @Test
    fun `値が読めなかったフレームでは採用しない`() {
        assertNull(selectIsbn(emptyList()))
        assertNull(selectIsbn(listOf(null)))
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
Expected: コンパイルエラーで FAIL。`Unresolved reference: selectIsbn`

- [ ] **Step 3: 最小の実装を書く**

Create `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/IsbnSelection.kt`:

```kotlin
package io.github.longbowxxx.readingtracker.scanner

import io.github.longbowxxx.readingtracker.domain.model.Isbn

/**
 * 1フレームから読み取れた生値のうち、ISBN として採用できるものを選ぶ。
 *
 * 書籍バーコードは上段が ISBN、下段が日本図書コード（192 始まりの分類・価格コード）である。
 * 下段は EAN-13 として成立しチェックディジットも合うため、接頭辞で弾かなければ価格コードを
 * ISBN として取り込んでしまう（contracts/barcode-scanner.md）。
 *
 * 判定は [Isbn.parse] に委ねる。接頭辞に加えてチェックディジットも検証されるため、
 * 下段の読み捨てと誤読の排除が同時に効く。判定条件をここで二重に定義しない。
 *
 * @return 採用できる ISBN。1つも無ければ null。
 *   **null は失敗ではなく「このフレームでは確定しない」であり、呼び出し側は読み取りを継続する**（Issue #1）
 */
internal fun selectIsbn(rawValues: List<String?>): Isbn? =
    rawValues
        .asSequence()
        .filterNotNull()
        .mapNotNull { Isbn.parse(it).getOrNull() }
        .firstOrNull()
```

- [ ] **Step 4: テストが通ることを確認**

Run: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`。6件すべて PASS

- [ ] **Step 5: ビルドゲート**

Run: `.\gradlew.bat spotlessApply assembleDebug --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: コミット**

```bash
git add app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/IsbnSelection.kt app/src/test/kotlin/io/github/longbowxxx/readingtracker/scanner/IsbnSelectionTest.kt
git commit -m "feat: 読み取った値から採用する ISBN を選ぶ判定を追加（Issue #1）"
```

---

### Task 3: ImageAnalysis のアナライザ

1フレームずつ ML Kit に渡し、Task 2 の判定を通った値だけを通知する。
**採用できない値では何も起こさない**（通知もエラーもしない）ことが本クラスの要件である。

`ImageProxy.image` の参照には `@androidx.camera.core.ExperimentalGetImage` が要る。

**Files:**
- Create: `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/IsbnBarcodeAnalyzer.kt`

**Interfaces:**
- Consumes: `selectIsbn(rawValues: List<String?>): Isbn?`（Task 2）
- Produces: `class IsbnBarcodeAnalyzer(scanner: com.google.mlkit.vision.barcode.BarcodeScanner, onIsbn: (Isbn) -> Unit) : ImageAnalysis.Analyzer`

- [ ] **Step 1: アナライザを実装**

Create `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/IsbnBarcodeAnalyzer.kt`:

```kotlin
package io.github.longbowxxx.readingtracker.scanner

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import io.github.longbowxxx.readingtracker.domain.model.Isbn
import com.google.mlkit.vision.barcode.BarcodeScanner as MlKitBarcodeScanner

/**
 * カメラの各フレームを解析し、採用できる ISBN が読めたときだけ [onIsbn] を呼ぶ。
 *
 * **採用できない値は無言で捨て、解析を続ける**（Issue #1）。書籍バーコード下段の
 * 日本図書コード（192 始まり）を読んでも、エラーを出さず読み取りを止めない。
 * 何が採用できるかの判定は [selectIsbn] が持つ。
 *
 * [onIsbn] はカメラの解析スレッドから呼ばれる。UI の更新は呼び出し側でメインスレッドへ移すこと。
 * 複数フレームで成立しうるため、**[onIsbn] は複数回呼ばれうる**。1度だけ扱う責任は呼び出し側にある。
 *
 * @param scanner ML Kit のバーコード検出器。生成と破棄は呼び出し側が持つ
 */
class IsbnBarcodeAnalyzer(private val scanner: MlKitBarcodeScanner, private val onIsbn: (Isbn) -> Unit) :
    ImageAnalysis.Analyzer {
    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner
            .process(image)
            .addOnSuccessListener { barcodes ->
                selectIsbn(barcodes.map { it.rawValue })?.let(onIsbn)
            }.addOnCompleteListener {
                // 次のフレームを受け取るために必ず閉じる。閉じ忘れると解析が止まる
                imageProxy.close()
            }
    }
}
```

- [ ] **Step 2: ビルドゲート**

Run: `.\gradlew.bat spotlessApply assembleDebug --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`

自動テストは書かない。`ImageProxy` はカメラ実機のフレームであり、エミュレータでも組み立てられない（憲法 原則IV、契約のテスト方針）。テストできる判定部分は Task 2 で切り出し済みである。

- [ ] **Step 3: コミット**

```bash
git add app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/IsbnBarcodeAnalyzer.kt
git commit -m "feat: フレームを連続解析するアナライザを追加（Issue #1）"
```

---

### Task 4: スキャン画面（CameraX プレビュー・トーチ・ガイド）

CameraX をライフサイクルに束縛し、プレビューと連続解析を動かす。
トーチ切替ボタンと「上段のバーコードに向けてください」のガイドを置く。

**Files:**
- Create: `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/ScanScreen.kt`

**Interfaces:**
- Consumes: `IsbnBarcodeAnalyzer(scanner, onIsbn)`（Task 3）
- Produces: `@Composable fun ScanScreen(onScanned: (Isbn) -> Unit, onCameraUnavailable: (Throwable) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: スキャン画面を実装**

Create `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/ScanScreen.kt`:

```kotlin
package io.github.longbowxxx.readingtracker.scanner

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.longbowxxx.readingtracker.domain.model.Isbn
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * バーコード読み取り画面（FR-001）。
 *
 * プレビューを維持したまま連続解析し、上段の ISBN が読めた時点で [onScanned] を呼ぶ。
 * 下段の日本図書コードを読んでも**何も起こらない**。エラーも出さず、カメラも閉じない（Issue #1）。
 *
 * 手入力への切り替えは戻る操作で行う。呼び出し元がキャンセルとして受け取り、
 * エラーを出さずに手入力へ落とす（FR-003、憲法 原則VI）。
 *
 * @param onScanned 採用できる ISBN が読めた。**1度しか呼ばれない**
 * @param onCameraUnavailable カメラを起動できなかった。呼び出し側は手入力へ落とす
 */
@Composable
fun ScanScreen(onScanned: (Isbn) -> Unit, onCameraUnavailable: (Throwable) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    // 複数フレームで成立しうるため、確定は1度だけに絞る
    val alreadyScanned = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val barcodeScanner =
            BarcodeScanning.getClient(
                BarcodeScannerOptions
                    .Builder()
                    // 書籍バーコードは EAN-13。他のシンボルは解析対象にしない（契約 対象シンボル）
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13)
                    .build(),
            )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                boundProvider = provider

                val preview =
                    Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val analysis =
                    ImageAnalysis
                        .Builder()
                        // 解析が追いつかないフレームは捨てる。遅延を溜めない
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                analysis.setAnalyzer(
                    analysisExecutor,
                    IsbnBarcodeAnalyzer(barcodeScanner) { isbn ->
                        if (alreadyScanned.compareAndSet(false, true)) {
                            mainExecutor.execute { onScanned(isbn) }
                        }
                    },
                )

                provider.unbindAll()
                camera =
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
            } catch (e: Exception) {
                // カメラを開けない理由は端末とタイミングに依る。契約どおり例外は投げず、
                // 呼び出し側が手入力へ落とせるように通知する
                onCameraUnavailable(e)
            }
        }, mainExecutor)

        onDispose {
            boundProvider?.unbindAll()
            analysisExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    // トーチは束縛が済んでからでないと操作できない
    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 背景はカメラの映像であり明るさが読めない。テーマ色ではなく、
                // 自前の暗幕と白文字で可読性を確保する
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(24.dp),
        ) {
            // 下段を読んでも無反応になるため、なぜ確定しないのかを伝える（憲法 原則VI）
            Text(
                text = "上段のバーコード（978 で始まる ISBN）に向けてください。下段の価格コードは読み取りません。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )

            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                Button(
                    onClick = { torchOn = !torchOn },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text(if (torchOn) "ライトを消す" else "ライトを点ける")
                }
            }
        }
    }
}
```

- [ ] **Step 2: ビルドゲート**

Run: `.\gradlew.bat spotlessApply assembleDebug --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`

`LocalLifecycleOwner` は `androidx.lifecycle.compose` のものを使う。`app/build.gradle.kts` に
`libs.androidx.lifecycle.runtime.compose` が既にあるため依存の追加は要らない。
`androidx.compose.ui.platform.LocalLifecycleOwner` を import すると非推奨の警告が出るので使わないこと。

- [ ] **Step 3: コミット**

```bash
git add app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/ScanScreen.kt
git commit -m "feat: CameraX によるスキャン画面を追加（Issue #1）"
```

---

### Task 5: スキャン Activity と BarcodeScanner 実装

スキャン画面を Activity として起動し、結果を `ScanResult` へ変換する。
`suspend fun scan(): ScanResult` というポートの形を変えないことが要点である。

**Files:**
- Create: `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/ScanActivity.kt`
- Create: `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/BarcodeScannerFactory.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ScanScreen(onScanned, onCameraUnavailable, modifier)`（Task 4）
- Produces:
  - `class ScanActivity : ComponentActivity`、`ScanActivity.EXTRA_ISBN: String`、`ScanActivity.RESULT_CAMERA_UNAVAILABLE: Int`
  - `@Composable fun rememberBarcodeScanner(): BarcodeScanner`

- [ ] **Step 1: Activity を実装**

Create `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/ScanActivity.kt`:

```kotlin
package io.github.longbowxxx.readingtracker.scanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

/**
 * バーコード読み取り専用の全画面 Activity。
 *
 * 結果は ActivityResult として返す。呼び出し側は [CameraXMlKitBarcodeScanner] 越しに
 * `BarcodeScanner` としてのみ触れるため、この Activity は UI 層とドメイン層から見えない。
 *
 * 戻る操作は `RESULT_CANCELED` になる。呼び出し側はエラーを出さず手入力へ落とす（FR-003）。
 */
class ScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScanScreen(
                        onScanned = { isbn ->
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_ISBN, isbn.value))
                            finish()
                        },
                        onCameraUnavailable = {
                            setResult(RESULT_CAMERA_UNAVAILABLE)
                            finish()
                        },
                    )
                }
            }
        }
    }

    companion object {
        /** 読み取れた ISBN（13桁・ハイフンなし）を載せる Intent の extra キー。 */
        const val EXTRA_ISBN = "io.github.longbowxxx.readingtracker.scanner.ISBN"

        /** カメラを起動できなかった。呼び出し側は手入力へ落とす（FR-003）。 */
        const val RESULT_CAMERA_UNAVAILABLE = Activity.RESULT_FIRST_USER
    }
}
```

- [ ] **Step 2: BarcodeScanner 実装と Composable ファクトリを実装**

Create `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/BarcodeScannerFactory.kt`:

```kotlin
package io.github.longbowxxx.readingtracker.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.longbowxxx.readingtracker.domain.port.BarcodeScanner
import io.github.longbowxxx.readingtracker.domain.port.ScanResult
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/** [ScanActivity] を起動し、その結果を [ScanResult] へ変換する。 */
class ScanContract : ActivityResultContract<Unit, ScanResult>() {
    override fun createIntent(context: Context, input: Unit): Intent = Intent(context, ScanActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): ScanResult = when (resultCode) {
        Activity.RESULT_OK -> {
            val raw = intent?.getStringExtra(ScanActivity.EXTRA_ISBN)
            if (raw.isNullOrBlank()) {
                ScanResult.Unavailable(IOException("バーコードの値を読み取れませんでした"))
            } else {
                ScanResult.Scanned(raw)
            }
        }

        ScanActivity.RESULT_CAMERA_UNAVAILABLE -> ScanResult.Unavailable(IOException("カメラを起動できませんでした"))

        // 戻る操作。エラーではなく、手入力への切り替えとして扱う（FR-003）
        else -> ScanResult.Cancelled
    }
}

/**
 * CameraX + ML Kit による読み取り（research.md R-003 の差し替え先）。
 *
 * プレビューを維持したまま連続解析し、978/979 で始まる ISBN が読めた時点で確定する。
 * 書籍バーコード下段（192 始まりの日本図書コード）を読んでも**エラーにせず読み取りを続ける**
 * （contracts/barcode-scanner.md 対象シンボル、Issue #1）。
 *
 * スキャン画面は Activity として起動するため、実際の起動は [rememberBarcodeScanner] が
 * 用意した関数に委ねる。この分離により `suspend fun scan(): ScanResult` という
 * ポートの形を変えずに済み、UI 層とドメイン層へ差し替えが波及しない。
 */
class CameraXMlKitBarcodeScanner(private val launchScan: (CompletableDeferred<ScanResult>) -> Unit) : BarcodeScanner {
    override suspend fun scan(): ScanResult {
        val result = CompletableDeferred<ScanResult>()
        launchScan(result)
        return result.await()
    }
}

/**
 * [BarcodeScanner] を Compose のライフサイクルに載せて組み立てる。
 *
 * Activity の起動には `ActivityResultLauncher` が要り、これは Composable が保持しなければ
 * ならない。launcher の結果で [CompletableDeferred] を完了させることで、
 * 呼び出し側からは suspend 関数1つに見せる。
 */
@Composable
fun rememberBarcodeScanner(): BarcodeScanner {
    // 起動と結果受け取りの間で待っている呼び出しを保持する
    val pending = remember { AtomicReference<CompletableDeferred<ScanResult>?>(null) }

    val launcher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            pending.getAndSet(null)?.complete(result)
        }

    return remember(launcher) {
        CameraXMlKitBarcodeScanner { deferred ->
            pending.set(deferred)
            launcher.launch(Unit)
        }
    }
}
```

- [ ] **Step 3: Activity をマニフェストに登録**

`app/src/main/AndroidManifest.xml` の `MainActivity` の `</activity>` の直後、`</application>` の直前に次を追加する。

```xml

        <!-- バーコード読み取り（Issue #1）。アプリ内からのみ起動する -->
        <activity
            android:name=".scanner.ScanActivity"
            android:exported="false"
            android:theme="@style/Theme.ReadingTracker" />
```

- [ ] **Step 4: ビルドゲート**

Run: `.\gradlew.bat spotlessApply assembleDebug --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: コミット**

```bash
git add app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/ScanActivity.kt app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/BarcodeScannerFactory.kt app/src/main/AndroidManifest.xml
git commit -m "feat: スキャン Activity と CameraX 版 BarcodeScanner を追加（Issue #1）"
```

---

### Task 6: 差し替えと旧実装の削除

呼び出し側を新実装へ切り替え、Google Code Scanner を消す。
契約書が「差し替えは1行」と述べていたとおり、`RecordScreen` の変更は生成箇所のみである。

**Files:**
- Modify: `app/src/main/kotlin/io/github/longbowxxx/readingtracker/ui/record/RecordScreen.kt`
- Delete: `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/GoogleCodeScannerBarcodeScanner.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: `rememberBarcodeScanner(): BarcodeScanner`（Task 5）
- Produces: なし

- [ ] **Step 1: RecordScreen のスキャナ生成を差し替え**

`app/src/main/kotlin/io/github/longbowxxx/readingtracker/ui/record/RecordScreen.kt` の import 行

```kotlin
import io.github.longbowxxx.readingtracker.scanner.GoogleCodeScannerBarcodeScanner
```

を、次で置き換える。

```kotlin
import io.github.longbowxxx.readingtracker.scanner.rememberBarcodeScanner
```

続けて、以下の3行

```kotlin
    // スキャン UI は Activity を起動するため Activity のコンテキストを渡す。
    // 差し替え（CameraX + ML Kit）はこの1行を変えるだけで済む（research.md R-003）
    val scanner = remember(context) { GoogleCodeScannerBarcodeScanner(context) }
```

を、次で置き換える。

```kotlin
    // CameraX + ML Kit の自前実装。下段（192 始まり）を読んでも読み取りを止めない（Issue #1）
    val scanner = rememberBarcodeScanner()
```

- [ ] **Step 2: 未使用になった import を確認して整理**

`context` は `rememberCameraPermissionRequest` が内部で `LocalContext` を自前で取るため、
`RecordScreen` 内で他に使われていなければ `val context = LocalContext.current` と
`import androidx.compose.ui.platform.LocalContext` を削除する。
`remember` も他に使われていなければ `import androidx.compose.runtime.remember` を削除する。

Run: `.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`。未使用 import の警告が出ないこと

- [ ] **Step 3: 旧実装を削除**

```bash
git rm app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/GoogleCodeScannerBarcodeScanner.kt
```

- [ ] **Step 4: play-services-code-scanner の依存を削除**

`app/build.gradle.kts` から次の2行を削除する。

```kotlin
    // バーコード読み取り（初期実装。差し替え可能なようにインターフェースで抽象化する — research.md R-003）
    implementation(libs.play.services.code.scanner)
```

`gradle/libs.versions.toml` の `[versions]` から次の1行を削除する。

```toml
playServicesCodeScanner = "16.1.0"
```

`gradle/libs.versions.toml` の `[libraries]` から次の2行を削除する。

```toml
# バーコード読み取り（初期実装。将来 CameraX + ML Kit へ差し替えうる — research.md R-003）
play-services-code-scanner = { group = "com.google.android.gms", name = "play-services-code-scanner", version.ref = "playServicesCodeScanner" }
```

- [ ] **Step 5: 旧実装への参照が残っていないことを確認**

Run: `git grep -n "GoogleCodeScanner\|play-services-code-scanner\|playServicesCodeScanner\|play.services.code.scanner" -- app gradle`
（`specs/` と `docs/` は Task 7 で扱うので対象外）
Expected: 出力なし

- [ ] **Step 6: 全テストとビルドゲート**

Run: `.\gradlew.bat spotlessApply --no-daemon --console=plain`
次に Run: `.\gradlew.bat :domain:test :data:testDebugUnitTest :app:testDebugUnitTest spotlessCheck assembleDebug --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`。既存のドメイン・データ層のテストがすべて PASS

- [ ] **Step 7: コミット**

```bash
git add -A app gradle
git commit -m "refactor: バーコード読み取りを CameraX + ML Kit 実装へ差し替え（Issue #1）"
```

---

### Task 7: 契約と research の更新

実装が契約に追いついたことと、R-003 の判断が更新されたことを仕様側に反映する。

**Files:**
- Modify: `specs/001-reading-shelf-record/contracts/barcode-scanner.md`
- Modify: `specs/001-reading-shelf-record/research.md`

**Interfaces:**
- Consumes: なし
- Produces: なし

- [ ] **Step 1: 契約の実装セクションを差し替え**

`specs/001-reading-shelf-record/contracts/barcode-scanner.md` の
「## 実装: GoogleCodeScannerBarcodeScanner（初期実装）」から
「## 差し替え候補: CameraXMlKitBarcodeScanner（未実装）」の節の末尾までを、次で置き換える。

```markdown
## 実装: CameraXMlKitBarcodeScanner

- CameraX のプレビューと ML Kit の解析を自前で組み、**プレビューを維持したまま連続解析する**。
- 採用条件は `Isbn.parse(rawValue).isSuccess`。接頭辞（978/979）とチェックディジットの両方を検証するため、下段の日本図書コード（`192` 始まり）と誤読を同時に排除できる。判定条件を実装側で二重に定義しない。
- **採用できない値は無言で捨て、読み取りを続ける**。エラーを出さず、カメラも閉じない（Issue #1）。
- ML Kit はバンドル版（`com.google.mlkit:barcode-scanning`）を用いる。モデルの初回ダウンロードが不要であり、圏外の個室でも初回から読み取れる。
- トーチ（ライト）の切替ボタンを持つ。暗所での効果は実機で確認する。
- スキャン画面は専用の `ScanActivity` として起動し、結果を `ActivityResult` で返す。`suspend fun scan()` の形は `CompletableDeferred` で保つ。

## 採用しなかった実装: GoogleCodeScannerBarcodeScanner

`play-services-code-scanner` のスキャン UI をそのまま用いる初期実装。実装量は小さかったが、`startScan()` 1回につき1件返して終了するため、**下段を読み捨てて読み取りを継続できない**。上の対象シンボルの規定を満たせず、差し替えた（Issue #1）。

Google Play 開発者サービス経由のモジュール配信を必要とする点も、オフラインの個室という利用環境に合わなかった。
```

- [ ] **Step 2: 契約の実機確認項目を更新**

同ファイルの「## 実機での確認が必要な項目（憲法 原則IV）」の箇条書きを、次で置き換える。

```markdown
- 下段のバーコードを読んでもカメラが閉じず、エラーも出ないこと（Issue #1 の受け入れ条件）
- 下段に向けた状態から上段へずらすと確定すること
- 個室の照明環境（暗所）での読み取り可否と所要時間、およびトーチの効果
- 棚番号シールがバーコードを覆う頻度（要求定義書 9. 未確定事項）。頻度が高い場合、手入力を主導線へ組み替える判断が必要になる
- 片手保持での読み取り成功率
- 戻る操作で、エラーを出さずに手入力へ落ちること（FR-003, SC-002）
- オフライン状態での初回スキャン（バンドル版 ML Kit の効果確認）
```

- [ ] **Step 3: research.md の R-003 を更新**

`specs/001-reading-shelf-record/research.md` の R-003 の **Decision** 行

```markdown
**Decision**: 初期実装は Google Code Scanner（`play-services-code-scanner`）を用いる。読み取り部分は `BarcodeScanner` インターフェースで抽象化し、CameraX + ML Kit の自前実装へ差し替えられる構造とする。
```

を、次で置き換える。

```markdown
**Decision**: CameraX + ML Kit（バンドル版 `com.google.mlkit:barcode-scanning`）の自前実装を用いる。読み取り部分は `BarcodeScanner` インターフェースで抽象化する。

**更新（2026-09-01, Issue #1）**: 当初は Google Code Scanner（`play-services-code-scanner`）を初期実装として採用したが、`startScan()` 1回につき1件返して終了する仕様のため、書籍バーコード下段（`192` 始まりの日本図書コード）を読み捨てて読み取りを継続できなかった。契約が定めた対象シンボルの規定を満たせず、ここで想定していた差し替えを実施した。インターフェース境界を先に引いていたため、差し替えは `RecordScreen` のスキャナ生成1行に収まり、UI とドメインへ波及しなかった。
```

- [ ] **Step 4: 記述の整合を確認**

Run: `git grep -n "Google Code Scanner\|play-services-code-scanner" -- specs`
Expected: 出力に現れるのは「採用しなかった実装」「更新（2026-09-01, Issue #1）」および Alternatives の表など、**経緯として過去形で書かれた箇所のみ**。現行実装として説明している箇所が残っていたら直す。

- [ ] **Step 5: コミット**

```bash
git add specs/001-reading-shelf-record/contracts/barcode-scanner.md specs/001-reading-shelf-record/research.md
git commit -m "docs: 契約と R-003 を CameraX + ML Kit 実装に合わせて更新（Issue #1）"
```

---

### Task 8: tasks.md への埋め戻しと実機確認の依頼

**Files:**
- Modify: `specs/001-reading-shelf-record/tasks.md`

- [ ] **Step 1: tasks.md へ埋め戻す**

`/speckit-converge` を実行し、本計画で実装した内容を `tasks.md` の差分として埋め戻す。

- [ ] **Step 2: 実機確認を依頼する**

以下を**未確認**として報告する。自動テストでは検証できず、実機でしか確かめられない（憲法 原則IV）。
**確認が取れるまで Issue を閉じない。**

1. 下段のバーコードを読んでもカメラが閉じず、エラーも出ないこと（**Issue #1 の受け入れ条件**）
2. 下段に向けた状態から上段へずらすと確定すること
3. トーチの点灯・消灯が効き、暗所で読み取れること
4. 片手保持での読み取り成功率と、Google Code Scanner と比べた体感
5. 戻る操作で、エラーを出さずに手入力へ落ちること
6. オフライン状態での初回スキャンが成功すること（バンドル版 ML Kit の効果）

導入コマンド: `.\gradlew.bat installDebug --no-daemon --console=plain`

- [ ] **Step 3: コミット**

```bash
git add specs/001-reading-shelf-record/tasks.md
git commit -m "docs: CameraX 差し替えの実施内容を tasks.md へ埋め戻す（Issue #1）"
```
