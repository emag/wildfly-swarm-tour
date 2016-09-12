package lifelog.domain.repository;

import lifelog.domain.model.Entry;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.List;

@ApplicationScoped
@Transactional
public class EntryRepository {

  @PersistenceContext
  private EntityManager em;

  /**
   * 全件取得、タイムスタンプの降順。エントリが 1 件も存在しない場合は空リストを返す
   */
  public List<Entry> findAll() {
    return em
        .createQuery("SELECT e FROM Entry e ORDER BY  e.createdAt DESC", Entry.class)
        .getResultList();
  }

  /**
   * id をキーに 1 件取得
   */
  public Entry find(Integer id) {
    return em.find(Entry.class, id);
  }

  /**
   * 新規作成・更新処理
   */
  public Entry save(Entry entry) {
    // id を持っていない場合は新しい Entry なので、永続化
    if (entry.getId() == null) {
      em.persist(entry);
      return entry;
    // id がある場合は既存エントリの更新なので、そのままマージ
    } else {
      return em.merge(entry);
    }
  }

  /**
   * 全件削除。実体は delete(Entry entry) をぐるぐる呼んでるだけ
   */
  public void deleteAll() {
    findAll().forEach(this::delete);
  }

  /**
   * id をキーに 1 件削除。実体は delete(Entry entry)
   */
  public void delete(Integer id) {
    delete(em.find(Entry.class, id));
  }

  /**
   * 渡された Entry インスタンスに対して削除処理
   */
  private void delete(Entry entry) {
    em.remove(entry);
  }

}
