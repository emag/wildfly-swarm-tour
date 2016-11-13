# CRUD アプリの作成

ようやく CRUD アプリを作っていきます。簡単な一言メモみたいなアプリで、`lifelog` という名前をつけました(安直...)。

完成版は以下リポジトリにありますので、適宜参照ください。

https://github.com/emag/wildfly-swarm-tour/tree/{{book.versions.swarm}}/code/lifelog

まずは適当なディレクトリに移動し、以下コマンドを実行して Maven プロジェクトを作成します。

``` sh
$ mvn archetype:generate -DgroupId=wildflyswarm -DartifactId=lifelog -DinteractiveMode=false
```

また、テンプレートとして作成される不要なファイルを削除しておきます。

``` sh
$ cd lifelog
$ rm -fr src/main/java/wildflyswarm/App.java src/test/*
```

次に、以下のように pom.xml を書き換えます。

## pom.xml

<pre><code class="lang-xml">&lt;?xml version="1.0" encoding="UTF-8"?&gt;
&lt;project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"&gt;
  &lt;modelVersion&gt;4.0.0&lt;/modelVersion&gt;

  &lt;groupId&gt;wildflyswarmtour&lt;/groupId&gt;
  &lt;artifactId&gt;lifelog&lt;/artifactId&gt;
  &lt;version&gt;{{book.versions.swarm}}&lt;/version&gt;

  &lt;properties&gt;
    &lt;project.build.sourceEncoding&gt;UTF-8&lt;/project.build.sourceEncoding&gt;
    &lt;project.reporting.outputEncoding&gt;UTF-8&lt;/project.reporting.outputEncoding&gt;

    &lt;maven.compiler.source&gt;1.8&lt;/maven.compiler.source&gt;
    &lt;maven.compiler.target&gt;1.8&lt;/maven.compiler.target&gt;

    &lt;version.wildfly-swarm&gt;${project.version}&lt;/version.wildfly-swarm&gt;
    &lt;version.lombok&gt;{{book.versions.lombok}}&lt;/version.lombok&gt;
  &lt;/properties&gt;

  &lt;dependencyManagement&gt;
    &lt;dependencies&gt;
      &lt;dependency&gt;
        &lt;groupId&gt;org.wildfly.swarm&lt;/groupId&gt;
        &lt;artifactId&gt;bom-all&lt;/artifactId&gt;
        &lt;version&gt;${version.wildfly-swarm}&lt;/version&gt;
        &lt;type&gt;pom&lt;/type&gt;
        &lt;scope&gt;import&lt;/scope&gt;
      &lt;/dependency&gt;
    &lt;/dependencies&gt;
  &lt;/dependencyManagement&gt;

  &lt;dependencies&gt;
    &lt;!-- (1) --&gt;
    &lt;dependency&gt;
      &lt;groupId&gt;org.wildfly.swarm&lt;/groupId&gt;
      &lt;artifactId&gt;jaxrs-cdi&lt;/artifactId&gt;
    &lt;/dependency&gt;
    &lt;dependency&gt;
      &lt;groupId&gt;org.wildfly.swarm&lt;/groupId&gt;
      &lt;artifactId&gt;jpa&lt;/artifactId&gt;
    &lt;/dependency&gt;

    &lt;!-- (2) --&gt;
    &lt;dependency&gt;
      &lt;groupId&gt;org.projectlombok&lt;/groupId&gt;
      &lt;artifactId&gt;lombok&lt;/artifactId&gt;
      &lt;version&gt;${version.lombok}&lt;/version&gt;
      &lt;scope&gt;provided&lt;/scope&gt;
    &lt;/dependency&gt;
  &lt;/dependencies&gt;

  &lt;build&gt;
    &lt;finalName&gt;${project.artifactId}&lt;/finalName&gt;

    &lt;plugins&gt;
      &lt;plugin&gt;
        &lt;groupId&gt;org.wildfly.swarm&lt;/groupId&gt;
        &lt;artifactId&gt;wildfly-swarm-plugin&lt;/artifactId&gt;
        &lt;version&gt;${version.wildfly-swarm}&lt;/version&gt;
        &lt;configuration&gt;
          &lt;mainClass&gt;wildflyswarm.Bootstrap&lt;/mainClass&gt;
        &lt;/configuration&gt;
        &lt;executions&gt;
          &lt;execution&gt;
            &lt;goals&gt;
              &lt;goal&gt;package&lt;/goal&gt;
            &lt;/goals&gt;
          &lt;/execution&gt;
        &lt;/executions&gt;
      &lt;/plugin&gt;
    &lt;/plugins&gt;
  &lt;/build&gt;

