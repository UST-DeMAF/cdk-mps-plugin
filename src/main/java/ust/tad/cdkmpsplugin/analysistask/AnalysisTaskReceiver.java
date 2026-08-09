package ust.tad.cdkmpsplugin.analysistask;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ust.tad.cdkmpsplugin.analysis.AnalysisService;

@Service
public class AnalysisTaskReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisTaskReceiver.class);

    @Autowired
    private MessageConverter jsonMessageConverter;

    @Autowired
    private AnalysisTaskResponseSender analysisTaskResponseSender;

    @Autowired
    private AnalysisService analysisService;

    public void receive(Message message) {
        String formatIndicator = message.getMessageProperties().getHeader("formatIndicator");
        if (formatIndicator == null) {
            analysisTaskResponseSender.sendFailureResponse(null,
                "Could not process message: Header with formatIndicator missing.");
            return;
        }
        switch (formatIndicator) {
            case "AnalysisTaskStartRequest":
                receiveAnalysisTaskStartRequest(message);
                break;
            default:
                analysisTaskResponseSender.sendFailureResponse(null,
                    "Could not process message: Unknown format '" + formatIndicator + "'.");
        }
    }

    private void receiveAnalysisTaskStartRequest(Message message) {
        ObjectMapper mapper = new ObjectMapper();
        AnalysisTaskStartRequest request = mapper.convertValue(
            jsonMessageConverter.fromMessage(message), AnalysisTaskStartRequest.class);
        LOG.info("Received AnalysisTaskStartRequest: {}", request);
        analysisService.startAnalysis(
            request.getTaskId(),
            request.getTransformationProcessId(),
            request.getCommands(),
            request.getLocations());
    }
}
