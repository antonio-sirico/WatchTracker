package it.watchtracker.catalog;

import java.util.List;

/** Risultati della ricerca e avvisi sui cataloghi non raggiunti. */
public record SearchResponse(String query, List<ContentSummary> results, List<String> warnings) {}
