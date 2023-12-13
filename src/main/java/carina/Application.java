package carina;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import org.slf4j.LoggerFactory;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.retriever.VectorStoreRetriever;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@EnableConfigurationProperties(DemoProperties.class)
@ImportRuntimeHints({ Application.KnuddelsHints.class, Application.OpenAiEmbeddingsHints.class,
		Application.PdfReaderHints.class })
@SpringBootApplication
public class Application {

	static class KnuddelsHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			hints.resources().registerResource(new ClassPathResource("/com/knuddels/jtokkit/cl100k_base.tiktoken"));
		}

	}

	static class OpenAiEmbeddingsHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var memberCategories = MemberCategory.values();
			for (var c : new Class[] { Embedding.class, EmbeddingRequest.class, EmbeddingResult.class })
				hints.reflection().registerType(c, memberCategories);

			hints.resources()
				.registerResource(new ClassPathResource("embedding/embedding-model-dimensions.properties"));
		}

	}

	static class PdfReaderHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			try {

				var resolver = new PathMatchingResourcePatternResolver();

				var patterns = Set.of("/org/apache/pdfbox/resources/glyphlist/zapfdingbats.txt",
						"/org/apache/pdfbox/resources/glyphlist/glyphlist.txt", "/org/apache/fontbox/cmap/**",
						"/org/apache/pdfbox/resources/afm/**", "/org/apache/pdfbox/resources/glyphlist/**",
						"/org/apache/pdfbox/resources/icc/**", "/org/apache/pdfbox/resources/text/**",
						"/org/apache/pdfbox/resources/ttf/**", "/org/apache/pdfbox/resources/version.properties");

				for (var pattern : patterns)
					for (var resourceMatch : resolver.getResources(pattern))
						hints.resources().registerResource(resourceMatch);

			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}

		}

	}

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
	ApplicationRunner demo(VectorStore vectorStore, AiAssistant assistant, JdbcClient jdbcClient,
			DemoProperties properties) {
		return args -> {
			var log = LoggerFactory.getLogger(getClass());

			if (properties.initializeVectorDb()) {
				jdbcClient.sql("DELETE FROM vector_store").update();
				for (var r : properties.documents())
					load(vectorStore, r);
			}

			var count = jdbcClient.sql("SELECT COUNT(*) as c FROM vector_store")
				.query((rs, rowNum) -> rs.getInt("c"))
				.single();
			log.info("there are " + count + " records in the vector store");

			var question = "What is Carina?";
			var stuffedAnswer = assistant.generate(question, true);
			var defaultAnswer = assistant.generate(question, false);

			Map.of("stuffed", stuffedAnswer, "default", defaultAnswer)
				.forEach((k, v) -> System.out.println(k + '=' + v + System.lineSeparator()));

		};
	}

	@Bean
	AiAssistant aiAssistant(DemoProperties properties, AiClient aic, VectorStoreRetriever vectorStoreRetriever) {
		return new AiAssistant(properties.chatbotSystemPrompt(), properties.qaSystemPrompt(), aic,
				vectorStoreRetriever);
	}

	private static void load(VectorStore vectorStore, File pdfResource) {
		var config = PdfDocumentReaderConfig.builder()
			.withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder().withNumberOfBottomTextLinesToDelete(3)
				.withNumberOfTopPagesToSkipBeforeDelete(1)
				.build())
			.withPagesPerDocument(1)
			.build();

		var pdfReader = new PagePdfDocumentReader(new FileSystemResource(pdfResource), config);
		var textSplitter = new TokenTextSplitter();
		vectorStore.accept(textSplitter.apply(pdfReader.get()));
	}

}
