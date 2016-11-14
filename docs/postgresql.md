# PostgreSQL の利用

前章で見たとおり、従来の WildFly と同様にすぐに使えるデータベースとして H2 が利用できます。

ただ、せっかくなので今回は PostgreSQL での場合もやってみたいと思います。開発は H2、プロダクションは PostgreSQL みたいなイメージで、起動時にシステムプロパティで切り替えられるようにしましょう。

PostgreSQL はお好みの方法で用意してもらえればと思いますが以下を想定しています。

* データベース名、データベースのユーザ、パスワード： lifelog
* ホスト: localhost
* ポート: 5432

PostgreSQL が初めてという方でかつ、Docker が利用できる環境であれば以下のようにしてコンテナを用意するのが楽だと思います。

> このドキュメントではいろいろと Docker を使うので、そういう意味でも Docker での利用をおすすめします。

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

PostgreSQL がセットアップできたらアプリケーションから使えるようにしてみましょう。

完成版は以下リポジトリにありますので、適宜参照ください。

https://github.com/emag/wildfly-swarm-tour/tree/{{book.versions.swarm}}/code/postgresql

まず、利用する PostgreSQL JDBC ドライバの依存性を追加します。

<pre><code class="lang-xml">&lt;properties&gt;
  [...]
  &lt;version.postgresql-jdbc&gt;{{book.versions.postgresql_jdbc}}&lt;/version.postgresql-jdbc&gt;
  [...]
&lt;/properties&gt;

[...]

&lt;dependency&gt;
  &lt;groupId&gt;org.postgresql&lt;/groupId&gt;
  &lt;artifactId&gt;postgresql&lt;/artifactId&gt;
  &lt;version&gt;${version.postgresql-jdbc}&lt;/version&gt;
&lt;/dependency&gt;
</code></pre>

次に、後述する PostgreSQL JDBC ドライバ用の module.xml で `${version.postgresql-jdbc}` の値が上書きされるようにリソース処理を設定しています。

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

次に、PostgreSQL JDBC ドライバの module.xml を `src/main/resources/modules/org/postgresql/main/module.xml` に以下内容で配置します。

> この module.xml は WildFly 独自のもので、モジュールクラスローディングをするために必要です。

``` xml
<?xml version="1.0" ?>
<module xmlns="urn:jboss:module:1.3" name="org.postgresql">
  <resources>
    <artifact name="org.postgresql:postgresql:${version.postgresql-jdbc}"/>
  </resources>

  <dependencies>
    <module name="javax.api"/>
    <module name="javax.transaction.api"/>
  </dependencies>
</module>
```

`${version.postgresql-jdbc}` の部分はビルド時に置換されます。

次に、システムプロパティの値によって H2 と PostgreSQL を切り替えられるようにする部分です。

まず `lifelog-project-stages.ym` というファイルを以下の内容で適当なパス(ここではプロジェクト直下)に配置します。

``` yml
database:
  driver:
    name: "h2"
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

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/configuration/project_stages.html

> なお、`project-stages.yml` という名前にした場合、このファイルがモジュール内やアプリケーションのクラスパスに存在すると自動的に読み込まれますが、値を変更するたびにビルドし直すのも面倒ですので外出ししています。また、java コマンド実行時のカレントパスにあった場合も読まれますが、Arquillian 実行時はカレントパスが変わるため、自分で指定する方が無難です。

次に `lifelog-project-stages.yml` をもとに DatasourcesFraction を組み立てるクラス(LifeLogConfiguration)を用意します。これも別に用意せずに LifeLogContainer にベタ書きでもいいですが、今後 Fraction の設定も少し増えるのでわけておきます。ついでに JPAFraction を提供するメソッドも作っておきました。

``` java
package wildflyswarm;

