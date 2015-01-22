package id.ac.itb.lumen.persistence

import com.google.common.collect.ImmutableList
import com.rabbitmq.client.ConnectionFactory
import groovy.transform.CompileStatic
import id.ac.itb.lumen.core.Channel
import org.apache.camel.builder.RouteBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import javax.inject.Inject

/**
 * Created by ceefour on 1/19/15.
 */
@CompileStatic
@Configuration
class LumenRouteConfig {

    private static final Logger log = LoggerFactory.getLogger(LumenRouteConfig.class)

    @Inject
    protected PersonRepository personRepo
    @Inject
    protected ToJson toJson

    @Bean
    ConnectionFactory amqpConnFactory() {
      final connFactory = new ConnectionFactory()
      connFactory.host = 'localhost'
      connFactory.username = 'guest'
      connFactory.password = 'guest'
      return connFactory
    }

    //@Bean
    def RouteBuilder personFindAllRouteBuilder() {
        log.info('Initializing personFindAll RouteBuilder')
        new RouteBuilder() {
            @Override
            void configure() throws Exception {
                new RouteBuilder() {
                    @Override
                    void configure() throws Exception {
                        from('rabbitmq://localhost/amq.topic?connectionFactory=#amqpConnFactory&exchangeType=topic&autoDelete=false&routingKey=' + Channel.PERSISTENCE_FACT.key('arkan'))
                                .to('log:persistence-fact')
                                .process {
                            final findAllQuery = toJson.mapper.readValue(it.in.body as byte[], FindAllQuery)
                            switch (findAllQuery.classUri) {
                                case 'http://yago-knowledge.org/resource/wordnet_person_100007846':
                                    final people = ImmutableList.copyOf(personRepo.findAll())
                                    log.info('People: {}', people)
                                    final resources = new Resources<>(people)
                                    it.in.body = resources
                                    it.in.headers['rabbitmq.ROUTING_KEY'] = it.in.headers['reply-to']
                                    break
                                default:
                                    throw new IllegalArgumentException("unknown class URI: ${findAllQuery.classUri}")
                            }
                        }.bean(toJson)
                            .to('log:persistence-fact')
                            .to('rabbitmq://localhost/?connectionFactory=#amqpConnFactory')
                    }
                }
            }
        }
    }

}
