# Keycloak を用いた認証

お次は認証・認可処理を追加したいと思います。ここでは Keycloak というサーバアプリケーションを利用します。

http://www.keycloak.org/

Keycloak は SSO 機構を提供します。また、OAuth2 や OpenID Connect など様々な方式をサポートしているほか、Twitter などのソーシャルログイン機能も用意されているなど、なかなか高機能です。今回はこの Keycloak を用いて lifelog の POST/PUT/DELETE の API に認証をかけます。

> もちろん WildFly Swarm では通常の Java EE アプリケーション同様、JAAS や Apache Shiro といったライブラリを利用することで認証･認可を実現できます。また、まだアプリケーションは lifelog の 1 つしかありませんのでそういう意味でもちょっとオーバーエンジニアリングというか、大げさかもしれません。しかし、今後同じ認証･認可情報を用いたサービスを作ることも踏まえて、ここでその基盤を据えておきたいと思います。

Keycloak を利用した場合の大ざっぱな仕組みとしては以下のようなものです。

1. Keycloak サーバに TOKEN をもらう
1. 1 でもらった TOKEN をヘッダにつけて lifelog の API をリクエストする

ここではすでに用意した Keycloak の設定ファイル(keycloak.json/lifelog-realm.json)を利用しますので、以下からダウンロードしそれぞれ配置してください。

* keycloak.json(プロジェクト直下)
 * https://gist.githubusercontent.com/emag/c16eb10eed22d1cb944cecb4b7168dd4/raw/1ca821c8e845cb2ec8a8bcd618ca96f1bbdc0f2b/keycloak.json
* lifelog-realm.json(プロジェクト直下)
 * https://gist.githubusercontent.com/emag/c16eb10eed22d1cb944cecb4b7168dd4/raw/1ca821c8e845cb2ec8a8bcd618ca96f1bbdc0f2b/lifelog-realm.json

ご自分で設定ファイルを作成してみたい場合は [付録 Keycloak の設定](keycloak-settings.md) を参照ください。

## Keycloak の実行

