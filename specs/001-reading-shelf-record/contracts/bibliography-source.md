# Contract: 書誌情報の取得（BibliographySource）

**Module**: インターフェースは `:domain`、実装は `:data`
**関連**: [research.md](../research.md) R-001, R-002 / FR-006, FR-007

---

## インターフェース

```kotlin
interface BibliographySource {
    suspend fun lookup(isbn: Isbn): BibliographyResult
}

sealed interface BibliographyResult {
    data class Found(val record: BibliographyRecord) : BibliographyResult
    data object NotFound : BibliographyResult          // 経路は生きているが該当なし
    data class Unavailable(val cause: Throwable) : BibliographyResult  // 圏外・タイムアウト・障害
}

data class BibliographyRecord(
    val isbn: Isbn,
    val rawTitle: String,        // 巻数表記を含みうる、取得したままのタイトル
    val author: String?,
    val publisher: String?,
    val publishedDate: String?,  // 粒度が経路により異なるため文字列
    val volumeNumber: Int?,      // 経路が独立項目として返した場合のみ。通常は null
    val sourceName: String,      // "openBD" / "NDL" — 表示ではなく診断用
)
```

**インターフェースがドメイン層にある理由**: 取得経路の差し替え（openBD 単独／NDL 単独／将来の別経路）が UI とドメインへ波及しないようにするため。`:data` 側の実装が変わっても、この契約が変わらなければ上位は影響を受けない。

---

## 契約

| 項目 | 規定 |
| --- | --- |
| 例外 | `lookup` は例外を投げない。通信失敗は `Unavailable` として返す |
| タイムアウト | 1経路あたり 3 秒。超過は `Unavailable` |
| 取得失敗の扱い | `NotFound` / `Unavailable` はいずれも**エラー画面ではなく手入力への遷移**（FR-007）。個室の電波状況（要求定義書 3.3）から、失敗は通常経路として扱う |
| 巻数 | `volumeNumber` は経路が独立項目として返した場合のみ設定する。null のとき、上位は `parseVolumeTitle()`（[domain-api.md](./domain-api.md)）で `rawTitle` から抽出する |
| 改変 | 取得内容は端末内でのみ保持・修正する。外部への再配布は行わない（research.md R-001 の留意点、憲法 原則V） |

---

## 実装: 連鎖（ChainedBibliographySource）

```kotlin
class ChainedBibliographySource(
    private val sources: List<BibliographySource>,  // [openBD, NDL] の順
) : BibliographySource
```

**規則**:

1. 先頭から順に `lookup` する
2. `Found` が返った時点で確定し、以降の経路は呼ばない
3. `NotFound` なら次の経路へ進む
4. `Unavailable` も次の経路へ進む（一方が落ちていても他方で拾える）
5. すべて尽きたとき、1件でも `Unavailable` があれば `Unavailable` を、すべて `NotFound` なら `NotFound` を返す

**全体のタイムアウト**: 2経路合計で 6 秒を上限とする。SC-001（記録完了 30 秒以内）に対する余裕を残す。

---

## 実装: openBD

| 項目 | 内容 |
| --- | --- |
| エンドポイント | `https://api.openbd.jp/v1/get?isbn=<ISBN13>` |
| 形式 | JSON（kotlinx.serialization） |
| 認証 | 不要 |
| 該当なし | 配列要素が `null` で返る。これを `NotFound` に写像する |
| 備考 | 10桁 ISBN は受け付けない。`Isbn` が13桁へ正規化済みであることが前提 |

## 実装: 国立国会図書館サーチ（NDL）

| 項目 | 内容 |
| --- | --- |
| エンドポイント | `https://ndlsearch.ndl.go.jp/api/sru?operation=searchRetrieve&query=isbn=<ISBN>` |
| 形式 | XML / SRU（Android 標準の `XmlPullParser`） |
| 認証 | 不要（個人・非営利で利益を得ない場合は利用申請不要） |
| 該当なし | ヒット件数 0 を `NotFound` に写像する |
| 備考 | API 側が10桁/13桁の双方に変換して完全一致検索する |

**実装時に一次情報で確認すること**: 双方の応答スキーマの実フィールド名、および巻次に相当する項目の有無。research.md の「未解決事項」に記載。

---

## テスト方針

- `ChainedBibliographySource` の連鎖規則は、`BibliographySource` のフェイク実装を並べて `:data` のユニットテストで検証する（ネットワーク不要）。
- 各経路の実装は、記録済みの応答サンプルに対するパースのテストを持つ。**実ネットワークへアクセスするテストは書かない**。
- 実際の疎通は、実機での確認項目とする（[quickstart.md](../quickstart.md)）。
