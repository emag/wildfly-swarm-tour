# Hello, WildFly Swarm!

まずは動作確認がてら、Hello World レベルの JAX-RS アプリケーションを作成します。

以下リポジトリを clone して、`code/helloworld/initial` ディレクトリに移動してください。

``` sh
$ https://github.com/emag/wildfly-swarm-tour.git
$ cd code/helloworld/initial
```

まずは pom.xml を見てみます。

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project ...>
  <modelVersion>4.0.0</modelVersion>

  [...]

  <!-- (1) jar としてパッケージング -->
  <packaging>jar</packaging>

  [...]

  <!-- (2) bom を指定する -->
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.wildfly.swarm</groupId>
        <artifactId>bom</artifactId>
        <version>${version.wildfly-swarm}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- (3) JAX-RS を使う -->
    <dependency>
      <groupId>org.wildfly.swarm</groupId>
      <artifactId>jaxrs</artifactId>
    </dependency>
  </dependencies>

  <build>
    <finalName>${project.artifactId}</finalName>

    <plugins>
      <!-- (4) 実行可能 jar(uber jar)を作成するためのプラグイン -->
      <plugin>
        <groupId>org.wildfly.swarm</groupId>
        <artifactId>wildfly-swarm-plugin</artifactId>
        <version>${version.wildfly-swarm}</version>
        <configuration>
          <!-- (5) main() を持つクラスを指定 -->
          <mainClass>helloworld.App</mainClass>
        </configuration>
        <executions>
          <execution>
            <goals>
              <!-- (6)  package ゴールで動くようにする -->
              <goal>package</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

</project>
```

ここでは jar でパッケージングするようにしています(1)。

WildFly Swarm には必要なモジュール(Fraction と呼ばれます)だけ選んで使うことができますが、各 Fraction は成熟度がまちまちのためバージョンが統一されていません。そこで、WildFly Swarm では BOM を用意しており(2)、ユーザが各 Fraction のバージョンを意識しないですむようになっています。ユーザは BOM のバージョンを指定するだけでよく、WildFly Swarm のリリースバージョンは通常この BOM のバージョンを指します。

JAX-RS Fraction をここでは利用します(3)。上述の BOM により、version 指定は不要です。

WildFly Swarm は実行可能 jar(uber jar)を作成するプラグインを提供しており、アプリケーションのエンドポイントとなる main() メソッドを持つクラスを指定します(5)。また、このプラグインは Maven の package 時に実行されるようにするとよいでしょう。

次に JAX-RS のリソースクラス(`helloworld.HelloWorld`)を作成します。

``` java
package helloworld;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/hello") //(1)
public class HelloWorld {

  @GET //(2)
  @Produces(MediaType.APPLICATION_JSON) // (3)
  public String hello() { // (4)
    return "{\"message\" : \"Hello, WildFly Swarm!\"}";
  }

}
```

上記クラスはアノテーションによってリソースパスとして (1) を、リクエストする際のメソッドとして (2) を定義しており、`GET /hello` と HTTP リクエストすると、(4) のhello() メソッドが実行されます。ここでは return に指定されている JSON フォーマットの文字列をレスポンスします。また、(3) によってレスポンスヘッダに `Content-Type: application/json` がつけられます。これらは WildFly Swarm とは関係ない、JAX-RS を利用したふつうのコードです。

次にもろもろの設定をする App クラスを以下のように作ります。これが WildFly Swarm 利用時の固有クラスです。pom.xml で mainClass に指定したクラスですね。

``` java
package helloworld;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

public class App {

  public static void main(String[] args) throws Exception {
    Container container = new Container(args); // (1)

    // (4) ShrinkWrap の API を使ってデプロイできる形にパッケージング
    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);
    deployment.addPackages(true, App.class.getPackage()); // (5) helloworld パッケージ以下をすべて突っ込む

