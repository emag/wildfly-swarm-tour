# WildFly Swarm Tour

本ドキュメントは [WildFly Swarm](http://wildfly-swarm.io/) を一通り触ってみるためのガイドです。

まず Hello World レベルのもので動作確認をし、その後 JAX-RS + CDI + JPA を用いた簡単な CRUD アプリケーションを作ります。

また、[Arquillian](http://arquillian.org/) を用いた Integration Test や [Keycloak](http://www.keycloak.org/) を用いた認証、Docker イメージの作成あたりまでやってみたいと思います。

Java EE、特に JAX-RS + CDI + JPA を簡単に使ったことがあって、さらに WildFly 自体もデータソースなど設定したことがあると理解は楽勝で、ふつうの Java EE での開発との差異を感じていただければと思います。

とはいえ、アプリケーション自体は単純であり、また簡単にですが説明は入れていきますので、まったく Java EE は初めての方でも Java で Web アプリケーションを作ったことがあればすんなりわかる内容かと思います。

ここがおかしいですとか、わかりづらいことがあれば [Twitter](https://twitter.com/emaggame) で声をかけていただると幸いです。

## その他情報

* WildFly Swarm のバージョン: {{book.versions.swarm}}
* このドキュメントのありか: https://github.com/emag/wildfly-swarm-tour
* 筆者 Twitter: https://twitter.com/emaggame
