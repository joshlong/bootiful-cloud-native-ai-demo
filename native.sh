#!/usr/bin/env bash

[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

sdk install java 23.1.1.r21-nik || echo "the right version of Java couldn't be installed or is already installed. moving on..."
sdk use java 23.1.1.r21-nik     || echo "using Liberica's NIK"

./mvnw spring-javaformat:apply
./mvnw -DskipTests -Pnative clean package
./mvnw -DskipTests -Pnative native:compile


./target/ai -Ddemo.chatbot-system-prompt=`pwd`/src/main/resources/prompts/system-chatbot.st -Ddemo.qa-system-prompt=`pwd`/src/main/resources/prompts/system-qa.st -Ddemo.documents=`pwd`/src/main/resources/data/medicaid-wa-faqs.pdf