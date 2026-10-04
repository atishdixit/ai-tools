package com.example.chatbot.ingest;

import com.example.chatbot.config.ChatBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** On start: reload the stored index at once (so questions work immediately), then bring it up to date with the data folder. */
@Component
@Order(1)
public class StartupIndexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupIndexer.class);

    private final IngestService ingest;
    private final ChatBotProperties props;

    public StartupIndexer(IngestService ingest, ChatBotProperties props) {
        this.ingest = ingest;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        ingest.restore();
        if (props.ingestOnStartup()) {
            try {
                ingest.ingest();
            } catch (RuntimeException e) {
                log.error("Indexing at start-up failed; the bot keeps whatever index it has", e);
            }
        }
    }
}
