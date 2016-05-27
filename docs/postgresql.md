# PostgreSQL の利用

前章で見たとおり、従来の WildFly と同様にすぐに使えるデータベースとして H2 が同梱されています。

ただ、せっかくなので今回は PostgreSQL での場合もやってみたいと思います。開発は H2、プロダクションは PostgreSQL みたいなイメージで、起動時にシステムプロパティで切り替えられるようにしましょう。

PostgreSQL はお好みの方法で用意してもらえればと思いますが以下を想定しています。

* データベース名、データベースのユーザ、パスワード： lifelog
* ホスト: localhost
* ポート: 5432

PostgreSQL が初めてという方でかつ、Docker が利用できる環境であれば以下のようにしてコンテナを用意するのが楽だと思います。

> このエントリでは最後の章で Docker を使うので、そういう意味でも Docker での利用をおすすめします。

``` sh
$ docker run --name lifelog-db \
  -e POSTGRES_USER=lifelog -e POSTGRES_PASSWORD=lifelog \
  -p 5432:5432 \
  -d postgres:<version>
```

`<version>` の部分は今回は {{book.versions.postgresql}} とします。

> 本来こういった永続化されるデータに対しては Data Volume Container を用意したほうがよいですが、簡単のため割愛します。

ちゃんとデータベースができたか、psql クライアントで確認してみます。

``` sh
$ docker run --rm -it \
  --link lifelog-db:db \
  postgres:<version> \
  sh -c 'exec psql -h "$DB_PORT_5432_TCP_ADDR" -p "$DB_PORT_5432_TCP_PORT" -U lifelog'
```

上記コマンドを実行すると以下のようなプロンプトが表示されるので、適宜入力していきます。

``` sh
Password for user lifelog: <- lifelog と入力
psql (PostgreSQL のバージョン)
Type "help" for help.

lifelog=# \l <- \l と入力
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
```

だいじょぶそうですね。

もうちょっと凝った設定がしたいぜ、という方は以下の Docker Hub のページを参照ください。

https://hub.docker.com/_/postgres/

PostgreSQL がセットアップできたらアプリケーションから使えるようにします。

`postgresql/initial` の pom.xml を見てみてください。

まずは利用する PostgreSQL JDBC ドライバの依存性を追加されています。

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

まず `stage-config.yml` というファイルを以下の内容で `src/main/resources` 以下に配置します。

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

`project: stage:` の部分でステージを指定し、各ステージは `---` で区切ります。一番上のように何も指定しない場合は default ステージとみなされます。なお、このファイルは `stage-config.yml` がクラスパス上に存在する場合、自動的に読み込まれます。

その他の情報については以下ドキュメントを参考ください。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/configuration/index.html#_configuration_overlays_using_stage_properties

次に `stage-config.yml` をもとに DatasourcesFraction を組み立てるクラス(LifeLogConfiguration)を用意します。これも別に用意せずに LifeLogContainer にベタ書きでもいいですが、今後 Fraction の設定も少し増えるのでわけておきます。

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

private メソッドの resolve(String key) が肝のところです。`container.stageConfig().resolve(key).getValue()` の key は `stage-config.yml` の各キーをピリオド区切りで渡します。例えば `database: connection: url` なら `database.connection.url` です。

> ついでに JPAFraction を提供するメソッドも用意しています。

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

プロジェクト構成はおおよそ以下のようになります。

``` sh
.
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
    │       ├── modules
    │       │   └── org
    │       │       └── postgresql
    │       │           └── main
    │       │               └── module.xml
    │       └── project-stages.yml
    └── test
        └── java
            └── lifelog
                └── api
                    └── EntryControllerIT.java
```

ここまで出来て、PostgreSQL も起動していることも確認したうえで lifelog をビルド、実行します。ステージを指定する場合はシステムプロパティ `swarm.project.stage` を渡します。

``` sh
$ ./mvnw clean package && java -jar target/lifelog-swarm.jar -Dswarm.project.stage=production
```

POST したり psql でデータベースの中を見たりして、実際に PostgreSQL が使われていることを確認してみてください。

// TODO 1.0.0.CR1 では stage config が読めない

ついでに Arquillian でのテストも PostgreSQL を使ってやってみましょう。LifeLogContainer を共有しているため、特にテスト側で変更するところはありません。

``` sh
$ ./mvnw clean verify -Dswarm.project.stage=production
```
