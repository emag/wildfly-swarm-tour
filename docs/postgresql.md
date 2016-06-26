# PostgreSQL の利用

前章で見たとおり、従来の WildFly と同様にすぐに使えるデータベースとして H2 が同梱されています。

ただ、せっかくなので今回は PostgreSQL での場合もやってみたいと思います。開発は H2、プロダクションは PostgreSQL みたいなイメージで、起動時にシステムプロパティで切り替えられるようにしましょう。

PostgreSQL はお好みの方法で用意してもらえればと思いますが以下を想定しています。

* データベース名、データベースのユーザ、パスワード： lifelog
* ホスト: localhost
* ポート: 5432

PostgreSQL が初めてという方でかつ、Docker が利用できる環境であれば以下のようにしてコンテナを用意するのが楽だと思います。

> このドキュメントでは最後の章で Docker を使うので、そういう意味でも Docker での利用をおすすめします。

<pre><code class="lang-sh">$ docker run -it -d \
  --name lifelog-db \
  -e POSTGRES_USER=lifelog -e POSTGRES_PASSWORD=lifelog \
  -p 5432:5432 \
  postgres:{{book.versions.postgresql}}
</code></pre>

> 本来こういった永続化されるデータに対しては Data Volume Container を用意したほうがよいですが、簡単のため割愛します。

ちゃんとデータベースができたか、psql クライアントで確認してみます。

<pre><code class="lang-sh">$ docker run --rm -it \
  --link lifelog-db:db \
  postgres:{{book.versions.postgresql}} \
  sh -c 'exec psql -h "$DB_PORT_5432_TCP_ADDR" -p "$DB_PORT_5432_TCP_PORT" -U lifelog'
</code></pre>

上記コマンドを実行すると以下のようなプロンプトが表示されるので、適宜入力していきます。

<pre><code class="lang-sh">Password for user lifelog: # lifelog と入力
psql ({{book.versions.postgresql}})
Type "help" for help.

lifelog=# \l # \l と入力
                                 List of databases
   Name    |  Owner   | Encoding |  Collate   |   Ctype    |   Access privileges
-----------+----------+----------+------------+---------- --+-----------------------
 lifelog   | postgres | UTF8     | en_US.utf8 | en_US.utf8 |
 postgres  | postgres | UTF8     | en_US.utf8 | en_US.utf8 |
 template0 | postgres | UTF8     | en_US.utf8 | en_US.utf8 | =c/postgres          +
           |          |          |            |            | postgres=CTc/postgres
 template1 | postgres | UTF8     | en_US.utf8 | en_US.utf8 | =c/postgres          +
           |          |          |            |            | postgres=CTc/postgres
(4 rows)
</code></pre>

だいじょぶそうですね。

もうちょっと凝った設定がしたいぜ、という方は以下の Docker Hub のページを参照ください。

https://hub.docker.com/_/postgres/

PostgreSQL がセットアップできたらアプリケーションから使えるようにします。

`postgresql/initial` の pom.xml を見てみてください。

まず、利用する PostgreSQL JDBC ドライバの依存性を追加されています。

``` xml
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <version>${version.postgresql}</version>
</dependency>
```

次に、後述する PostgreSQL JDBC ドライバ用の module.xml で `${version.postgresql}` の値が上書きされるようにリソース処理を設定しています。

``` xml
<build>
  <finalName>${project.artifactId}</finalName>

  <resources>
    <resource>
      <directory>src/main/resources</directory>
      <filtering>true</filtering>
    </resource>
  </resources>
  [...]
</build>
```

また、maven-failsafe-plugin に自分で追加した module を読めるようにする設定(`<configuration>` 要素)を追加しています。これがなくてもプロダクションコードは動きますが、Arquillian 側で PostgreSQL の JDBC ドライバの module.xml が読めません。

``` xml
<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <version>${version.maven-failsafe-plugin}</version>
  <configuration>
    <systemPropertyVariables>
      <swarm.build.modules>${project.build.outputDirectory}/modules/</swarm.build.modules>
    </systemPropertyVariables>
  </configuration>
  [...]
</plugin>
```

