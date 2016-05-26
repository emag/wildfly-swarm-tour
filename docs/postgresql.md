# PostgreSQL の利用

前章で見たとおり、従来の WildFly と同様にすぐに使えるデータベースとして H2 が同梱されています。

ただ、せっかくなので今回は PostgreSQL での場合もやってみたいと思います。開発は H2、プロダクションは PostgreSQL みたいなイメージで、起動時にシステムプロパティで切り替えられるようにしましょう。

PostgreSQL はお好みの方法で用意してもらえればと思いますが、データベース名、データベースのユーザ、パスワードは `lifelog` とします。リスンするポートはデフォルtの 5432 でオッケーです。

PostgreSQL が初めてという方でかつ、Docker が利用できる環境であれば以下のようにしてコンテナを用意するのが楽だと思います。

> このエントリでは最後の章で Docker を使うので、そういう意味でも Docker での利用をおすすめします。

※ `/tmp` は適当なパスに変更してください。

``` sh
$ docker run --name lifelog-db \
  -e POSTGRES_USER=lifelog -e POSTGRES_PASSWORD=lifelog \
  -v /tmp/lifelog/pgdata/data:/var/lib/postgresql/data \
  -p 5432:5432 \
  -d postgres:9.4.5
```

ちゃんとデータベースができたか、psql クライアントで確認してみます。

``` sh
$ docker run --link lifelog-db:db \
  --rm -it postgres:9.4.5 \
  sh -c 'exec psql -h "$DB_PORT_5432_TCP_ADDR" -p "$DB_PORT_5432_TCP_PORT" -U lifelog'
Password for user lifelog: <- lifelog と入力
psql (9.4.5)
Type "help" for help.

lifelog=# \l
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

lifelog-postgresql の pom.xml を見てみてください。

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
  <resources>
    <resource>
      <directory>src/main/resources</directory>
      <filtering>true</filtering>
    </resource>
  </resources>
  [...]
</build>
```

最後に maven-failsafe-plugin で自分で追加した module を読めるようにする設定(`<configuration>` 要素)を追加しています。これがなくてもプロダクションコードは動きますが、Arquillian 側で PostgreSQL の JDBC ドライバの module.xml が読めません。

``` xml
<plugin>
<groupId>org.apache.maven.plugins</groupId>
<artifactId>maven-failsafe-plugin</artifactId>
<version>${version.maven-failsafe-plugin}</version>
[...]
<configuration>
  <systemPropertyVariables>
    <swarm.build.modules>${project.build.outputDirectory}/modules/</swarm.build.modules>
  </systemPropertyVariables>
</configuration>
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

最後に LifeLogContainer に対して、H2 と PosetgreSQL をシステムプロパティ(`swarm.lifelog.production` )によって切り替えられるようにしておきます。

``` java
package wildflyswarmtour.lifelog;

import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogContainer {

  public static Container newContainer() throws Exception {
    Container container = new Container();

    boolean production = Boolean.parseBoolean(System.getProperty("swarm.lifelog.production"));

    if (production) {
      container.fraction(new DatasourcesFraction()
        .jdbcDriver("org.postgresql", (d) -> {
          d.driverClassName("org.postgresql.Driver");
          d.xaDatasourceClass("org.postgresql.xa.PGXADataSource");
          d.driverModuleName("org.postgresql");
        })
        .dataSource("lifelogDS", (ds) -> {
          ds.driverName("org.postgresql");
          ds.connectionUrl("jdbc:postgresql://localhost:5432/lifelog");
          ds.userName("lifelog");
          ds.password("lifelog");
        })
      );
    } else {
      // h2 の時の設定
    }

    [...]
}
```

プロジェクト構成はおおよそ以下のようになります。

``` sh
lifelog-postgresql
├── pom.xml
└── src
    ├── main
    │   ├── java
    │   │   └── wildflyswarmtour
    │   │       └── lifelog
    │   │           ├── App.java
    │   │           ├── LifeLogContainer.java
    │   │           ├── LifeLogDeployment.java
    │   │           ├── api
    │   │           │   ├── EntryController.java
    │   │           │   └── EntryResponse.java
    │   │           └── domain
    │   │               ├── model
    │   │               │   ├── Entry.java
    │   │               │   └── converter
    │   │               │       └── LocalDateTimeConverter.java
    │   │               ├── repository
    │   │               │   └── EntryRepository.java
    │   │               └── service
    │   │                   └── EntryService.java
    │   └── resources
    │       ├── META-INF
    │       │   └── persistence.xml
    │       └── modules
    │           └── org
    │               └── postgresql
    │                   └── main
    │                       └── module.xml
    └── test
        └── java
            └── wildflyswarmtour
                └── lifelog
                    └── api
                        └── EntryControllerIT.java
```

ここまで出来て、PostgreSQL も起動していることも確認したうえで lifelog をビルド、実行します。

``` sh
$ ./mvnw clean package -pl lifelog-postgresql \
  && java -Dswarm.lifelog.production=true -jar lifelog-postgresql/target/lifelog-postgresql-swarm.jar
```

POST したり psql でデータベースの中を見たりして、実際に PostgreSQL が使われていることを確認してみてください。

ついでに Arquillian でのテストも PostgreSQL を使ってやってみましょう。LifeLogContainer を共有しているため、特にテスト側で変更するところはありません。

``` sh
$ ./mvnw clean verify -pl lifelog-postgresql -Dswarm.lifelog.production=true
```
