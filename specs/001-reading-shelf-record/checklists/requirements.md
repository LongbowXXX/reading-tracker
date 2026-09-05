# Specification Quality Checklist: 読書記録と棚番号の管理（中核）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-30
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- **2026-08-30 の再検証で全16項目が合格**（15/16 → 16/16）。`/speckit-clarify` により `[NEEDS CLARIFICATION]` 3件が解消され、追加で2件の未決事項も確定した。
- 解消済みの3件（初回検証時の未達理由）
  - FR-015: 棚番号の継承元となる「直前の巻」の決定基準 → 巻番号順を優先し、該当がなければ記録日時順でフォールバック
  - FR-021: 店舗の登録手段（F-1 がスコープ外だと店舗を作れないというスコープの矛盾） → 記録フロー内の最小限の新規登録のみを本スコープに追加（FR-030, FR-031）
  - FR-023: 中断中の巻がなく次巻が未記録の場合の提示内容 → 読了済み最大巻番号の次の巻番号を示す（実在は判定しない）
- 曖昧性スキャンで追加検出し、併せて確定した2件
  - 作品（シリーズ）の同定方法 → タイトルから巻数表記を除いた文字列による自動照合＋確認画面での修正（FR-027, FR-028）
  - 同一店舗での同一巻の再記録 → 既存記録の編集として扱う（FR-029）
- 状態が変化した項目は `No [NEEDS CLARIFICATION] markers remain` の1件のみ。合格から不合格へ戻った項目（リグレッション）はない。
- 実装詳細の混入について: FR-025 が「外部データソースへの参照」に言及しているが、具体的な技術・製品名は含めておらず、データの所在（端末内）という業務制約の記述に留めている。
- **2026-09-05 の再検証（Issue #9 の追加分）でも全16項目が合格**。FR-032〜FR-035・SC-008・SC-009・Edge Cases 3件・Assumptions 4件の追加、および SC-006 の削除を対象に確認した。`[NEEDS CLARIFICATION]` の残存はなく、リグレッションもない。
  - 要求への遡及: `/speckit-analyze` が「FR-032〜FR-035 が要求 ID を持たない」（憲法 原則I）を検出したため、`docs/requirements.md` を承認のうえ改訂し（3.2 前提条件・3.3 制約・G 群 G-1/G-2）、各 FR を G-1 / G-2 へ紐づけて解消した。
  - 実装詳細の混入について: FR-032〜FR-035 と SC-008・SC-009 は「オンデバイス AI」という語を含むが、これは製品名ではなく**サポート対象端末を定める業務上の前提**（要求定義書 3.2）である。ML Kit・Gemini Nano といった具体の技術名は spec に持ち込んでおらず、[research.md](../research.md) R-008 と [contracts/ai-availability.md](../contracts/ai-availability.md) に留めている。
