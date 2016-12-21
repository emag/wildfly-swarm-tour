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
$ ./mvnw clean package
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
  -p 8080:8080 \
  emag/lifelog \
  -Dswarm.project.stage.file=file:///tmp/project/lifelog-project-stages.yml
```

* -d: デーモンとして起動
* --name: コンテナに名前をつける場合指定。ここでは lifelog
* -v: `<host_path>:<container_path>` という書式でホストの `<host_path>` を `<container_path>` にマウント
* -p: 8080:8080 を指定することで、ローカルホスト(Docker ホスト)の 8080 ポートを Docker コンテナの 8080 ポートにポートフォワード
* emag/lifelog: イメージを指定。以降に指定した値は ENTRYPOINT に指定したコマンドのオプションのように扱えます。

`docker ps` コマンドで、起動中のコンテナを確認できます。以下のように表示されていれば OK です。

``` sh
$ docker ps
CONTAINER ID  IMAGE         COMMAND                CREATED        STATUS        PORTS                   NAMES
fe8ece48806b  emag/lifelog  "java -jar /opt/lifel" 3 minutes ago  Up 3 minutes  0.0.0.0:8080->8080/tcp  lifelog
```

では、ここで curl で先ほどのように POST してみるとどうなるでしょうか。

``` sh
$ curl -X POST -H "Content-Type: application/json" -H "Authorization: bearer $TOKEN" -d '{"description" : "test"}' localhost:8080/entries -v
[...]
< HTTP/1.1 403 Forbidden
```

403 が返ってきてしまいました。これはどういうことでしょう?

これは keycloak.json が正しくアーカイブに含まれていなかったことに起因します。`docker logs <コンテナ名>` コマンドで lifelog コンテナのログを確認してみます。

> ちなみに docker logs コマンドは -f をつけると follow モードになります

``` sh
$ docker logs lifelog
[...]
2016-12-21 13:27:21,472 WARN  [org.wildfly.swarm.keycloak.runtime.SecuredArchivePreparer] (main) Unable to get keycloak.json from 'keycloak.json', fall back to get from classpath: java.nio.file.NoSuchFileException: keycloak.json
```

'keycloak.json' というパスに keycloak.json がなかった、と言っています。

lifelog コンテナ起動時に -v オプションとして `pwd`:/tmp/project としました。これは Docker ホストのカレントディレクトリである docker プロジェクト直下を、コンテナ内の /tmp/project にマウントすることを表します。
そしてさりげなく `-Dswarm.project.stage.file=file:///tmp/project/lifelog-project-stages.yml` と project-stages.yml の指定を `file:///tmp/project` としていますね。
よって lifelog コンテナの lifelog アプリケーションは project-stages.yml のパスは解決できています。

しかし、project-stages.yml 内で `swarm.keycloak.json.path` の値は相対パスで keycloak.json とパス指定しているのでした。
コンテナ内で java コマンドを実行するパスには確かに keycloak.json はないので、この相対パスではだめですね。
project-stages.yml と同様、`/tmp/project/keycloak.json` といった形にする必要がありそうです。

> 今回のように `swarm.keycloak.json.path` で指定されたパスに keycloak.json が無かった場合はクラスパスから探すようにフォールバックするのですが、
> クラスパスにも存在しないのでアーカイブには keycloak.json が含まれていません。そういった場合、Keycloak クライアントは 403 を返すという挙動になっています。

話は変わりますがこの lifelog コンテナを起動する際、ステージ指定していなかったため default ステージとなり、データベースは H2 が利用されます。PostgreSQL を利用するよう production を指定するとどうなるでしょうか。

とりあえず、いったん lifelog コンテナは削除しておきます。

``` sh
$ docker rm -f lifelog
```

ついでにここで停止・削除関係のコマンドも確認しておきましょう。

* 起動中のコンテナを停止: `docker stop lifelog`
* 停止中のコンテナを削除: `docker rm lifelog`
* 起動中のコンテナを強制削除: `docker rm -f lifelog`

