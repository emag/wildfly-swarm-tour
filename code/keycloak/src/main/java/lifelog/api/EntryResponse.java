package lifelog.api;

import lifelog.domain.model.Entry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntryResponse implements Serializable {

  private Integer id;
  private String createdAt;
  private String description;

  public static EntryResponse from(Entry entry) {
    return new EntryResponse(
      entry.getId(),
      entry.getCreatedAt().toString(),
      entry.getDescription());
  }

}