import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogConfiguration {

  private Swarm swarm;

  LifeLogConfiguration(Swarm swarm) {
    this.swarm = swarm;
  }

  DatasourcesFraction datasourcesFraction(String datasourceName) {
    DatasourcesFraction datasourcesFraction = new DatasourcesFraction()
      .dataSource(datasourceName, (ds) -> ds
        .driverName(resolve("database.driver.name"))
        .connectionUrl(resolve("database.connection.url"))
        .userName(resolve("database.userName"))
        .password(resolve("database.password"))
      );

    // production の場合は合わせて JDBC ドライバの設定もしておく
    if(swarm.stageConfig().getName().equals("production")) {
      datasourcesFraction.jdbcDriver("postgresql", (d) -> d
        .driverClassName(resolve("database.driver.className"))
        .xaDatasourceClass(resolve("database.driver.xaDatasourceClass"))
        .driverModuleName(resolve("database.driver.moduleName"))
      );
    }

    return datasourcesFraction;
  }

  JPAFraction jpaFraction(String datasourceName) {
    return new JPAFraction()
      .defaultDatasource("jboss/datasources/" + datasourceName);
  }

  private String resolve(String key) {
    return swarm.stageConfig().resolve(key).getValue();
  }

}
```

private メソッドの resolve(String key) が肝のところです。`swarm.stageConfig().resolve(key).getValue()` の key は `lifelog-project-stages.yml` の各キーをピリオド区切りで渡します。例えば `database: connection: url` なら `database.connection.url` です。

最後に LifeLogConfiguration を利用するように LifeLogContainer を変更します。

``` java
package wildflyswarm;

import org.wildfly.swarm.Swarm;

public class LifeLogContainer {

  private static final String DATASOURCE_NAME = "lifelogDS";

  public static Swarm newContainer(String[] args) throws Exception {
    Swarm swarm = new Swarm(args);

    LifeLogConfiguration configuration = new LifeLogConfiguration(swarm);

    swarm
      .fraction(configuration.datasourcesFraction(DATASOURCE_NAME))
      .fraction(configuration.jpaFraction(DATASOURCE_NAME));

    return swarm;
  }

}
```

ここまででプロジェクト構成はおおよそ以下のようになります。

``` sh
.
├── lifelog-project-stages.yml
├── pom.xml
└── src
    ├── main
    │   ├── java
    │   │   ├── lifelog
    │   │   │   ├── api
    │   │   │   │   ├── EntryController.java
    │   │   │   │   └── EntryResponse.java
    │   │   │   └── domain
    │   │   │       ├── model
    │   │   │       │   ├── converter
    │   │   │       │   │   └── LocalDateTimeConverter.java
    │   │   │       │   └── Entry.java
    │   │   │       ├── repository
    │   │   │       │   └── EntryRepository.java
    │   │   │       └── service
    │   │   │           └── EntryService.java
    │   │   └── wildflyswarm
    │   │       ├── Bootstrap.java
    │   │       ├── LifeLogConfiguration.java
    │   │       ├── LifeLogContainer.java
    │   │       └── LifeLogDeployment.java
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
$ mvn clean package \
  && java -Dswarm.project.stage.file=file://`pwd`/lifelog-project-stages.yml \
    -Dswarm.project.stage=production \
    -jar target/lifelog-swarm.jar
```

POST したり psql でデータベースの中を見たりして、実際に PostgreSQL が使われていることを確認してみてください。

## IT 用のステージを用意

ついでに Arquillian でのテストも PostgreSQL を使ってやってみましょう。LifeLogConfiguration クラスを追加したため、EntryControllerIT を修正しておきます。

``` java
@Deployment(testable = false)
public static JAXRSArchive createDeployment() {
  // addClass(...) ではなく、addClasses(...) になっていることにも注意
  return LifeLogDeployment.deployment().addClasses(LifeLogContainer.class, /*追加*/ LifeLogConfiguration.class);
}
```

上記の変更ができたら IT を実行します。

``` sh
$ mvn clean verify \
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

<pre><code class="lang-xml">&lt;properties&gt;
  [...]
  &lt;version.docker-maven-plugin&gt;{{book.versions.docker_maven_plugin}}&lt;/version.docker-maven-plugin&gt;
  &lt;version.postgresql-server&gt;{{book.versions.postgresql}}&lt;/version.postgresql-server&gt;
