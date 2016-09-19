# 環境

このドキュメントでは以下環境を想定しています。

* WildFly Swarm {{book.versions.swarm}}
* JDK {{book.versions.jdk}}
* Maven {{book.versions.maven}}
* PostgreSQL {{book.versions.postgresql}}
* Keycloak {{book.versions.keycloak}}
* Docker {{book.versions.docker}}
* Docker Compose {{book.versions.docker_compose}}

とりあえず最低限 JDK 8 をインストールしておいていただければ Hello World はすぐに試せます。

OS はなんでもよいですが、筆者は Linux(Fedora) で動作確認しています。道中何回かコマンドの実行がありますが、Windows な方は適宜読み替えていただければと。すみません。

## Maven

Maven は最新のものを、少なくとも 3.2.5 以上の利用を推奨します。また、IDE 経由でビルドする場合、バンドルする Maven のバージョンが低いと以下の問題が発生します。

[error packaging project: java.lang.NoClassDefFoundError: org/eclipse/aether/RepositorySystemSession](https://issues.jboss.org/browse/SWARM-24)

## Gradle

Gradle でやりたいぜ、という方は以下ドキュメントやサンプルを参考ください。ただし、現状あまり Gradle についてはサポートされていないようです。。

* https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.version.swarm}}/getting-started/tooling/gradle-plugin.html
* https://github.com/wildfly-swarm/wildfly-swarm-examples/tree/{{book.versions.swarm}}/gradle


## Docker

Docker のセットアップについては下記公式サイトや[日本語化プロジェクト](http://docs.docker.jp/)、各種 Web 情報をご覧ください。

* [Windows](http://docs.docker.com/windows/started/)
* [Mac OS X](http://docs.docker.com/mac/started/)
* [Linux](https://docs.docker.com/engine/installation/#/on-linux)

Docker セットアップ後は sudo なしで docker コマンドが叩けるよう、ユーザを `docker` グループに入れておくと便利です。
