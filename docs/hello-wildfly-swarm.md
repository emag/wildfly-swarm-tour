# Hello, WildFly Swarm!

まずは動作確認がてら、Hello World レベルの JAX-RS アプリケーションを作成します。

完成版は以下リポジトリにありますので、適宜参照ください。

https://github.com/emag/wildfly-swarm-tour/tree/{{book.versions.swarm}}/code/helloworld

まずは適当なディレクトリに移動し、以下コマンドを実行して Maven プロジェクトを作成します。

``` sh
$ mvn archetype:generate -DgroupId=wildflyswarm -DartifactId=helloworld -DinteractiveMode=false
```

また、テンプレートとして作成される不要なファイルを削除しておきます。

``` sh
$ cd helloworld
$ rm -fr src/main/java/wildflyswarm/App.java src/test/*
```

次に、以下のように pom.xml を書き換えます。

<pre><code class="lang-xml">&lt;?xml version="1.0" encoding="UTF-8"?&gt;
&lt;project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"&gt;
  &lt;modelVersion&gt;4.0.0&lt;/modelVersion&gt;

  &lt;groupId&gt;wildflyswarmtour&lt;/groupId&gt;
  &lt;artifactId&gt;helloworld&lt;/artifactId&gt;
  &lt;version&gt;{{book.versions.swarm}}&lt;/version&gt;
  &lt;!-- (1) jar としてパッケージング --&gt;
  &lt;packaging&gt;jar&lt;/packaging&gt;

  &lt;properties&gt;
    &lt;project.build.sourceEncoding&gt;UTF-8&lt;/project.build.sourceEncoding&gt;
    &lt;project.reporting.outputEncoding&gt;UTF-8&lt;/project.reporting.outputEncoding&gt;

    &lt;maven.compiler.source&gt;1.8&lt;/maven.compiler.source&gt;
    &lt;maven.compiler.target&gt;1.8&lt;/maven.compiler.target&gt;

    &lt;version.wildfly-swarm&gt;${project.version}&lt;/version.wildfly-swarm&gt;
  &lt;/properties&gt;

  &lt;dependencyManagement&gt;
    &lt;dependencies&gt;
      &lt;!-- (2) bom-all を指定する --&gt;
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
    &lt;!-- (3) JAX-RS を使う --&gt;
    &lt;dependency&gt;
      &lt;groupId&gt;org.wildfly.swarm&lt;/groupId&gt;
      &lt;artifactId&gt;jaxrs&lt;/artifactId&gt;
    &lt;/dependency&gt;
  &lt;/dependencies&gt;

  &lt;build&gt;
    &lt;finalName&gt;${project.artifactId}&lt;/finalName&gt;

    &lt;plugins&gt;
      &lt;!-- (4) 実行可能 jar(uber jar)を作成するためのプラグイン --&gt;
      &lt;plugin&gt;
        &lt;groupId&gt;org.wildfly.swarm&lt;/groupId&gt;
        &lt;artifactId&gt;wildfly-swarm-plugin&lt;/artifactId&gt;
        &lt;version&gt;${version.wildfly-swarm}&lt;/version&gt;
        &lt;configuration&gt;
          &lt;!-- (5) main() を持つクラスを指定 --&gt;
          &lt;mainClass&gt;wildflyswarm.Bootstrap&lt;/mainClass&gt;
        &lt;/configuration&gt;
        &lt;executions&gt;
          &lt;execution&gt;
            &lt;goals&gt;
              &lt;!-- (6)  package ゴールで動くようにする --&gt;
              &lt;goal&gt;package&lt;/goal&gt;
            &lt;/goals&gt;
          &lt;/execution&gt;
        &lt;/executions&gt;
      &lt;/plugin&gt;
    &lt;/plugins&gt;
  &lt;/build&gt;

&lt;/project&gt;
<code></pre>

ここでは jar でパッケージングするようにしています(1)。デプロイするアプリケーションの設定や WildFly 自体の設定(データソースなど)を 1 から行いたい場合に jar パッケージングを選択します。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/getting-started/jar-applications.html

WildFly Swarm には必要なモジュール(Fraction と呼ばれます)だけ選んで使うことができ、各 Fraction をバージョン指定することもできます。しかし、上記の (2) のように用意されている bill of materials(BOM)を利用すると Fraction のバージョンを意識しないですむようになるので、こちらの利用を推奨します。

> なお、ここでは bom-all を選択しましたが、その他に bom や bom-experimental などが存在します。各 Fraction には安定度が設定されており、安定度によってどの bom-* に含まれるかが決まっています。安定度の見方については以下を参照してください。
>
> http://wildfly-swarm.io/posts/announcing-wildfly-swarm-2016-8-1/#_fraction_stability_indicators

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

次にもろもろの設定をする Bootstrap クラスを以下のように作ります。これが WildFly Swarm 利用時の固有クラスです。pom.xml で mainClass に指定したクラスですね。

``` java
package wildflyswarm;

import helloworld.HelloWorld;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

public class Bootstrap {

  public static void main(String[] args) throws Exception {
    Swarm swarm = new Swarm(args); // (1)

    // (4) ShrinkWrap の API を使ってデプロイできる形にパッケージング
    JAXRSArchive archive = ShrinkWrap.create(JAXRSArchive.class);
    archive.addClass(HelloWorld.class); // (5)

    swarm
      .start() // (2)
      .deploy(archive); // (3)
  }

}
```