    container
      .start()  // (2)
      .deploy(deployment); // (3)
  }

}
```

`org.wildfly.swarm.container.Container` インスタンス(1)を介して WildFly の設定や起動(2)、アプリケーションのデプロイ(3)を行えます。この Hello World アプリケーションでは特に設定することはないのですが、次章ではデータベースを使うためデータソースの設定をします。

(4) がデプロイするアプリケーションの設定です。これは ShrinkWrap というオンデマンドでアプリケーションを jar や war といった形にアーカイブするライブラリを利用しています。ここでは愚直にすべてアーカイブに突っ込んでいますが(5)、システムプロパティなどを用いれば起動時に何を追加するかコントロールできますね。

* https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/getting-started/jar-applications.html
* https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/getting-started/shrinkwrap.html

ここまででだいたい以下のようなディレクトリ構成になっているかと思います。

``` sh
.
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src
    └── main
        └── java
            └── helloworld
                ├── App.java
                └── HelloWorld.java
```

必要なものはそろったので、以下コマンドを実行しビルドします。

``` sh
$ ./mvnw clean package
```

> スーパー jar ダウンロードタイムが始まるのでコーヒーでも用意して気長にお待ちください。
> たぶん、初回は 10 分くらいかかるかもしれません。-T2 とかつけるとちょっとマシかも。

ビルドが成功すると `target` 以下に `helloworld-swarm.jar` という uber jar ができています。

では、さっそくこのアプリケーションを動かしてみましょう。

``` sh
$ java -jar target/helloworld-swarm.jar
```

WildFly をお使いの方にはおなじみの WildFly の起動およびアプリケーションのデプロイといったログが出力されます。

ではアクセス。

``` sh
$ curl localhost:8080/hello
{"message" : "Hello, WildFly Swarm!"}
```

やりましたね。通常はここで WildFly をダウンロード、アプリをビルドしてから WildFly にデプロイといった手順が入りますが、WildFly Swarm では不要です。

アプリケーションを作って実行するところまではなんとなくつかめたでしょうか? では次の章では CRUD なアプリケーションに取りかかっていきます。

以降は補足なので、読まなくても差し支えありません。

## 補足1 java -jar 以外の実行方法

このドキュメントでは `mvn package` でビルドを行い、`java -jar` で生成された uber jar を指定して実行するようにしていますが、その他にも実行方法があります。

### wildfly-swarm:run

WildFly Swarm Plugin は wildfly-swarm:run というゴールが用意されています。

``` sh
$ ./mvnw wildfly-swarm:run
```

### IDE からの実行

App クラスは main() メソッドを持つクラスですので各 IDE からこの main() メソッドを指定して実行することもできます。

## 補足2 javax.ws.rs.core.Application を extends したクラスは?

今までに JAX-RS を触ったことがある方は `javax.ws.rs.core.Application` を extends したクラスを用意していないことに気づかれたかと思います。

通常、JAX-RS を Java EE アプリケーションサーバで利用する場合は、JAX-RS の有効化のために以下のようなクラス(または web.xml での定義)が必要です。

``` java
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/")
public class JaxRsActivator extends Application {}
```

JAX-RS Fraction は上記のようなクラスが見つからない場合、`org.wildfly.swarm.generated.WildFlySwarmDefaultJAXRSApplication` というクラスを生成します。このクラスの `@ApplicationPath` には `"/"` が設定されています。

`@ApplicationPath` に別の値を設定したい場合は別途上記のようなクラスを自分で用意します。ここでは横着して、さっき作った HelloWorld に `@ApplicationPath` をつけたうえで `javax.ws.rs.core.Application` を継承させてしまいましょう。

``` java
@ApplicationPath("/api") // 追加
@Path("/hello")
public class HelloWildFlySwarm extends Application { // 追加
```

では再ビルドしてアクセス。

``` sh
$ ./mvnw clean package && java -jar target/helloworld-swarm.jar
$ curl localhost:8080/api/hello
{"message" : "Hello, WildFly Swarm!"}%
```

いいですね。ただ、このエントリ内では画面を返すようなフレームワークは使わず、アプリケーションのエンドポイントはすべて JAX-RS なクラスなので、この設定はしないでおきます。

## 補足3 コンテキストルートは "/" 固定なの?

例によって WildFly Swarm が勝手に設定しているのですが、コンテキストルートが `"/"` であるのが不都合な場合もあるかと思います。変更方法は 3 種類あります。

1. システムプロパティ swarm.context.path を渡す
1. jboss-web.xml で設定
1. WildFly Swarm の API を利用

### システムプロパティ swarm.context.path を渡す

まずシステムプロパティで設定する方法です。渡し方は 2 種類あります。

1 つは実行時に渡す方法です。

``` sh
$ java -jar hello-wildfly-swarm/target/hello-wildfly-swarm-swarm.jar
```
or
```
$ java -jar target/helloworld-swarm.jar -Dswarm.context.path=helloworld
```

> 後者のように引数として渡す場合は new Container(args) と main() の引数を渡しておく必要があります。

もう 1 つは wildfly-swarm-plugin に指定する方法です。

``` xml
<configuration>
  <mainClass>wildflyswarmtour.App</mainClass>
  <properties>
    <swarm.context.path>helloworld</swarm.context.path> <!-- ここ -->
  </properties>
</configuration>
```

その他に利用できるシステムプロパティは以下に記載されています。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/configuration_properties.html

### jboss-web.xml で設定

以下のような `jboss-web.xml` というファイルをアーカイブに含める方法もあります。

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<jboss-web>
  <context-root>/helloworld</context-root>
</jboss-web>
```

そして App クラスでこのファイルを含めるように設定します。`addAsWebInfResource(...)` はその名の通り、war パッケージングでの WEB-INF 以下に含めてね、というメソッドです。

``` java
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
[...]
deployment.addAsWebInfResource(
      new ClassLoaderAsset("jboss-web.xml", App.class.getClassLoader()), "jboss-web.xml");
```

> ここでは jboss-web.xml がクラスパス(src/main/resources 以下)にあると仮定して `org.jboss.shrinkwrap.api.asset.ClassLoaderAsset` を使っています。

### WildFly Swarm の API を利用

以下のように指定することもできます。

``` java
deployment.setContextRoot("helloworld");
```

これは前述の jboss-web.xml での設定をプログラムでできるようにしています。その他のメソッドについては javadoc を参照ください。

http://wildfly-swarm.github.io/wildfly-swarm/{{book.versions.swarm}}/apidocs/org/wildfly/swarm/undertow/descriptors/JBossWebContainer.html

### 動作確認

(jboss-web.xml も作ったとすると)プロジェクトとしてはこんな感じです。

``` sh
.
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src
    └── main
        ├── java
        │   └── helloworld
        │       ├── App.java
        │       └── HelloWorld.java
        └── resources
            └── jboss-web.xml
```

では上記いずれかの方法で修正し、必要であれば再ビルドしてからアクセス。

``` sh
$ curl localhost:8080/helloworld/hello
{"message" : "Hello, WildFly Swarm!"}
```

いいかんじです。ただ、このエントリではこの設定もやりません。

## 補足4 war でのパッケージング

実は WildFly Swarm では以下サンプルのように war でもパッケージングできます。

https://github.com/wildfly-swarm/wildfly-swarm-examples/tree/{{book.versions.swarm}}/jaxrs/jaxrs-war

この場合は App クラスでやっていたような Container の設定に制限がかかります。この Hello World のように何も設定しない場合は war でも問題ないですが、多かれ少なかれなんらかの設定はするかと思いますので、たいていは jar でパッケージングすることになります。本ドキュメントでも基本的に jar パッケージとしています。

war パッケージングに関するドキュメントは以下です。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/getting-started/war-applications.html
