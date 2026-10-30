package com.seatlock.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatlock.config.QueueProperties;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Queue names, retry policy and message format for queued checkouts.
 *
 * <p>Retries are done by the broker: when the consumer throws, its transacted session rolls
 * back and Artemis redelivers the message after {@code redelivery-delay}, growing by
 * {@code redelivery-multiplier} each time. After {@code max-delivery-attempts} the message is
 * moved to {@link #DEAD_LETTER_QUEUE}.
 */
@Configuration
public class TicketQueueConfig {

    public static final String PURCHASE_QUEUE = "ticket-purchase-queue";
    public static final String DEAD_LETTER_QUEUE = "DLQ." + PURCHASE_QUEUE;

    /** Messages with the same group id are delivered in order to one consumer at a time. */
    public static final String GROUP_ID_PROPERTY = "JMSXGroupID";

    @Bean
    ArtemisConfigurationCustomizer purchaseQueueRetryPolicy(QueueProperties properties) {
        return configuration -> configuration.addAddressSetting(PURCHASE_QUEUE, new AddressSettings()
                .setMaxDeliveryAttempts(properties.maxDeliveryAttempts())
                .setRedeliveryDelay(properties.redeliveryDelay().toMillis())
                .setRedeliveryMultiplier(properties.redeliveryMultiplier())
                .setDeadLetterAddress(SimpleString.of(DEAD_LETTER_QUEUE)));
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }
}
