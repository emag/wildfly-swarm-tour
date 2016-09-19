# Docker による環境構築

ここまでで lifelog アプリケーション、PostgreSQL や Keycloak と 3 つのプロセスが出てきました。

さらにリバースプロキシや別の API サーバ、Web フロントエンドなども増えていくとしたら、さらに構築が面倒になりそうです。というわけで、簡単にセットアップできるよう Docker イメージを作ってみましょう。

Docker なしでここまで頑張ってこられた方も、そろそろあきらめて Dokcer が使える環境を用意してみてください。

> Docker のセットアップについては [環境](environment.md#docker) を参照ください。

## lifelog の Docker イメージのビルド

まずは lifelog の Dokcer イメージを作るところからです。

以下内容で `Dockerfile` という Makefile のようなものをプロジェクト直下に作ります。

``` dockerfile
FROM jboss/base-jdk:8
MAINTAINER yourname <someone at example.com>

ADD target/lifelog-swarm.jar /opt/lifelog-swarm.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/lifelog-swarm.jar"]
```

おおざっぱに以下に内容を説明します。

* FROM: `jboss/base-jdk:8` は、OpenJDK 8 がインストールされている環境です。これをベースにイメージを作っていきます。
* MAINTAINER: javadoc でいう @author のようなもので、内容は任意です。
* ADD: ホスト側でビルドした `target/lifelog-swarm.jar` をコンテナの /opt 以下に追加します。
* EXPOSE: ここで指定したポート番号は他の Docker コンテナから環境変数を通して取得できます。
* ENTRYPOINT: このイメージから Dokcer コンテナを起動する際に実行されるコマンドです。

> ここでは最低限のことしかしていませんが、実行可能 jar だと Dockerfile はずいぶん書きやすいです。
> もし普通の WildFly でやろうとすると以下のようなイメージになります。WildFly をダウンロードしたりいろいろ設定するシェルを用意したりして、ちょっとめんどっちいですね。
>
> https://github.com/rafabene/devops-demo/tree/master/Dockerfiles/ticketmonster

上記の Dockerfile の通り、ビルドした lifelog-swarm.jar が必要ですので、先に lifelog をビルドしておきます。

``` sh
$ mvn clean package
```

`docker build` で lifelog の Docker イメージを作成します。

``` sh
$ docker build -t <お好きなお名前>/lifelog .
```

`<お好きなお名前>` のところは慣例として [Docker Hub](https://hub.docker.com/) でのログインユーザ名を記載します。ここでは emag としておきます。

> 今回は利用しませんが、Docker Hub でアカウントを作成すると、自分で作ったイメージを push できるようになります。
> push したイメージは docker pull で任意のホスト上で利用できるようになります。
> FROM で指定している jboss/base-jdk:8 も Docker Hub に登録されているもので、これを pull しています。
>
> https://hub.docker.com/r/jboss/base-jdk/

イメージができたかは `docker images` で確認できます。

``` sh
$ docker images
REPOSITORY    TAG     IMAGE ID      CREATED         VIRTUAL SIZE
emag/lifelog  latest  544dbb966fa0  37 minutes ago  509.6 MB
```

次に `docker run` で lifelog のコンテナを起動します。

``` sh
$ docker run -it -d \
  --name lifelog  \
  -v `pwd`:/tmp/project \
  -e _JAVA_OPTIONS="-Dswarm.project.stage.file=file:///tmp/project/lifelog-project-stages.yml" \
  -p 8080:8080 \
  emag/lifelog
```

* -d: デーモンとして起動
* --name: コンテナに名前をつける場合指定。ここでは lifelog
* -v: `<host_path>:<container_path>` という書式でホストの `<host_path>` を `<container_path>` にマウント
* -e: コンテナに設定する環境変数
* -p: 8080:8080 を指定することで、ローカルホスト(Docker ホスト)の 8080 ポートを Docker コンテナの 8080 ポートにポートフォワード
* emag/lifelog: イメージを指定

`docker ps` コマンドで、起動中のコンテナを確認できます。以下のように表示されていれば OK です。いつものように curl でアクセスしてみてください。

``` sh
$ docker ps
CONTAINER ID  IMAGE         COMMAND                CREATED        STATUS        PORTS                   NAMES
fe8ece48806b  emag/lifelog  "java -jar /opt/lifel" 3 minutes ago  Up 3 minutes  0.0.0.0:8080->8080/tcp  lifelog
```

停止・削除関係のコマンドも確認しておきましょう。

* 起動中のコンテナを停止 : `docker stop lifelog`
* 停止中のコンテナを削除: `docker rm lifelog`
* 起動中のコンテナを強制削除: `docker rm -f lifelog`
* コンテナの出力の確認: `docker logs -f lifelog`

いったん lifelog コンテナは削除しておきます。

``` sh
$ docker rm -f lifelog
```

## PostgreSQL、Keycloak Server とのコンテナ間通信

今までデータソース設定の接続 URL や keycloak.json 中の Keycloak Server の URL は Docker コンテナに対してホストからポートフォワードした `localhost:<port>` に決め打ちでした。ローカルでやっている分にはいいのですが、運用環境などでは別の IP アドレス(またはホスト名)であったりポート番号になるでしょう。

Docker コンテナは同じホスト上では `--link <コンテナIDまたは名前>:<適当な名前>` オプションを使うと、その指定したコンテナの EXPOSE したポートやコンテナの IP アドレスを環境変数として取得することができます。

例えば、今回 PostgreSQL を以下のように起動しています。

<pre><code class="lang-sh">$ docker run -it -d \
  --name lifelog-db \
  -e POSTGRES_USER=lifelog -e POSTGRES_PASSWORD=lifelog \
  -p 5432:5432 \
  postgres:{{book.versions.postgresql}}
</code></pre>

ここで `--link lifelog-db:db` としてコンテナを起動すると、`db` を大文字にした `DB` を prefix とし、EXPOSE したポート番号(5432)を含んだ各種環境変数が得られます。確認のため、以下のようにチェック用のコンテナを起動してみます。

``` sh
$ docker run -it --rm --link lifelog-db:db jboss/base-jdk:8 env
[...]
DB_PORT_5432_TCP_ADDR=172.17.0.13
DB_PORT_5432_TCP_PORT=5432
[...]
```

よって、`--link` をつけて起動するアプリケーション(lifelog)側でこの環境変数を読めばよいということになります。

> 別ホストの場合は `docker run` に `-e` オプションで環境変数を渡せるので、`-e DB_PORT_5432_TCP_ADDR=db.server` などとします。

というわけで、環境変数が与えられた場合はそちらを利用するように PostgreSQL と Keycloak Server の URL の設定部分を変更します。

完成版は以下リポジトリにありますので、適宜参照ください。

https://github.com/emag/wildfly-swarm-tour/tree/{{book.versions.swarm}}/code/docker

PostgreSQL の URL は lifelog.LifeLogConfiguration で設定しているので、こちらを以下のように変更します。

``` java
DatasourcesFraction datasourcesFraction(String datasourceName) {
  return new DatasourcesFraction()
   [...]
   .dataSource(datasourceName, ds -> ds
     .driverName(resolve("database.driver.name"))
     .connectionUrl(databaseConnectionUrl()) // 変更
     .userName(resolve("database.userName"))
     .password(resolve("database.password"))
   );
}

// 追加
private String databaseConnectionUrl() {
  String urlFromEnv = System.getenv("DB_PORT_5432_TCP_ADDR") + ":" + System.getenv("DB_PORT_5432_TCP_PORT");

  return urlFromEnv.equals("null:null")
    ? resolve("database.connection.url")
    : "jdbc:postgresql://" + urlFromEnv + "/lifelog";
}
```

Keycloak Server の URL は lifelog.LifeLogDeployment#replaceKeycloakJson で設定しているので、以下のようにします。

``` java
private static void replaceKeycloakJson(Archive deployment) {
  [...]
  try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
    reader.lines().forEach(line -> {
      line = line.replace("change_me", authServerUrl()); // 変更
      sb.append(line).append("\n");
    });
  } catch (IOException e) {
  [...]
}

// 追加
private static String authServerUrl() {
  String urlFromEnv = System.getenv("AUTH_PORT_8080_TCP_ADDR") + ":" + System.getenv("AUTH_PORT_8080_TCP_PORT");

  return urlFromEnv.equals("null:null")
    ? System.getProperty("swarm.auth.server.url", "http://localhost:18080/auth")
    : "http://" + urlFromEnv +  "/auth";
}
```

ここまで変更したら lifelog のビルド及びイメージのビルドを実行します。

``` sh
$ mvn clean package && docker build -t emag/lifelog .
```

これで `--link` を用いて各サーバのコンテナの URL を取得できるようになります。

PostgreSQL と Keycloak Server のコンテナをそれぞれ lifelog-db、lifelog-auth という名前で起動しているとして、以下のように `docker run` で lifelog コンテナを起動します。

``` sh
$ docker run -it -d \
  --name lifelog  \
  -v `pwd`:/tmp/project \
  -e _JAVA_OPTIONS="-Dswarm.project.stage.file=file:///tmp/project/lifelog-project-stages.yml -Dswarm.project.stage=production" \
  --link lifelog-db:db \
  --link lifelog-auth:auth \
  -p 8080:8080 \
  emag/lifelog
```

今まで TOKEN を取得するのにポートフォワードしていた http://localhost:18080/auth を指していましたが、lifelog の keycloak.json ではもうこちらではなく Keycloak Server コンテナの IP アドレスを指しているため、取得時の URI を変える必要があります。

まずは Keycloak Server コンテナの IP アドレスを取得します。

``` sh
$ KEYCLOAK_SERVER=`docker ps | grep lifelog-auth | awk '{print $1}' | xargs docker inspect --format="{{.NetworkSettings.IPAddress}}:8080"`
```

> 8080 のところもがんばればできますが、ちょっと複雑になるようなので妥協! どなたかスマートなやり方をご存知でしたらこっそり教えてください。

上記で得た Keycloak Server コンテナの IP アドレスを用いて TOKEN を取得します。

``` sh
$ RESULT=`curl --data "grant_type=password&client_id=curl&username=user1&password=password1" http://${KEYCLOAK_SERVER}/auth/realms/lifelog/protocol/openid-connect/token`
$ TOKEN=`echo $RESULT | sed 's/.*access_token":"//g' | sed 's/".*//g'`
```

あとは同様に POST して新規作成されることを確認してみてください。

``` sh
$ curl -X POST -H "Content-Type: application/json" -H "Authorization: bearer $TOKEN" -d '{"description" : "test"}' localhost:8080/entries -v
```

> 逆に、TOKEN を取得するところで今までどおり localhost:18080 で行うとどうなるかも試してみるとおもしろいかもしれません。

## Docker Compose

ここまでは PostgreSQL や Keycloak Server のコンテナは事前に手動で立ち上げていました。

lifelog が PostgreSQL や Keycloak Server に依存していることがすぐわかればいいのですが、依存先が複雑になってくると管理が面倒になってきます。今後もリバースプロキシや別の API などのコンテナが増えていくことも考えると、そろそろコンテナ管理も楽をしたいところです。そこで、今回は Docker Compose を利用します。

Docker Compose は複数コンテナの管理を簡単にするためのものです。

http://docs.docker.com/compose/

まずは以下ドキュメントに従ってインストールします。バイナリをパスの通ったところにインストールした上で実行権限をつけておきます。

http://docs.docker.com/compose/install/

<pre><code class="lang-sh">$ docker-compose --version
docker-compose version {{book.versions.docker_compose}}, build &lt;some number&gt;
</code></pre>

次に、以下のような `docker-compose.yml` という設定ファイルを用意します。

`docker run` するときの情報を並べただけって感じですね。

<pre><code class="yml">lifelog:
  image: emag/lifelog
  volumes:
    - .:/tmp/project
  environment:
    _JAVA_OPTIONS: "-Dswarm.project.stage.file=file:///tmp/project/lifelog-project-stages.yml -Dswarm.project.stage=production"
  links:
    - lifelog-db:db
    - lifelog-auth:auth
  ports:
    - 8080:8080

lifelog-db:
  image: postgres:{{book.versions.postgresql}}
  environment:
    POSTGRES_USER: lifelog
    POSTGRES_PASSWORD: lifelog

lifelog-auth:
  image: jboss/keycloak:{{book.versions.keycloak}}
  volumes:
    - .:/tmp/project
  command: -b 0.0.0.0 -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/tmp/project/lifelog.json
</code></pre>

まぎらわしいので前に手動で上げた lifelog/lifelog-db/lifelog-auth コンテナは削除しておきましょう。

コンテナの起動は以下のように行います。以下で全部のコンテナが起動します。`-d` をつけない場合はフォアグラウンドです。

``` sh
$ docker-compose up -d
```

> 引数に設定ファイルで指定した名前(サービス名)を渡すとそのサービスだけ上がります。以降に示す他のコマンドも同じです。
> ただし、lifelog のような links で他のサービスに依存しているサービスの場合は依存先のサービスも上がります。

`-d` をつけるとデーモンとして起動します。ちゃんと起動してるか気になる場合は以下で全コンテナを混ぜたログが出ます。

``` sh
$ docker-compose -f logs
```

コンテナの停止は以下です。

``` sh
$ docker-compose stop
```

停止したコンテナは以下で削除できます。y/N を聞かれるのが面倒であれば `-f` をつけます。

``` sh
$ docker-compose rm
```

駆け足でしたが Docker のところはだいたいこんなところで説明を終わります。
