# Keycloak を用いた認証

お次は認証・認可処理を追加したいと思います。ここでは Keycloak というサーバアプリケーションを利用します。

http://keycloak.jboss.org/

Keycloak は SSO 機構を提供します。また、OAuth2 や OpenID Connect など様々な方式をサポートしているほか、Twitter などのソーシャルログイン機能も用意されているなど、なかなか高機能です。今回はこの Keycloak を用いて lifelog の POST/PUT/DELETE の API に認証をかけます。

> もちろん WildFly Swarm では通常の Java EE アプリケーション同様、JAAS や Apache Shiro といったライブラリを利用することで認証･認可を実現できます。また、まだアプリケーションは lifelog の 1 つしかありませんのでそういう意味でもちょっとオーバーエンジニアリングというか、大げさかもしれません。しかし、今後同じ認証･認可情報を用いたサービスを作ることも踏まえて、ここでその基盤を据えておきたいと思います。

Keycloak を利用した場合の大ざっぱな仕組みとしては以下のようなものです。

1. Keycloak サーバに TOKEN をもらう
1. 1 でもらった TOKEN をヘッダにつけて lifelog の API をリクエストする

ここではすでに用意した Keycloak の設定ファイルを利用します。ご自分で設定してみたい場合は [付録 Keycloak の設定](keycloak-settings.md) を参照ください。

## Keycloak の実行

Keycloak サーバは実体としては Java EE の Web アプリケーションで、WildFly にデプロイして利用します。なので、既存の WildFly にデプロイしたり、コミュニティから提供されている WildFly 込のものを用いて起動可能です。また [Docker イメージ](https://hub.docker.com/r/jboss/keycloak/)も存在します。また、以下のように WildFly Swarm では Keycloak Server 用 Fraction を用意していますので、lifelog と同様に uber jar を簡単に作成することもできます。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/security/keycloak_server.html

> Maven リポジトリからダウンロードしてすぐ使えるものもあります。
>
> https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/servers.html#_keycloak_server

今回は Docker イメージを利用していきたいと思います。さっそくお呼びいたしましょう。

<pre><code class="lang-sh">$ docker run -it -d \
  --name lifelog-auth \
  -p 18080:8080 \
  -v `pwd`:/tmp \
  jboss/keycloak:{{book.versions.keycloak}} \
  -b 0.0.0.0 -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/tmp/lifelog.json
</code></pre>

Keycloak の Admin ユーザを admin/admin というユーザ名/パスワードで設定しています。また、ホストの 18080 ポートから -> コンテナの 8080 へポートフォワードしています。

一番最後に渡している lifelog.json というのはこちらで用意した Keycloak の設定ファイルです。起動時にインポートすることができます。

## Keycloak による認証を用いた API へのアクセス

Keycloak が起動できたら、lifelog の方で認証・認可処理を追加します。

まず、pom.xml には以下が追加されています。

``` xml
<dependency>
  <groupId>org.wildfly.swarm</groupId>
  <artifactId>keycloak</artifactId>
  <version>${version.wildfly-swarm}</version>
</dependency>
```

上記依存性を追加すると `org.wildfly.swarm.keycloak.Secured` が利用できるようになるので、以下の処理を `LifeLogDeployment#deployment()` に追加します。

``` java
import org.wildfly.swarm.keycloak.Secured;
[...]
deployment.as(Secured.class)
  .protect("/entries/*")
    .withMethod("POST", "PUT", "DELETE")
    .withRole("author");
```

これは `/entries` 以下のリソースに対する `POST/PUT/DELETE` メソッドによるアクセスは `author` ロールを持ったユーザのみ、という意味になります。

> メソッドの実体としては上記内容で web.xml のセキュリティ関連の設定を組み立てているだけです。
> ドキュメントも以下にあります。
>
> https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/security/keycloak.html

また、Keycloak のクライアント側の設定ファイルとして keycloak.json がありますので、これもクラスパス配下に置いておきます(すでに置いておきました)。

> keycloak.json はセキュリティ設定の realm や Keyclaok サーバの URL などを設定します。

では上記変更をふまえて lifelog をビルド・実行し、アクセスしてみましょう(Keycloak を 18080 ポートで起動しておくことを忘れずに)。

``` sh
$ ./mvnw clean package \
    && java -Dswarm.project.stage.file=file://`pwd`/lifelog-project-stages.yml -Dswarm.project.stage=production -jar target/lifelog-swarm.jar
```

> PostgreSQL を使わない場合は -Dswarm.project.stage=default にするか、このシステムプロパティを渡さないようにしてください

さっきまでと同じようにアクセスすると、以下のように 401 が返ってきます。

``` sh
$ curl -X POST -H "Content-Type: application/json" -d '{"description" : "test"}' localhost:8080/entries -v
[...]
< HTTP/1.1 401 Unauthorized
[...]
<html><head><title>Error</title></head><body>Unauthorized</body></html>%
```

しからばと、トークンを取りに行きましょう。下記のような形で TOKEN(access_token) として覚えておきます。

``` sh
$ RESULT=`curl --data "grant_type=password&client_id=curl&username=user1&password=password1" http://localhost:18080/auth/realms/lifelog/protocol/openid-connect/token`
$ TOKEN=`echo $RESULT | sed 's/.*access_token":"//g' | sed 's/".*//g'`
```

RESULT では `curl` として用意しておいたクライアント id でトークンを取りに行っています。この際 lifelog realm として、user1/password1 というユーザ名/パスワードをもったユーザがいるため、この情報を利用しています。RESULT として入っている情報はいろいろプロパティがついていますが、認証に必要なのは `access_token` のみなので、これだけもらって TOKEN にしまっています。

あらためて Authorization ヘッダにトークンを渡して POST します。

``` sh
$ curl -X POST -H "Content-Type: application/json" -H "Authorization: bearer $TOKEN" -d '{"description" : "test"}' localhost:8080/entries -v
[...]
< HTTP/1.1 201 Created
[...]
< Location: http://localhost:8080/entries/1
```

いいですね。ちなみに TOKEN は 5 分で切れるのでお急ぎください。

ここでもう少し編集しておきます。lifelog アプリケーション中に配置した keycloak.json を見ると、Keycloak Server の URL(auth-server-url)や公開鍵がハードコードされています。とりあえず URL だけでも外側から変更できるようにしておきましょう。ここではシステムプロパティ swarm.auth.server.url を渡すことにします。

> 本当は lifelog-project-stages.yml で設定できるようにしたかったのですが、その場合 LifeLogDeployment に Container インスタンスを渡さなければいけなくなります。こうなってしまうと EntryContainerIT とコードが共有できなくなるため泣く泣くあきらめました。
> まあ別に無理して共有しなくてもいいんですが。。

LifeLogDeployment を以下のように変更します。

``` java
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;
[...]
public static JAXRSArchive deployment() {
  [...]
  deployment.as(Secured.class)
    .protect("/entries/*")
    .withMethod("POST", "PUT", "DELETE")
    .withRole("author");
  replaceKeycloakJson(deployment);

  return deployment;
}

private static void replaceKeycloakJson(Archive deployment) {
  String keycloakPath = "WEB-INF/keycloak.json";
  Node keycloakJson = deployment.get(keycloakPath);
  if (keycloakJson == null) {
    // FIXME keycloak.json は wildfly-swarm:run で読めない
    return;
  }

  InputStream is = keycloakJson.getAsset().openStream();
  StringBuilder sb = new StringBuilder();
  try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
    reader.lines().forEach(line -> {
      line = line.replace("change_me", System.getProperty("swarm.auth.server.url", "http://localhost:18080/auth"));
      sb.append(line).append("\n");
    });
  } catch (IOException e) {
    e.printStackTrace();
  }
  deployment.add(new ByteArrayAsset(sb.toString().getBytes()), keycloakPath);
}
```

`deployment.ad(Secured.class)` を行うと、内部ではクラスパス上の keycloak.json をアーカイブの WEB-INF 以下に追加する処理が行われます。replaceKeycloakJson() ではその keycloak.json を取り出し、Keyclaok Server の URL を変更(change_me の部分を置換)したうえでまた同じパスでアーカイブに詰めなおしています。

keycloak.json の auth-server-url を `change_me` に変更しておきます。

``` json
{
  [...]
  "auth-server-url": "change_me",
  [...]
}
```

ここまでできたら lifelog を再起動します。以下のように Keycloak Server の URL をシステムプロパティで渡すことができるようになりました。

``` sh
/mvnw clean package && \
  java -Dswarm.auth.server.url=http://localhost:18080/auth -Dswarm.project.stage.file=file://(pwd)/lifelog/lifelog-project-stages.yml -Dswarm.project.stage=production -jar lifelog/target/lifelog-swarm.jar