では、あらためて以下コマンドで production 指定で lifelog コンテナを起動します。

``` sh
$ docker run -it --rm \
  --name lifelog  \
  -v `pwd`:/tmp/project \
  -p 8080:8080 \
  emag/lifelog \
  -Dswarm.project.stage.file=file:///tmp/project/lifelog-project-stages.yml \
  -Dswarm.project.stage=production
```

> ここでは -d をつけていないのでフォアグランドでコンテナが起動します。
> また、--rm はコンテナ停止とともにコンテナを削除するオプションです。

わんさと wARN と ERROR が出ますね。注目すべきは以下です。

``` sh
Caused by: org.postgresql.util.PSQLException: Connection to localhost:5432 refused.
Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

localhost:5432 にリスンしている PostgreSQL に接続できなかった、とあります。localhost:5432 というのは project-stages.yml で指定した値ですね。

この場合、コンテナ内で起動している lifelog アプリケーションから見た場合、localhost というのはコンテナ内の自分自身ということになります。
lifelog コンテナで PostgreSQL が動いているわけではないので、接続に失敗したというのはそりゃそうだ、ということですね。

実際に PostgreSQL が起動しているのは別途起動していた lifelog-db コンテナでした。コンテナ内の PostgreSQL の 5432 ポートに対して Docker ホストの 5432 ポートをポートフォワードしています。
なので localhost は Docker ホスト自身のことを指していたのでしたね。実際には lifelog コンテナが lifelog-db コンテナに通信できるようにならないといけません。

先ほどの keycloak.json のパス指定の問題とあわせて、このコンテナ間通信については次節で対応します。

なお、今フォアグランドで起動しているコンテナは `Ctrl + C` で停止できます。

## PostgreSQL、Keycloak Server とのコンテナ間通信

まずはコンテナ間通信の問題を解決しましょう。

今までデータソース設定の接続 URL や keycloak.json 中の Keycloak Server の URL は Docker コンテナに対してホストからポートフォワードした `localhost:<port>` に決め打ちでした。これでは前節で述べたとおり、コンテナ間でやりとりはできなくなってしまいます。コンテナ間で通信したい場合は `--link` オプションを利用します。

Docker コンテナは同じホスト上では `--link <コンテナIDまたは名前>:<エイリアス>` オプションを使うと、エイリアス名で名前解決することができます。

例えば、今回 PostgreSQL を以下のように起動しています。

<pre><code class="lang-sh">$ docker run -it -d \
  --name lifelog-db \
  -e POSTGRES_USER=lifelog -e POSTGRES_PASSWORD=lifelog \
  -p 5432:5432 \
  postgres:{{book.versions.postgresql}}
</code></pre>

確認のため `--link lifelog-db:db` を付与して適当なコンテナを起動し、`db` に対して ping を打ってみます。

``` sh
$ $ docker run -it --rm --link lifelog-db:db jboss/base-jdk:8 ping db
PING db (172.17.0.3) 56(84) bytes of data.
64 bytes from db (172.17.0.3): icmp_seq=1 ttl=64 time=0.130 ms
64 bytes from db (172.17.0.3): icmp_seq=2 ttl=64 time=0.076 ms
64 bytes from db (172.17.0.3): icmp_seq=3 ttl=64 time=0.119 ms
[...]
# 気が済んだら Ctrl + C で停止
```

`172.17.0.3` と表示されている通り、コンテナの IP アドレスが名前解決できています。
よって今まで localhost:5432 としていた PostgreSQL の URL は `db:5432` にすればよいということになります。
同様に lifelog-auth コンテナのエイリアスを auth としたして、localhost:18080 は `auth:8080` となります。

project-stages.yml に Docker 用のステージを追加してみましょう。

``` yml
---
project:
  stage: docker
swarm:
  datasources:
    data-sources:
      lifelogDS:
        driver-name: postgresql
        connection-url: jdbc:postgresql://db:5432/lifelog
        user-name: lifelog
        password: lifelog
  keycloak:
    json:
      path: /tmp/project/keycloak-docker.json
