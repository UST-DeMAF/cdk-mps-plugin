package ust.tad.cdkmpsplugin.models;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import ust.tad.cdkmpsplugin.models.tadm.TechnologyAgnosticDeploymentModel;

@Service
public class ModelsService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelsService.class);

    @Autowired
    private WebClient modelsServiceApiClient;

    public TechnologyAgnosticDeploymentModel getTechnologyAgnosticDeploymentModel(
            UUID transformationProcessId) {
        LOG.info("Requesting technology-agnostic deployment model");
        return modelsServiceApiClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/technology-agnostic/" + transformationProcessId)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(TechnologyAgnosticDeploymentModel.class)
            .block();
    }

    public void updateTechnologyAgnosticDeploymentModel(
            TechnologyAgnosticDeploymentModel tadm) {
        LOG.info("Updating technology-agnostic deployment model");
        modelsServiceApiClient.post()
            .uri("/technology-agnostic")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(tadm))
            .retrieve()
            .bodyToMono(TechnologyAgnosticDeploymentModel.class)
            .block();
    }
}
