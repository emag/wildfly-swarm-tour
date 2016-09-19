# WildFly Swarm とは

開発に入る前に、WildFly Swarm とは何か簡単に説明します。

まず、WildFly というのは Java EE 7 フルプロファイル対応のアプリケーションサーバです。

http://wildfly.org/

WildFly Swram はこの WildFly を取り込んで実行可能 jar(uber jar) にできるようにしたもので、従来の WildFly をダウンロード・設定・アプリケーションをデプロイとしていた手順が 1 つのアプリケーション上で実施できるようになります。要は WildFly/Java EE で Spring Boot のようにアプリケーションを作ってすぐに起動できる、というようなイメージで差し支えありません。

http://wildfly-swarm.io/

特徴としては以下が挙げられます。

* 使いたいモジュールだけ利用(JAX-RS, CDI, JPA などなど)
 * WildFly ではこういったモジュールのことを [Fraction](https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/v/{{book.versions.swarm}}/getting-started/concepts.html) と呼んでいます
* Logstash や Netflix OSS、Swagger などのインテグレーション
* WildFly の設定を網羅
