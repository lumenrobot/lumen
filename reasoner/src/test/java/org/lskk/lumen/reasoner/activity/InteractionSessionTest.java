package org.lskk.lumen.reasoner.activity;

import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lskk.lumen.core.LumenCoreConfig;
import org.lskk.lumen.core.LumenLocale;
import org.lskk.lumen.persistence.service.FactService;
import org.lskk.lumen.reasoner.nlp.WordNetConfig;
import org.lskk.lumen.reasoner.skill.Connection;
import org.lskk.lumen.reasoner.skill.Skill;
import org.lskk.lumen.reasoner.skill.SkillRepository;
import org.lskk.lumen.reasoner.ux.Channel;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Locale;
import java.util.Optional;

import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * uses Mockito: http://www.baeldung.com/injecting-mocks-in-spring
 * Created by ceefour on 18/02/2016.
 */
@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(classes = JavaScriptIntentTest.IntentConfig.class)
@SpringApplicationConfiguration(InteractionSessionTest.Config.class)
@ActiveProfiles("InteractionSessionTest")
public class InteractionSessionTest {
    public static final String AVATAR_ID = "anime1";

    @Profile("InteractionSessionTest")
    @SpringBootApplication(scanBasePackageClasses = {PromptTask.class, WordNetConfig.class, SkillRepository.class},
            exclude = {HibernateJpaAutoConfiguration.class, DataSourceAutoConfiguration.class, JmxAutoConfiguration.class, CamelAutoConfiguration.class, GroovyTemplateAutoConfiguration.class})
    @Import({LumenCoreConfig.class})
//    @Configuration
//    @ComponentScan(basePackageClasses = {IntentExecutor.class, ThingRepository.class, YagoTypeRepository.class, FactServiceImpl.class})
//    @EnableJpaRepositories(basePackageClasses = YagoTypeRepository.class)
//    @EnableNeo4jRepositories(basePackageClasses = ThingRepository.class)
    public static class Config {
        @Bean
        public Channel<Void> mockChannel() {
//            return new LogChannel();
            return mock(Channel.class, withSettings().verboseLogging());
        }

        @Bean
        public FactService mockFactService() {
            return mock(FactService.class, withSettings()/*.verboseLogging()*/);
        }
    }

    @Inject
    private TaskRepository taskRepo;
    @Inject
    private Channel<Void> mockChannel;
    @Inject
    private FactService factService;
    @Inject
    private Provider<InteractionSession> sessionProvider;

    @Test
    public void askNameThenAffirmSimple() {
        reset(factService, mockChannel);
        try (final InteractionSession session = sessionProvider.get()) {
            session.getActiveLocales().add(LumenLocale.INDONESIAN);
            session.getActiveLocales().add(Locale.US);
            session.open(null, null);

            final Skill skill = new Skill("askNameThenAffirmSimple");
            final PromptTask promptName = taskRepo.createPrompt("promptName");
            promptName.setAutoStart(true);
            final AffirmationTask affirmationTask = taskRepo.createAffirmation("affirmSimple");
            skill.add(affirmationTask);
            skill.add(promptName);
            skill.getConnections().add(new Connection("promptName.name", "affirmSimple.any"));
            session.add(skill);
            //session.add(affirmationTask);
            //promptName.setAffirmationTask(affirmationTask);
            //session.add(promptName);

            skill.initialize();

            session.activate(skill, LumenLocale.INDONESIAN);
            session.update(mockChannel, null);

            session.receiveUtterance(Optional.of(LumenLocale.INDONESIAN), "namaku Hendy Irawan", AVATAR_ID, factService, taskRepo, null);
            assertThat(promptName.getState(), Matchers.equalTo(ActivityState.COMPLETED));
//            verify(factService, times(5)).assertLabel(any(), any(), any(), eq("id-ID"), any(), any(), any());
//            verify(factService, times(0)).assertPropertyToLiteral(any(), any(), any(), any(), any(), any(), any());

            session.update(mockChannel, null);
            verify(mockChannel, times(2)).express(any(), any(), any());
        }
    }

    @Test
    public void askNameThenAffirmYourLabel() {
        reset(factService, mockChannel);
        try (final InteractionSession session = sessionProvider.get()) {
            session.getActiveLocales().add(LumenLocale.INDONESIAN);
            session.getActiveLocales().add(Locale.US);
            session.open(null, null);

            final Skill skill = new Skill("askNameThenAffirmYourLabel");
            final PromptTask promptName = taskRepo.createPrompt("promptName");
            promptName.setAutoStart(true);
            final AffirmationTask affirmationTask = taskRepo.createAffirmation("affirmYourLabel");
            skill.add(affirmationTask);
            skill.add(promptName);
            skill.getConnections().add(new Connection("promptName.name", "affirmYourLabel.name"));
            session.add(skill);
            //session.add(affirmationTask);
            //promptName.setAffirmationTask(affirmationTask);
            //session.add(promptName);

            skill.initialize();

            session.activate(skill, LumenLocale.INDONESIAN);
            session.update(mockChannel, null);

            session.receiveUtterance(Optional.of(LumenLocale.INDONESIAN), "namaku Hendy Irawan", AVATAR_ID, factService, taskRepo, null);
            assertThat(promptName.getState(), Matchers.equalTo(ActivityState.COMPLETED));
//            verify(factService, times(5)).assertLabel(any(), any(), any(), eq("id-ID"), any(), any(), any());
//            verify(factService, times(0)).assertPropertyToLiteral(any(), any(), any(), any(), any(), any(), any());

            session.update(mockChannel, null);
            verify(mockChannel, times(2)).express(any(), any(), any());
        }
    }

}