次に、PostgreSQL JDBC ドライバの module.xml を `src/main/resources/modules/org/postgresql/main/module.xml` に以下内容で配置します。

> この module.xml は WildFly 独自のもので、モジュールクラスローディングをするために必要です。

``` xml
<?xml version="1.0" ?>
<module xmlns="urn:jboss:module:1.3" name="org.postgresql">
  <resources>
    <artifact name="org.postgresql:postgresql:${version.postgresql}"/>
  </resources>

  <dependencies>
    <module name="javax.api"/>
    <module name="javax.transaction.api"/>
  </dependencies>
</module>
```

`${version.postgresql}` の部分はビルド時に置換されます。

次に、システムプロパティの値によって H2 と PostgreSQL を切り替えられるようにする部分です。

まず `lifelog-stage-config.yml` というファイルを以下の内容で適当なパス(ここではプロジェクト直下)に配置します。

``` yml
database:
  driver:
    name: "h2"
    className: "org.h2.Driver"
    xaDatasourceClass: "org.h2.jdbcx.JdbcDataSource"
    moduleName: "com.h2database.h2"
  connection:
    url: "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
  userName: "sa"
  password: "sa"
---
project:
  stage: production
database:
  driver:
    name: "postgresql"
    className: "org.postgresql.Driver"
    xaDatasourceClass: "org.postgresql.xa.PGXADataSource"
    moduleName: "org.postgresql"
  connection:
    url: "jdbc:postgresql://localhost:5432/lifelog"
  userName: "lifelog"
  password: "lifelog"
```

`project: stage:` の部分でステージを指定し、各ステージは `---` で区切ります。一番上のように何も指定しない場合は default ステージとみなされます。

その他の情報については以下ドキュメントを参考ください。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/configuration/index.html#_configuration_overlays_using_stage_properties

> なお、`stage-config.yml` という名前にした場合、このファイルがモジュール内やアプリケーションのクラスパスに存在すると自動的に読み込まれますが、値を変更するたびにビルドし直すのも面倒ですので外出ししています。また、java コマンド実行時のカレントパスにあった場合も読まれますが、Arquillian 実行時はカレントパスが変わるため、自分で指定する方が無難です。
>
> https://github.com/wildfly-swarm/wildfly-swarm-core/blob/{{book.versions.swarm_core}}/container/api/src/main/java/org/wildfly/swarm/cli/CommandLine.java#L105-L125

次に `lifelog-stage-config.yml` をもとに DatasourcesFraction を組み立てるクラス(LifeLogConfiguration)を用意します。これも別に用意せずに LifeLogContainer にベタ書きでもいいですが、今後 Fraction の設定も少し増えるのでわけておきます。ついでに JPAFraction を提供するメソッドも作っておきました。


``` java
package lifelog;

import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogConfiguration {

  private Container container;

  LifeLogConfiguration(Container container) {
    this.container = container;
  }

  DatasourcesFraction datasourcesFraction(String datasourceName) {
    return new DatasourcesFraction()
        .jdbcDriver(resolve("database.driver.name"), (d) -> {
          d.driverClassName(resolve("database.driver.className"));
          d.xaDatasourceClass(resolve("database.driver.xaDatasourceClass"));
          d.driverModuleName(resolve("database.driver.moduleName"));
        })
        .dataSource(datasourceName, (ds) -> {
          ds.driverName(resolve("database.driver.name"));
          ds.connectionUrl(resolve("database.connection.url"));
          ds.userName(resolve("database.userName"));
          ds.password(resolve("database.password"));
        });
  }

  JPAFraction jpaFraction(String datasourceName) {
    return new JPAFraction()
        .inhibitDefaultDatasource()
        .defaultDatasource("jboss/datasources/" + datasourceName);
  }

  private String resolve(String key) {
    return container.stageConfig().resolve(key).getValue();
  }

}
```

