# Arquillian を用いた Integration Test

次は Arquillian を利用して Integration Test を記述します(ユニットテストもないのにって感じですが)。

Arquillian は WildFly などのアプリケーションサーバを実際に動かして、テストの中で実際にアプリケーションサーバを起動してしまおう、というものです。実際のアプリケーションサーバを起動するので、EJB や CDI などのコンテナが必要な機能に対してモックなどでエミュレートする必要がありません。

http://arquillian.org/

通常、Arquillian でテストする際はアプリケーションサーバを別途インストールしあらかじめ起動しておくか(remote)、設定ファイルでインストールパスを指定してテスト実行時に起動する(managed)必要があります。

しかし、WildFly Swarm では WildFly 本体を含んで実行するのでちょっと事情が違います。そこで WildFly Swarm では Arquillian のモジュールも用意しており、簡単に使えるようになっています。

https://wildfly-swarm.gitbooks.io/wildfly-swarm-users-guide/content/testing_with_arquillian.html

では lifelog-arquillian プロジェクトの pom.xml を見てみますと、テスト用の設定として以下が追加されています。

``` xml
<dependency>
  <groupId>org.wildfly.swarm</groupId>
  <artifactId>arquillian</artifactId>
  <version>${version.wildfly-swarm}</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.jboss.arquillian.junit</groupId>
  <artifactId>arquillian-junit-container</artifactId>
  <version>${version.org.arquillian}</version>
  <scope>test</scope>
</dependency>
```

これが WildFly Swarm 用の Arquillian に必要な設定です。

また、今回作るテストは起動した WildFly の内部でなく外からテスト(@RunAsClient)するため、HTTP クライアントの依存性を別途追加しておきます。

``` xml
<dependency>
  <groupId>org.jboss.resteasy</groupId>
  <artifactId>resteasy-client</artifactId>
  <version>${version.resteasy}</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.jboss.resteasy</groupId>
  <artifactId>resteasy-jackson2-provider</artifactId>
  <version>${version.resteasy}</version>
  <scope>test</scope>
</dependency>
```

最後に、このテストは Integration Test として verify で実行したいため、maven-failsafe-plugin の設定をしておきます。IT と末尾についたテストが verify 時に実行されます。

``` xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <version>${version.maven-failsafe-plugin}</version>
  <executions>
    <execution>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

では実際にテストを記述します。

``` java
package wildflyswarmtour.lifelog.api;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.ContainerFactory;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.jaxrs.JAXRSArchive;
import wildflyswarmtour.lifelog.LifeLogContainer;
import wildflyswarmtour.lifelog.LifeLogDeployment;
import wildflyswarmtour.lifelog.domain.model.Entry;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;

import static org.hamcrest.core.Is.*;
import static org.junit.Assert.*;

// (1) Arquillian でテストする場合は以下のように @RunWith を指定
@RunWith(Arquillian.class)
public class EntryControllerIT implements ContainerFactory {

  @Deployment(testable = false) // (2) testable=false としておくと、コンテナの外からのテスト(@RunAsClient アノテーションも同様)
  public static JAXRSArchive createDeployment() {
    // (3) デプロイするアーカイブ設定。LifeLogDeployment.deployment() をそのまま使う
    return LifeLogDeployment.deployment();
  }

  // (4) org.wildfly.swarm.container.Container を implements した場合、このメソッドでコンテナ設定を行う
  @Override
  public Container newContainer(String... args) throws Exception {
    // コンテナの設定。LifeLogContainer.newContainer() をそのまま使う
    return LifeLogContainer.newContainer();
  }

  // (5) testable = false の時に使う。ホスト名やポート番号がインジェクションされる
  @ArquillianResource
  private URI deploymentUri;

  // (6) テスト内容
  @Test
  public void test() {
    UriBuilder baseUri = UriBuilder.fromUri(deploymentUri).path("entries");

    // Create a new entry
    Client client = ClientBuilder.newClient();
    WebTarget target = client.target(baseUri);

    Entry entry = new Entry();
    entry.setDescription("Test");
    Response response = target.request().post(Entity.json(entry));

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    URI newEntryLocation = response.getLocation();

    client.close();

    // Get the entry
    client = ClientBuilder.newClient();
    target = client.target(newEntryLocation);
    response = target.request(MediaType.APPLICATION_JSON_TYPE).get();

    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    assertThat(response.readEntity(EntryResponse.class).getDescription(), is("Test"));

    client.close();

    // Delete the entry
    client = ClientBuilder.newClient();
    target = client.target(newEntryLocation);
    response = target.request().delete();

    assertThat(response.getStatus(), is(Response.Status.NO_CONTENT.getStatusCode()));

    client.close();

    // Check no entries
    client = ClientBuilder.newClient();
    target = client.target(baseUri);
    response = target.request(MediaType.APPLICATION_JSON_TYPE).get();

    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    List<EntryResponse> entries = response.readEntity(new GenericType<List<EntryResponse>>() {});
    assertThat(entries.size(), is(0));

    client.close();
  }

}
```

(1) - (5) までが Arquillian の設定のもろもろです。通常の Arquillian 設定と異なるところとしては (4) のコンテナ設定があげられます。

また、(3)(4) ではプロダクションコードで作っていおいたコードをそのまま利用しています。

> もしテスト用に違う設定が必要な場合は似たようなクラスを作るか、ここにベタ書きします。

(6) からが実際のテスト内容です。1 つのテストにいろいろやっていてちょっと行儀悪いですが、GET/POST/DELETE を一通り実施しています。

それでは以下コマンドでテストを実行します。

``` sh
$ ./mvnw clean verify -pl lifelog-arquillian
[...]
-------------------------------------------------------
 T E S T S
-------------------------------------------------------
Running wildflyswarmtour.lifelog.api.EntryControllerIT
[...]
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 68.808 sec - in wildflyswarmtour.lifelog.api.EntryControllerIT

Results :

Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

[INFO]
[INFO] --- maven-failsafe-plugin:2.19:verify (default) @ lifelog-arquillian ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 27.195 s
[INFO] Finished at: 2016-02-11T23:34:38+09:00
[INFO] Final Memory: 45M/490M
[INFO] ------------------------------------------------------------------------
```

エラー無く終われば成功です。PUT がないなど抜けがありますが、とりあえずひとつテストができて安心ですね。
