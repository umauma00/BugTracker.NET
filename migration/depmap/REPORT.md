# 01 — Dependency map report (gate)

- 日付: 2026-11-03
- スクリプト: `migration/scripts/depmap_csharp.py`（tree-sitter-c-sharp 0.23.5、決定的・実行約1秒）
- 回帰テスト: `migration/scripts/test_depmap_csharp.sh`（フィクスチャの期待辺＋本番ツリーで2回実行してバイト一致）
- 再実行:
  ```bash
  migration/scripts/.venv/bin/python -I migration/scripts/depmap_csharp.py --repo . --out migration/depmap
  ```

## 件数（`wc -l` から。ヘッダー行を除く）

| 項目 | 件数 |
|---|---|
| 走査したファイル | 455 |
| ユニット | **217**（aspx 117 / ascx 3 / Master 2 / asax 1 / inc 1 / cs 93）|
| 辺 | **1,025** |
| バッチ（order.txt） | **213**（1ユニット 209、2ユニット 4）|
| ファイル単位の循環 | **4**（すべて2ユニット）|
| パッケージ | 10（C# の名前空間 = Java パッケージ、1対1）|
| パッケージ単位の循環 | **1**（8パッケージ）|
| 作業一覧（manifest）の行 | **217**（`wc -l migration/manifest.tsv` = 218、ヘッダー込み）|

辺の根拠の内訳：型の位置 T1 388、式の位置 T2 464、拡張メソッド T3 51、`#include` M6 56、`MasterPageFile` M1 55、`Register Src` M3 10、`Inherits` M5 1。

依存先として多いもの：`btnet/util.cs` 133、`BasePage.cs` 116、`btnet/SQLString.cs` 102、`btnet/dbutil.cs` 99、`Security/BtnetRoles.cs` 65。

## 循環

**ファイル単位（`cycles.txt`）**：それぞれ1バッチとして一緒に移行する。

| # | ユニット | 理由 |
|---|---|---|
| 1 | `btnet/dbutil.cs` ⇄ `btnet/util.cs` | 相互に静的メソッドを呼んでいる |
| 2 | `App_Start/Startup.Auth.cs` ⇄ `Startup.cs` | 同じ partial class `btnet.Startup` → **Java では1クラスにまとめる** |
| 3 | `Mail/email.cs` ⇄ `btnet/bug.cs` | 相互に呼んでいる |
| 4 | `Models/BugTask.cs` ⇄ `Models/bug.cs` | EF のナビゲーションプロパティ |

**パッケージ単位（`package_cycles.txt`）**：`btnet`, `btnet.App_Start`, `btnet.Controls`, `btnet.Mail`, `btnet.Models`, `btnet.Models.Mapping`, `btnet.Search`, `btnet.Security` の8つが1つの強連結成分になっている。

- **影響**：Java のコンパイラは、1つのモジュール内のパッケージ間の循環を禁止しない。そのため、移行先を**1つの Maven モジュール**（`port/web`）にすればビルドは止まらない。
- 複数の Maven モジュールに分けると、この循環がそのままビルドエラーになる。→ ルールブック（工程02）で「web は1モジュール」と決めることを提案する。
- `btnet_service` は別プロジェクト（`port/service`）で、web とのあいだに辺はない。

## レビュー（第1ラウンド、Opus 5.5・エフォート中、互いに独立）

| レビュー担当 | 抜き取り | ソースから書き出した辺 | 見落とし | でっち上げ |
|---|---|---|---|---|
| A | 無作為10（前半）＋最大・最複雑10（`edit_bug.aspx`, `util.cs`, `bug.cs`, `print_bug.cs`, `version_control.cs`, `bug_list.cs`, `search.aspx`, `edit_user.aspx`, `Context.cs`, `POP3Main.cs`） | 137 | **0** | **0** |
| B | 無作為10（後半）＋生成コード・designer・特殊な結び付け・範囲外の確認10（`TranslationService.cs`, `LoggedOut.Master`, `inc_diff.inc`, `bugs.aspx`, `BugList.ascx`, `WebApiConfig.cs`, `queryMap.cs`, `btnet_service.cs`, `translate.aspx`, `svn_diff.aspx`） | 105 | **0** | **0** |

