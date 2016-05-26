# WildFly Swarm とは

開発に入る前に、WildFly Swarm とは何か簡単に説明します。

まず、WildFly というのは Java EE 7 フルプロファイル対応のアプリケーションサーバです。

http://wildfly.org/

WildFly Swram はこの WildFly を取り込んで実行可能 jar にできるようにしたもので、従来の WildFly をダウンロード・設定・アプリケーションをデプロイとしていた手順が 1 つのアプリケーション上で実施できるようになります。要は WildFly/Java EE で Spring Boot のようにアプリケーションを作ってすぐに起動できる、というようなイメージで差し支えありません。

http://wildfly-swarm.io/

特徴としては以下が挙げられます。

* 使いたいモジュールだけ利用(JAX-RS, CDI, JPA などなど)
* Logstash や Netflix OSS、Swagger などのインテグレーション
* WildFly の設定を網羅

なお、2015 年の JavaOne で開発者の方が講演されています。

* [JavaOne 2015 - Ken Finnigan - Java EE 7 Applications as a Microservice with WildFly Swarm](https://developers.redhat.com/video/youtube/i1aiUaa8RZ8/)
* [JavaOne 2015 - Ken Finnigan - WildFly Swarm and Netflix OSS: The Perfect Union?](https://developers.redhat.com/video/youtube/RsaKmBTgEhM/)

また、[@n_agetsu](https://twitter.com/n_agetsu) さんの JavaOne 報告会資料中でも取り上げられています(※ P36 以降)。

<iframe src="//www.slideshare.net/slideshow/embed_code/key/pfAEokEuAEUFZr" width="595" height="485" frameborder="0" marginwidth="0" marginheight="0" scrolling="no" style="border:1px solid #CCC; border-width:1px; margin-bottom:5px; max-width: 100%;" allowfullscreen> </iframe> <div style="margin-bottom:5px"> <strong> <a href="//www.slideshare.net/agetsuma/javaone2015-java-ee-j1jp" title="JavaOne2015報告会 Java EE アップデート #j1jp" target="_blank">JavaOne2015報告会 Java EE アップデート #j1jp</a> </strong> from <strong><a href="//www.slideshare.net/agetsuma" target="_blank">Norito Agetsuma</a></strong> </div>
