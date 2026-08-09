package ust.tad.cdkmpsplugin.analysistask;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalysisTaskResponseSender {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisTaskResponseSender.class);

    @Autowired
    private RabbitTemplate template;

    @Value("${messaging.analysistask.response.exchange.name}")
    private String responseExchangeName;

    public void sendSuccessResponse(UUID taskId) {
        LOG.info("Transformation completed successfully, sending success response");
        send(new AnalysisTaskResponse(taskId, true, null));
    }

    public void sendFailureResponse(UUID taskId, String errorMessage) {
        LOG.info("Sending failure response: {}", errorMessage);
        send(new AnalysisTaskResponse(taskId, false, errorMessage));
    }

    private void send(AnalysisTaskResponse response) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Message message = MessageBuilder
                .withBody(mapper.writeValueAsString(response).getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("formatIndicator", "AnalysisTaskResponse")
                .build();
            template.convertAndSend(responseExchangeName, "", message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
