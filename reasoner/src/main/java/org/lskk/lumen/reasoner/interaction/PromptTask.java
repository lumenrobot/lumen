package org.lskk.lumen.reasoner.interaction;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.apache.commons.lang3.RandomUtils;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.lskk.lumen.core.CommunicateAction;
import org.lskk.lumen.core.ConversationStyle;
import org.lskk.lumen.core.IConfidence;
import org.lskk.lumen.persistence.neo4j.Literal;
import org.lskk.lumen.persistence.neo4j.ThingLabel;
import org.lskk.lumen.reasoner.ReasonerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p><strong>Prompt (Ask Back + Insist/Persuade)</strong></p>
 *
 * <p>Gather information from person, with a levelof persuasion if person won't answer or just partially.</p>
 *
 * Created by ceefour on 17/02/2016.
 */
@JsonTypeInfo(property = "@type", use = JsonTypeInfo.Id.NAME, defaultImpl = PromptTask.class)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "PromptTask", value = PromptTask.class),
    @JsonSubTypes.Type(name = "PromptNameTask", value = PromptNameTask.class),
    @JsonSubTypes.Type(name = "PromptGenderTask", value = PromptGenderTask.class),
    @JsonSubTypes.Type(name = "PromptReligionTask", value = PromptReligionTask.class),
    @JsonSubTypes.Type(name = "PromptAgeTask", value = PromptAgeTask.class)
})
public class PromptTask extends InteractionTask {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected static final TokenizerME TOKENIZER_ENGLISH;

    static {
        try {
            TOKENIZER_ENGLISH = new TokenizerME(new TokenizerModel(
                    PromptTask.class.getResource("/org/lskk/lumen/reasoner/en-token.bin")));
        } catch (IOException e) {
            throw new ReasonerException("Cannot initialize tokenizer", e);
        }
    }

    private List<QuestionTemplate> askSsmls = new ArrayList<>();
    private List<UtterancePattern> utterancePatterns = new ArrayList<>();
    private String property;
    private Map<String, String> expectedTypes;
    private String unit;
    private AffirmationTask affirmationTask;

    /**
     * e.g. to ask birth date:
     * <p>
     * <pre>
     * [
     *   {"inLanguage": "id-ID", "object": "kapan tanggal lahirmu?"},
     *   {"inLanguage": "id-ID", "object": "kamu lahir tanggal berapa?"},
     *   {"inLanguage": "en-US", "object": "when were you born?"}
     * ]
     * </pre>
     * <p>
     * There can be several SSMLs for each language.
     *
     * @return
     */
    public List<QuestionTemplate> getAskSsmls() {
        return askSsmls;
    }

    public void setAskSsmls(List<QuestionTemplate> askSsmls) {
        this.askSsmls = askSsmls;
    }

    /**
     * e.g. utterance to provide birth date: "aku lahir tanggal {birthDate}"@id-ID
     *
     * @return
     */
    public List<UtterancePattern> getUtterancePatterns() {
        return utterancePatterns;
    }

    /**
     * e.g. {@code yago:wasBornOnDate}
     *
     * @return
     */
    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    /**
     * Key is slot name, e.g. <tt>{chapter}</tt>
     * Value is type QName, e.g. {@code xs:date}, {@code yago:wordnet_person_100007846}
     *
     * <p>Example:</p>
     *
     * <pre>
     * "expectedTypes": {
     *   "chapter": "xsd:string",
     *   "verse": "xsd:integer"
     * }
     * </pre>
     *
     * @return
     */
    public Map<String, String> getExpectedTypes() {
        return expectedTypes;
    }

    /**
     * Any {@link javax.measure.unit.Unit}.
     *
     * @return
     */
    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    /**
     * Word-tokenizes the plain-text part, quotes using {@link Pattern#quote(String)} each segment independently.
     * Each token with be separated by regex whitespace ({@code \s+}).
     * @param plainPart
     * @return
     */
    protected final String plainToRegex(String plainPart) {
        final String[] tokens = TOKENIZER_ENGLISH.tokenize(plainPart);
        String result = "";
        for (final String token : tokens) {
            if (!result.isEmpty()) {
                // Special handling: OpenNLP English Tokenizer tokenizes "I'm" as [I, 'm]
                // which makes sense but you need to be aware of this
                if (token.startsWith("'")) {
                    result += "\\s*";
                } else {
                    result += "\\s+";
                }
            }
            result += Pattern.quote(token);
        }
        // leading and/or trailing whitespace
        if (plainPart.startsWith(" ")) {
            result = "\\s+" + result;
        }
        if (plainPart.endsWith(" ")) {
            result += "\\s+";
        }
        return result;
    }

