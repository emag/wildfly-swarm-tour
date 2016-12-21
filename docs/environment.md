# 環境

このドキュメントでは以下環境を想定しています。

* WildFly Swarm {{book.versions.swarm}}
* JDK {{book.versions.jdk}}
* Maven {{book.versions.maven}}
* PostgreSQL {{book.versions.postgresql}}
* Keycloak {{book.versions.keycloak}}
* Docker {{book.versions.docker}}
* Docker Compose {{book.versions.docker_compose}}

とりあえず最低限 JDK 8 をインストールしておいていただければ、Hello World はすぐに試せます。

OS はなんでもよいですが、筆者は Linux(Fedora) で動作確認しています。ビルド時などコマンドを実行する必要がありますが、Windows な方は適宜読み替えていただければと。すみません。

## Maven

本資料はビルドツールとして [Maven](https://maven.apache.org/) を利用します。[Maven Wrapper](https://github.com/takari/maven-wrapper) 込みの雛形のプロジェクトを用意してありますので、Maven を個別でインストールする必要はありません。

Maven Warpper でなく、ご自身でインストールした Maven を利用したい場合は、最新バージョン(少なくとも 3.2.5 以上)の利用を推奨します。また、IDE 経由でビルドする場合、デフォルトで利用されるバンドルされた Maven のバージョンが古いことがあるのでご注意ください。

> Maven のバージョンが低いと以下の問題が発生します。
>
> [SWARM-24: error packaging project: java.lang.NoClassDefFoundError: org/eclipse/aether/RepositorySystemSession](https://issues.jboss.org/browse/SWARM-24)

## Gradle

[Gradle](https://gradle.org/) でやりたいぜ、という方は以下ドキュメントやサンプルを参考ください。ただし、現状あまり Gradle についてはサポートされていないようです。。

* https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/getting-started/tooling/gradle-plugin.html
* https://github.com/wildfly-swarm/wildfly-swarm-examples/tree/{{book.versions.swarm}}/gradle

## Docker

Docker のセットアップについては下記公式サイトや[日本語化プロジェクト](http://docs.docker.jp/)、各種 Web 情報をご覧ください。

* https://docs.docker.com/engine/getstarted/step_one/

Docker セットアップ後は sudo なしで docker コマンドが叩けるよう、ユーザを `docker` グループに入れておくと便利です。

## ハンズオン向け事前準備

本資料は基本的に独習可能なものを目指していますが、ハンズオンなどワークショップでもお使いいただけます(利用にあたって筆者に事前許諾は不要です)。

### インストールするもの

事前に以下のものを PC にインストールしてください。

* 任意の OS
* JDK 8(なるべく新しいバージョン)
* 任意のエディタ/IDE
* curl コマンド
    * バイナリは https://curl.haxx.se/download.html からダウンロードできます
* jq コマンド
   * バイナリは https://stedolan.github.io/jq/ からダウンロードできます
* Docker
    * インストールは https://docs.docker.com/engine/getstarted/step_one/ を参考ください
        * 日本語化プロジェクト: http://docs.docker.jp/engine/installation/toc.html
* Docker Compose
    * インストールは https://docs.docker.com/compose/install/ を参考ください
        * 日本語化プロジェクト: http://docs.docker.jp/compose/install.html

### ライブラリのインストールおよび Docker イメージの pull

WildFly Swarm は非常に多くのライブラリからなるため、
ハンズオン中には最低限のダウンロードで済むよう、事前に一通りウォームアップしていただければと思います。
また、同様に Docker イメージの pull も実施ください。

以下を実施いただくと、一通り必要な資材がダウンロードされます。

まず、適当なディレクトリにハンズオン用の資料をダウンロードし展開後、`docker` ディレクトリに移動します。

<pre><code class="lang-sh">$ curl -sL https://github.com/emag/wildfly-swarm-tour/archive/{{book.versions.swarm}}.zip -o /tmp/wildfly-swarm-tour.zip \
  && unzip -q /tmp/wildfly-swarm-tour.zip -d /tmp/ \
  && cd /tmp/wildfly-swarm-tour-{{book.versions.swarm}}/code/docker/
</code></pre>

必要なライブラリのインストール及び、Docker イメージの一部を pull します(その1)。

``` sh
$ ./mvnw clean package && docker build -t test/lifelog . && docker rmi -f test/lifelog
```

必要なライブラリのインストール及び、Docker イメージの一部を pull します(その2)。

``` sh
$ ./mvnw clean verify \
  -Dswarm.project.stage.file=file://`pwd`/project-stages.yml \
  -Dswarm.project.stage=it \
  -Dauth.url=http://localhost:28080/auth \
  -Pit
```

これで準備は完了です。