&lt;/properties&gt;

[...]

&lt;profiles&gt;
  &lt;profile&gt;
    &lt;id&gt;it&lt;/id&gt;
    &lt;build&gt;
      &lt;plugins&gt;
        &lt;plugin&gt;
          &lt;groupId&gt;io.fabric8&lt;/groupId&gt;
          &lt;artifactId&gt;docker-maven-plugin&lt;/artifactId&gt;
          &lt;version&gt;${version.docker-maven-plugin}&lt;/version&gt;
          &lt;configuration&gt;
            &lt;logDate&gt;default&lt;/logDate&gt;
            &lt;autoPull&gt;true&lt;/autoPull&gt;
            &lt;images&gt;
              &lt;image&gt;
                &lt;alias&gt;lifelog-db&lt;/alias&gt;
                &lt;name&gt;postgres:${version.postgresql-server}&lt;/name&gt;
                &lt;run&gt;
                  &lt;env&gt;
                    &lt;POSTGRES_USER&gt;lifelog&lt;/POSTGRES_USER&gt;
                    &lt;POSTGRES_PASSWORD&gt;lifelog&lt;/POSTGRES_PASSWORD&gt;
                  &lt;/env&gt;
                  &lt;ports&gt;
                    &lt;port&gt;15432:5432&lt;/port&gt;
                  &lt;/ports&gt;
                  &lt;wait&gt;
                    &lt;log&gt;database system is ready to accept connections&lt;/log&gt;
                    &lt;time&gt;20000&lt;/time&gt;
                  &lt;/wait&gt;
                  &lt;log&gt;
                    &lt;prefix&gt;LIFELOG_DB&lt;/prefix&gt;
                    &lt;color&gt;yellow&lt;/color&gt;
                  &lt;/log&gt;
                &lt;/run&gt;
              &lt;/image&gt;
            &lt;/images&gt;
          &lt;/configuration&gt;

          &lt;executions&gt;
            &lt;execution&gt;
              &lt;id&gt;start&lt;/id&gt;
              &lt;phase&gt;pre-integration-test&lt;/phase&gt;
              &lt;goals&gt;
                &lt;goal&gt;build&lt;/goal&gt;
                &lt;goal&gt;start&lt;/goal&gt;
              &lt;/goals&gt;
            &lt;/execution&gt;
            &lt;execution&gt;
              &lt;id&gt;stop&lt;/id&gt;
              &lt;phase&gt;post-integration-test&lt;/phase&gt;
              &lt;goals&gt;
                &lt;goal&gt;stop&lt;/goal&gt;
              &lt;/goals&gt;
            &lt;/execution&gt;
          &lt;/executions&gt;
        &lt;/plugin&gt;
      &lt;/plugins&gt;
    &lt;/build&gt;
  &lt;/profile&gt;
&lt;/profiles&gt;
</code></pre>

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

https://dmp.fabric8.io/

また、LifeLogConfiguration にも以下の修正が必要です。

``` java
DatasourcesFraction datasourcesFraction(String datasourceName) {
  [...]

  // stage が it の場合もドライバ設定を行う
  if(swarm.stageConfig().getName().equals("it")
    || swarm.stageConfig().getName().equals("production")) {
    datasourcesFraction.jdbcDriver("postgresql", (d) -> d
      [...]
    );
  }

  return datasourcesFraction;
}
```

ここまできたらステージおよびプロファイルに `it` を指定したうえで実行してみます。

``` sh
$ mvn clean verify \
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

<pre><code class="lang-sh">[INFO] DOCKER> [postgres:{{book.versions.postgresql}}] "lifelog-db": Stop and remove container 4b644b479795
</code></pre>

コンテナ起動･削除のオーバーヘッドが数秒程度ありますが、これだけの設定でその場限りのデータベースを用意できるのはいいですね。
