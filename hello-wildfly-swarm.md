# Hello, WildFly Swarm!

まずは動作確認がてら、Hello World レベルの JAX-RS アプリケーションを作成します。

以下リポジトリを clone して、`initial` ディレクトリに移動してください。

https://github.com/emag/wildfly-swarm-tour.git

すでに `hello-wildfly-swarm` というプロジェクトを用意してありますが、まずは pom.xml を見てみます。

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project ...>
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <artifactId>wildfly-swarm-tour-parent</artifactId>
    <groupId>com.example</groupId>
    <version>1.0.0</version>
  </parent>

  <artifactId>hello-wildfly-swarm</artifactId>
  <!-- (1) jar としてパッケージング -->
  <packaging>jar</packaging>

  <dependencies>
    <!-- (2) JAX-RS を使う -->
    <dependency>
      <groupId>org.wildfly.swarm</groupId>
      <artifactId>jaxrs</artifactId>
      <version>${version.wildfly-swarm}</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!-- (3) 実行可能 jar を作成するためのプラグイン -->
      <plugin>
        <groupId>org.wildfly.swarm</groupId>
        <artifactId>wildfly-swarm-plugin</artifactId>
        <configuration>
          <!-- (4) main() を持つクラスを指定 -->
          <mainClass>wildflyswarmtour.lifelog.App</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>

</project>
```

ここで重要な点としては以下の 4 点です。

|ポイント|説明                                           |
|----|---------------------------------------------|
|(1) |jar としてパッケージング                               |
|(2) |JAX-RS を利用                                   |
|(3) |実行可能 jar にするための WildFly Swarm が用意しているプラグインの利用|
|(4) |main() を持つクラスを指定 |

artifact のバージョンなどは親 pom に書いてありますので、適宜参照ください。(3)に関して言えば親 pom に `package` 時にこのプラグインが動くように指定しています。

次に JAX-RS のリソースクラス(`wildflyswarmtour.HelloWildFlySwarm`)を作成します。

``` java
package wildflyswarmtour;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/hello") //(1)
public class HelloWildFlySwarm {

  @GET //(2)
  public String hello() { // (3)
    return "Hello, WildFly Swarm!";
  }

}
```

上記クラスはリソースパスとして (1) を、リクエストする際のメソッドとして (2) を定義しており、`GET /hello` と HTTP リクエストすると、(3) のhello() メソッドが実行されます。ここでは return に指定されている文字列をレスポンスします。

次にもろもろの設定をする App クラスを以下のように作ります。

``` java
package wildflyswarmtour;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

public class App {

  public static void main(String[] args) throws Exception {
    Container container = new Container(); // (1)

    // (4) ShrinkWrap の API を使ってデプロイできる形にパッケージング
    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);
    deployment.addPackages(true, App.class.getPackage()); // (5) wildflyswarmtour パッケージ以下をすべて突っ込む

    container
      .start()  // (2)
      .deploy(deployment); // (3)
  }

}
```

`org.wildfly.swarm.container.Container` インスタンス(1)を介して WildFly の設定や起動(2)、アプリケーションのデプロイ(3)を行えます。この Hello World アプリケーションでは特に設定することはないのですが、次章ではデータベースを使うためデータソースの設定をします。

(4) がデプロイするアプリケーションの設定です。これは ShrinkWrap というオンデマンドでアプリケーションを jar や war といった形にアーカイブするライブラリを利用しています。ここでは愚直にすべてアーカイブに突っ込んでいますが(5)、システムプロパティなどを用いれば起動時に何を追加するかコントロールできますね。

* https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/jar-applications.html
* https://github.com/shrinkwrap/shrinkwrap

ここまででだいたい以下のようなディレクトリ構成になっているかと思います。

``` sh
hello-wildfly-swarm/
├── pom.xml
└── src
    └── main
        └── java
            └── wildflyswarmtour
                  ├── App.java
                  └── HelloWildFlySwarm.java
