package carina;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.SystemPromptTemplate;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.UserMessage;
import org.springframework.ai.retriever.VectorStoreRetriever;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class AiAssistant {

	private static final Logger log = LoggerFactory.getLogger(AiAssistant.class);

	private final File qaSystemPromptResource;

	private final File chatbotSystemPromptResource;

	private final AiClient aiClient;

	private final VectorStoreRetriever vectorStoreRetriever;

	AiAssistant(File chatBotSystemPromptResource, File qaSystemPromptResource, AiClient aiClient,
			VectorStoreRetriever vectorStoreRetriever) {
		this.aiClient = aiClient;
		this.vectorStoreRetriever = vectorStoreRetriever;
		this.chatbotSystemPromptResource = chatBotSystemPromptResource;
		this.qaSystemPromptResource = qaSystemPromptResource;
	}

	public String generate(String message, boolean stuffit) {
		var systemMessage = getSystemMessage(message, stuffit);
		var userMessage = new UserMessage(message);
		var prompt = new Prompt(List.of(systemMessage, userMessage));
		log.debug("Asking AI model to reply to question.");
		var aiResponse = aiClient.generate(prompt);
		log.debug("AI responded.");
		return aiResponse.getGeneration().getContent();
	}

	private Message getSystemMessage(String message, boolean stuffit) {
		if (stuffit) {
			log.debug("Retrieving relevant documents");
			var similarDocuments = vectorStoreRetriever.retrieve(message);
			log.debug(String.format("Found %s relevant documents.", similarDocuments.size()));
			var documents = similarDocuments.stream().map(Document::getContent).collect(Collectors.joining("\n"));
			var systemPromptTemplate = new SystemPromptTemplate(new FileSystemResource(this.qaSystemPromptResource));
			return systemPromptTemplate.createMessage(Map.of("documents", documents));
		}
		else {
			log.info("Not stuffing the prompt, using generic prompt");
			return new SystemPromptTemplate(new FileSystemResource(this.chatbotSystemPromptResource)).createMessage();
		}
	}

}
