package io.kaoto.backend.camel.service.step.parser.camelroute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;

import io.kaoto.backend.api.service.step.parser.StepParserService;
import io.kaoto.backend.camel.KamelHelper;
import io.kaoto.backend.camel.model.deployment.camelroute.CamelRoute;
import io.kaoto.backend.camel.model.deployment.kamelet.FlowStep;
import io.kaoto.backend.camel.model.deployment.rest.Rest;
import io.kaoto.backend.camel.service.step.parser.kamelet.KameletStepParserService;
import io.kaoto.backend.model.step.Step;
import io.quarkus.runtime.util.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 🐱miniclass CamelRouteStepParserService (StepParserService)
 */
@ApplicationScoped
public class CamelRouteStepParserService implements StepParserService<Step> {
    private static final Logger LOG = Logger.getLogger(CamelRouteStepParserService.class);

    private KameletStepParserService ksps;

    @Override
    public ParseResult<Step> deepParse(final String input) {
        // Right now we discard any flow that is not the first
        return getParsedFlows(input).get(0);
    }

    @Override
    public List<ParseResult<Step>> getParsedFlows(final String input) {
        if (!appliesTo(input)) {
            throw new IllegalArgumentException(
                    "Wrong format provided. This is not parseable by us.");
        }

        List<ParseResult<Step>> resultList = new ArrayList<>();

        try {
            CamelRoute route = getCamelRoute(input);
            processFlows(route, resultList);
            processBeans(route, resultList);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error trying to parse.", e);
        }

        return resultList;
    }

    private void processFlows(CamelRoute route, List<ParseResult<Step>> resultList) {
        var flows = route.getFlows();
        if (flows == null) {
            return;
        }

        for (var flow : flows) {
            var from = flow.getFrom();
            if (from == null) {
                from = flow.getRest();
            }

            ParseResult<Step> res = new ParseResult<>();
            List<Step> steps = new ArrayList<>();

            if (from instanceof Rest rest) {
                steps.add(rest.getStep(ksps, false, true));
            } else if (from.getSteps() != null) {
                steps.add(ksps.processStep(from, true, false));
                if (from.getSteps() != null) {
                    for (FlowStep step : from.getSteps()) {
                        //end is always false in this case because we can always edit one step after it
                        steps.add(ksps.processStep(step, false, false));
                    }
                }
            }
            res.setSteps(steps.stream().filter(Objects::nonNull).toList());
            if (!StringUtil.isNullOrEmpty(flow.getId())) {
                if (res.getMetadata() == null) {
                    res.setMetadata(new LinkedHashMap<>());
                }
                res.getMetadata().put(KamelHelper.NAME, flow.getId());
            } else if (flow.getRest() != null && flow.getRest().getId() != null) {
                if (res.getMetadata() == null) {
                    res.setMetadata(new LinkedHashMap<>());
                }
                res.getMetadata().put(KamelHelper.NAME, flow.getRest().getId());
            }
            if (!StringUtil.isNullOrEmpty(flow.getRouteConfigurationId())) {
                if (res.getMetadata() == null) {
                    res.setMetadata(new LinkedHashMap<>());
                }
                res.getMetadata().put("route-configuration-id", flow.getRouteConfigurationId());
            }
            if (!StringUtil.isNullOrEmpty(flow.getDescription())) {
                if (res.getMetadata() == null) {
                    res.setMetadata(new LinkedHashMap<>());
                }
                res.getMetadata().put(KamelHelper.DESCRIPTION, flow.getDescription());
            }
            resultList.add(res);
        }
    }

    private void processBeans(CamelRoute route, List<ParseResult<Step>> resultList) {
        if (route.getBeans() == null || route.getBeans().isEmpty()) {
            return;
        }
        ParseResult<Step> res = new ParseResult<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("beans", route.getBeans());
        res.setMetadata(metadata);
        resultList.add(res);
    }

    @Override
    public boolean appliesTo(final String input) {
        return getCamelRoute(input) != null;
    }

    private CamelRoute getCamelRoute(final String input) {
        try {
            return KamelHelper.YAML_MAPPER.readerFor(CamelRoute.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(input);
        } catch (JsonProcessingException e) {
            //We don't care what happened, it is wrongly formatted and that's it
            LOG.trace("Error trying to parse camel route.", e);
        }
        return null;
    }

    @Inject
    public void setKsps(final KameletStepParserService ksps) {
        this.ksps = ksps;
    }
}
