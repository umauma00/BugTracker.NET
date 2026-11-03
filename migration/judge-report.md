# 00b — Judge setup report (gate)

- 日付: 2026-11-03
- 成果物: `judge/`（Maven、Java 21、JUnit 6 + jsoup + mssql-jdbc + Jackson）
- 方式: 逸脱 D-001 のもとで実施。元アプリは動かせないため、期待値はすべて元ソースから書き起こし、各テストに `@SourceRef` を付けた。

## 1. 分類（Categorize）

| 区分 | 件数 | 根拠 |
|---|---|---|
| 移植可能（portable） | **0** | テストプロジェクトがない（`src/BugTracker.NET.sln`）|
| 内部依存（internal-bound） | **0** | 同上。`nunit` の検索ヒットは全件 `tk_duration_units` の時間単位文字列で、テストではない |

既存テストがないので、判定器は新しく書いた**利用シナリオ**だけでできている。

## 2. 判定器（Rewrite → scenarios）

| シナリオ | テスト数 | 対象 |
|---|---|---|
| S1 ログイン・ログオフ・権限 | 17 | `default.aspx`, `logoff.aspx`, `api/login`, ロックアウト |
| S2 バグ一覧・クエリ API | 12 | `bugs.aspx`, `api/BugQuery` |
| S3 バグ作成・編集・コメント | 9 | `edit_bug.aspx` |
| S4 添付 | 10 | `add_attachment.aspx`, `view_attachment.aspx`（内容をバイト単位で比較）|
| S5 メールからのバグ登録 | 8 | `api/BugFromEmail` |
| S6 管理画面の作成・編集・削除 | 17 | ユーザー・プロジェクト・カテゴリ |
| S7 印刷・エクスポート | 9 | `print_bug(s)(2).aspx`, CSV エクスポート |
| S8 通知キュー | 13 | `queued_notifications`, `notifications.aspx` |
| S9 検索（SQL） | 9 | `search.aspx`, `search_text.aspx` |
| **計** | **104** ＋ 自己チェック 1 | |

- 比較するのは**データ**（id で指定した要素のテキスト・表のセル・フォームの値・リダイレクト先・HTTP ステータス・DB の行）で、マークアップは比べない。理由は、画面を Thymeleaf で作り直すため。
- 移行先が守るべき約束（URL・フィールドの論理名・要素 id・JSON の形・DB への書き込み）は `judge/README.md` の「Judge contract」にまとめた。Step 1 でルールブックに取り込む。
- 自己チェック `JudgeSelfCheckTest` は、各テストに引用があること、引用先ファイルが実在すること、引用した行がファイル内に収まっていることを確かめる。

### シードの補正（判定器側の判断、要承認）

| # | 元のシードの問題 | 補正 |
|---|---|---|
| P1 | `setup.sql:168` が IDENTITY 列に `us_id` を直接 INSERT している（IDENTITY_INSERT なし）→ admin 行が作られない | その1文だけを `IDENTITY_INSERT ON/OFF` で囲む |
| P2 | EF が対応付ける `queries.qu_columns` を setup.sql が作らない → `queries` の読み出しがすべて失敗する | DB プロジェクトの定義どおりに列を追加し、`Script.PostDeployment.sql` を実行する |

## 3. レビュー（独立した2人、Opus 5.5・エフォート中、担当範囲は重ならない）

| レビュー担当 | 担当範囲 | 重大 | 中 | 軽微 |
|---|---|---|---|---|
| A | S1, S3, S5, S7, core | 1 | 2 | 6 |
| B | S2, S4, S6, S8, S9, MUTATIONS | 1 | 3 | 3 |

指摘のうち確認できたものは**すべて修正済み**（修正担当: Sonnet 5.5・エフォート中）。主なもの:

- **重大×2**：S3 の新規作成と S4 のアップロードで、ASP.NET のセッション ID が確定しないまま POST していた（`ViewStateUserKey = Session.SessionID`、`edit_bug.aspx.cs:32` / `add_attachment.aspx.cs:15`）。このままだと、正しい元アプリでも 500 になる。→ 共通の準備処理 `primeAspNetSession` を `Scenario` に移し、該当テストで使うようにした。
- **中**：見逃しうる改変が3件あった（上限ちょうどの判定、Content-Type の推測、非管理者の認可）。上限ちょうどと Content-Type はテストを追加した。非管理者の認可は、元アプリで非管理者がログインできないため検証不能と明記した。ほかに、コメントのみの更新で列の確認が漏れていたもの、ログイン時刻の確認が緩かったものを直した。
- **軽微**：行番号が誤った引用が44件 → すべて直し、以後は自己チェックで止まるようにした。

## 4. 確かめ方（Validate）— 実施できず（D-001）

| 確認 | 結果 |
|---|---|
| 元アプリで N/N パス | **未実施**：Windows/IIS がない |
| 壊した元アプリで失敗するか | **未実施**：同上 |
| SQL Server でのシード読み込み | **未実施**：この環境では SQL Server を入れられなかった（ネットワーク制限で `pmc-geofence.trafficmanager.net` が 403）|
| コンパイルと自己チェック | **済**：`mvn -B test` → 105件中、自己チェック1件がパス、シナリオ104件はスキップ（接続先なし）|
| 改変の一覧 | `judge/specs/MUTATIONS.md` に27件。各改変で失敗するべきテストを指定済み。Step 5 で移行先に適用して確かめる |

## 5. 元のコードで見つかった重大な欠陥（`@Inherited`、30件）

このリポジトリの元アプリ（2015年に改修途中で止まったもの）は、**主な機能がソース上すでに壊れている**。

| 欠陥 | 場所 | 影響 |
|---|---|---|
| 編集画面のボタンで保存されない（`user_hit_submit` 要素がない） | `edit_bug.aspx.cs:144`、`Scripts/Site/EditBug.js:189` | ブラウザからのバグ作成・更新ができない |
| 項目を変更すると履歴の INSERT が構文エラーになる（`)` が欠落） | `edit_bug.aspx.cs:1748` | 更新は 500 で終わり、履歴が残らない |
| 通知キューの INSERT が構文エラー（`N''not sent'`） | `btnet/bug.cs:1183` | 購読者がいるバグを編集すると 500 になる |
| 管理者以外はログインできない | `Security/Security.cs:117`、ソルトが NULL のシード | ログインすると 500 |
| ログインのたびに admin のパスワードが `admin` に書き換わる | `Security/Authenticate.cs:215` | セキュリティ上の欠陥 |

キットの原則（旧コードが仕様、欠陥も含めて再現し、直すのは工程06）に従い、判定器はこれらを**そのまま再現すべき動作として固定**している。ただ、これは「動かない機能を Java で動かないまま再現する」ことを意味する。人の判断が必要（§6）。

## 6. ゲートで決めること

1. **シード補正 P1・P2 を承認するか**
2. **元アプリの欠陥（§5）の扱い**
   - (a) キットどおり再現し、工程06で個別に直す
   - (b) 主な欠陥は「直した動作」を仕様にする。判定器の該当テストを、修正後の期待値に書き換える
3. **SQL Server の用意**：判定器も移行先アプリも SQL Server がないと動かない。環境のネットワーク許可に `pmc-geofence.trafficmanager.net` を追加するか、別の SQL Server を用意するか
4. **判定器が本物だと承認するか**（キットの 00b のゲート）：元アプリでの検証は D-001 により先送り
