# 00 — Feasibility report: BugTracker.NET → Java

- 調査日: 2026-11-03
- キット: [anthropics/code-migration-kit-with-claude-code](https://github.com/anthropics/code-migration-kit-with-claude-code) commit `cf91c9d`（`migration-kit/` に同梱）
- 対象: このリポジトリ `src/` 一式（コミット `2a064af`）
- 方法: 読み取り専用（`find` / `grep` / `wc` / `git log`）。コード編集・テスト実行なし。ビルドは**実行できず**（後述）。
- 表記: 数値はすべてこの調査で実測したもの。実測でないものは **【仮定】** と明記。

---

## 0. キットの6ステップ（索引）

1. **Create the map and the rules** — 依存マップ・ギャップ一覧・ルールブックを作る
2. **Stress-test the rules** — 難しい少数ファイルで bakeoff と pilot を行い、ルールだけを直す
3. **Translate everything** — 1ユニットごとに実装者＋独立レビュー2人＋修正者でファンアウト
4. **Compile** — サーベイビルド1回でエラーをキュー化し、コンパイラ無しの修正者が潰す
5. **Run it** — hello world → スモークテスト
6. **Match behavior** — 旧コードを仕様とするパリティ判定器で新旧を比較

---

## 1. 移行する理由（このコードにある痛み）

| # | 痛み | 根拠（実測） |
|---|---|---|
| 1 | **サポート切れのランタイム** | `src/BugTracker.Web/BugTracker.Web.csproj` が `TargetFrameworkVersion v4.5`。.NET Framework 4.5.2/4.6/4.6.1 は 2022-04-26 でサポート終了（[Microsoft](https://devblogs.microsoft.com/dotnet/net-framework-4-5-2-4-6-4-6-1-will-reach-end-of-support-on-april-26-2022/)）。4.5 はそれ以前に終了 |
| 2 | **Windows / IIS / LocalDB に固定** | `Web.config` の接続文字列が `(localdb)\v11.0`、プロジェクト種別 GUID `{349c5851-…}`（ASP.NET Web Application）。Linux ではビルドも起動もできない（本調査環境で実際に不可） |
| 3 | **WebForms 依存の UI** | `runat="server"` 780箇所/112ファイル、`IsPostBack` 60箇所/45ファイル、`ViewState` 27ファイル、`Response.Write` 719箇所/111ファイル |
| 4 | **古い依存パッケージ** | `packages.config`: EntityFramework 6.1.2, NEST/Elasticsearch.Net 1.0.2, NLog 3.1, OpenPop.NET 2.0.6, Newtonsoft.Json 6.0.4, jQuery 1.11.1, bootstrap 3.3.1, ckeditor 4.4.4 |
| 5 | **テストが1件もない** | テストプロジェクト0。`nunit/xunit/mstest/[Test]/[Fact]` の検索ヒットは全件 `tk_duration_units` 等の時間単位文字列（誤検出）|
| 6 | **開発停止** | `src/` への最終コード変更は 2015-07-26（`git log`、コミット数 2014:27 / 2015:42 / 2024:1 は README のみ）|

> 注意：「Java にすれば良くなる」は痛み 1・2 の解消（ランタイムのサポートとプラットフォーム非依存）に対してのみ根拠があります。痛み 3〜5 は言語ではなく**構造と依存の問題**で、Java 移行でも自動的には解決しません（＝ルールブックで決める事項）。

---

## 2. 規模（移行単位）

| 区分 | ファイル | 行数 | 移行方針（提案） |
|---|---|---|---|
| ページ（`.aspx` + `.aspx.cs` + `.designer.cs`） | 117 ページ | aspx 13,046 / code-behind 12,791（ascx・Master 含む）| **1ページ = 1ユニット** |
| User Control / Master | ascx 3 / Master 2 | 328 / 77 | 各1ユニット |
| コア C#（`btnet/`, `Models/`, `Security/`, `Search/`, `Mail/`, `Controllers/`, `App_Start/` ほか） | 92 | 10,921 | 1ファイル = 1ユニット |
| `btnet_service/`（POP3 → バグ登録の常駐サービス） | 3 | 924 | 1ファイル = 1ユニット |
| 自前 JS / TS（`bug_list.js`, `Scripts/Site/` 等） | JS 12 / TS 6 | 約1,550 / 536 | **移行しない**（ブラウザ側なのでそのまま再利用）|
| サードパーティ JS（`Scripts/r.js` 27,993行、ckeditor、DataTables、edit_area、datejs） | — | — | 移行しない |
| DB スキーマ（`BugTracker.Database/dbo/` 28テーブル+1ビュー、`upgrade.sql`） | 31 | 661 + 912 | **SQL Server のまま維持** |
| `screen_capture/`（WinForms デスクトップ） | 14（うち `System.Windows.Forms` 使用 10） | 2,597 | **範囲外を提案**（別判断）|
| `source_control_hooks/`（Python）, `firefox/`（拡張） | 4 / 2 | 601 / 519 | 範囲外（C# ではない）|

**移行ユニット数（範囲内）: 117 + 3 + 2 + 92 + 3 = 217 ユニット、C# 約 24,700 行 + aspx 約 13,000 行。**

---

## 3. 三つの判断

### 判断1：構造維持（structure-preserving）か再設計か

- **コア層は1対1で写せる**：`btnet/dbutil.cs` は `SqlConnection`/`SqlCommand`＋パラメーター化した `SQLString`（`new SQLString` 254箇所/100ファイル）→ JDBC `PreparedStatement` に直訳可能。`DataSet/DataView/DataRow/DataTable` 260箇所/103ファイル → 薄い互換クラス（`DataSet` 相当の行リスト）を1つ作れば機械的に写せる。C# の難所は少ない：`dynamic` 0、`async/await` 0、`unsafe` 0、`struct` 0、`goto` 0、`ref/out` 43箇所/13ファイル、LINQ 13箇所/5ファイル、event/delegate 5箇所/4ファイル。
- **UI 層は Java に同等物がない**：WebForms のサーバーコントロール（`asp:CheckBox` 51、`asp:DropDownList` 39、`asp:ListItem` 175、`asp:DataGrid` 3 ほか）、ViewState（27ファイル）、ポストバック（`IsPostBack` 45ファイル）は Java 標準に存在しない。ただし、**ページの大半は実質「サーバー側テンプレート＋Response.Write」**：サーバーコントロールが form 以外に無いページが 40（`ajax.aspx`, `download_file.aspx`, `svn_diff.aspx` 等）、イベントハンドラー（`OnClick`/`AutoPostBack` 等）を持つページは 5 だけ。インラインコード `<% %>` を持つページ 61、`<%=` 84箇所。
- 結論：**構造維持＋WebForms 互換シム**。1ページ = 1 JSP + 1 Servlet（code-behind 相当）に写し、サーバーコントロール・ViewState・ポストバックは Step 1 で手書きする小さな互換ライブラリ（`btnet.web.compat`）に寄せる。これはルールブックの一部として先に作り、翻訳者は使うだけにする。ページの作り直し（SPA 化等）はしない。

> **判断：構造維持。ただし WebForms 互換シムをルールブックと同時に先に用意する — 判断を強いたファイル：`src/BugTracker.Web/edit_bug.aspx(.cs)`（code-behind 2,223行、最大）、`src/BugTracker.Web/BasePage.cs`、`src/BugTracker.Web/LoggedIn.Master`、`src/BugTracker.Web/btnet/dbutil.cs`、`src/BugTracker.Web/btnet/SQLString.cs`。**

### 判断2：検証コスト

- **元のビルド時間：計測不能**。この環境には MSBuild / .NET Framework 参照アセンブリ / Mono が無く、Docker デーモンも起動していない（`docker info` → `/var/run/docker.sock` なし）。.NET Framework 4.5 の Web Application プロジェクトは Windows 上の MSBuild（Visual Studio Build Tools）が前提。→ **ベースライン = unknown**。
- 移行先のビルド：Java 25 + Maven（`mvn -q compile`、本環境に `javac`/`mvn` あり）。**【仮定】** C# 24.7k 行 → Java 約 30〜40k 行、`javac` は行数にほぼ線形で、フルコンパイル 1 分未満。JSP は `jspc` プラグインで事前コンパイルしてビルドに含める（実行時まで JSP エラーが隠れるのを防ぐ）。
- ユニット内での型検査：翻訳順は依存の葉から根へ進むが、各ユニットの依存先が未翻訳の段階では `javac` は通らないため、**Step 4（サーベイビルド）を残す**（Step 3 への溶かし込みはしない）。
- 動作確認の値段は高い：パリティ判定器は旧アプリ（Windows + IIS Express + SQL Server）と新アプリ（Tomcat + SQL Server）を**同じ DB スナップショット**で並走させる必要がある。

> **判断：ビルドは安価（【仮定】1分未満）、判定器は高価（Windows 環境が必須）。Step 4 は分離したまま — 判断を強いたファイル：`src/BugTracker.Web/BugTracker.Web.csproj`、`src/BugTracker.Web/Web.config`（`(localdb)\v11.0`）、`src/BugTracker.NET.sln`。**

### 判断3：テストは生き残るか

| | 件数 | 例 |
|---|---|---|
| 公開面（HTTP/API）を叩くテスト | **0** | — |
| 内部を import するテスト | **0** | — |

テストが存在しないため、3例ずつは挙げられません。**判定器がない＝出口条件がない**ので、これはゲートです。

> **判断：テストは生き残らない（存在しない）。Step 1 の前に `prompts/00b-judge-setup.md` で判定器を先に作る — 判断を強いたファイル：`src/BugTracker.NET.sln`（テストプロジェクト無し）、`src/BugTracker.Web/BugTracker.Web.csproj`。**

**00b に渡すパリティシナリオの種（HTTP の外から叩く・新旧で同じ DB を使う）**

1. ログイン／ログオフ（`default.aspx`, `logoff.aspx`, API `LoginController.Post`）と権限別のメニュー表示
2. バグ一覧（`bugs.aspx`）と JSON API `BugQueryController.Get`（並び順・ページング・フィルター）
3. バグ新規作成・編集・コメント追加（`edit_bug.aspx` のポストバック）→ DB の `bugs` / `bug_posts` 差分
4. 添付のアップロードとダウンロード（`add_attachment.aspx`, `view_attachment.aspx`）のバイト一致
5. メールからのバグ登録（API `BugFromEmailController.Post`）
6. 管理画面：ユーザー・プロジェクト・カテゴリ等の CRUD（`admin.aspx` 配下）
7. 印刷・エクスポート（`print_bugs.aspx`, `tasks_all_excel.aspx`）
8. 通知キュー（`queued_notifications` テーブル）への書き込み内容
9. 検索（`SearchSQL` 設定・Elasticsearch 連携 `Search/BugSearch.cs`）

比較器の観点：HTML は DOM 正規化後のテキスト＋フォーム値で比較（空白・属性順・自動生成 ID・日時を正規化）、DB は操作後のテーブル差分で比較。**壊した実装（例：並び順を逆にする）で判定器が「不一致」を出せることを先に確かめる**（キットの要求）。

---

## 4. このリポジトリでの6ステップ

| Step | このリポジトリでの中身 | 使うプロンプト | コスト（ユニット × 単価） |
|---|---|---|---|
| **00b（Step 1 の前）** | Windows + SQL Server で旧アプリを起動し、上記9シナリオの HTTP パリティ判定器を作り、壊した実装で検証 | `prompts/00b-judge-setup.md` | 9 シナリオ × unknown |
| 1 | C# 用の依存マップ（キット同梱は Python/TS/C のみ → **C# 用 depmap を新規作成**）、ギャップ一覧（WebForms・DataSet・null・`ref/out`・T-SQL）、ルールブック（下記の決定事項）、互換シム | `prompts/01`, `prompts/02`, `templates/RULEBOOK.md`, `templates/inventory.tsv` | 217 ユニット × 走査のみ（安価）|
| 2 | bakeoff＋pilot：`edit_bug.aspx`（最大・ポストバック）、`bugs.aspx`、`btnet/dbutil.cs`、`btnet/bug_list.cs`、`Security/Authenticate.cs`、`Search/BugSearch.cs` の6本 | `prompts/03-stress-test.md` | 6 ユニット × 2翻訳＋差分検査 |
| 3 | 217 ユニットをキューで変換。出力は `java/` 配下の Maven プロジェクト | `prompts/04-translation-kickoff.md`, `scripts/queue_runner.mjs` | 217 × 4 エージェント（実装・レビュー×2・修正）＋不一致時の裁定 |
| 4 | `mvn -q compile` ＋ `jspc` のサーベイビルド → モジュール別エラーキュー | `prompts/05-survey-build.md`, `scripts/build_daemon.sh` | ラウンド数 × unknown |
| 5 | Tomcat 起動 → `hello.aspx` 相当 → ログイン → バグ1件作成 | —（手順はキット README Step 5）| 小 |
| 6 | 00b の判定器で新旧比較、`TODO(port)` 等の消化 | `prompts/06-post-parity.md` | 9 シナリオ × 不一致数 |

**ルールブックで最初に決めるべき事項（Step 1 の論点）**

| 論点 | 提案（【仮定】要承認） |
|---|---|
| Java / サーバー | Java 25 LTS（[Oracle roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)）＋ Tomcat 11（Servlet 6.1 / JSP 4.0、[tomcat.apache.org](https://tomcat.apache.org/whichversion)）、Maven、WAR |
| フレームワーク | **素の Jakarta Servlet + JSP**（1ページ1対1を保つため）。Spring Boot は再設計寄りなので採らない |
| DB | SQL Server を維持、`mssql-jdbc`。T-SQL はそのまま（`isnull` 227、`convert(` 37、`scope_identity` 12、`top 1` 7）|
| EF6（13ファイル/43箇所） | 素の JDBC へ寄せる（`DbUtil` に統一）か JPA か — 要決定 |
| 認証（OWIN Cookie） | Servlet Filter ＋ HttpSession。パスワードハッシュ形式は旧 DB と互換に |
| 外部ライブラリ | NLog→SLF4J/Logback、Newtonsoft→Jackson、OpenPop→Jakarta Mail、NEST 1.0.2→要調査（ES サーバーの版次第）|
| null / 型 | C# の値型 `int`・`DateTime` → Java プリミティブ／`java.time`、`DBNull` の扱い |

---

## 5. コストと期間

**トークン：10⁷〜10⁸ 台（数千万〜数億の帯）。精密な数字は出しません。**

駆動要因（見える掛け算）：

- ユニット数 **217** × レビュートポロジー **4 エージェント/ユニット**（実装1・独立レビュー2・修正1、不一致時に裁定1）≒ **870〜1,100 回**のエージェント実行
- × 1回あたりの入力 **【仮定】** 3万〜15万トークン（ルールブック＋インベントリ抜粋＋対象ファイル＋依存の型）
- ＋ Step 2（6ユニット×bakeoff）、Step 4 の修正ラウンド（unknown）、00b の判定器構築（unknown）
- 審判の値段：`javac` は安い／**パリティ判定器は高い**（Windows 環境の確保が支配的）

| Step | 壁時計（【仮定】） | 人の注意時間（【仮定】） |
|---|---|---|
| 00b 判定器 | 1〜3 日（Windows 環境の用意次第） | 2〜4 時間 |
| 1 マップ・ルール・シム | 1〜2 日 | 2〜3 時間（ルールブックのレビュー）|
| 2 ストレステスト | 半日 | 1〜2 時間 |
| 3 全変換 | 半日〜1 日（並列度次第） | 30 分（ゲートのみ）|
| 4 コンパイル | 半日〜1 日 | 30〜60 分 |
| 5 起動 | 1〜2 時間 | 30 分 |
| 6 動作一致 | 1〜3 日 | 2〜4 時間 |

---

## 6. モデル計画（ゲートでの決定事項）

| 工程 | 推奨 | 理由 |
|---|---|---|
| ルールブック作成・改訂、互換シム設計 | **最大（Opus 5.5）** | 1回きり。誤りは 217 ユニットすべてに複製される |
| 懐疑的レビュアー×2 | **最大（Opus 5.5）** | ギャップが複雑（WebForms のライフサイクル、ViewState、DataSet、DBNull）|
| 変換の実装者 | **中（Sonnet 5.5）** | 大量・定型。レビュー2人とコンパイラが後ろに控える |
| 修正者（Step 3/4） | **中（Sonnet 5.5）** | 本当の審判はコンパイラ |
| 裁定（不一致時） | **最大（Opus 5.5）** | 件数が少なく、判断の重みが大きい |

01〜06 のプロンプトには、ここで決めたモデルを `[model]` として明示的に渡します。

---

## 7. 判定（Verdict）

**今は移行しない → 先に判定器を作る（00b）。判定器ができたら移行する。**

- 移行の価値はある：サポート切れのランタイムと Windows 固定（痛み1・2）は実在し、コア C# の構造は Java に素直に写せる（判断1）。
- ただし判定器がゼロ（判断3）で、旧アプリをこの環境では起動できない（判断2）。このまま Step 1 に進むと出口条件がない。

**判定を変える唯一の事実（リポジトリからは分からない）**
旧アプリを **Windows + SQL Server（LocalDB 可）で実際に起動し、ログインしてバグ一覧が表示できるか**。

- 確かめ方（1日未満）：Windows PC（Visual Studio Build Tools + SQL Server Express/LocalDB）または GitHub Actions の Windows ランナーで `BugTracker.NET.sln` を MSBuild でビルドし、`BugTracker.Database` を配備して `hello.aspx` と `default.aspx` を開く。
- 起動できない場合：キットの前提（旧コードが実行可能な仕様）が崩れるため、判定は「ソースのみを仕様とする」方式へ格下げとなり、Step 6 の動作一致は未検証のまま残る。

---

## 8. 読んだもの・実行したもの

- 読んだ：`migration-kit/README.md`, `migration-kit/CLAUDE.md`, `migration-kit/prompts/00-feasibility.md`, `migration-kit/skill/SKILL.md`, `README.md`, `Blog 2 - Evaluating the Code.md`（冒頭）, `src/BugTracker.Web/packages.config`, `src/btnet_service/packages.config`, `src/BugTracker.Web/Web.config`（接続・検索設定）, `src/BugTracker.Web/btnet/dbutil.cs`（冒頭）, `src/BugTracker.Web/Controllers/*.cs`（シグネチャ）
- 抜き取りで済ませたもの：個々の `.aspx` / `.aspx.cs` は全文を読んでいない（`grep` による構文の集計のみ）。`screen_capture/`, `firefox/`, `source_control_hooks/` は範囲外として中身を読んでいない。
- 実行した：`find` / `wc -l` / `grep -c` / `git log`、`docker info`（デーモンなし）、`which java javac mvn gradle dotnet mono msbuild`（Java 系のみあり）。**ビルド・テストは実行していない（できない）。**

---

## 9. ゲートでの決定（2026-11-03、人が承認）

この節は上の提案より優先します。

| # | 質問 | 決定 | 影響 |
|---|---|---|---|
| 1 | 元アプリを動かせる Windows 環境 | **なし** | 下記「判定器への影響」 |
| 2 | モデル計画 | **推奨どおり**：ルールブック・互換設計・レビュアー×2・裁定 = Opus 5.5／実装者・修正者 = Sonnet 5.5。**全サブエージェントのエフォートは「中」(medium)** | 01〜06 の `[model]` はこの割り当てで固定。既定値のまま動かしたら逸脱ログへ |
| 3 | 移行先の構成 | **Java 21 + Spring Boot 4 + Thymeleaf**（§4 の Servlet + JSP / Java 25 案は不採用） | 下記「構成変更の影響」 |
| 4 | `screen_capture/`（WinForms）を範囲外にする | **範囲外で確定** | 移行ユニット 217 は変わらない（元から含めていない） |

### 構成変更の影響（判断1の改訂）

- 確認した事実（2026-11-03）：Spring Boot 4.0 は Java 17 以上・Servlet 6.1 前提（[System Requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html)、[Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)）。Maven Central の `spring-boot-starter-thymeleaf` の最新は 4.1.2。この環境の JDK は 21.0.12 なので、**新しい側はここでビルドできる**。
- **コア層（92 ファイル）は構造維持のまま**：`DbUtil`/`SQLString` → `JdbcTemplate` か素の JDBC、`DataSet` 系 → 互換クラス。1ファイル = 1ユニット。bakeoff は有効。
- **UI 層（117 ページ + ascx 3 + Master 2）は再設計扱いになる**：WebForms のページ（マークアップ＋code-behind）は「Spring MVC の `@Controller` 1つ + Thymeleaf テンプレート1つ」に写す。`Response.Write`（719か所）はテンプレートに渡すモデル値に、`LoggedIn.Master` は Thymeleaf のレイアウト断片に、ポストバックは POST ハンドラーに変える。キットの README「If you're redesigning」に従い、この層では次のように扱う：
  - ルールブックの UI の章は**設計文書**（ページ→コントローラー/テンプレートの対応、フォームの結び付け方、レイアウト、認証）として書く
  - UI ユニットには **bakeoff を使わない**。代わりに、設計文書を対立的にレビューし、少数ページで使い捨ての通し実行をする
  - 作業単位は 1ページ（= コントローラー + テンプレート + そのページ専用の SQL）
- Web API の3コントローラー（`Controllers/*.cs`）は `@RestController` にほぼそのまま写す。
- 認証（OWIN Cookie）は Spring Security に置き換える。パスワードハッシュは旧 DB と互換にする。

### 判定器への影響（判断3の改訂）と逸脱

- 00b の手順3「判定器を**元のコード**に当てて全件パスさせ、壊した元コードで失敗を確かめる」は**実行できない**。旧コードを動かせる環境がないため。
- **逸脱 D-001（記録）**：キットの常設ルール7「旧コードが仕様」のうち、旧コードを*実行*する部分が満たせない。代わりの扱い：
  1. 判定器は **HTTP + DB の外部シナリオ**として作る（§3 の9シナリオ）。期待値は、旧ソース（`.aspx` / `.aspx.cs` / SQL）を読んで書き起こした「ソース由来の期待値」とし、各期待値に根拠のファイル・行を付ける。
  2. 判定器の検証は、新アプリができた後（Step 5 以降）に、**新アプリへの意図的な改変**で失敗を確かめる形に置き換える。
  3. Step 6 の完了条件は「判定器が全件パス」に加え、**「旧アプリとの実測パリティは未検証」と明記して引き継ぐ**。後で Windows 環境が手に入れば、同じ判定器を旧アプリにも当てて検証し直せるように作る（URL・DB 接続先を差し替え可能にする）。
- 詳細は Step 1 で作る `migration/RULEBOOK.md` の Deviation log に転記する。
