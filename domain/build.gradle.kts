// :domain は Android フレームワークに依存しない純粋な Kotlin モジュール（憲法 原則III）。
// Android 依存を追加するとコンパイルが通らなくなるため、原則違反が実装時に自動検出される。
// ここに android / androidx / com.google.android のいずれの依存も追加しないこと。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // kotlinx-coroutines-core は純 Kotlin（マルチプラットフォーム）であり Android に依存しない
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
