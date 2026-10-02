package org.acme.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@ApplicationScoped
public class RoleFilteredRagAugmentor implements Supplier<RetrievalAugmentor> {

    private static final Path INTERNAL_RAG_PATH = Path.of("src/main/resources/rag/internal");
    private static final int MAX_RESULTS = 30;

    @Inject
    EmbeddingStore<TextSegment> publicStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    SecurityIdentity identity;

    private final EmbeddingStore<TextSegment> internalStore = new InMemoryEmbeddingStore<>();

    @PostConstruct
    void ingestInternalDocuments() {
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(INTERNAL_RAG_PATH, new TextDocumentParser());
        EmbeddingStoreIngestor.builder()
                .embeddingStore(internalStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(DocumentSplitters.recursive(100, 25))
                .build()
                .ingest(documents);
    }

    ContentRetriever retriever() {
        ContentRetriever publicRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(publicStore)
                .embeddingModel(embeddingModel)
                .maxResults(MAX_RESULTS)
                .build();
        ContentRetriever internalRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(internalStore)
                .embeddingModel(embeddingModel)
                .maxResults(MAX_RESULTS)
                .build();
        return query -> {
            List<Content> results = new ArrayList<>(publicRetriever.retrieve(query));
            if (identity.hasRole("organizer")) {
                results.addAll(internalRetriever.retrieve(query));
            }
            return results;
        };
    }

    @Override
    public RetrievalAugmentor get() {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever())
                .build();
    }
}
