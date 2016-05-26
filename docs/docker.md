# Docker による環境構築

ここまでで lifelog アプリケーション、PostgreSQL や Keycloak と 3 つのプロセスが出てきました。

さらにリバースプロキシや別の API サーバ、Web フロントエンドなども増えていくとしたら、さらに構築が面倒になりそうです。というわけで、簡単にセットアップできるよう Docker イメージを作ってみましょう。

Docker なしでここまで頑張ってこられた方も、そろそろあきらめて Dokcer が使える環境を用意してみてください。

再掲になりますが Docker のセットアップについては下記公式サイトや[日本語化プロジェクト](http://docs.docker.jp/)、各種 Web 情報をご覧ください。

* [Windows](http://docs.docker.com/windows/started/)
* [Mac OS X](http://docs.docker.com/mac/started/)
* [Linux](http://docs.docker.com/linux/started/)

Docker セットアップ後は sudo なしで docker コマンドが叩けるよう、ユーザを `docker` グループに入れておくと便利です。

## lifelog の Docker イメージのビルド

まずは lifelog の Dokcer イメージを作るところからです。

以下内容で `Dockerfile` という Makefile のようなものを lifelog-docker ディレクトリ直下に作ります。

``` dockerfile
FROM jboss/base-jdk:8
MAINTAINER hogehoge <hoge at example.com>

ADD lifelog/target/lifelog-docker-swarm.jar /opt/lifelog-docker-swarm.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/lifelog-docker-swarm.jar"]
```

おおざっぱに以下に内容を説明します。

* FROM: `jboss/base-jdk:8` は、OpenJDK 8 がインストールされている CentOS 7.1.1503 環境です。これをベースにイメージを作っていきます。
* MAINTAINER: javadoc でいう @author のようなもので、内容は任意です。
* ADD: ホスト側の lifelog-docker-swarm.jar を /opt 以下に追加します。
* EXPOSE: ここで指定したポート番号は他の Docker コンテナから環境変数を通して取得できます。
* ENTRYPOINT: このイメージから Dokcer コンテナを起動する際に実行されるコマンドです。

> ここでは最低限のことしかしていませんが、実行可能 jar だと Dockerfile はずいぶん書きやすいです。
> もし普通の WildFly でやろうとすると以下のようなイメージになります。いろいろシェルを用意したりして、ちょっとめんどっちいですね。
> https://github.com/rafabene/devops-demo/tree/master/Dockerfiles/ticketmonster

上記の Dockerfile の通り、ビルドした lifelog-docker-swarm.jar が必要ですので、先に lifelog をビルドしておきます。

``` sh
$ ./mvnw clean package -pl lifelog-docker/lifelog
```

`docker build` で lifelog の Docker イメージを作成します。`lifelog-docker` としているところは Dockerfile があるディレクトリを指定します。initial ディレクトリにいるのであれば下記コマンドです。

``` sh
$ docker build -t <お好きなお名前>/lifelog lifelog-docker
```

`<お好きなお名前>` のところは慣例として [Docker Hub](https://hub.docker.com/) でのログインユーザ名を記載します。ここでは emag としておきます。

> 今回は利用しませんが、Docker Hub でアカウントを作成すると、自分で作ったイメージを push できるようになります。
> push したイメージは docker pull で任意のホスト上で利用できるようになります。
> FROM で指定している jboss/base-jdk:8 も Docker Hub に登録されているもので、これを pull しています。
> https://hub.docker.com/r/jboss/base-jdk/

イメージができたかは `docker images` で確認できます。

``` sh
$ docker images
REPOSITORY    TAG     IMAGE ID      CREATED         VIRTUAL SIZE
emag/lifelog  latest  544dbb966fa0  37 minutes ago  630.4 MB
```

次に `docker run` で lifelog のコンテナを起動します。

``` sh
$ docker run -d --name lifelog -p 8080:8080 <docker build 時につけたお好きなお名前。ここでは emag>/lifelog
```

* -d: デーモンとして起動
* --name: コンテナに名前をつける場合指定。ここでは lifelog
* -p: 8080:8080 を指定することで、ローカルホスト(Docker ホスト)の 8080 ポートを Docker コンテナの 8080 ポートにポートフォワード

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

## docker-maven-plugin

アプリケーションを直すたびに `docker build` をするのでもまあいいのですが、せっかくなら Maven と連携したいところです。

いろいろ Docker 向けの Maven プラグインはあるようですが、ここでは `org.jolokia:docker-maven-plugin` を使ってみます。

https://github.com/rhuss/docker-maven-plugin

``` xml
<groupId>org.jolokia</groupId>
<artifactId>docker-maven-plugin</artifactId>
```

もう lifelog-docker/lifelog/pom.xml には設定済みですが、まず `<configuration>` -> `<images>` -> `<image>` 以下に Dockerfile 相当の設定を記述します。

以下の部分が Dockerfile 相当です。

``` xml
<name>emag/lifelog</name>
<alias>lifelog</alias>
<build>
  <from>jboss/base-jdk:8</from>
  <maintainer>emag</maintainer>
  <tags>
    <tag>latest</tag>
    <tag>${project.version}</tag>
  </tags>
  <ports>
    <port>8080</port>
  </ports>
  <entryPoint>
    <exec>
      <arg>java</arg>
      <arg>-jar</arg>
      <arg>/opt/lifelog-swarm.jar</arg>
    </exec>
  </entryPoint>
  <assembly>
    <user>jboss:jboss:jboss</user>
    <basedir>/opt</basedir>
    <inline>
      <dependencySets>
        <dependencySet>
          <useProjectAttachments>true</useProjectAttachments>
          <includes>
            <include>com.example:lifelog-docker:jar:swarm</include>
          </includes>
          <outputFileNameMapping>lifelog-swarm.jar</outputFileNameMapping>
        </dependencySet>
      </dependencySets>
    </inline>
  </assembly>
</build>
```

以下のところが `docker run` するところに相当します。alias のところがちょっとわかりづらいですが、上記で指定した `<alias>lifelog</alias>` の部分が `docker run` の `--name` オプションとして使われます。

``` xml
<run>
  <namingStrategy>alias</namingStrategy>
  <ports>
    <port>8080:8080</port>
  </ports>
</run>
```

設定できたら以下コマンドでアプリケーションのビルドをしつつ Docker イメージのビルド、コンテナの起動までできます。

``` sh
$ ./mvnw clean package docker:build docker:start -pl lifelog-docker/lifelog
```

その他の設定については以下を参照ください。

http://ro14nd.de/docker-maven-plugin

あ、あとついでに Keycloak の方も同じ要領で設定しておきます。

``` xml
<plugin>
  <groupId>org.jolokia</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>${version.docker-maven-plugin}</version>
  <configuration>
    <images>
      <image>
        <name>emag/keycloak</name>
        <alias>lifelog-keycloak</alias>
        <build>
          <from>jboss/base-jdk:8</from>
          <maintainer>emag</maintainer>
          <tags>
            <tag>latest</tag>
            <tag>${project.version}</tag>
          </tags>
          <ports>
            <port>8080</port>
          </ports>
          <entryPoint>
            <exec>
              <arg>java</arg>
              <arg>-Dkeycloak.migration.action=import</arg>
              <arg>-Dkeycloak.migration.provider=singleFile</arg>
              <arg>-Dkeycloak.migration.file=/opt/lifelog.json</arg>
              <arg>-jar</arg>
              <arg>/opt/keycloak-server-lifelog-docker-swarm.jar</arg>
            </exec>
          </entryPoint>
          <assembly>
            <user>jboss:jboss:jboss</user>
            <basedir>/opt</basedir>
            <inline>
              <dependencySets>
                <dependencySet>
                  <useProjectAttachments>true</useProjectAttachments>
                  <includes>
                    <include>com.example:keycloak-server-lifelog-docker:jar:swarm</include>
                  </includes>
                  <outputFileNameMapping>keycloak-server-lifelog-docker-swarm.jar</outputFileNameMapping>
                </dependencySet>
              </dependencySets>
              <fileSets>
                <fileSet>
                  <directory>${project.basedir}</directory>
                  <outputDirectory>.</outputDirectory>
                  <includes>
                    <include>lifelog.json</include>
                  </includes>
                </fileSet>
              </fileSets>
            </inline>
          </assembly>
        </build>
        <run>
          <namingStrategy>alias</namingStrategy>
          <ports>
            <port>8180:8080</port>
          </ports>
        </run>
      </image>
    </images>
  </configuration>
</plugin>
```

上記が設定できたら以下コマンドで Docker イメージのビルドと起動しておきます。

``` sh
$ ./mvnw clean package docker:build docker:start -pl lifelog-docker/keycloak-server
```

## PostgreSQL、Keycloak とのコンテナ間通信

今までデータソース設定の接続 URL や keycloak.json 中の Keycloak サーバの URL は(ポートフォワードした) `localhost:<port>` に決め打ちでした。ローカルでやっている分にはいいのですが、運用環境などでは別の IP アドレス(またはホスト名)であったりポート番号になるでしょう。

Docker コンテナは同じホスト上では `--link <コンテナIDまたは名前>:<適当な名前>` オプションを使うと、その指定したコンテナの EXPOSE したポートやコンテナの IP アドレスを環境変数として取得することができます。

例えば PostgreSQL を以下のように起動したとします。

/tmp は適宜変更

``` sh
$ docker run -d --name lifelog-db \
  -e POSTGRES_USER=lifelog -e POSTGRES_PASSWORD=lifelog \
  -v /tmp/lifelog/pgdata/data:/var/lib/postgresql/data \
  -p 5432:5432 \
  postgres:9.4.5
```

ここで `--link lifelog-db:db` としてコンテナを起動すると、`db` を大文字にした `DB` を prefix とし、EXPOSE したポート番号(5432)を含んだ各種環境変数が得られます。確認のため、以下のようにチェック用のコンテナを起動してみます。

``` sh
$ docker run -it --rm --link lifelog-db:db jboss/base-jdk:8
[jboss@e4d670ea17fe ~]$ env
DB_PORT_5432_TCP_ADDR=172.17.0.13
DB_PORT_5432_TCP_PORT=5432
[他にも色々]

[コンテナから抜けるには exit や Ctrl + D など。--rm を指定しているので自動的にこのコンテナは削除されます]
[jboss@e4d670ea17fe ~]$ exit
```

よって、`--link` をつけて起動するアプリケーション(lifelog)側でこの環境変数を読めばよいということになります。

> 別ホストの場合は `docker run` に `-e` オプションで環境変数を渡せるので、`-e DB_PORT_5432_TCP_ADDR=db.server` などとします。

というわけで、URL は lifelog 実行時に設定できるよう、新たに LifeLogConfig という環境情報を外部からもらって解決するためのクラスを用意しておきました。

``` java
package wildflyswarmtour.lifelog;

import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;

class LifeLogConfig {

  private LifeLogConfig() {}

  static boolean isProduction() {...}
  static String dbHost() {...}
  static int dbPort() {...}

  static String kyeCloakJsonFromTemplate() {...}
  static private String keycloakHost() {...}
  static private int keycloakPort() {...}

}
```

LifeLogContainer では PostgreSQL のホスト名およびポート番号をこの LifeLogConfig から取得するようにします。

``` java
if (LifeLogConfig.isProduction()) {
  container.fraction(new DatasourcesFraction()
    .jdbcDriver("org.postgresql", (d) -> {
      [...]
    })
    .dataSource("lifelogDS", (ds) -> {
      ds.driverName("org.postgresql");
      ds.connectionUrl(String.format("jdbc:postgresql://%s:%d/lifelog", LifeLogConfig.dbHost(), LifeLogConfig.dbPort()));
      ds.userName("lifelog");
      ds.password("lifelog");
    })
  );
}
```

また、`keycloak.json` を `keycloak.json.template` とリネームしておきます。LifeLogDeployment では keycloak.json.template を書き換えて、keycloak.json という名前でアーカイブに含めるようにします。こういうことが簡単にできるのも WildFly Swarm の魅力ですね。

``` java
deployment.addAsWebInfResource(
      new StringAsset(LifeLogConfig.kyeCloakJsonFromTemplate()), "keycloak.json");
```

> LifeLogConfig.kyeCloakJsonFromTemplate() はちょっと苦しい実装ですね。
> Keycloak サーバの URL を変更できる API が Keycloak のクライアント API にあった気がしたのですが、忘れてしまいました。。

詳しい内容は complete の方を見ていただくとして、重要なのは LifeLogConfig でシステムプロパティ以外に `--link` 指定時に得られる環境変数も利用して DB や Keycloak の情報を設定しているところです。

ではこれから `--link` を用いて実行したいと思いますが、まず先に PostgreSQL と Keycloak をそれぞれの Docker コンテナを `lifelog-db`、`lifelog-keycloak` という名前をつけて起動しておきます。

PostgreSQL は以下で起動します。

``` sh
$ docker run -d --name lifelog-db \
  -e POSTGRES_USER=lifelog -e POSTGRES_PASSWORD=lifelog \
  -v /tmp/lifelog/pgdata/data:/var/lib/postgresql/data \
  -p 5432:5432 \
  postgres:9.4.5
```

Keycloak は maven-docker-plugin が設定されている前提ですが以下でビルドと起動を行います(前節で起動していれば不要)。

``` sh
$ ./mvnw clean package docker:build docker:start -pl lifelog-docker/keycloak-server
```

PostgreSQL と Keycloak が起動したら、lifelog のビルドおよび `docker build` で Docker イメージをビルドしたあと、以下のように `docker run` で lifelog コンテナを起動します。`-e ENVIRONMENT_PRODUCTION` で DB の切替を、`--link` で DB と Keycloak の情報を取得しています。

``` sh
$ docker run -d --name lifelog \
  -e ENVIRONMENT_PRODUCTION=true \
  --link lifelog-db:db \
  --link lifelog-keycloak:keycloak \
  -p 8080:8080 \
  emag/lifelog
```

Maven プラグインを利用する場合は、以下のように `<run>` のところに `<env>` と `<links>` 要素を追加しておきます。

``` xml
<run>
  [...]
  <env>
    <ENVIRONMENT_PRODUCTION>${swarm.lifelog.production}</ENVIRONMENT_PRODUCTION>
  </env>
  <links>
    <link>lifelog-db:db</link>
    <link>lifelog-keycloak:keycloak</link>
  </links>
  [...]
</run>
```

``` sh
$ ./mvnw clean package docker:build docker:start -pl lifelog-docker/lifelog -Dswarm.lifelog.production=true
```

今まで TOKEN を取得するのにポートフォワードしていた http://localhost:8180/auth を指していましたが、lifelog の keycloak.json ではもうこちらではなく Keycloak コンテナの IP アドレスを指しているため、取得時の URI を変える必要があります。

まずは Keycloak コンテナの IP アドレスを取得します。

``` sh
$ KEYCLOAK_SERVER=`docker ps | grep keycloak | awk '{print $1}' | xargs docker inspect --format="{{.NetworkSettings.IPAddress}}:8080"`
```

> 8080 のところもがんばればできますが、ちょっと複雑になるようなので妥協! どなたかスマートなやり方をご存知でしたらこっそり教えてください。

上記で得た Keycloak コンテナの IP アドレスを用いて TOKEN を取得します。

``` sh
$ RESULT=`curl --data "grant_type=password&client_id=curl&username=user1&password=password1" http://$KEYCLOAK_SERVER/auth/realms/lifelog/protocol/openid-connect/token`
$ TOKEN=`echo $RESULT | sed 's/.*access_token":"//g' | sed 's/".*//g'`
```

あとはおなじみの curl でアクセスしてみてください。

> 逆に、TOKEN を取得するところで今までどおり localhost:8180 で行うとどうなるかも試してみるとおもしろいかもですね。

## Docker Compose

ここまでは PostgreSQL や Keycloak は事前に手動で立ち上げていました。

lifelog が PostgreSQL や Keycloak に依存していることがすぐわかればいいのですが、依存先が複雑になってくると管理が面倒になってきます。今後もリバースプロキシなどコンテナが増えていくことも考えると、そろそろコンテナ管理も楽をしたいところです。そこで、今回は Docker Compose を利用します。

Docker Compose は複数コンテナの管理を簡単にするためのものです。

http://docs.docker.com/compose/

まずは以下ドキュメントに従ってインストールします。バイナリをパスの通ったところにインストールした上で実行権限をつけておきます。

http://docs.docker.com/compose/install/

``` sh
$ docker-compose --version
docker-compose version: 1.5.1
```

次に、以下のような `docker-compose.yml` という設定ファイルを用意します。

`docker run` するときの情報を並べただけって感じですね。

``` yaml
lifelog:
  image: emag/lifelog:1.0.0
  environment:
    ENVIRONMENT_PRODUCTION: "true"
  links:
    - lifelog-db:db
    - lifelog-keycloak:keycloak
  ports:
    - 8080:8080
lifelog-db:
  image: postgres:9.4.5
  environment:
    POSTGRES_USER: lifelog
    POSTGRES_PASSWORD: lifelog
  volumes:
    - /tmp/lifelog/pgdata/data:/var/lib/postgresql/data
  ports:
    - 5432:5432
lifelog-keycloak:
  image: emag/keycloak:1.0.0
  ports:
    - 8180:8080
```

前に上げていたプロセスや Docker コンテナがある場合は、ポートが衝突するので停止しておいてください。

コンテナの起動は以下のように行います。以下で全部のコンテナが起動します。`-d` をつけない場合はフォアグラウンドです。

> 引数に設定ファイルで指定した名前(サービス名)を渡すとそのサービスだけ上がります。以降に示す他のコマンドも同じです。
> ただし、lifelog のような links で他のサービスに依存しているサービスの場合は依存先のサービスも上がります。

``` sh
$ docker-compose -f lifelog-docker/docker-compose.yml up -d
```

デーモンとして起動しているので(`up -d`)すぐ上記コマンドは抜けます。ちゃんと起動してるか気になる場合は以下で全コンテナを混ぜたログが出ます。

``` sh
$ docker-compose -f lifelog-docker/docker-compose.yml logs
```

コンテナの停止は以下です。

``` sh
$ docker-compose -f lifelog-docker/docker-compose.yml stop
```

停止したコンテナは以下で削除できます。y/N を聞かれるのが面倒であれば `-f` をつけます。

> 停止しないとコンテナ削除できないみたいですね。ちょっと面倒。。

``` sh
$ docker-compose -f lifelog-docker/docker-compose.yml rm
```

駆け足でしたが Docker のところはだいたいこんなところで説明を終わります。
