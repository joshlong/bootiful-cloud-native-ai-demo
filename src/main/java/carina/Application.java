package carina;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.retriever.VectorStoreRetriever;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.File;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		new SpringApplicationBuilder().headless(false).sources(Application.class).run(args);
	}

	@Bean
	VectorStore vectorStore(EmbeddingClient embeddingClient, JdbcTemplate jdbcTemplate) {
		return new PgVectorStore(jdbcTemplate, embeddingClient);
	}

	@Bean
	VectorStoreRetriever vectorStoreRetriever(VectorStore vectorStore) {
		return new VectorStoreRetriever(vectorStore, 4, 0.75);
	}

	@Bean
	ApplicationRunner demo(VectorStore vectorStore, AiGopher assistant, JdbcClient jdbcClient,
			@Value("${demo.file}") File demoFile) {
		return args -> {

			reset(vectorStore, jdbcClient, new FileSystemResource(demoFile));

			var question = "who's who on carina	?";
			var answer = assistant.chat(question);
			System.out.println("answer: " + answer);

		};
	}

	@Bean
	AiGopher aiAssistant(AiClient aic, VectorStoreRetriever vectorStoreRetriever) {

		var qa = """
				You're assisting with questions about services offered by Carina.
				Carina is a two-sided healthcare marketplace focusing on home care aides (caregivers)
				and their Medicaid in-home care clients (adults and children with developmental disabilities and low income elderly population).
				Carina's mission is to build online tools to bring good jobs to care workers, so care workers can provide the
				best possible care for those who need it.

				Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
				If unsure, simply state that you don't know.

				DOCUMENTS:
				{documents}
				""";

		return new AiGopher(qa, aic, vectorStoreRetriever);
	}

	private static void reset(VectorStore vectorStore, JdbcClient jdbcClient, Resource pdfResource) {

		jdbcClient.sql("DELETE FROM vector_store").update();

		var config = PdfDocumentReaderConfig.builder()
			.withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder().withNumberOfBottomTextLinesToDelete(3)
				.withNumberOfTopPagesToSkipBeforeDelete(1)
				.build())
			.withPagesPerDocument(1)
			.build();

		var pdfReader = new PagePdfDocumentReader(pdfResource, config);
		var textSplitter = new TokenTextSplitter();
		vectorStore.accept(textSplitter.apply(pdfReader.get()));
	}

}