private メソッドの resolve(String key) が肝のところです。`container.stageConfig().resolve(key).getValue()` の key は `lifelog-stage-config.yml` の各キーをピリオド区切りで渡します。例えば `database: connection: url` なら `database.connection.url` です。

最後に LifeLogConfiguration を利用するように LifeLogContainer を変更します。

``` java
package lifelog;

import org.wildfly.swarm.container.Container;

public class LifeLogContainer {

  private static final String DATASOURCE_NAME = "lifelogDS";

  public static Container newContainer(String[] args) throws Exception {
    Container container = new Container(args);

    LifeLogConfiguration configuration = new LifeLogConfiguration(container);

    container
        .fraction(configuration.datasourcesFraction(DATASOURCE_NAME))
        .fraction(configuration.jpaFraction(DATASOURCE_NAME));

    return container;
  }

}
```

ここまででプロジェクト構成はおおよそ以下のようになります。

``` sh
.
├── lifelog-project-stages.yml
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src
    ├── main
    │   ├── java
    │   │   └── lifelog
    │   │       ├── App.java
    │   │       ├── LifeLogConfiguration.java
    │   │       ├── LifeLogContainer.java
    │   │       ├── LifeLogDeployment.java
    │   │       ├── api
    │   │       │   ├── EntryController.java
    │   │       │   └── EntryResponse.java
    │   │       └── domain
    │   │           ├── model
    │   │           │   ├── Entry.java
    │   │           │   └── converter
    │   │           │       └── LocalDateTimeConverter.java
    │   │           ├── repository
    │   │           │   └── EntryRepository.java
    │   │           └── service
    │   │               └── EntryService.java
    │   └── resources
    │       ├── META-INF
    │       │   └── persistence.xml
    │       └── modules
    │           └── org
    │               └── postgresql
    │                   └── main
    │                       └── module.xml
    └── test
        └── java
            └── lifelog
                └── api
                    └── EntryControllerIT.java
```

ここまで出来て、PostgreSQL も起動していることも確認したうえで lifelog をビルド、実行します。ステージ用ファイルとステージの指定はそれぞれシステムプロパティ `swarm.project.stage.file`　と `swarm.project.stage` を渡します。なお、ファイルの指定にはプロトコルを渡す必要があります。

``` sh
$ ./mvnw clean package \
  && java \
    -Dswarm.project.stage.file=file://`pwd`/lifelog-project-stages.yml \
    -Dswarm.project.stage=production \
    -jar target/lifelog-swarm.jar
```

POST したり psql でデータベースの中を見たりして、実際に PostgreSQL が使われていることを確認してみてください。

## IT 用のステージを用意

ついでに Arquillian でのテストも PostgreSQL を使ってやってみましょう。LifeLogContainer を共有しているため、特にテスト側で変更するところはありません。

``` sh
$ ./mvnw clean verify \
  -Dswarm.project.stage.file=file://`pwd`/lifelog-project-stages.yml \
  -Dswarm.project.stage=production
```

ただしこのままだと Integration Test なのにプロダクション環境のデータベースを使ってしまっていますね。`---` で区切って 1 つステージを増やしておきましょう。

``` yml
[...]
---
project:
  stage: it
database:
  driver:
    name: "postgresql"
    className: "org.postgresql.Driver"
    xaDatasourceClass: "org.postgresql.xa.PGXADataSource"
    moduleName: "org.postgresql"
  connection:
    url: "jdbc:postgresql://localhost:15432/lifelog"
  userName: "lifelog"
  password: "lifelog"
---
project:
  stage: production
[...]
```

ここでは PostgreSQL をもう 1 インスタンス、15432 ポートでリスンされていることを想定しています。

では Docker なりなんなりで上げておいて、としてもいいですが、Integration Test しか使わないサーバをずっと上げておくのも微妙ですね。できればテストの時だけ上がっているとうれしいところです。

シェルスクリプトを書いたりいろいろやり方はあると思いますが、Docker を前提として今回は以下の Maven プラグインを使ってみたいと思います。

https://github.com/fabric8io/docker-maven-plugin

先に設定を記載しておきます。

