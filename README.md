# 漫画喫茶 読書記録アプリ

漫画喫茶で読む漫画の記録と、店舗ごとの棚番号を管理するための Android アプリです。

## 目的

- どの巻まで読んだかを忘れにくくする
- その店で読める続きをすぐ確認する
- 読みたい作品と新刊を管理する
- 店舗ごとの棚番号情報を記録して、来店時に迷わないようにする

## 対応端末

**オンデバイス AI（ML Kit GenAI Prompt API / Gemini Nano）が動作する端末に限ります。**
作品の判別に AI を用いるため、非対応の端末では起動時に警告を表示し、記録・参照のいずれの機能も
利用できません（回避手段は開発ビルドにのみあります）。

- 対応端末の例: Pixel 9 以降、Galaxy S26、Galaxy Z Fold7 など。**Galaxy S25 は Prompt API の対応表に含まれません**
- 対応端末でもモデルが未取得の場合は、起動時に準備が必要である旨と「ダウンロードを開始」の導線を表示します。
  取得には時間と通信量がかかるため、**開始は利用者の操作を起点とし、アプリが自動では始めません**。
  Wi-Fi に接続した状態での実行をおすすめします
- 利用可否は端末側の更新で変わりうるため、**起動のたびに判定します**

判定の詳細は [specs/001-reading-shelf-record/contracts/ai-availability.md](specs/001-reading-shelf-record/contracts/ai-availability.md) を参照してください。

## 要件定義

要件の詳細は [docs/requirements.md](docs/requirements.md) を参照してください。
仕様・計画・タスクは [specs/001-reading-shelf-record/](specs/001-reading-shelf-record/) にあります。

## 開発方針

- Kotlin を中心としたアプリ開発を想定
- 個人利用を前提としたローカルデータ管理
- 店舗別の棚番号情報を巻単位で管理
- バーコード入力や手入力を両対応で運用

開発手法は spec-kit（仕様駆動開発）。プロジェクトの原則は
[.specify/memory/constitution.md](.specify/memory/constitution.md) に定めています。

## 構成

Gradle マルチモジュール。`:domain` は Android フレームワークに依存しない純粋な Kotlin で、
棚番号の継承ロジックなどをここに隔離しています（憲法 原則III）。

| モジュール | 役割 |
| --- | --- |
| `:domain` | ドメインモデル、棚番号の継承、次巻判定、ISBN 検証、ユースケース、port |
| `:data` | Room による永続化、書誌情報の取得（openBD → 国立国会図書館サーチ） |
| `:app` | Jetpack Compose の UI、バーコード読み取り、DI |

## ビルドと検証

```bash
./gradlew :domain:test              # ドメイン層のユニットテスト（エミュレータ不要）
./gradlew :data:testDebugUnitTest   # Room の制約検証（Robolectric）
./gradlew assembleDebug             # ビルド
./gradlew spotlessApply             # コード整形
```

必要環境: JDK 17 / Android SDK（compileSdk 36）。minSdk は 26（Android 8.0）。
実機で動かすには、上記「対応端末」の条件を満たす端末が必要です。

実機での確認手順は [specs/001-reading-shelf-record/quickstart.md](specs/001-reading-shelf-record/quickstart.md) にあります。

## 実装済みの範囲

要求定義書「7. 優先度」の【中核】を対象としています。

| 要求 | 内容 |
| --- | --- |
| A-1〜A-8, A-10 | 読書の記録（バーコード読み取り／ISBN 手入力／暫定名、読了・中断、棚番号の継承、メモ） |
| B-1, B-2, B-3 | 来店時の参照（店舗ごとの読みかけ一覧、棚番号の併記、次に読むべき巻） |
| F-1 の一部 | 記録フロー内での店舗の新規登録のみ（編集・削除は含まない） |

## 未実装の範囲

以下は本スコープ外です。後から追加できるデータ構造にしてありますが、機能は実装していません。

| 要求 | 内容 |
| --- | --- |
| A-9 | 作品単位での棚番号の一括更新（ドメイン関数のみ存在。UI からの導線なし） |
| B-4, B-5 | 棚番号がいつ時点の情報かの提示、一覧からの棚番号の訂正 |
| B-6 | 現在地からの店舗の自動選択 |
| C群 | 読みたい作品の管理 |
| D群 | 新刊の把握（通知、未読巻の一覧） |
| E群 | 作品の「離脱」と復帰 |
| F群 | 店舗の編集・削除、記録の削除、エクスポート、メモの検索 |

## 既知の問題

- [#1](https://github.com/LongbowXXX/reading-tracker/issues/1) バーコード読み取りで下段（日本図書コード）を読むとエラーで終了し、上段を読み直せない
