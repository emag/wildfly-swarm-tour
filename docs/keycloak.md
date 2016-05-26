# Keycloak を用いた認証

お次は認証・認可処理を追加したいと思います。ここでは Keycloak という認証・認可用のサーバを利用します。

http://keycloak.jboss.org/

今回は lifelog の POST/PUT/DELETE の API に認証をかけます。

仕組みとしては以下のようなものです。

1. Keycloak サーバに TOKEN をもらう
2. 1 でもらった TOKEN をヘッダにつけて lifelog の API をリクエストする

なお、以下のエントリを真似しています(ただし 1.6.0 向け)。このエントリではあまり Keycloak の設定方法には触れないので、ご興味がある方は一読いただければと。

[Getting started with Keycloak - Securing a REST Service](http://blog.keycloak.org/2015/10/getting-started-with-keycloak-securing.html)

## Keycloak の実行

Keycloak は実体としては Java の Web アプリケーションで、WildFly や Tomcat などにデプロイできます。

以下の WildFly Swarm 化した Keycloak Server を使って、lifelog と同様 実行可能 jar にしてみましょう。

* https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/keycloak_server.html

> 完全にスタンドアロンの以下もあります。
> https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/servers.html

lifelog-keycloak プロジェクトはマルチプロジェクトになっています。そのうち、keycloak-server は pom.xml と lifelog.json だけが存在するプロジェクトです。

pom.xml は以下のようになっています。

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example</groupId>
    <artifactId>lifelog-kyecloak-parent</artifactId>
    <version>1.0.0</version>
  </parent>

  <artifactId>keycloak-server-lifelog-keycloak</artifactId>

  <dependencies>
    <dependency>
      <groupId>org.wildfly.swarm</groupId>
      <!-- (1) WildFly Swarm を用いた Keycloak を利用 -->
      <artifactId>wildfly-swarm-keycloak-server</artifactId>
      <version>${version.wildfly-swarm}</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.wildfly.swarm</groupId>
        <artifactId>wildfly-swarm-plugin</artifactId>
        <configuration>
          <!-- (2) Keycloak のような war アプリケーションを使う場合は指定しておくとよい -->
          <mainClass>org.wildfly.swarm.ContainerOnlySwarm</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>

</project>
```

また、lifelog.json というのはこちらで用意した Keycloak の設定ファイルです。起動時にインポートすることができます。

では Keycloak サーバのビルドと実行を以下で行います。Keycloak の利用ポートはさしあたり 8180 にしておきます。

``` sh
$ ./mvnw clean package -pl lifelog-keycloak/keycloak-server \
    && java \
    -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile \
    -Dkeycloak.migration.file=lifelog-keycloak/keycloak-server/lifelog.json \
    -Dswarm.http.port=8180 \
    -jar lifelog-keycloak/keycloak-server/target/keycloak-server-lifelog-keycloak-swarm.jar
```

上記では起動時に設定ファイル(`lifelog-keycloak/keycloak-server/lifelog.json`)をインポートしていますが、もし手動でインストールする場合は以下のように インポート用のシステムプロパティを外して実行後、

__注意: WildfFly Swarm 1.0.0.Alpha8 で利用できる Keycloak 1.8.0.Final では以下手順だと import に失敗します。__

``` sh
$ java -Dswarm.http.port=8180 -jar lifelog-keycloak/keycloak-server/target/keycloak-server-lifelog-keycloak-swarm.jar
```

下記手順で lifelog.json をインポートしてみてください。

1. http://localhost:8180/auth にブラウザでアクセスし、admin ユーザを作成
2. `Administration Console` のリンクをクリック、先ほど作った admin ユーザでログイン
3. 左上の Master というところにマウスオーバし、`Add Realm` をクリック
4. Import のところで lifelog.json を選択し、`Create` をクリック

Docker を利用する場合は Docker Hub に Keycloak イメージが登録されているのでそちらを利用するのが簡単です。

``` sh
$ docker run -d --name keycloak \
  -v /path/to/wildfly-swarm-tour/complete/lifelog-keycloak/keycloak-server:/tmp/lifelog:ro \
  -p 8180:8080 jboss/keycloak:1.8.0.Final \
  /opt/jboss/keycloak/bin/standalone.sh -b 0.0.0.0 -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/tmp/lifelog/lifelog.json
```

ご自分で設定ファイルをインポートしたい場合は以下です。

``` sh
$ docker run -d --name keycloak -p 8180:8080 jboss/keycloak:1.8.0.Final
```

Docker 以外のインストールを試してみたい方は、以下を参考ください(ただし下記は 1.6.0 向け)。

http://blog.keycloak.org/2015/10/getting-started-with-keycloak.html

## Keycloak による認証を用いた API へのアクセス

Keycloak が起動できたら、lifelog の方で認証・認可処理を追加します。

lifelog-keycloak/lifelog プロジェクトを見てみます。

まず、pom.xml には以下が追加されています。

``` xml
<dependency>
  <groupId>org.wildfly.swarm</groupId>
  <artifactId>wildfly-swarm-keycloak</artifactId>
  <version>${version.wildfly-swarm}</version>
</dependency>
```

上記依存性を追加すると `org.wildfly.swarm.keycloak.Secured` が利用できるようになるので、以下の処理を `LifeLogDeployment#deployment()` に追加します。

``` java
deployment.as(Secured.class)
  .protect("/entries/*")
    .withMethod("POST", "PUT", "DELETE")
    .withRole("author");
```

これは `/entries` 以下のリソースに対する `POST/PUT/DELETE` メソッドによるアクセスは `author` ロールを持ったユーザのみ、という意味になります。

> メソッドの実体としては上記内容で web.xml のセキュリティ関連の設定を組み立てているだけです。
> ドキュメントも以下にあります。
> https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/keycloak.html

また、Keycloak のクライアント側の設定ファイルとして keycloak.json がありますので、これもクラスパス配下に置いておきます(すでに置いておきました)。

> keycloak.json はセキュリティ設定の realm や Keyclaok サーバの URL などを設定します。

では上記変更をふまえて lifelog をビルド・実行し、アクセスしてみましょう(Keycloak を 8180 ポートで起動しておくことを忘れずに)。

``` sh
$ ./mvnw clean package -pl lifelog-keycloak/lifelog \
    && java -Dswarm.lifelog.production=true -jar lifelog-keycloak/lifelog/target/lifelog-keycloak-swarm.jar
```

> PostgreSQL を使わない場合は -Dswarm.lifelog.production=false にするか、このシステムプロパティを渡さないようにしてください

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
$ RESULT=`curl --data "grant_type=password&client_id=curl&username=user1&password=password1" http://localhost:8180/auth/realms/lifelog/protocol/openid-connect/token`
$ TOKEN=`echo $RESULT | sed 's/.*access_token":"//g' | sed 's/".*//g'`
```

RESULT では `curl` として用意しておいたクライアント id でトークンを取りに行っています。この際 lifelog realm として、user/password1 というユーザ名/パスワードをもったユーザがいるため、この情報を利用しています。RESULT として入っている情報はいろいろプロパティがついていますが、認証に必要なのは `access_token` のみなので、これだけもらって TOKEN にしまっています。

あらためて Authorization ヘッダにトークンを渡して POST します。

``` sh
$ curl -X POST -H "Content-Type: application/json" -H "Authorization: bearer $TOKEN" -d '{"description" : "test"}' localhost:8080/entries -v
[...]
< HTTP/1.1 201 Created
[...]
< Location: http://localhost:8080/entries/1
```

いいですねえ。ちなみに TOKEN は 5 分で切れるのでお急ぎください。

せっかくなので EntryControllerIT もこの認証に対応しておきましょう。

まずはテストの最初で以下のようなトークン取得処理を追加します。

``` java
String keycloakUrl = "http://localhost:8180/auth/realms/lifelog/protocol/openid-connect/token";
Client client = ClientBuilder.newClient();
WebTarget target = client.target(keycloakUrl);

Form form = new Form();
form.param("grant_type", "password");
form.param("client_id", "curl");
form.param("username", "user1");
form.param("password", "password1");

Token token = target.request(MediaType.APPLICATION_JSON).post(Entity.form(form), Token.class);
```

Keycloak の URL が決め打ちなのがちょっとあれですが、
curl でやっていたことと同じことをやればよいので上記のような感じでトークンが取得できます。

なんとなくトークン格納用の Token クラスも用意。とりあえず必要な `access_token` だけ取るようにしました。

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
$ ./mvnw clean verify -pl lifelog-keycloak/lifelog -Dswarm.lifelog.production=true
```

うまくいきました? 余裕があればトークンなしでリクエストすると 401 が出ることを確認するテストをしてみてもいいですね。
