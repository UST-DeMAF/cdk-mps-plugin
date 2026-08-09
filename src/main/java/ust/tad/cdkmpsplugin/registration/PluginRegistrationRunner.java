package ust.tad.cdkmpsplugin.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import ust.tad.cdkmpsplugin.analysistask.AnalysisTaskReceiver;

@Component
public class PluginRegistrationRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistrationRunner.class);

    @Autowired
    private GenericApplicationContext context;

    @Autowired
    private WebClient pluginRegistrationApiClient;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private AnalysisTaskReceiver analysisTaskReceiver;

    @Value("${plugin.technology}")
    private String pluginTechnology;

    @Value("${plugin.analysis-type}")
    private String pluginAnalysisType;

    @Value("${messaging.analysistask.response.exchange.name}")
    private String responseExchangeName;

    @Override
    public void run(ApplicationArguments args) throws JsonProcessingException {
        LOG.info("Registering CDK-MPS plugin");

        String body = createRegistrationBody();

        PluginRegistrationResponse response = pluginRegistrationApiClient.post()
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(body))
            .retrieve()
            .bodyToMono(PluginRegistrationResponse.class)
            .block();

        LOG.info("Registration response: {}", response);

        AbstractMessageListenerContainer listener =
            createRequestQueueListener(response.getRequestQueueName(),
                message -> analysisTaskReceiver.receive(message));

        context.registerBean("requestQueueListener", listener.getClass(), listener);

        context.registerBean(responseExchangeName, FanoutExchange.class,
            () -> new FanoutExchange(response.getResponseExchangeName(), true, false));
    }

    private String createRegistrationBody() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("technology", pluginTechnology);
        node.put("analysisType", pluginAnalysisType);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    private AbstractMessageListenerContainer createRequestQueueListener(
            String queueName, MessageListener listener) {
        SimpleMessageListenerContainer container =
            new SimpleMessageListenerContainer(
                rabbitAdmin.getRabbitTemplate().getConnectionFactory());
        container.addQueueNames(queueName);
        container.setMessageListener(listener);
        container.start();
        return container;
    }
}
