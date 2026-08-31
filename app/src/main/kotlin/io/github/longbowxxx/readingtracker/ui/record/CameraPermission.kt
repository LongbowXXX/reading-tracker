package io.github.longbowxxx.readingtracker.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * カメラ権限の要求（FR-003 の前提）。
 *
 * 拒否された場合はエラーにせず、**手入力へ落とす**。手入力はバーコード読み取りと同格の
 * 経路であり、権限が無いことは記録できない理由にならない（憲法 原則VI）。
 *
 * @return 呼び出すと、権限を確認したうえで [onGranted] か [onDenied] のいずれかを呼ぶ関数
 */
@Composable
fun rememberCameraPermissionRequest(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onGranted() else onDenied()
        }

    return remember(context, onGranted, onDenied) {
        {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onGranted()
            } else {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}