```

せっかくなので EntryControllerIT もこの認証に対応しておきましょう。

まずは PostgreSQL と同じように、IT 実行前に Keycloak Server を起動するようにしておきます。pom.xml の it プロファイルに以下を追記します。

``` xml
<plugin>
  <groupId>io.fabric8</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>${version.docker-maven-plugin}</version>
  <configuration>
    <images>
      <image>
        [... PostgreSQL の設定 ...]
      </image>
      <!-- ここから追記 -->
      <image>
        <alias>lifelog-auth</alias>
        <name>jboss/keycloak:${version.keycloak-server}</name>
        <run>
          <ports>
            <port>28080:8080</port>
          </ports>
          <volumes>
            <bind>
              <volume>${project.basedir}:/tmp</volume>
            </bind>
          </volumes>
          <cmd>
            -b 0.0.0.0 -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/tmp/lifelog.json
          </cmd>
          <wait>
            <log>WFLYSRV0025</log>
            <time>20000</time>
          </wait>
          <log>
            <prefix>LIFELOG_AUTH</prefix>
            <color>yellow</color>
          </log>
        </run>
      </image>
      <!-- ここまで追記 -->
    </images>
  </configuration>
  [...]
</plugin>
```

これで準備はできたので、テストを変更していきます。

まず最初の方に以下のようなトークン取得処理を追加します。

``` java
// (1)
String keycloakUrl = System.getProperty("swarm.auth.server.url") + "/realms/lifelog/protocol/openid-connect/token";
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
```

Keycloak の URL は IT 用のものをシステムプロパティから渡されるようにしておきます(1)。

curl でやっていたことと同じことをやればよいので上記のような感じでトークンが取得できます(2)。

なんとなくトークン格納用の Token クラスも用意。とりあえず必要な `access_token` だけ取るようにしました(3)。

``` java
package wildflyswarmtour.lifelog.api;

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

response = target.request()
  .header("Authorization", "bearer " + token.getAccessToken())
  .delete();
```

以下コマンドで実行します。

``` sh
$ ./mvnw clean verify \
  -Dswarm.project.stage.file=file://`pwd`/lifelog-project-stages.yml \
  -Dswarm.project.stage=it \
  -Pit
```

うまくいきました? 余裕があればトークンなしや不正なトークンでリクエストすると 401 が出ることを確認するテストをしてみてもいいですね。
