package org.lskk.lumen.reasoner.nlp.en;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.lskk.lumen.core.CommunicateAction;
import org.lskk.lumen.reasoner.ReasonerException;
import org.lskk.lumen.reasoner.expression.Greeting;
import org.lskk.lumen.reasoner.expression.SpoAdj;
import org.lskk.lumen.reasoner.expression.SpoNoun;
import org.lskk.lumen.reasoner.nlp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Natural language generation.
 *
 * The process of language generation involves a series of stages, which may be defined in various ways, such as:
 * <ol>
 *     <li>Content determination: figuring out what needs to be said in a given context</li>
 *     <li>Discourse planning: overall organization of the information to be communicated</li>
 *     <li>Lexicalization: assigning words to concepts</li>
 *     <li>Reference generation: linking words in the generated sentences using pronouns and other kinds of reference</li>
 *     <li>Syntactic and morphological realization: the generation of sentences via a process inverse to parsing, representing the information gathered in the above phases</li>
 *     <li>Phonological or orthographic realization: turning the above into spoken or written words, complete with timing (in the spoken case), punctuation (in the written case), etc.</li>
 * </ol>
 *
 * Created by ceefour on 27/10/2015.
 *
 * @see <a href="http://wiki.opencog.org/w/SegSim">SegSim | OpenCog</a>
 */
@Service
@NaturalLanguage("en")
public class SentenceGenerator {
    private static Logger log = LoggerFactory.getLogger(SentenceGenerator.class);

    @Inject
    private PronounMapper pronounMapper;

    /**
     * Generates a clause (not a proper sentence). The first word is not capitalized.
     * @param locale
     * @param expression
     * @return
     */
    public CommunicateAction generate(Locale locale, Object expression) {
        Preconditions.checkNotNull(expression, "expression not null");
        final CommunicateAction action = new CommunicateAction();
        String msg = null;
        if (expression instanceof Greeting) {
            Greeting greeting = (Greeting) expression;
            msg = "Good " + greeting.getTimeOfDay();
            if (Pronoun.YOU != greeting.getToPronoun()) {
                // TODO: this is weird
                msg += ", " + pronounMapper.getPronounLabel(Locale.US, greeting.getToPronoun(), PronounCase.OBJECT).get();
            }
        } else if (expression instanceof SpoNoun) {
            final SpoNoun spo = (SpoNoun) expression;
            msg = toText(locale, spo.getSubject(), PronounCase.SUBJECT) + " ";
            final Pronoun pronoun = Optional.ofNullable(spo.getSubject().getPronoun()).orElse(Pronoun.IT);
            msg += toText(locale, spo.getPredicate(), pronoun.getPerson(), pronoun.getNumber()) + " ";
            msg += toText(locale, spo.getObject(), PronounCase.OBJECT);
        } else if (expression instanceof SpoAdj) {
            final SpoAdj spo = (SpoAdj) expression;
            msg = toText(locale, spo.getSubject(), PronounCase.SUBJECT) + " ";
            final Pronoun pronoun = Optional.ofNullable(spo.getSubject().getPronoun()).orElse(Pronoun.IT);
            msg += toText(locale, spo.getPredicate(), pronoun.getPerson(), pronoun.getNumber()) + " ";
            msg += toText(locale, spo.getObject());
        } else {
            log.warn("Unknown expression class: {}", expression.getClass().getName());
        }
        action.setObject(msg);
        return action;
    }

    public String toText(Locale locale, NounClause noun, PronounCase pronounCase) {
        String result = "";
        if (noun.getOwner() != null) {
            result += toText(locale, noun.getOwner(), PronounCase.POSSESSIVE_ADJ) + " ";
        }
        if (noun.getName() != null) {
            result += noun.getName();
            if (PronounCase.POSSESSIVE_ADJ == pronounCase) {
                result += "'s";
            }
        } else if (noun.getPronoun() != null) {
            result += pronounMapper.getPronounLabel(Locale.US, noun.getPronoun(), pronounCase).get();
        } else if (noun.getHref() != null) {
            // FIXME: yago entity to text
            result += noun.getHref();
            if (PronounCase.POSSESSIVE_ADJ == pronounCase) {
                result += "'s";
            }
        } else {
            throw new ReasonerException("Invalid noun: " + noun);
        }
        return result;
    }

    public String toText(Locale locale, Adjective adj) {
        String result = "";
        if (adj.getHref() != null) {
            // FIXME: yago entity to text
            result += adj.getHref();
        } else {
            throw new ReasonerException("Invalid adjective: " + adj);
        }
        return result;
    }

    public String toText(Locale locale, Verb verb, PronounPerson person, PronounNumber number) {
        String result = "";
        if (verb.getHref() != null) {
            // FIXME: yago entity to text
            result += verb.getHref();
            if (PronounPerson.THIRD == person && PronounNumber.SINGULAR == number) {
                result += "s";
            }
        } else {
            throw new ReasonerException("Invalid verb: " + verb);
        }
        return result;
    }

    public String makeSentence(List<String> clauses, SentenceMood mood) {
        String sentence = clauses.stream().collect(Collectors.joining(", "));
        sentence = StringUtils.capitalize(sentence);
        switch (mood) {
            case STATEMENT: sentence += "."; break;
            case EXCLAMATION: sentence += "!"; break;
            case QUESTION: sentence += "?"; break;
            case DANGLING: sentence += "..."; break;
            case CONFUSED: sentence += "?!?!?!"; break;
            case HYPERBOLIC: sentence += "!!!!!!"; break;
            default:
                throw new ReasonerException("Unknown sentence mood: " + mood);
        }
        return sentence;
    }

}