&lt;/project&gt;
<code></pre>

今回は JAX-RS と CDI、JPA を使えるようにしておきます(1)。また、サードパーティのライブラリを利用します(2)。なお、lombok 自体はコンパイル時のみにしか利用しないため(provided スコープ)、実際にアーカイブに含めないのであまり影響ありませんが、ランタイム時にも必要なライブラリがある場合は、後述する `Archive#addAllDependencies()` を Bootstrap クラスで設定します。

では次からいろいろと作ってみましょう。

## persistence.xml

まずは JPA の設定ファイルです。`src/main/resources/META-INF/persistence.xml` として以下の内容で作成します。だいたいの意味もコメントしておきました。

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

クラス名は `lifelog.domain.model.Entry` です。

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

Java EE 7 の JPA 2.1 ではフィールドに Date and Time API はそのままでは使えないので、以下の `lifelog.domain.model.converter.LocalDateTimeConverter` のように `java.time.LocalDateTime` と `java.sql.Timestamp` とを相互に変換するコンバータを用意する必要があります。

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

実際にデータベースとやり取りする Repository クラスとして `lifelog.domain.repository.EntryRepository` を以下のように作成します。

JPA における各操作のインターフェースである `javax.persistence.EntityManager` をインジェクションし、ここから各種 CRUD 操作を行います。

``` java
package lifelog.domain.repository;

import lifelog.domain.model.Entry;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class EntryRepository {

  @PersistenceContext
  private EntityManager em;

  /**
   * 全件取得、タイムスタンプの降順。エントリが 1 件も存在しない場合は空リストを返す
   */
  public List<Entry> findAll() {
    return em
      .createQuery("SELECT e FROM Entry e ORDER BY e.createdAt DESC", Entry.class)
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

次に、JAX-RS などのプレゼンテーション層から呼ばれることを想定した Service クラスとして `lifelog.domain.service.EntryService` を作成します。実際の処理は先ほど作った EntryRepository に委譲しています。

また、クラスレベルで `javax.transaction.Transactional` を設定しているため、すべてのメソッドにおいてトランザクションが走ります。

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

JAX-RS のリソースクラスとして `lifelog.api.EntryController` を作成します。JSON でリクエストを受け付け(javax.ws.rs.Consumes)、レスポンス(javax.ws.rs.Produces)を行います。また、CRUD 操作の実体は EntryService クラスに処理を委譲しています。

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
   * DELETE /entries
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
    return new EntryResponse(
      entry.getId(),
      entry.getCreatedAt().toString(),
      entry.getDescription());
  }

}
```

保守性のためビューを分けるという意味もあるのですが、createdAt フィールドを LocalDateTime のまま返すと以下のようなレスポンスになってしまいました。

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
>
> https://github.com/FasterXML/jackson-datatype-jsr310

## WildFly Swarm 固有クラスによるアプリケーションの設定

ここまではふつうの Java EE アプリケーション開発という感じでした。最後に Hello World の時と同様、WildFly Swarm 固有のクラスとして WildFly の起動からアプリケーションのデプロイまでを表現する `wildflyswarm.Bootstrap` クラスを作成します。

