# 付録 WildFly Swarm の注意点

ここでは WildFly Swarm を利用するにあたっての注意点を記載します。

## サポートされない設定

WildFly Swarm は主に Microservices での利用を念頭に置かれていることから、以下の設定は WildFly Swarm では利用できません。

### domain モード

従来の WildFly ではアプリケーションサーバが 1 個単体で動く standalone モードに加え、複数のサーバを管理することができる domain モードという形態が利用できますが、WildFly Swarm では利用できません。

### クラスタリング/セッションレプリケーション

WildFly Swarm では、以下の ML でのトピックで結論づけられているように、通常の WildFly で利用できるクラスタリングおよびセッションレプリケーションはサポートされないことになりました。

https://groups.google.com/d/msg/wildfly-swarm/tHJ6e0ifKGg/-kgokWyYAQAJ

クラスタリング/セッションレプリケーションを利用したい場合は、通常の WildFly または [Payara Micro](http://www.payara.fish/payara_micro) を利用するのがよいでしょう。

また、開発陣はコミュニティがこのような機能を作ることを禁止しているわけではないので、ご自身で有効化した状態の WildFly Swarm をビルドしたり、独自の Fraction を作成することは問題ありません。以下の [@nekop](https://twitter.com/nekop) さんの Pull Request がヒントになるかと思います。

[SWARM-665 Setup infinispan module for cachecontainer](https://github.com/wildfly-swarm/wildfly-swarm/pull/132)