**第1ラウンドで両方向とも食い違いなし（clean）。** レビュー担当は地図とスクリプトを見ずに辺を書き出し、突き合わせは `migration/scripts/depmap_diff.py` で行った。

レビューの前に、手作業の試し（5ユニット超）でスクリプトのバグを3件直した。

- `#include` のテキスト取り込みと、取り込んだ先の `Import` の反映（見落とし）
- `<% @Import` のような空白入りの書き方（見落とし）
- ページ内の `User.Identity` を、リポジトリ内のクラス `btnet.User` と誤って結んでいた（でっち上げ）→ フレームワークから継承したメンバーで隠れるようにした

## 契約上の判断（記録）

- **辺に数えないもの**：`Master.Menu.SelectedItem` のように、メンバーの型を経由して型名を書かずに使う場合。
  - 例：ページは型 `MainMenu` を名前で書かずに使っている。
  - 依存の順番は `LoggedIn.Master → MainMenu.ascx` 経由で保たれるので、移行の順番は変わらない（2人とも同じ判断）。
- **生成コード**：`btnet/TranslationService.cs` は wsdl から生成された SOAP クライアントだが、`translate.aspx` が使うのでユニットに残す。1行ずつ訳すのではなく、作り直すか置き換える候補として扱う。
- **使われていないコード**：`LoggedOut.Master` のマークアップは `Inherits="btnet.LoggedIn"`（元のソースのコピーの誤り）なので、`LoggedOut.Master.cs` は実行時に使われない。
- **範囲外**：`src/screen_capture`, `src/firefox`, `src/source_control_hooks`, `src/import_users_from_ldap_sample.cs`, `Scripts/` などのサードパーティ JS, `Properties/AssemblyInfo.cs`。

## 作業一覧の名前の付け方（暫定。工程02でルールブック §4 として承認を受ける）

パスの単純な置き換えでは成り立たない。理由は3つ。

- 33個の `.cs` で、ファイル名とクラス名または名前空間が違う（例：`btnet/bug.cs` には `btnet.Bug` が入っている）
- ページのクラスとコアのクラスが衝突する（例：`print_bug.aspx` と `btnet/print_bug.cs`）
- 大文字小文字だけが違う組がある（`tags.aspx` と `btnet/tags.cs` は、大文字小文字を区別しないファイルシステムで衝突する）

そこで `migration/scripts/manifest_subs.py` が、宣言された型からユニットごとの置き換えを作り（`migration/depmap/manifest_subs.txt`）、キットの `make_manifest.py` に渡している。

| ユニット | 移行先 |
|---|---|
| `.cs` | `port/<web|service>/src/main/java/<名前空間>/<主な型>.java` |
| `.aspx` / `.Master` / `.asax` / `.inc` | `port/web/src/main/java/btnet/pages/<ファイル名の語幹>.java`（Thymeleaf テンプレートの置き場所は §4 で決める）|
| `.ascx` | `port/web/src/main/java/btnet/Controls/<クラス>.java` |
| partial class が2ユニットにまたがる場合 | 同じ移行先にして、1バッチで1クラスにまとめる（`Startup`）|

## ファイル

`migration/depmap/` 配下：`edges.tsv`, `order.txt`, `cycles.txt`, `units.tsv`, `package_edges.tsv`, `package_cycles.txt`, `warnings.txt`, `manifest_subs.txt`, `REPORT.md`。作業一覧は `migration/manifest.tsv`。

## 次の工程（02 ギャップ一覧）の見積もり

- **前提（【仮定】）**
  - 217ユニット（C# 約24,700行＋aspx 約13,000行）
  - ギャップの区分は約10種類（WebForms のライフサイクル／ViewState、DataSet、DBNull と null、`ref`/`out` 43か所、LINQ 13、event/delegate 5、partial class、T-SQL の方言、認証、拡張メソッド 51辺 など）
- **進め方**：区分ごとに調査担当を並行で動かす（約10人）。そのあとルールブックを作り、レビュー担当2人で確認する。
- **時間の目安（【仮定】）**
  - 実時間：3〜6時間
  - 人の注意時間：60〜120分（ルールブックの確認と、§5 の欠陥の扱いの判断）
