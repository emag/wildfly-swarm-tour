# CRUD アプリの作成

ようやく CRUD アプリを作っていきます。簡単な一言メモみたいなアプリで、`lifelog` という名前をつけました(安直...)。`lifelog/initial` にどんどん書いてきましょう。

## pom.xml

まずは pom.xml を見てみます。

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project ...>

  [...]

  <!-- (1) -->
  <dependencies>
    <dependency>
      <groupId>org.wildfly.swarm</groupId>
      <artifactId>jaxrs-cdi</artifactId>
    </dependency>
    <dependency>
      <groupId>org.wildfly.swarm</groupId>
      <artifactId>jpa</artifactId>
    </dependency>

    <!-- (2) -->
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>${version.lombok}</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>

  [...]

</project>
```

今回は JAX-RS と CDI、JPA を使えるようにしておきます(1)。また、サードパーティのライブラリを利用します(2)。なお、lombok 自体はコンパイル時のみにしか利用しないため(provided スコープ)、実際にアーカイブに含めないのであまり影響ありませんが、ランタイム時にも必要なライブラリがある場合は、後述する `deployment.addAllDependencies()` を App クラスで設定します。

では次からいろいろと作ってみましょう。

## persistence.xml

まずは JPA の設定ファイルです。以下内容で `src/main/resources/META-INF` 以下に置いておきます。だいたいの意味もコメントしておきました。

``` xml
<persistence version="2.1"
             xmlns="http://xmlns.jcp.org/xml/ns/persistence" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="
        http://xmlns.jcp.org/xml/ns/persistence
        http://xmlns.jcp.org/xml/ns/persistence/persistence_2_1.xsd">

  <persistence-unit name="primary">
    <properties>
      <!-- アプリケーションのデプロイ時にテーブルの削除と作成を行う -->
      <property name="javax.persistence.schema-generation.database.action" value="drop-and-create"/>

      <!-- 実際に発行される SQL を標準出力に出力。デバッグ用途 -->
      <property name="hibernate.show_sql" value="true"/>
      <property name="hibernate.format_sql" value="true"/>
    </properties>
  </persistence-unit>

</persistence>
```

## Entity

次に JPA の Entity クラス(データベースのテーブルとマッピングされるクラス)を作成します。

``` java
package lifelog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Entry implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private String description;

  @PrePersist
  private void setCreatedAt() {
    setCreatedAt(LocalDateTime.now());
  }

}
```

フィールド数も少なくてちょっと味気ないですが、createdAt フィールドは `java.time.LocalDateTime` 型にするというちょっと味な真似をしてみました。

といっても以下 [@lbtc_xxx](https://twitter.com/lbtc_xxx) さんのブログを真似しただけですが。。

[Using JPA 2.1 AttributeConverter against Java8 LocalDate / LocalDateTime](http://www.nailedtothex.org/roller/kyle/entry/using-jpa-2-1-attributeconverter)

Java EE 7 の JPA 2.1 ではフィールドに Date and Time API はそのままでは使えないので、以下のようなコンバータを用意してあげる必要があります。

``` java
package lifelog.domain.model.converter;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Converter(autoApply = true)
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, Timestamp> {

  @Override
  public Timestamp convertToDatabaseColumn(LocalDateTime attribute) {
    return attribute == null ? null : Timestamp.valueOf(attribute);
  }

  @Override
  public LocalDateTime convertToEntityAttribute(Timestamp dbData) {
    return dbData == null ? null : dbData.toLocalDateTime();
  }

}
```

また、Entry クラスにある `javax.persistence.PrePersist` というアノテーションが付与されたメソッドは、このエンティティが永続化される直前で呼ばれるコールバック処理を記載できます。ここでは createdAt フィールドに `LocalDateTime.now()` を設定しています。こうしておくと次節での Repository クラスでこの処理を書かなくてすみます。@Prepersist 以外にも以下が存在します。たとえば @PostXxx なんかはログ処理なんかを書いても良いですね。

* @PrePersist
* @PreRemove
* @PostPersist
* @PostRemove
* @PreUpdate
* @PostUpdate
* @PostLoad

## Repository

実際にデータベースとやり取りする Repository クラスは以下のようにします。JPA における各操作のインターフェースである `javax.persistence.EntityManager` をインジェクションし、ここから各種 CRUD 操作を行います。また、クラスレベルで `javax.transaction.Transactional` を設定しているため、すべてのメソッドにおいてトランザクションが走ります。

``` java
package lifelog.domain.repository;