`org.wildfly.swarm.Swarm` インスタンス(1)を介して WildFly の設定や起動(2)、アプリケーションのデプロイ(3)を行えます。この Hello World アプリケーションでは特に設定することはないのですが、次章ではデータベースを使うためデータソースの設定をします。

(4) がデプロイするアプリケーションの設定です。これは ShrinkWrap という指定したクラスやリソースファイルを jar や war といった形にオンデマンドでアーカイブするライブラリを利用しています。ここでは先ほど作成した JAX-RS の HelloWorld クラスを追加しています(5)。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/getting-started/shrinkwrap.html

ここまででだいたい以下のようなディレクトリ構成になっているかと思います。

``` sh
.
├── pom.xml
└── src
    └── main
        └── java
            ├── helloworld
            │   └── HelloWorld.java
            └── wildflyswarm
                └── Bootstrap.java
```

必要なものはそろったので、以下コマンドを実行しビルドします。

``` sh
$ mvn clean package
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

> Spring Boot をはじめて触った時の感動が蘇ってきますね。

アプリケーションを作って実行するところまではなんとなくつかめたでしょうか? では次の章では CRUD なアプリケーションに取りかかっていきます。

以降は補足なので、読まなくても差し支えありません。

## 補足1 java -jar 以外の実行方法

このドキュメントでは `mvn package` でビルドを行い、`java -jar` で生成された uber jar を指定して実行するようにしていますが、その他にも実行方法があります。

### wildfly-swarm:run

WildFly Swarm Plugin は wildfly-swarm:run というゴールが用意されています。

``` sh
$ mvn wildfly-swarm:run
```

### IDE からの実行

Bootstrap クラスは main() メソッドを持つクラスですので、各 IDE からこの main() メソッドを指定して実行することもできます。

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
$ mvn clean package && java -jar target/helloworld-swarm.jar
$ curl localhost:8080/api/hello
{"message" : "Hello, WildFly Swarm!"}%
```

いいですね。

## 補足3 コンテキストルートは "/" 固定なの?

例によって WildFly Swarm が勝手に設定しているのですが、コンテキストルートが `"/"` であるのが不都合な場合もあるかと思います。変更方法は 3 種類あります。

1. システムプロパティ swarm.context.path を渡す
1. jboss-web.xml で設定
1. WildFly Swarm の API を利用

### システムプロパティ swarm.context.path を渡す

まずシステムプロパティで設定する方法です。渡し方は 2 種類あります。

1 つは実行時に渡す方法です。

``` sh
$ java -Dswarm.context.path=helloworld -jar target/helloworld-swarm.jar
```

or

``` sh
$ java -jar target/helloworld-swarm.jar -Dswarm.context.path=helloworld
```

> 後者のように引数として渡す場合は、Bootstrap クラスにおいて Swarm インスタンスを作成する際、new Swarm(args) とコマンドライン引数を渡しておく必要があります。

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

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/configuration_properties.html

### jboss-web.xml で設定

以下のような `jboss-web.xml` というファイルをアーカイブに含める方法もあります。

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<jboss-web>
  <context-root>/helloworld</context-root>
</jboss-web>
```

そして Bootstrap クラスでこのファイルを含めるように設定します。`addAsWebInfResource(...)` はその名の通り、war パッケージングでの WEB-INF 以下に含めてね、というメソッドです。

``` java
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
[...]
archive.addAsWebInfResource(
      new ClassLoaderAsset("jboss-web.xml", Bootstrap.class.getClassLoader()), "jboss-web.xml");
```

ここでは以下のように jboss-web.xml がクラスパス(src/main/resources 以下)にあるとします。

``` sh
.
├── pom.xml
└── src
    └── main
        ├── java
        │   ├── helloworld
        │   │   └── HelloWorld.java
        │   └── wildflyswarm
        │       └── Bootstrap.java
        └── resources
            └── jboss-web.xml
```


### WildFly Swarm の API を利用

以下のように指定することもできます。

``` java
archive.setContextRoot("/helloworld");
```

これは前述の jboss-web.xml での設定をプログラムでできるようにしています。その他のメソッドについては javadoc を参照ください。

https://wildfly-swarm.github.io/wildfly-swarm-javadocs/{{book.versions.swarm}}/apidocs/org/wildfly/swarm/undertow/descriptors/JBossWebContainer.html

### 動作確認

では上記いずれかの方法で修正し、必要であれば再ビルドしてからアクセス。

``` sh
$ curl localhost:8080/helloworld/hello
{"message" : "Hello, WildFly Swarm!"}
```

いいかんじです。

## 補足4 war でのパッケージング

実は WildFly Swarm では以下サンプルのように war でもパッケージングできます。

https://github.com/wildfly-swarm/wildfly-swarm-examples/tree/{{book.versions.swarm}}/jaxrs/jaxrs-war

また main() を設定 することもできます。

https://github.com/wildfly-swarm/wildfly-swarm-examples/tree/{{book.versions.swarm}}/jaxrs/jaxrs-war-main

既存のアプリケーションをとりあえず WildFly Swarm に対応させるのに便利かと思います。

war パッケージングに関するドキュメントは以下です。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/getting-started/war-applications.html