Keycloak サーバは実体としては Java EE の Web アプリケーションで、WildFly にデプロイして利用します。なので、既存の WildFly にデプロイしたり、コミュニティから提供されている WildFly 込のものを用いて起動可能です。また [Docker イメージ](https://hub.docker.com/r/jboss/keycloak/)も存在します。また、以下のように WildFly Swarm では Keycloak Server 用 Fraction を用意していますので、lifelog と同様に uber jar を簡単に作成することもできます。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/security/keycloak_server.html

> Maven リポジトリからダウンロードしてすぐ使えるものもあります。
>
> https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/server/keycloak.html

今回は Docker イメージを利用していきたいと思います。さっそくお呼びいたしましょう。

<pre><code class="lang-sh">$ docker run -it -d \
  --name lifelog-auth \
  -p 18080:8080 \
  -v `pwd`:/tmp \
  jboss/keycloak:{{book.versions.keycloak}} \
  -b 0.0.0.0 -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/tmp/lifelog-realm.json
</code></pre>

Keycloak の Admin ユーザを admin/admin というユーザ名/パスワードで設定しています。また、ホストの 18080 ポートから -> コンテナの 8080 へポートフォワードしています。

一番最後に渡している lifelog-realm.json というのはこちらで用意した Keycloak の設定ファイルです。起動時にインポートすることができます。

## Keycloak による認証を用いた API へのアクセス

Keycloak が起動できたら、lifelog の方で認証・認可処理を追加していきます。

完成版は以下リポジトリにありますので、適宜参照ください。

https://github.com/emag/wildfly-swarm-tour/tree/{{book.versions.swarm}}/code/keycloak

まず、pom.xml に以下を追加します。

``` xml
<dependency>
  <groupId>org.wildfly.swarm</groupId>
  <artifactId>keycloak</artifactId>
</dependency>
```

上記依存性を追加すると `org.wildfly.swarm.keycloak.Secured` が利用できるようになるので、以下の処理を `LifeLogDeployment#deployment()` に追加します。

``` java
import org.wildfly.swarm.keycloak.Secured;
[...]
archive.as(Secured.class)
  .protect("/entries/*")
  .withMethod("POST", "PUT", "DELETE")
  .withRole("author");
```

これは `/entries` 以下のリソースに対する `POST/PUT/DELETE` メソッドによるアクセスは `author` ロールを持ったユーザのみ、という意味になります。

> メソッドの実体としては上記内容で web.xml のセキュリティ関連の設定を組み立てているだけです。
> ドキュメントも以下にあります。
>
> https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/security/keycloak.html

また、Keycloak のクライアント側の設定ファイルとして keycloak.json がありますので、これをプロジェクト配下に置いておきます(すでに冒頭で置いたものです)。

> keycloak.json はセキュリティ設定の realm や Keyclaok サーバの URL などを設定します。

最後に keyloak.json のパスを project-stages.yml に default ステージに指定します。システムプロパティは `swarm.keycloak.json.path` です。

``` yml
swarm:
  datasources:
    data-sources:
      lifelogDS:
        driver-name: h2
        connection-url: jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE
        user-name: sa
        password: sa
  # 追記ここから
  keycloak:
    json:
      path: keycloak.json # java コマンド実行パス(user.dir)からの相対パス、または絶対パスで指定
  # 追記ここまで
---
project:
  stage: it
[...]
```

では上記変更をふまえて lifelog をビルド・実行し、アクセスしてみましょう(Keycloak を 18080 ポートで起動しておくことを忘れずに)。

``` sh
$ ./mvnw clean package \
  && java \
  -Dswarm.project.stage.file=file://`pwd`/lifelog-project-stages.yml \
  -Dswarm.project.stage=production \
  -jar target/lifelog-swarm.jar
```

> PostgreSQL を使わない場合は -Dswarm.project.stage=default にするか、このシステムプロパティを渡さないようにしてください

さっきまでと同じようにアクセスすると、以下のように 401 が返ってきます。

``` sh
$ curl -X POST -H "Content-Type: application/json" -d '{"description" : "test"}' localhost:8080/entries -v
[...]
< HTTP/1.1 401 Unauthorized
[...]
<html><head><title>Error</title></head><body>Unauthorized</body></html>
```

しからばと、トークンを取りに行きましょう。下記のような形で TOKEN(access_token) として覚えておきます。

``` sh
$ RESULT=`curl --data "grant_type=password&client_id=curl&username=user1&password=password1" http://localhost:18080/auth/realms/lifelog/protocol/openid-connect/token`
$ TOKEN=`echo $RESULT | sed 's/.*access_token":"//g' | sed 's/".*//g'`
```

RESULT では `curl` として用意しておいたクライアント id でトークンを取りに行っています。この際 lifelog realm として、user1/password1 というユーザ名/パスワードをもったユーザがいるため、この情報を利用しています。RESULT として入っている情報はいろいろプロパティがついていますが、認証に必要なのは `access_token` のみなので、これだけもらって TOKEN にしまっています。

> realm は Keycloak での認証の単位です。ここでは　lifelog という realm を作っておきました。
> lifelog realm の内容は lifelog-realm.json 内で定義されており、user1 の存在も確認できます。

あらためて Authorization ヘッダにトークンを渡して POST します。

``` sh
$ curl -X POST -H "Content-Type: application/json" -H "Authorization: bearer $TOKEN" -d '{"description" : "test"}' localhost:8080/entries -v
[...]
< HTTP/1.1 201 Created
[...]
< Location: http://localhost:8080/entries/1
```

いいですね。ちなみに TOKEN は 5 分で切れるのでお急ぎください。

## 認証を含んだ IT の実施

せっかくなので EntryControllerIT もこの認証に対応しておきましょう。

まずは PostgreSQL と同じように、IT 実行前に Keycloak Server を起動するようにしておきます。pom.xml の it プロファイルに以下を追記します。Keycloak Server は 28080 ポートでフォワードすることとします。

<pre><code class="lang-xml">&lt;properties&gt;
  [...]
  &lt;version.keycloak-server&gt;{{book.versions.keycloak}}&lt;/version.keycloak-server&gt;
&lt;/properties&gt;

[...]

&lt;plugin&gt;
  &lt;groupId&gt;io.fabric8&lt;/groupId&gt;
  &lt;artifactId&gt;docker-maven-plugin&lt;/artifactId&gt;
  &lt;version&gt;${version.docker-maven-plugin}&lt;/version&gt;
  &lt;configuration&gt;
    &lt;images&gt;
      &lt;image&gt;
        [... PostgreSQL の設定 ...]
      &lt;/image&gt;
      &lt;!-- ここから追記 --&gt;
      &lt;image&gt;
        &lt;alias&gt;lifelog-auth&lt;/alias&gt;
        &lt;name&gt;jboss/keycloak:${version.keycloak-server}&lt;/name&gt;
        &lt;run&gt;
          &lt;ports&gt;
            &lt;port&gt;28080:8080&lt;/port&gt;
          &lt;/ports&gt;
          &lt;volumes&gt;
            &lt;bind&gt;
              &lt;volume&gt;${project.basedir}:/tmp&lt;/volume&gt;
            &lt;/bind&gt;
          &lt;/volumes&gt;
          &lt;cmd&gt;
            -b 0.0.0.0 -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/tmp/lifelog-realm.json
          &lt;/cmd&gt;
          &lt;wait&gt;
            &lt;log&gt;WFLYSRV0025&lt;/log&gt;
            &lt;time&gt;20000&lt;/time&gt;
          &lt;/wait&gt;
          &lt;log&gt;
            &lt;prefix&gt;LIFELOG_AUTH&lt;/prefix&gt;
            &lt;color&gt;cyan&lt;/color&gt;
          &lt;/log&gt;
        &lt;/run&gt;
      &lt;/image&gt;
      &lt;!-- ここまで追記 --&gt;
    &lt;/images&gt;
  &lt;/configuration&gt;
  [...]
&lt;/plugin&gt;
</code></pre>

また、以下 URL からダウンロードできるファイルを `src/test/resources/keycloak-it.json` に配置します。

https://gist.githubusercontent.com/emag/c16eb10eed22d1cb944cecb4b7168dd4/raw/1ca821c8e845cb2ec8a8bcd618ca96f1bbdc0f2b/keycloak-it.json

> project-stages.yml の it ステージに IT 用の keycloak.json のパスを指定したいところですが、
> Arquillian 実行時に project-stages.yml を読み込む際の user.dir が /tmp/arquillian5574290908184081425 といったパスになってしまい、
> うまく相対パスが取れないのでここではクラスパスから取得するようにします。
> /path/to/keycloak-it.json などと絶対パスでもよいならそちらを指定するのでもよいでしょう

次に、`lifelog.api.EntryControllerIT` を修正します。

まずは先ほどクラスパスに追加した keycloak-it.json をアーカイブに含める処理を `createDeployment()` に記述します。

``` java
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
[...]

@Deployment(testable = false)
public static JAXRSArchive createDeployment() {
  JAXRSArchive archive = LifeLogDeployment.deployment();
  // keycloak-it.json を keycloak.json として WEB-INF 配下に追加
  archive.addAsWebInfResource(
    new ClassLoaderAsset("keycloak-it.json", EntryControllerIT.class.getClassLoader()),
    "keycloak.json");
  return archive;
}
```

`test()` の内容も認証処理を含んだ形に変更していきます。

まず最初の方に以下のようなトークン取得処理を追加します。

``` java
import javax.ws.rs.core.Form;
[...]

// (1)
String keycloakUrl = System.getProperty("auth.url") + "/realms/lifelog/protocol/openid-connect/token";
Client client = ClientBuilder.newClient();
WebTarget target = client.target(keycloakUrl);

// (2)
Form form = new Form();
form.param("grant_type", "password");
form.param("client_id", "curl");
form.param("username", "user1");
form.param("password", "password1");

// (3)
Token token = target.request(MediaType.APPLICATION_JSON).post(Entity.form(form), Token.class);

// 以降、既存のコード
UriBuilder baseUri = UriBuilder.fromUri(deploymentUri).path("entries");

// 上で Client と WebTarget を宣言したので、変数代入のみに変更
client = ClientBuilder.newClient();
target = client.target(baseUri);
[...]
```

Keycloak の URL は IT 用のものをシステムプロパティから渡されるようにしておきます(1)。

curl でやっていたことと同じことをやればよいので上記のような感じでトークンが取得できます(2)。

なんとなくトークン格納用の Token クラスも用意。とりあえず必要な `access_token` だけ取るようにしました(3)。

``` java
package lifelog.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Token {
  @JsonProperty("access_token")
  private String accessToken;
}
```

あとは認証が必要な POST と DELETE でこのトークンを渡してやればよいですね。

``` java
Response response = target.request()
  .header("Authorization", "bearer " + token.getAccessToken())
  .post(Entity.json(entry));
[...]
response = target.request()
  .header("Authorization", "bearer " + token.getAccessToken())
  .delete();
```

以下コマンドで実行します。前述の通り IT 用の Keycloak の URL をシステムプロパティ(`auth.url`)として渡しておく必要があります。

``` sh
$ ./mvnw clean verify \
  -Dswarm.project.stage.file=file://`pwd`/lifelog-project-stages.yml \
  -Dswarm.project.stage=it \
  -Dauth.url=http://localhost:28080/auth \
  -Pit
```

うまくいきました? 余裕があればトークンなしや不正なトークンでリクエストすると 401 が出ることを確認するテストをしてみてもいいですね。

## 注意点

docker-maven-plugin の設定では、以下のように Keycloak Server が起動したことを表す `WFLYSRV0025` を含むログが出力されるまで待つようにしています。

``` xml
<wait>
  <log>WFLYSRV0025</log>
  <!-- 単位は ミリ秒 -->
  <time>20000</time>
</wait>
```

ここではタイムアウト値として `<time>` 要素に 20000 ミリ秒設定しているのですが、もし この時間以内に起動しないと以下のようなエラーとなります。

<pre><code class="lang-sh">[ERROR] DOCKER> [jboss/keycloak:{{book.versions.keycloak}}] "lifelog-auth": Timeout after 20071 ms while waiting on log out 'WFLYSRV0025'
</code></pre>

上記のようなエラーが出たら、`<time>` 要素の値を 60000 など増やしてみてください。
