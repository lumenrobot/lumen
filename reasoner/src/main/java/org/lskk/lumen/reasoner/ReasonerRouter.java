package org.lskk.lumen.reasoner;

import org.apache.camel.builder.LoggingErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.lskk.lumen.core.AvatarChannel;
import org.lskk.lumen.core.CommunicateAction;
import org.lskk.lumen.core.Status;
import org.lskk.lumen.core.util.AsError;
import org.lskk.lumen.core.util.ToJson;
import org.lskk.lumen.persistence.service.FactService;
import org.lskk.lumen.reasoner.activity.InteractionSession;
import org.lskk.lumen.reasoner.activity.ScriptRepository;
import org.lskk.lumen.reasoner.activity.SessionManager;
import org.lskk.lumen.reasoner.activity.TaskRepository;
import org.lskk.lumen.reasoner.social.SocialJournalRepository;
import org.lskk.lumen.reasoner.ux.ChatChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Optional;

/**
 * Created by ceefour on 10/2/15.
 */
@Component
@Profile({"reasonerApp", "reasonerSocmedApp"})
public class ReasonerRouter extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(ReasonerRouter.class);

    @Inject
    private ToJson toJson;
    @Inject
    private AsError asError;
//    @Inject
//    private AimlService aimlService;
    @Inject
    private ChatChannel chatChannel;
    @Inject
    private DroolsService droolsService;
    @Inject
    private SocialJournalRepository socialJournalRepo;
    @Inject
    private SessionManager sessionManager;
    @Inject
    private FactService factService;
    @Inject
    private ScriptRepository scriptRepo;
    @Inject
    private TaskRepository taskRepo;

    @Override
    public void configure() throws Exception {
        onException(Exception.class).bean(asError).bean(toJson).handled(true);
        errorHandler(new LoggingErrorHandlerBuilder(log));
//        from("timer:hello?period=3s")
//                .process(exchange -> {
//                    exchange.getIn().setBody(new GreetingReceived("Hendy"));
//                })
//                .to("seda:greetingReceived");

//        from("timer:tell me a good story?period=1s&repeatCount=1")
//                .process(exchange -> {
//                    final AgentResponse agentResponse = aimlService.process(Locale.US, "tell me a good story", logChannel);
//                    droolsService.process(agentResponse);
//                });

        final String agentId = "arkan";
        from("rabbitmq://localhost/amq.topic?connectionFactory=#amqpConnFactory&exchangeType=topic&autoDelete=false&queue=" + AvatarChannel.CHAT_INBOX.wildcard() + "&routingKey=" + AvatarChannel.CHAT_INBOX.wildcard())
                .process(exchange -> {
                    final long startTime = System.currentTimeMillis();
                    final CommunicateAction inCommunicate = toJson.getMapper().readValue(
                            exchange.getIn().getBody(byte[].class), CommunicateAction.class);
                    inCommunicate.setAvatarId(AvatarChannel.getAvatarId((String) exchange.getIn().getHeader(RabbitMQConstants.ROUTING_KEY)));
                    log.info("Chat inbox for {}: {}", inCommunicate.getAvatarId(), inCommunicate);

                    final Optional<Locale> origLocale = Optional.ofNullable(inCommunicate.getInLanguage());
                    final float[] speechTruthValue = Optional.ofNullable(inCommunicate.getSpeechTruthValue()).orElse(new float[]{0f, 0f, 0f});
                    final boolean speechInput = speechTruthValue.length >= 2 && speechTruthValue[1] > 0f;

                    // AIML style
//                    final AgentResponse agentResponse = aimlService.process(origLocale, inCommunicate.getObject(),
//                            chatChannel, inCommunicate.getAvatarId(), speechInput);
//                    if (!agentResponse.getCommunicateActions().isEmpty()) {
//                        for (final CommunicateAction communicateAction : agentResponse.getCommunicateActions()) {
//                            chatChannel.express(inCommunicate.getAvatarId(), communicateAction, null);
//                        }
//                    } else if (agentResponse.getUnrecognizedInput() != null) {
//                        chatChannel.express(inCommunicate.getAvatarId(), Proposition.I_DONT_UNDERSTAND, true, null);
//                    }
//                    droolsService.process(agentResponse);

                    final InteractionSession session = sessionManager.getOrCreate(chatChannel, inCommunicate.getAvatarId());
                    session.receiveUtterance(origLocale, inCommunicate.getObject(), inCommunicate.getAvatarId(), factService, taskRepo, scriptRepo);
                    session.update(chatChannel, inCommunicate.getAvatarId());

                    // FIXME: re-implement SocialJournal
//                    final SocialJournal socialJournal = new SocialJournal();
//                    socialJournal.setFromResponse(origLocale, inCommunicate.getAvatarId(),
//                            inCommunicate.getObject(), SocialChannel.DIRECT,
//                            agentResponse, Duration.millis(System.currentTimeMillis() - startTime));
//                    socialJournalRepo.save(socialJournal);

                    exchange.getIn().setBody(new Status());
                })
                .bean(toJson);
    }

}