> H2 データベースでテストするときなど常に Docker コンテナを起動したいとも限らないので、it という id のプロファイルに設定をわけています。

``` xml
<profiles>
  <profile>
    <id>it</id>
    <build>
      <plugins>
        <plugin>
          <groupId>io.fabric8</groupId>
          <artifactId>docker-maven-plugin</artifactId>
          <version>${version.docker-maven-plugin}</version>
          <configuration>
            <logDate>default</logDate>
            <autoPull>true</autoPull>
            <images>
              <image>
                <alias>lifelog-db</alias>
                <name>postgres:${version.postgresql-server}</name>
                <run>
                  <env>
                    <POSTGRES_USER>lifelog</POSTGRES_USER>
                    <POSTGRES_PASSWORD>lifelog</POSTGRES_PASSWORD>
                  </env>
                  <ports>
                    <port>15432:5432</port>
                  </ports>
                  <wait>
                    <log>database system is ready to accept connections</log>
                    <time>20000</time>
                  </wait>
                  <log>
                    <prefix>LIFELOG_DB</prefix>
                    <color>yellow</color>
                  </log>
                </run>
              </image>
            </images>
          </configuration>

          <executions>
            <execution>
              <id>start</id>
              <phase>pre-integration-test</phase>
              <goals>
                <goal>build</goal>
                <goal>start</goal>
              </goals>
            </execution>
            <execution>
              <id>stop</id>
              <phase>post-integration-test</phase>
              <goals>
                <goal>stop</goal>
              </goals>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

まず大事なのは以下の設定です。

``` xml
<image>
  <alias>lifelog-db</alias>
  <!-- PostgreSQL のイメージ -->
  <name>postgres:${version.postgresql-server}</name>
  <run>
    <!-- 環境変数 -->
    <env>
      <POSTGRES_USER>lifelog</POSTGRES_USER>
      <POSTGRES_PASSWORD>lifelog</POSTGRES_PASSWORD>
    </env>
    <!-- ポート設定 -->
    <ports>
      <port>15432:5432</port>
    </ports>
    [...]
  </run>
</image>
```

PostgreSQL の Docker コンテナを実行するときの引数と対応しています。

<pre><code class="lang-sh">$ docker run -it -d \
  --name lifelog-db \
  -e POSTGRES_USER=lifelog -e POSTGRES_PASSWORD=lifelog \
  -p 5432:5432 \
  postgres:{{book.versions.postgresql}}
</code></pre>

また、以下の設定により、Integration Test の開始前にコンテナが起動し、終了すると削除されるようにしています。

``` xml
<executions>
  <execution>
    <id>start</id>
    <phase>pre-integration-test</phase>
    <goals>
      <goal>build</goal>
      <goal>start</goal>
    </goals>
  </execution>
  <execution>
    <id>stop</id>
    <phase>post-integration-test</phase>
    <goals>
      <goal>stop</goal>
    </goals>
  </execution>
</executions>
```

その他の設定については以下ドキュメントを参考ください。

https://fabric8io.github.io/docker-maven-plugin/

では実際に実行してみます。

``` sh
$ ./mvnw clean verify \
  -Dswarm.project.stage.file=file://`pwd`/lifelog-project-stages.yml \
  -Dswarm.project.stage=it \
  -Pit
```

以下のように PostgreSQL のコンテナが起動するログから始まります。

<pre><code class="lang-sh">[INFO] DOCKER> [postgres:{{book.versions.postgresql}}] "lifelog-db": Start container 4b644b479795
23:21:10.537 LIFELOG_DB> The files belonging to this database system will be owned by user "postgres".
[...]
</code></pre>

無事テストが終わると以下のようにコンテナが削除されるログが表示されます。

<pre><code class="lang-sh">[INFO] DOCKER> [postgres:9.5.3] "lifelog-db": Stop and remove container 4b644b479795
</code></pre>

コンテナ起動･削除のオーバーヘッドが数秒程度ありますが、これだけの設定でその場限りのデータベースを用意できるのはいいですね。
