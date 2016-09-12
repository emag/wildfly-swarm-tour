package lifelog.domain.service;

import lifelog.domain.model.Entry;
import lifelog.domain.repository.EntryRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.List;

@ApplicationScoped
@Transactional
public class EntryService {

  @Inject
  private EntryRepository entryRepository;

  public List<Entry> findAll() {
    return entryRepository.findAll();
  }

  public Entry find(Integer id) {
    return entryRepository.find(id);
  }

  public Entry save(Entry entry) {
    return entryRepository.save(entry);
  }

  public void deleteAll() {
    entryRepository.deleteAll();
  }

  public void delete(Integer id) {
    entryRepository.delete(id);
  }

}
