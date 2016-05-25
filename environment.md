# 環境

* WildFly Swarm {{book.versions.swarm}}
* JDK {{book.versions.jdk}}
* Maven {{book.versions.maven}}
* PostgreSQL {{book.versions.postgresql}}
* Keycloak {{book.versions.keycloak}}
* Docker {{book.versions.docker}}
* Docker Compose {{book.versions.docker_compose}}

とりあえず最低限 JDK 8 をインストールしておいていただければすぐに試せます。

OS はなんでもよいですが、Fedora 22/CentOS 7.2.1511 64bit で動作確認しています。道中何回かコマンドの実行がありますが、Windows な方は適宜読み替えていただければと。すみません。

Docker のセットアップについては下記公式サイトや[日本語化プロジェクト](http://docs.docker.jp/)、各種 Web 情報をご覧ください。

* [Windows](http://docs.docker.com/windows/started/)
* [Mac OS X](http://docs.docker.com/mac/started/)
* [Linux](http://docs.docker.com/linux/started/)

Docker セットアップ後は sudo なしで docker コマンドが叩けるよう、ユーザを `docker` グループに入れておくと便利です。

また、このエントリで用いるリポジトリを利用する場合、[Maven Wrapper](https://github.com/takari/takari-maven-plugin) を用意していますので Maven を別途インストールする必要はありません。

ただし IDE 経由でビルドする場合、バンドルする Maven のバージョンが低いと以下の問題が発生します。

[error packaging project: java.lang.NoClassDefFoundError: org/eclipse/aether/RepositorySystemSession](https://issues.jboss.org/browse/SWARM-24)

その場合は別途インストールするか、 Maven Wrapper でインストールされた Maven (`~/.m2/wrapper/dists/` 以下の深いところにあります)のパスを IDE 側で指定してください。

Gradle でやりたいぜ、という方は以下公式サンプルの `build.gradle` をご参考ください。

https://github.com/wildfly-swarm/wildfly-swarm-examples/tree/{{book.versions.swarm}}/gradle