```

変更点は `swarm.datasources.data-sources.lifelogDS.connection-url` と `swarm.keycloak.json.path` です。
keycloak.json も Docker 用のものを keycloak-docker.json として以下の内容でプロジェクト直下に用意しておきます。auth-server-url だけ変更しておきます。

``` json
{
  [...]
  "auth-server-url": "http://auth:8080/auth",
  [...]
}
```

PostgreSQL と Keycloak Server のコンテナをそれぞれ lifelog-db、lifelog-auth という名前で起動しているとして、以下のように `docker run` で lifelog コンテナを起動します。

``` sh
$ docker run -it -d \
  --name lifelog  \
  -v `pwd`:/tmp/project \
  --link lifelog-db:db \
  --link lifelog-auth:auth \
  -p 8080:8080 \
  emag/lifelog \
  -Dswarm.project.stage.file=file:///tmp/project/lifelog-project-stages.yml \
  -Dswarm.project.stage=docker
```

`docker logs -f lifelog` とすると lifelog のログが確認できます。

まず、ログ中にあるコネクションの URL が、以下のように設定されており、DB とのコネクションエラーが出ていなければオッケーです。

```
swarm.datasources.data-sources.lifelogDS.connection-url = jdbc:postgresql://db:5432/lifelog
```

また、keycloak.json が見つからない旨のログが出ていないことも確認してみてください。

問題なさそうであれば lifelog コンテナが正しく動作しているかチェックしてみます。

今まで TOKEN を取得するのにポートフォワードしていた http://localhost:18080/auth を指していましたが、lifelog の keycloak.json ではもうこちらではなく http://auth:8080/auth を指しているため、取得時の URI を変える必要があります。

Docker ホストからは auth というホスト名の名前解決はできないため、`docker exec` コマンドを利用して lifelog コンテナ経由でトークンを取得することにします。

> 先ほど --link を試したときのように使い捨てのコンテナで行っても構いません

``` sh
$ RESULT=`docker exec -it lifelog curl --data "grant_type=password&client_id=curl&username=user1&password=password1" http://auth:8080/auth/realms/lifelog/protocol/openid-connect/token`
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

次に、以下のような `docker-compose.yml` という設定ファイルをプロジェクト直下に用意します。

`docker run` するときの情報を並べただけって感じですね。

<pre><code class="yml">version: '2'

services:
  lifelog:
    image: emag/lifelog
    volumes:
      - .:/tmp/project
    links:
      - lifelog-db:db
      - lifelog-auth:auth
    ports:
      - 8080:8080
    command: ["-Dswarm.project.stage.file=file:///tmp/project/lifelog-project-stages.yml", "-Dswarm.project.stage=docker"]

  lifelog-db:
    image: postgres:{{book.versions.postgresql}}
    environment:
      POSTGRES_USER: lifelog
      POSTGRES_PASSWORD: lifelog

  lifelog-auth:
    image: jboss/keycloak:{{book.versions.keycloak}}
    volumes:
      - .:/tmp/project
    command: ["-b 0.0.0.0", "-Dkeycloak.migration.action=import", "-Dkeycloak.migration.provider=singleFile", "-Dkeycloak.migration.file=/tmp/project/lifelog-realm.json"]
</code></pre>

まぎらわしいので前に手動で上げた lifelog/lifelog-db/lifelog-auth コンテナは止めておくか削除しておきましょう。

コンテナの起動は以下のように行います。以下で全部のコンテナが起動します。`-d` をつけない場合はフォアグラウンドです。

``` sh
$ docker-compose up -d
```

> 引数に設定ファイルで指定した名前(サービス名)を渡すとそのサービスだけ上がります。以降に示す他のコマンドも同じです。
> ただし、lifelog のような links で他のサービスに依存しているサービスの場合は依存先のサービスも上がります。

`-d` をつけるとデーモンとして起動します。ちゃんと起動してるか気になる場合は以下で全コンテナを混ぜたログが出ます。

``` sh
$ docker-compose logs -f
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