import lifelog.domain.model.Entry;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.List;

@ApplicationScoped
@Transactional
public class EntryRepository {

  @PersistenceContext
  private EntityManager em;

  /**
   * 全件取得、タイムスタンプの降順。エントリが 1 件も存在しない場合は空リストを返す
   */
  public List<Entry> findAll() {
    return em
        .createQuery("SELECT e FROM Entry e ORDER BY  e.createdAt DESC", Entry.class)
        .getResultList();
  }

  /**
   * id をキーに 1 件取得
   */
  public Entry find(Integer id) {
    return em.find(Entry.class, id);
  }

  /**
   * 新規作成・更新処理
   */
  public Entry save(Entry entry) {
    // id を持っていない場合は新しい Entry なので、永続化
    if (entry.getId() == null) {
      em.persist(entry);
      return entry;
    // id がある場合は既存エントリの更新なので、そのままマージ
    } else {
      return em.merge(entry);
    }
  }

  /**
   * 全件削除。実体は delete(Entry entry) をぐるぐる呼んでるだけ
   */
  public void deleteAll() {
    findAll().forEach(this::delete);
  }

  /**
   * id をキーに 1 件削除。実体は delete(Entry entry)
   */
  public void delete(Integer id) {
    delete(em.find(Entry.class, id));
  }

  /**
   * 渡された Entry インスタンスに対して削除処理
   */
  private void delete(Entry entry) {
    em.remove(entry);
  }

}
```

## Service

JAX-RS などのプレゼンテーション層から呼ばれることを想定した Service クラスです。実際の処理は先ほど作った EntryRepository に委譲しています。

``` java
package lifelog.domain.service;

import lifelog.domain.model.Entry;
import lifelog.domain.repository.EntryRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.List;

@ApplicationScoped
@Transactional
public class EntryService {

  @Inject
  private EntryRepository entryRepository;

  public List<Entry> findAll() {
    return entryRepository.findAll();
  }

  public Entry find(Integer id) {
    return entryRepository.find(id);
  }

  public Entry save(Entry entry) {
    return entryRepository.save(entry);
  }

  public void deleteAll() {
    entryRepository.deleteAll();
  }

  public void delete(Integer id) {
    entryRepository.delete(id);
  }

}
```

## Resource

JAX-RS のリソースクラスです。JSON でリクエストを受け付け(javax.ws.rs.Consumes)、レスポンス(javax.ws.rs.Produces)をしています。また、CRUD 操作の実体は EntryService クラスに処理を委譲しています。

``` java
package lifelog.api;

import lifelog.domain.model.Entry;
import lifelog.domain.service.EntryService;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.stream.Collectors;

@Path("/entries")
public class EntryController {

  @Inject
  private EntryService entryService;

