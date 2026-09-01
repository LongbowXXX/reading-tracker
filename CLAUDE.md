<!-- SPECKIT START -->
技術スタック、プロジェクト構成、開発時のコマンドなどの文脈は、現在の実装計画を参照すること。

- 実装計画: specs/001-reading-shelf-record/plan.md
- 仕様: specs/001-reading-shelf-record/spec.md
- 憲法: .specify/memory/constitution.md
<!-- SPECKIT END -->

<!--
  ここから下は Spec Kit のマーカー外。/speckit-specify や /speckit-plan の後に走る
  agent-context 拡張は上のマーカー間だけを書き換えるため、この領域は保持される。
  マーカー内に手書きしないこと。
-->

## 開発環境

Windows + PowerShell。ラッパは `.\gradlew.bat`（`./gradlew` ではない）。

```powershell
.\gradlew.bat :domain:test                 # ドメイン層のユニットテスト（Android 非依存）
.\gradlew.bat :data:testDebugUnitTest      # データ層のユニットテスト
.\gradlew.bat spotlessCheck                # ktlint 整形チェック
.\gradlew.bat spotlessApply                # ktlint 整形の適用
.\gradlew.bat assembleDebug                # ビルドゲート（各タスク完了時に必須）
.\gradlew.bat installDebug                 # 実機導入（実機確認用）
```

CI 待ちを避けるため `--no-daemon --console=plain` を付けて実行する。

## モジュール構成

- `:domain` — 純 Kotlin。Android への依存を持ち込まない（憲法 原則III / NON-NEGOTIABLE）
- `:data` — Android ライブラリ。永続化と書誌データソース
- `:app` — Android アプリ。UI とカメラ連携

## 作業時の必須事項

- コード内の識別子は英語、コメント・コミットメッセージ・仕様書・応答は日本語（憲法 原則VII）。
- 各タスク完了時に `.\gradlew.bat assembleDebug` が通ることを確認する（憲法 ビルドゲート）。
- ドメインロジックとそのユニットテストを、UI やカメラ連携より先に実装する（憲法 原則III）。
- 実機でしか検証できない項目は「実機確認が必要」と明示し、未確認のまま完了としない（憲法 原則IV）。
- 人間が判断すべき事項を推測で決めない。迷う点は `[NEEDS CLARIFICATION]` として残す。

## Spec Kit のフロー

`/speckit-constitution` → `/speckit-specify` → `/speckit-clarify` → `/speckit-plan` →
`/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`

各フェーズの成果物を生成した時点で**必ず停止して承認を求める**（憲法 フェーズ停止）。
手作業で実装を進めた後は `/speckit-converge` で tasks.md との差分を埋め戻す。