```

必要なものはそろったので、`mvnw(.cmd)` のある initial ディレクトリから以下コマンドを実行しビルドします。

> スーパー jar ダウンロードタイムが始まるのでコーヒーでも用意して気長にお待ちください。
> たぶん、初回は 10 分くらいかかるかもしれません。-T2 とかつけるとちょっとマシかも。

``` sh
$ ./mvnw clean package -pl hello-wildfly-swarm
```

ビルドが成功すると `hello-wildfly-swarm/target/` に以下が作成されています。

* hello-wildfly-swarm.jar
* hello-wildfly-swarm-swarm.jar

上記の WildFly Swarm Plugin によって `hello-wildfly-swarm-swarm.jar` という実行可能 jar が作られています。

では、さっそくこのアプリケーションを動かしてみましょう。

``` sh
$ java -jar hello-wildfly-swarm/target/hello-wildfly-swarm-swarm.jar
```

WildFly をお使いの方にはおなじみの WildFly の起動からアプリケーションのデプロイまでのログが出力されます。

ではアクセス。

``` sh
$ curl -X GET localhost:8080/hello
Hello, WildFly Swarm!
```

やりましたね。通常はここで WildFly をダウンロード、ビルドしたアプリをデプロイといった手順が入りますが、WildFly Swarm では不要です。

アプリケーションを作って実行するところまではなんとなくつかめたでしょうか? では次の章では CRUD なアプリケーションに取りかかっていきます。

以降は補足なので、読まなくても差し支えありません。

## 補足1 IDE からの実行

このエントリでは Maven のコマンドを直接叩いてビルドを行い、`java -jar` で実行するようにしています。が、App クラスは main() メソッドを持つクラスですので各 IDE からこの main() メソッドを指定して実行することもできます。

## 補足2 javax.ws.rs.core.Application を extends したクラスは?

今までに JAX-RS を触ったことがある方は `javax.ws.rs.core.Application` を extends したクラスを用意していないことに気づかれたかと思います。

通常、JAX-RS を Java EE アプリケーションサーバで利用する場合は、JAX-RS の有効化のために以下のようなクラス(または web.xml での定義)が必要です。

``` java
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/")
public class JaxRsActivator extends Application {}
```

wildfly-swarm-jaxrs では上記のようなクラスが見つからない場合、`org.wildfly.swarm.generated.WildFlySwarmDefaultJAXRSApplication` を生成します。また、この場合は `@ApplicationPath` には `"/"` が設定されます。

別の値を設定したい場合は上記設定を上書きします。ここでは横着して、さっき作った HelloWildFlySwarm につけてしまいましょう。

``` java
@ApplicationPath("/api")
@Path("/hello")
public class HelloWildFlySwarm extends Application {

  @GET
  public String hello() {
    return "Hello, WildFly Swarm!";
  }

}
```

では再ビルドしてアクセス。

``` sh
$ ./mvnw clean package -pl hello-wildfly-swarm && java -jar hello-wildfly-swarm/target/hello-wildfly-swarm-swarm.jar
$ curl localhost:8080/api/hello
Hello, WildFly Swarm!%
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
$ java -Dswarm.context.path=hello-wildfly-swarm -jar hello-wildfly-swarm/target/hello-wildfly-swarm-swarm.jar
```

もう 1 つは wildfly-swarm-plugin に指定する方法です。

``` xml
<configuration>
  <mainClass>wildflyswarmtour.App</mainClass>
  <properties>
    <swarm.context.path>hello-wildfly-swarm</swarm.context.path> <!-- ここ -->
  </properties>
</configuration>
```

その他に利用できるシステムプロパティは以下に記載されています。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/configuration_properties.html

### jboss-web.xml で設定

`jboss-web.xml` というファイルをクラスパス上(例: src/main/resources)に用意する方法もあります。

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<jboss-web>
  <context-root>/hello-wildfly-swarm</context-root>
</jboss-web>
```

そして App クラスでこのファイルをアーカイブするように設定します。`addAsWebInfResource(...)` はその名の通り、war パッケージングでの WEB-INF 以下にアーカイブしてね、というメソッドです。

``` java
deployment.addAsWebInfResource(
      new ClassLoaderAsset("jboss-web.xml", App.class.getClassLoader()), "jboss-web.xml");
```

### WildFly Swarm の API を利用

### 動作確認

(jboss-web.xml も作ったとすると)プロジェクトとしてはこんな感じです。

``` sh
hello-wildfly-swarm/
├── pom.xml
└── src
    └── main
        ├── java
        │   └── wildflyswarmtour
        │       ├── App.java
        │       └── HelloWildFlySwarm.java
        └── resources
            └── jboss-web.xml
```

では上記いずれかの方法で修正し、再ビルドしてからアクセス。

``` sh
$ ./mvnw clean package -pl hello-wildfly-swarm && java -jar hello-wildfly-swarm/target/hello-wildfly-swarm-swarm.jar
$ curl localhost:8080/hello-wildfly-swarm/hello
Hello, WildFly Swarm!
```

いいかんじです。ただ、このエントリではこの設定もやりません。

## 補足4 war でのパッケージング

実は WildFly Swarm では以下サンプルのように war でもパッケージングできます。

https://github.com/wildfly-swarm/wildfly-swarm-examples/tree/{{book.versions.swarm}}/jaxrs/jaxrs-war

この場合は App クラスでやっていたような Container の設定に制限がかかります。この Hello World のように何も設定しない場合は war でも問題ないですが、多かれ少なかれなんらかの設定はするかと思いますので、たいていは jar でパッケージングすることになります。

war パッケージングに関するドキュメントは以下です。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/war-applications.html
