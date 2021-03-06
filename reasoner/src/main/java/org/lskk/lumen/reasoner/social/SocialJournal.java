package org.lskk.lumen.reasoner.social;

import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.lskk.lumen.core.CommunicateAction;
import org.lskk.lumen.core.SimpleTruthValue;
import org.lskk.lumen.core.SocialChannel;
import org.lskk.lumen.reasoner.event.AgentResponse;
import org.lskk.lumen.reasoner.expression.Proposition;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by ceefour on 19/11/2015.
 */
@Entity
@Table(schema = "lumen")
public class SocialJournal implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "timestamp with time zone", nullable = false)
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime creationTime;
    private String agentId;
    private String avatarId;
    private String socialChannelId;
    private String receivedLanguage;
    @Column(columnDefinition = "text")
    private String receivedText;
    @Embedded
    private SimpleTruthValue truthValue;
    private String responseLanguage;
    @Column(columnDefinition = "text")
    private String responseText;
    @Column
    private String responseKind;
    @Column(columnDefinition = "text")
    private String responseInsertables;
    private Float processingTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DateTime getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(DateTime creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * e.g. arkan
     * @return
     */
    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * e.g. nao1
     * @return
     */
    public String getAvatarId() {
        return avatarId;
    }

    public void setAvatarId(String avatarId) {
        this.avatarId = avatarId;
    }

    /**
     * e.g. direct, avatar, facebook, twitter
     * @return
     */
    public String getSocialChannelId() {
        return socialChannelId;
    }

    public void setSocialChannelId(String socialChannelId) {
        this.socialChannelId = socialChannelId;
    }

    public String getReceivedText() {
        return receivedText;
    }

    public void setReceivedText(String receivedText) {
        this.receivedText = receivedText;
    }

    /**
     * Truth value of the matching AIML {@link org.lskk.lumen.reasoner.aiml.Category}.
     * @return
     */
    public SimpleTruthValue getTruthValue() {
        return truthValue;
    }

    public void setTruthValue(SimpleTruthValue truthValue) {
        this.truthValue = truthValue;
    }

    /**
     * Immediate response generated by AIML.
     * @return
     */
    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public String getResponseKind() {
        return responseKind;
    }

    public void setResponseKind(String responseKind) {
        this.responseKind = responseKind;
    }

    /**
     * Comma-separated class name of insertables, from {@link AgentResponse#getInsertables()}.
     * @return
     */
    public String getResponseInsertables() {
        return responseInsertables;
    }

    public void setResponseInsertables(String responseInsertables) {
        this.responseInsertables = responseInsertables;
    }

    /**
     * Processing time between NLP matching and immediate response, in seconds.
     * @return
     */
    public Float getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(Float processingTime) {
        this.processingTime = processingTime;
    }

    public Locale getReceivedLanguage() {
        return Optional.ofNullable(receivedLanguage).map(Locale::forLanguageTag).orElse(null);
    }

    public void setReceivedLanguage(Locale receivedLanguage) {
        this.receivedLanguage = Optional.ofNullable(receivedLanguage).map(Locale::toLanguageTag).orElse(null);
    }

    public String getResponseLanguage() {
        return responseLanguage;
    }

    public void setResponseLanguage(String responseLanguage) {
        this.responseLanguage = responseLanguage;
    }

    public void setFromResponse(Locale inputLocale, String avatarId,
                                String receivedText,
                                SocialChannel socialChannel,
                                AgentResponse agentResponse,
                                Duration processingTime) {
        setAvatarId(avatarId);
        setAgentId(agentId);
        setSocialChannelId(socialChannel.getThingId());
        setReceivedLanguage(Optional.ofNullable(agentResponse.getStimuliLanguage()).orElse(inputLocale));
        setReceivedText(receivedText);
        setResponseInsertables(
                agentResponse.getInsertables().stream()
                        .map(it -> it.getClass().getName()).collect(Collectors.joining(", ")));
        setTruthValue(new SimpleTruthValue(agentResponse.getMatchingTruthValue()));

        if (!agentResponse.getCommunicateActions().isEmpty()) {
            for (final CommunicateAction communicateAction : agentResponse.getCommunicateActions()) {
                if (getResponseKind() != null) {
                    setResponseKind(getResponseKind() + ", ");
                    setResponseLanguage(getResponseLanguage() + ", ");
                    setResponseText(getResponseText() + "\n");
                } else {
                    setResponseKind("");
                    setResponseLanguage("");
                    setResponseText("");
                }
                setResponseKind(getResponseKind() + communicateAction.getClass().getName());
                setResponseLanguage(getResponseLanguage() + communicateAction.getInLanguage().toLanguageTag());
                setResponseText(getResponseText() + communicateAction.getObject());
            }
        } else if (agentResponse.getUnrecognizedInput() != null) {
            setResponseKind(agentResponse.getUnrecognizedInput().getClass().getName());
        }
        setProcessingTime(processingTime.getMillis() / 1000f);
    }

    @PrePersist
    public void prePersist() {
        if (getCreationTime() == null) {
            setCreationTime(new DateTime());
        }
    }
}
