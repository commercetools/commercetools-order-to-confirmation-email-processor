package com.commercetools.ordertomessageprocessor.timestamp;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.sphere.sdk.client.BlockingSphereClient;
import io.sphere.sdk.customobjects.CustomObject;
import io.sphere.sdk.customobjects.CustomObjectDraft;
import io.sphere.sdk.customobjects.commands.CustomObjectUpsertCommand;
import io.sphere.sdk.customobjects.queries.CustomObjectQuery;
import io.sphere.sdk.queries.PagedQueryResult;

public class TimeStampManagerImpl implements TimeStampManager {

    public static final Logger LOG = LoggerFactory.getLogger(TimeStampManagerImpl.class);
    
    private final String KEY = "lastUpdated";
    @Autowired
    BlockingSphereClient client;

    @Value("${ctp.this.servicename}")
    private String serviceName;
    private Optional<CustomObject<TimeStamp>> lastTimestamp = Optional.empty();
    private boolean wasTimeStampQueried = false;
    private ZonedDateTime lastActualProcessedMessageTimeStamp;
    
    @Override
    public Optional<ZonedDateTime> getLastProcessedMessageTimeStamp() {
        if (!wasTimeStampQueried) {
            queryTimeStamp();
        }
        if (lastTimestamp.isPresent()) {
            return Optional.of(lastTimestamp.get().getValue().getLastTimeStamp());
        }
        else {
            return Optional.empty();
        }
    }

    @Override
    public void setActualProcessedMessageTimeStamp(final ZonedDateTime timeStamp) {
        this.lastActualProcessedMessageTimeStamp = timeStamp;
    }

    @Override
    public void persistLastProcessedMessageTimeStamp() {
        final CustomObjectDraft<TimeStamp> draft = createCustomObjectDraft();
        final CustomObjectUpsertCommand<TimeStamp> updateCommad = CustomObjectUpsertCommand.of(draft);
        client.executeBlocking(updateCommad);
    }

    private void queryTimeStamp() {
        final CustomObjectQuery<TimeStamp> customObjectQuery = CustomObjectQuery.of(TimeStamp.class)
                .byContainer(serviceName);
        final PagedQueryResult<CustomObject<TimeStamp>> result = client.executeBlocking(customObjectQuery);
        final List<CustomObject<TimeStamp>> results = result.getResults();
        if (results.isEmpty()) {
            LOG.warn("No LastProcessedMessage was found");
        }
        else {
            lastTimestamp = Optional.of(results.get(0));
            LOG.info("Got LastProcessedMessageTimeStamp {} from CTP", lastTimestamp);
        }
        wasTimeStampQueried = true;
    }

    private CustomObjectDraft<TimeStamp> createCustomObjectDraft() {
        final TimeStamp timeStamp = new TimeStamp(lastActualProcessedMessageTimeStamp);
        LOG.info("Writing Custom Object {} ", lastActualProcessedMessageTimeStamp);
        if (lastTimestamp.isPresent()) {
            return CustomObjectDraft.ofVersionedUpdate(lastTimestamp.get(), timeStamp, TimeStamp.class);
        }
        else {
            return CustomObjectDraft.ofUnversionedUpsert(serviceName, KEY ,timeStamp, TimeStamp.class);
        }
    }
}