    @Override
    public void receiveUtterance(CommunicateAction communicateAction, InteractionSession session) {
        final List<UtterancePattern> matchedUtterancePatterns = matchUtterance(communicateAction.getInLanguage(), communicateAction.getObject(),
                isActive() ? UtterancePattern.Scope.ANY : UtterancePattern.Scope.GLOBAL);
        getMatchedUtterancePatterns().clear();
        getMatchedUtterancePatterns().addAll(matchedUtterancePatterns);
        // add to queue
        final List<ThingLabel> labelsToAssert = generateLabelsToAssert(matchedUtterancePatterns);
        getLabelsToAssert().addAll(labelsToAssert);
        final List<Literal> literalsToAssert = generateLiteralsToAssert(matchedUtterancePatterns);
        getLiteralsToAssert().addAll(literalsToAssert);
        final Locale realLocale = Optional.ofNullable(communicateAction.getInLanguage()).orElse(session.getLastLocale());

        session.complete(this, realLocale);
    }

    /**
     * Checks whether an utterance matched the defined patterns for this PromptTask.
     *
     * @param locale
     * @param utterance
     * @param scope     If PromptTask is not active, use {@link org.lskk.lumen.reasoner.interaction.UtterancePattern.Scope#GLOBAL}.
     *                  If PromptTask is active, use {@link org.lskk.lumen.reasoner.interaction.UtterancePattern.Scope#ANY}.
     * @return
     */
    public List<UtterancePattern> matchUtterance(Locale locale, String utterance, UtterancePattern.Scope scope) {
        final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[{](?<slot>[a-z0-9_]+)[}]", Pattern.CASE_INSENSITIVE);
        final List<UtterancePattern> matches = utterancePatterns.stream()
                .filter(it -> null == it.getInLanguage() || locale == Locale.forLanguageTag(it.getInLanguage()))
                .filter(it -> UtterancePattern.Scope.ANY == scope || scope == it.getScope())
                .map(it -> {
                    // Converts "aku lahir tanggal {birthdate}" where birthDate=xs:date
                    // into regex "aku lahir tanggal (?<birthdate>\d+ [a-z]+ \d+)"
                    String real = "";
                    int lastPlainOffset = 0;
                    final Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(it.getPattern());
                    final ArrayList<String> slots = new ArrayList<>();
                    int plainPartLength = 0;
                    while (true) {
                        final boolean found = placeholderMatcher.find();
                        if (found) {
                            String plainPart = it.getPattern().substring(lastPlainOffset, placeholderMatcher.start());
                            real += plainToRegex(plainPart);
                            plainPartLength += plainPart.length();
                            final String slot = placeholderMatcher.group("slot");
                            slots.add(slot);

                            Preconditions.checkNotNull(expectedTypes.containsKey(slot),
                                    "Utterance \"%s\" uses slot \"%s\" but not declared in expectedTypes. Declared expectedTypes are: %s",
                                    it.getPattern(), slot, expectedTypes.keySet());
                            final String slotStringPattern;
                            switch (expectedTypes.get(slot)) {
                                case "xsd:string":
                                    slotStringPattern = ".+";
                                    break;
                                case "xsd:integer":
                                    slotStringPattern = "[\\d-]+";
                                    break;
                                case "xs:date":
                                    slotStringPattern = "\\d+ [a-z]+ \\d+";
                                    break;
                                // this pattern is dependent on the Yago Type, not the prompt
                                case "yago:wordnet_sex_105006898":
                                    slotStringPattern = "[a-z0-9 -]+";
                                    break;
                                case "yago:wordnet_religion_105946687":
                                    slotStringPattern = "[a-z '-]+";
                                    break;
                                default:
                                    throw new ReasonerException("Slot '" + slot + "' uses unsupported type: " + expectedTypes);
                            }

                            real += "(?<" + slot + ">" + slotStringPattern + ")";
                            lastPlainOffset = placeholderMatcher.end();
                        } else {
                            String plainPart = it.getPattern().substring(lastPlainOffset, it.getPattern().length());
                            real += plainToRegex(plainPart);
                            plainPartLength += plainPart.length();
                            break;
                        }
                    }
                    final Pattern realPattern = Pattern.compile(real, Pattern.CASE_INSENSITIVE);
                    log.debug("Matching {} for \"{}\"@{} {}...", realPattern, utterance, locale.toLanguageTag(), scope);
                    final Matcher realMatcher = realPattern.matcher(utterance);
                    if (realMatcher.find()) {
                        final UtterancePattern matched = new UtterancePattern();
                        final Locale realLocale = it.getInLanguage() != null ? Locale.forLanguageTag(it.getInLanguage()) : locale;
                        matched.setInLanguage(realLocale.toLanguageTag());
                        matched.setPattern(it.getPattern());
                        matched.setScope(it.getScope());
                        matched.setActual(utterance);
                        matched.setStyle(it.getStyle());
                        // language-independent utterance pattern gets 0.9 multiplier
                        final float languageMultiplier = null != it.getInLanguage() ? 1f : 0.9f;
                        // GLOBAL scope has full multiplier, LOCAL scope has 0.9
                        final float scopeMultiplier = UtterancePattern.Scope.GLOBAL == it.getScope() ? 1f : 0.9f;
                        // we prefer as many matching plaintext as possible, i.e. "Read Quran {Al-Baqarah}" is preferred over "Read {Quran Al-Baqarah}" over "{Read Quran Al-Baqarah}"
                        final float plainPartMultiplier = 0.9f + (Math.min(plainPartLength, 20f) / 200f);
                        matched.setConfidence(Optional.ofNullable(it.getConfidence()).orElse(1f) * languageMultiplier * scopeMultiplier * plainPartMultiplier);

                        // for each slot, check if the captured slot value is valid in valid format for conversion to target value
                        boolean allValid = true;
                        for (final String slot : slots) {
                            final String slotString = realMatcher.group(slot);
                            matched.getSlotStrings().put(slot, slotString);
                            switch (expectedTypes.get(slot)) {
                                case "xsd:string":
                                    break;
                                case "xsd:integer":
                                    break;
                                case "xs:date":
                                    break;
                                case "yago:wordnet_sex_105006898":
                                case "yago:wordnet_religion_105946687":
                                    if (!isValidStringValue(matched.getInLanguage(), slotString, matched.getStyle())) {
                                        log.debug("Regex matched {} but no enum of {} has this label", matched, expectedTypes);
                                        allValid = false;
                                    }
                                    break;
                                default:
                                    throw new ReasonerException("Unsupported type: " + expectedTypes);
                            }
                        }
                        if (allValid) {
                            // for each slot, convert string values into target values
                            for (final String slot : slots) {
                                final String slotString = realMatcher.group(slot);
                                matched.getSlotStrings().put(slot, slotString);
                                // convert to target value
                                matched.getSlotValues().put(slot, toTargetValue(expectedTypes.get(slot), matched.getInLanguage(), slotString, matched.getStyle()));
                            }
                            log.debug("Matched {} multipliers: language={} scope={} plainPart={}({})", matched,
                                    languageMultiplier, scopeMultiplier, plainPartMultiplier, plainPartLength);
                            return matched;
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(new IConfidence.Comparator())
                .collect(Collectors.toList());
        return matches;
    }

    /**
     * By default returns empty list. Override this to return assertable {@link ThingLabel}s.
     * @param utteranceMatches Matches of utterance patterns returned by {@link #matchUtterance(Locale, String, UtterancePattern.Scope)}.
     * @return
     */
    public List<ThingLabel> generateLabelsToAssert(List<UtterancePattern> utteranceMatches) {
        return ImmutableList.of();
    }

    /**
     * By default returns empty list. Override this to return assertable {@link Literal}s.
     * @param utteranceMatches Matches of utterance patterns returned by {@link #matchUtterance(Locale, String, UtterancePattern.Scope)}.
     * @return
     */
    public List<Literal> generateLiteralsToAssert(List<UtterancePattern> utteranceMatches) {
        return ImmutableList.of();
    }

    public boolean isValidStringValue(String inLanguage, String value, ConversationStyle style) {
        throw new UnsupportedOperationException();
    }

    /**
     * Convert to target value. You can override this if you have your own format.
     *
     * @param expectedType
     * @param inLanguage
     * @param value
     * @param style
     * @return
     */
    public Object toTargetValue(String expectedType, String inLanguage, String value, ConversationStyle style) {
        switch (expectedType) {
            case "xsd:string":
                return value;
            case "xsd:integer":
                return Integer.parseInt(value);
            case "xs:date":
                final LocalDate localDate = DateTimeFormat.longDate().withLocale(Locale.forLanguageTag(inLanguage)).parseLocalDate(value);
                return localDate;
            default:
                throw new ReasonerException("Unsupported type: " + expectedTypes);
        }
    }

    /**
     * After this PromptTask is completed, the assigned UnderstoodTask will be used to express the collected information.
     * @return
     */
    public AffirmationTask getAffirmationTask() {
        return affirmationTask;
    }

    public void setAffirmationTask(AffirmationTask affirmationTask) {
        this.affirmationTask = affirmationTask;
    }

    @Override
    public void onStateChanged(InteractionTaskState previous, InteractionTaskState current, Locale locale, InteractionSession session) {
        super.onStateChanged(previous, current, locale, session);
        if (InteractionTaskState.ACTIVE == current) {
            // if we don't yet have the info, express the question
            // Get appropriate question for target language, if possible.
            // If not, returns first question.
            final List<QuestionTemplate> matches = askSsmls.stream().filter(it -> locale.equals(Locale.forLanguageTag(it.getInLanguage())))
                    .collect(Collectors.toList());
            final QuestionTemplate questionTemplate;
            if (!matches.isEmpty()) {
                questionTemplate = matches.get(RandomUtils.nextInt(0, matches.size()));
            } else {
                questionTemplate = askSsmls.get(0);
            }
            final CommunicateAction initiative = new CommunicateAction(
                    Optional.ofNullable(questionTemplate.getInLanguage()).map(Locale::forLanguageTag).orElse(locale),
                    questionTemplate.getObject(), null);
            initiative.setConversationStyle(questionTemplate.getStyle());
            getPendingCommunicateActions().add(initiative);
        } else if (InteractionTaskState.COMPLETED == current) {
            if (null != getAffirmationTask()) {
                session.schedule(getAffirmationTask());
            }
        }
    }
}