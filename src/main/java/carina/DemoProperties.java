package carina;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;

@ConfigurationProperties(prefix = "demo")
record DemoProperties(boolean initializeVectorDb, File qaSystemPrompt, File chatbotSystemPrompt, File[] documents) {
}