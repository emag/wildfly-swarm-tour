package lifelog.api;

import lifelog.domain.model.Entry;
import lifelog.domain.service.EntryService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/entries")
public class EntryController {

  @Inject
  private EntryService entryService;

  /**
   * GET /entries
   * JSON でエントリ一覧を返す。1 件もエントリがないときは空配列で返す
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<EntryResponse> findAll() {
    List<Entry> allEntries = entryService.findAll();
    return allEntries.stream()
      .map(EntryResponse::from)
      .collect(Collectors.toList());
  }

  /**
   * GET /entries/:id
   * その id のエントリがないときは 404
   */
  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response find(@PathParam("id") Integer id) {
    Entry entry = entryService.find(id);

    if (entry == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    return Response.ok(EntryResponse.from(entry)).build();
  }

  /**
   * POST /entries
   * JSON を受け取り、その内容をもって新規作成
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(@Context UriInfo uriInfo, Entry entry) {
    Entry created = entryService.save(entry);

    return Response
      .created(
        uriInfo.getAbsolutePathBuilder()
          .path(String.valueOf(created.getId()))
          .build())
      .build();
  }

  /**
   * PUT /entries/:id
   * JSON を受け取り、指定された id に対して更新。その id のエントリがないときは 404
   */
  @PUT
  @Path("{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response update(@PathParam("id") Integer id, Entry entry) {
    Entry old = entryService.find(id);

    if (old == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    old.setDescription(entry.getDescription());
    entryService.save(old);

    return Response.ok().build();
  }

  /**
   * DELETE /entries
   * 全件削除
   */
  @DELETE
  public Response deleteAll() {
    entryService.deleteAll();
    return Response.noContent().build();
  }

  /**
   * DELETE /entries/:id
   * 指定された id の削除。その id のエントリがないときは 404
   */
  @DELETE
  @Path("{id}")
  public Response delete(@PathParam("id") Integer id) {
    if (entryService.find(id) == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    entryService.delete(id);

    return Response.noContent().build();
  }

}