  /**
   * GET /entries
   * JSON でエントリ一覧を返す。1 件もエントリがないときは空配列で返す
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EntryResponse> findALL() {
    List<Entry> allEntries = entryService.findAll();
    return allEntries.stream()
        .map(EntryResponse::from)
        .collect(Collectors.toList());
  }

  /**
   * GET /entries/:id
   * その id のエントリがないときは 404
   */
  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response find(@PathParam("id") Integer id) {
    Entry entry = entryService.find(id);

    if (entry == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    return Response.ok(EntryResponse.from(entry)).build();
  }

  /**
   * POST /entries
   * JSON を受け取り、その内容をもって新規作成
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(@Context UriInfo uriInfo, Entry entry) {
    Entry created = entryService.save(entry);

    return Response
        .created(
            uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(created.getId()))
                .build())
        .build();
  }

  /**
   * PUT /entries/:id
   * JSON を受け取り、指定された id に対して更新。その id のエントリがないときは 404
   */
  @PUT
  @Path("{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response update(@PathParam("id") Integer id, Entry entry) {
    Entry old = entryService.find(id);

    if (old == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    old.setDescription(entry.getDescription());
    entryService.save(old);

    return Response.ok().build();
  }

  /**
   *  DELETE /entries
   * 全件削除
   */
  @DELETE
  public Response deleteAll() {
    entryService.deleteAll();
    return Response.noContent().build();
  }

  /**
   * DELETE /entries/:id
   * 指定された id の削除。その id のエントリがないときは 404
   */
  @DELETE
  @Path("{id}")
  public Response delete(@PathParam("id") Integer id) {
    if (entryService.find(id) == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    entryService.delete(id);

    return Response.noContent().build();
  }

}
```

見たとおりという感じなのですが、GET で返す際、エンティティの Entry ではなく、以下のような EntryResponse に変換してから返しています。

``` java
package lifelog.api;

import lifelog.domain.model.Entry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntryResponse implements Serializable {

  private Integer id;
  private String createdAt;
  private String description;

  public static EntryResponse from(Entry entry) {
    return new EntryResponse(entry.getId(), entry.getCreatedAt().toString(), entry.getDescription());
  }

}
```

保守性のためビューを分けるという意味もあるのですが、timestamp フィールドを LocalDateTime のまま返すと以下のようなレスポンスになってしまいました。

``` json
"createdAt": {
  "chronology": {
    "id": "ISO",
    "calendarType": "iso8601"
  },
  "monthValue": 11,
  "dayOfMonth": 23,
  "hour": 2,
  "minute": 25,
  "second": 45,
  "nano": 9.29e+08,
  "year": 2015,
  "month": "NOVEMBER",
  "dayOfYear": 327,
  "dayOfWeek": "MONDAY"
}
```

これはこれで使い出があるような気もしますが、ここでは単純な形でいいでしょう。LocalDateTime の toString() は `2015-11-23T02:52:17.333` のような ISO 8601 フォーマットになります。

> あとは Jackson の jackson-datetype-jsr310 とかが使えるんですかね。
> https://github.com/FasterXML/jackson-datatype-jsr310

## WildFly Swarm 固有クラスによるアプリケーションの設定

ここまではふつうの Java EE アプリケーション開発という感じでした。最後に Hello World の時と同様、WildFly Swarm 固有のクラスとして WildFly の起動からアプリケーションのデプロイまでを表現する App クラスを作成します。

``` java
package lifelog;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jaxrs.JAXRSArchive;
import org.wildfly.swarm.jpa.JPAFraction;

public class App {

  public static void main(String[] args) throws Exception {
    Container container = new Container(args);

    // (1) データソース設定
    container.fraction(new DatasourcesFraction()
        // (1-1) JDBC ドライバの登録 ここでは WildFly に同梱されている H2 データベースのものを利用
        .jdbcDriver("h2", (d) -> {
          d.driverClassName("org.h2.Driver");
          d.xaDatasourceClass("org.h2.jdbcx.JdbcDataSource");
          d.driverModuleName("com.h2database.h2");
        })
        // (1-2) アプリケーションで利用するデータソース(lifelogDS)の設定
        .dataSource("lifelogDS", (ds) -> {
          ds.driverName("h2");
          ds.connectionUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
          ds.userName("sa");
          ds.password("sa");
        })
    );

    // (2) Default の Datasource を lifelogDS に設定。これを設定しない場合は ExampleDS というデータソース設定が作られる。
    container.fraction(new JPAFraction()
        .inhibitDefaultDatasource()
        .defaultDatasource("jboss/datasources/lifelogDS")
    );

    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);
    deployment.addPackages(true, App.class.getPackage());

    // (3) persistence.xml をアーカイブに含める
    deployment.addAsWebInfResource(
        new ClassLoaderAsset("META-INF/persistence.xml", App.class.getClassLoader()), "classes/META-INF/persistence.xml");

    container
        .start()
        .deploy(deployment);
  }

}
```

(1) - (3) にデータソースおよび JPA 関係の設定を追加しています。

> 先ほど pom.xml の設定を見ていた時にちょろっと話題に出しましたが、コンパイル時だけではなくランタイムにも必要なライブラリで、
> かつ WildFly の module にも持ってなさそうなものがある場合は、deployment.addAllDependencies() を追加しておくとすべての依存性を追加してくれます。

ここでちょっとした機能分割をしておきます。上記の main() メソッドは Container の設定とデプロイするアプリケーションのアーカイブ設定が混ざっていますので、それぞれをわけておきます。別にわけなくてもいいのですが、役割がわかりやすくなるのと、このあと Arquillian を使ってテストするときに再利用できます。

Container の設定は LifeLogContainer#newContainer() というメソッドに切り出しました。

``` java
package lifelog;

import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogContainer {

  public static Container newContainer(String[] args) throws Exception {
    Container container = new Container(args);

    container.fraction(new DatasourcesFraction()
        .jdbcDriver("h2", (d) -> {
          d.driverClassName("org.h2.Driver");
          d.xaDatasourceClass("org.h2.jdbcx.JdbcDataSource");
          d.driverModuleName("com.h2database.h2");
        })
        .dataSource("lifelogDS", (ds) -> {
          ds.driverName("h2");
          ds.connectionUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
          ds.userName("sa");
          ds.password("sa");
        })
    );

    container.fraction(new JPAFraction()
        .inhibitDefaultDatasource()
        .defaultDatasource("jboss/datasources/lifelogDS")
    );

    return container;
  }

}
```

デプロイ関係は `LifeLogDeployment#deployment()` としています。

``` java
package lifelog;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

public class LifeLogDeployment {

  public static JAXRSArchive deployment() {
    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);

    deployment.addPackages(true, App.class.getPackage());
    deployment.addAsWebInfResource(
        new ClassLoaderAsset("META-INF/persistence.xml", App.class.getClassLoader()), "classes/META-INF/persistence.xml");

    return deployment;
  }

}
```

今作った 2 つのクラスを使うと、App は以下のようになります。

``` java
package lifelog;

public class App {

  public static void main(String[] args) throws Exception {
    LifeLogContainer.newContainer(args)
        .start()
        .deploy(LifeLogDeployment.deployment());
  }

}
```

ここまでで以下のようなプロジェクト構成になります。

``` sh
.
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src
    └── main
        ├── java
        │   └── lifelog
        │       ├── App.java
        │       ├── LifeLogContainer.java
        │       ├── LifeLogDeployment.java
        │       ├── api
        │       │   ├── EntryController.java
        │       │   └── EntryResponse.java
        │       └── domain
        │           ├── model
        │           │   ├── Entry.java
        │           │   └── converter
        │           │       └── LocalDateTimeConverter.java
        │           ├── repository
        │           │   └── EntryRepository.java
        │           └── service
        │               └── EntryService.java
        └── resources
            └── META-INF
                └── persistence.xml
```

ではビルド及び実行してみます。

``` sh
$ ./mvnw clean package && java -jar target/lifelog-swarm.jar
```

とりあえず全件取得。

``` sh
$ curl localhost:8080/entries
[]
```

この通り最初はデータが入っていないので、まずはひとつ POST してみましょう。

``` sh
$ curl -X POST -H "Content-Type: application/json" -d '{"description" : "test"}' localhost:8080/entries -v
< HTTP/1.1 201 Created
< Location: http://localhost:8080/entries/1
```

ステータスコード 201 と、Location ヘッダが見えれば成功です。

この Location ヘッダに対して GET してみると、

``` sh
$ curl localhost:8080/entries/1 | jq .
{
  "id": 1,
  "createdAt": "2016-05-27T20:23:58.437",
  "description": "test"
}
```

ちゃんと取れてますね。

id 1 のエントリに対して更新もしてみます。

``` sh
$ curl -X PUT -H "Content-Type: application/json" -d '{"description" : "updated"}' localhost:8080/entries/1
```

``` sh
$ curl localhost:8080/entries/1 | jq .
{
  "id": 1,
  "createdAt": "2016-05-27T20:23:58.437",
  "description": "updated"
}
```

更新されてます。

では id 1 のエントリ消しておきましょう。

``` sh
$ curl -X DELETE localhost:8080/entries/1 -v
< HTTP/1.1 204 No Content
```

``` sh
$ curl localhost:8080/entries/1 -I
HTTP/1.1 404 Not Found
```

ちゃんと消えました。

何個かエントリを登録している場合は以下で全件削除です。

``` sh
$ curl -X DELETE localhost:8080/entries/ -v
```

とりあえず簡単な CRUD アプリとしてはこれで完成です。