``` java
package wildflyswarm;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jaxrs.JAXRSArchive;
import org.wildfly.swarm.jpa.JPAFraction;

public class Bootstrap {

  public static void main(String[] args) throws Exception {
    Swarm swarm = new Swarm(args);

    // (1) データソース設定
    swarm.fraction(new DatasourcesFraction()
      .dataSource("lifelogDS", (ds) -> ds
        .driverName("h2")
        .connectionUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
        .userName("sa")
        .password("sa"))
    );

    // (2) Default の Datasource を lifelogDS に設定
    swarm.fraction(new JPAFraction()
      .defaultDatasource("jboss/datasources/lifelogDS")
    );

    JAXRSArchive archive = ShrinkWrap.create(JAXRSArchive.class);
    // (3) lifelog パッケージ以下のクラスを再帰的にアーカイブに含める
    archive.addPackages(true, "lifelog");
    // (4) persistence.xml をアーカイブに含める
    archive.addAsWebInfResource(
      new ClassLoaderAsset("META-INF/persistence.xml", Bootstrap.class.getClassLoader()), "classes/META-INF/persistence.xml");

    swarm
      .start()
      .deploy(archive);
  }

}
```

(1) - (2) にデータソースおよび JPA 関係の設定を、(3) - (4) ではアプリケーションのアーカイブを作成しています。なおデータベースは H2 を利用します。このデータベースは `org.wildfly.swarm:jpa` を依存性に追加しておくと合わせて使えるようになります。

> 先ほど pom.xml の設定を見ていた時にちょろっと話題に出しましたが、コンパイル時だけではなくランタイムにも必要なライブラリで、
> かつ WildFly の module にも持ってなさそうなものがある場合は、Archive#addAllDependencies() を追加しておくとすべての依存性を追加してくれます。

ここでちょっとした機能分割をしておきます。上記の main() メソッドは WildFly の設定とデプロイするアプリケーションのアーカイブ設定が混ざっていますので、それぞれをわけておきます。別にわけなくてもいいのですが、役割がわかりやすくなるのと、このあと Arquillian を使ってテストするときに再利用できます。

WildFly の設定は `wildflyswarm.LifeLogContainer#newContainer()` というメソッドに切り出しました。

``` java
package wildflyswarm;

import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

public class LifeLogContainer {

  public static Swarm newContainer(String[] args) throws Exception {
    Swarm swarm = new Swarm(args);

    swarm.fraction(new DatasourcesFraction()
      .dataSource("lifelogDS", (ds) -> ds
        .driverName("h2")
        .connectionUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
        .userName("sa")
        .password("sa"))
    );

    swarm.fraction(new JPAFraction()
      .defaultDatasource("jboss/datasources/lifelogDS")
    );

    return swarm;
  }

}
```

デプロイ関係は `wildflyswarm.LifeLogDeployment#deployment()` としています。

``` java
package wildflyswarm;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

public class LifeLogDeployment {

  public static JAXRSArchive deployment() {
    JAXRSArchive archive = ShrinkWrap.create(JAXRSArchive.class);

    archive.addPackages(true, "lifelog");
    archive.addAsWebInfResource(
      new ClassLoaderAsset("META-INF/persistence.xml", Bootstrap.class.getClassLoader()), "classes/META-INF/persistence.xml");

    return archive;
  }

}
```

今作った 2 つのクラスを使うと、Bootstrap は以下のようになります。

``` java
package wildflyswarm;

public class Bootstrap {

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
├── pom.xml
└── src
    └── main
        ├── java
        │   ├── lifelog
        │   │   ├── api
        │   │   │   ├── EntryController.java
        │   │   │   └── EntryResponse.java
        │   │   └── domain
        │   │       ├── model
        │   │       │   ├── converter
        │   │       │   │   └── LocalDateTimeConverter.java
        │   │       │   └── Entry.java
        │   │       ├── repository
        │   │       │   └── EntryRepository.java
        │   │       └── service
        │   │           └── EntryService.java
        │   └── wildflyswarm
        │       ├── Bootstrap.java
        │       ├── LifeLogContainer.java
        │       └── LifeLogDeployment.java
        └── resources
            └── META-INF
                └── persistence.xml
```

ではビルド及び実行してみます。

``` sh
$ mvn clean package && java -jar target/lifelog-swarm.jar
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
