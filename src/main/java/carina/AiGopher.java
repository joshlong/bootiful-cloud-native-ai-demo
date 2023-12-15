package carina;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.SystemPromptTemplate;
import org.springframework.ai.prompt.messages.UserMessage;
import org.springframework.ai.retriever.VectorStoreRetriever;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class AiGopher {

	private final String qaTemplate;

	private final AiClient aiClient;

	private final VectorStoreRetriever vectorStoreRetriever;

	AiGopher(String template, AiClient aiClient, VectorStoreRetriever vectorStoreRetriever) {
		this.aiClient = aiClient;
		this.vectorStoreRetriever = vectorStoreRetriever;
		this.qaTemplate = template;
	}

	public String chat(String message) {
		var similarDocuments = vectorStoreRetriever.retrieve(message);
		var documents = similarDocuments.stream()
			.map(Document::getContent)
			.collect(Collectors.joining(System.lineSeparator()));
		var systemMessage = new SystemPromptTemplate(this.qaTemplate).createMessage(Map.of("documents", documents));
		var userMessage = new UserMessage(message);//
		var prompt = new Prompt(List.of(systemMessage, userMessage));//
		var aiResponse = aiClient.generate(prompt);//
		return aiResponse.getGeneration().getContent();//
	}

}
