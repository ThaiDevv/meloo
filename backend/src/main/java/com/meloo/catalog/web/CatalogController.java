package com.meloo.catalog.web;

import com.meloo.catalog.search.TrackSearchService;
import com.meloo.generated.api.CatalogApi;
import com.meloo.generated.model.FilterOptionsDto;
import com.meloo.generated.model.PlaylistPageDto;
import com.meloo.generated.model.TrackPageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController implements CatalogApi {

  private final TrackSearchService trackSearchService;

  public CatalogController(TrackSearchService trackSearchService) {
    this.trackSearchService = trackSearchService;
  }

  @Override
  public ResponseEntity<TrackPageDto> searchTracks(String q, Integer page, Integer size) {
    TrackPageDto result = trackSearchService.searchTracks(q, page, size);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<PlaylistPageDto> listPlaylists(Integer page, Integer size) {
    PlaylistPageDto result = trackSearchService.listPlaylists(page, size);
    return ResponseEntity.ok(result);
  }

  @Override
  public ResponseEntity<FilterOptionsDto> getFilters() {
    FilterOptionsDto result = trackSearchService.getFilters();
    return ResponseEntity.ok(result);
  }
}
